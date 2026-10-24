package com.evgurov.expenses.domain.bank

import com.evgurov.expenses.domain.model.TransactionKind
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Строка выписки: операция и всё, что нужно, чтобы не добавить её дважды. */
data class StatementRow(
    val date: LocalDate,
    /** Время операции, если банк его указал: отличает две одинаковые покупки за день. */
    val time: String?,
    val operation: BankOperation,
    /** Описание как в выписке — подпись операции в приложении. */
    val description: String,
)

data class Statement(val bank: Bank, val rows: List<StatementRow>)

/**
 * Выписка по карте Т-Банка из PDF: «17.09.26 06:12 18.09.26 Оплата в VKUSVILL Moskva RUS 1 425.00 ₽ 1 425.00 ₽».
 * «Справку о движении средств» не читаем: в ней все счета сразу, и траты задваиваются.
 * Переводы между своими счетами («Внутренний перевод на договор», «Перевод с договора») пропускаем.
 */
object StatementParser {
    val TBANK = Bank("statement.tbank", "Т-Банк")

    private val shortDate = DateTimeFormatter.ofPattern("dd.MM.yy")
    private const val AMOUNT = """(\+ ?)?(\d{1,3}(?: \d{3})*[.,]\d{2}) ?₽"""

    /** Выписка по договору: сумма в конце строки, поступления со знаком «+». */
    private val contractRow = Regex("""^(\d{2}\.\d{2}\.\d{2})(?: (\d{2}:\d{2}))? \d{2}\.\d{2}\.\d{2} (.+?) $AMOUNT $AMOUNT$""")

    private val stopWords = listOf(
        "дата и время", "операции обработки", "операции списания", "описание", "операции по карте", "расходы:",
        "поступления:", "пополнения:", "баланс на", "выписка по", "страница", "итого", "ао «тбанк»", "бик ",
        "с уважением", "руководитель",
    )

    private class Draft(val date: LocalDate, val time: String?, val income: Boolean, val amount: String, val main: String) {
        val extra = mutableListOf<String>()
    }

    /** Null — если текст не похож на выписку, которую мы умеем читать. */
    fun parse(text: String): Statement? {
        val lines = text.lines().map { normalize(it) }
        val known = lines.any {
            it.startsWith("Выписка по договору") || it.contains("Выписка по счету") || it.contains("Выписка по счёту")
        }
        if (!known) return null
        val rows = mutableListOf<StatementRow>()
        var current: Draft? = null

        fun flush() {
            current?.let { toRow(it)?.let(rows::add) }
            current = null
        }

        for (line in lines) {
            val contract = contractRow.find(line)
            val draft = current
            when {
                contract != null -> {
                    flush()
                    val (date, time, description, sign, amount) = contract.destructured
                    current = Draft(LocalDate.parse(date, shortDate), time.ifEmpty { null }, sign.isNotBlank(), amount, description)
                }
                draft != null && line.isNotEmpty() && draft.extra.size < 3 && "₽" !in line &&
                    stopWords.none { line.lowercase().startsWith(it) } && !line.all(Char::isDigit) -> draft.extra += line
                line.isEmpty() -> Unit
                else -> flush()
            }
        }
        flush()
        return if (rows.isEmpty()) null else Statement(TBANK, rows)
    }

    private fun toRow(draft: Draft): StatementRow? {
        val kopecks = draft.amount.replace(" ", "").replace(',', '.').toBigDecimal().movePointRight(2).toLong()
        if (kopecks <= 0) return null
        val kind = if (draft.income) TransactionKind.INCOME else TransactionKind.EXPENSE
        val description = draft.main
        // Продолжение описания: у покупки это город, у перевода — номер телефона или название организации.
        val continuation = draft.extra.joinToString(" ")
        val full = listOf(description, continuation).filter { it.isNotBlank() }.joinToString(" ")
        if (BankNotificationParser.isOwnTransfer(full)) return null
        val type = BankNotificationParser.typeOf(full.lowercase())
        val merchant = merchantOf(if (continuation.any(Char::isDigit)) description else full)
        val note = when {
            merchant != null -> merchant
            continuation.any(Char::isLetter) && continuation.count(Char::isDigit) < 6 -> "$description $continuation"
            else -> description
        }
        return StatementRow(
            date = draft.date,
            time = draft.time,
            operation = BankOperation(TBANK, kind, kopecks, merchant ?: description, type),
            description = note,
        )
    }

    private val purchasePrefix = Regex("""^(Оплата в|Оплата услуг|Покупка в|Покупка)\s+""", RegexOption.IGNORE_CASE)
    private val location = setOf(
        "rus", "ru", "g", "g.", "moskva", "moscow", "samara", "sankt-pete", "sankt-peterbu", "sankt-", "sankt",
        "spb", "kazan", "ekaterinburg", "novosibirsk", "minsk", "blr", "russia", "самара",
    )

    /** Название магазина для покупок: без «Оплата в», кавычек, кодов терминала и города. */
    fun merchantOf(description: String): String? {
        val match = purchasePrefix.find(description) ?: return null
        val words = description.substring(match.range.last + 1)
            .replace(Regex("""^(mBank|iBank)\."""), "")
            .replace(Regex("""^(YM|PG|WBPT|SBER(\*\d+)?)\*"""), "")
            .replace("\"", "")
            .split(' ')
            .filter { it.isNotBlank() }
            .toMutableList()
        while (words.size > 1 && words.last().lowercase() in location) words.removeAt(words.lastIndex)
        return words.joinToString(" ").ifBlank { null }
    }

    private fun normalize(line: String): String =
        line.replace(' ', ' ').replace(' ', ' ').replace('−', '-').replace(Regex("""\s+"""), " ").trim()
}
