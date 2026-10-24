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
 * Выписка по карте из PDF. Сейчас понимает формат Т-Банка:
 * «17.09.26 06:12 18.09.26 Оплата в VKUSVILL Moskva RUS 1 425.00 ₽ 1 425.00 ₽»,
 * описание может продолжаться на следующих строках; поступления помечены «+».
 */
object StatementParser {
    val TBANK = Bank("statement.tbank", "Т-Банк")

    private val dateFormat = DateTimeFormatter.ofPattern("dd.MM.yy")
    private const val AMOUNT = """(\+ ?)?(\d{1,3}(?: \d{3})*[.,]\d{2}) ?₽"""
    private val row = Regex("""^(\d{2}\.\d{2}\.\d{2})(?: (\d{2}:\d{2}))? \d{2}\.\d{2}\.\d{2} (.+?) $AMOUNT $AMOUNT$""")
    private val stopWords = listOf("дата и время", "операции обработки", "описание", "операции по карте", "расходы:", "поступления:", "баланс на", "выписка по", "страница", "итого")

    /** Null — если текст не похож на выписку, которую мы умеем читать. */
    fun parse(text: String): Statement? {
        val lines = text.lines().map { normalize(it) }
        if (lines.none { it.startsWith("Выписка по договору") || it.contains("Выписка по счету") || it.contains("Выписка по счёту") }) return null
        val rows = mutableListOf<StatementRow>()
        var current: MatchResult? = null
        val extra = mutableListOf<String>()

        fun flush() {
            val match = current ?: return
            toRow(match, extra)?.let(rows::add)
            current = null
            extra.clear()
        }

        for (line in lines) {
            val match = row.find(line)
            when {
                match != null -> {
                    flush()
                    current = match
                }
                current != null && line.isNotEmpty() && extra.size < 3 && "₽" !in line &&
                    stopWords.none { line.lowercase().startsWith(it) } -> extra += line
                line.isEmpty() -> Unit
                else -> flush()
            }
        }
        flush()
        return if (rows.isEmpty()) null else Statement(TBANK, rows)
    }

    private fun toRow(match: MatchResult, extra: List<String>): StatementRow? {
        val (date, time, description, sign, amount) = match.destructured
        val kopecks = amount.replace(" ", "").replace(',', '.').toBigDecimal().movePointRight(2).toLong()
        if (kopecks <= 0) return null
        val kind = if (sign.isNotBlank()) TransactionKind.INCOME else TransactionKind.EXPENSE
        // Продолжение описания: у покупки это город, у перевода — номер телефона или название организации.
        val continuation = extra.joinToString(" ")
        val full = listOf(description, continuation).filter { it.isNotBlank() }.joinToString(" ")
        val type = BankNotificationParser.typeOf(full.lowercase())
        val merchant = merchantOf(description)
        val note = when {
            merchant != null -> merchant
            continuation.any(Char::isLetter) && continuation.count(Char::isDigit) < 6 -> "$description $continuation"
            else -> description
        }
        return StatementRow(
            date = LocalDate.parse(date, dateFormat),
            time = time.ifEmpty { null },
            operation = BankOperation(TBANK, kind, kopecks, merchant ?: description, type),
            description = note,
        )
    }

    private val purchasePrefix = Regex("""^(Оплата в|Оплата услуг|Покупка в|Покупка)\s+""", RegexOption.IGNORE_CASE)
    private val location = setOf(
        "rus", "ru", "g", "g.", "moskva", "moscow", "samara", "sankt-pete", "sankt-peterbu", "sankt-", "sankt",
        "spb", "kazan", "ekaterinburg", "novosibirsk",
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
