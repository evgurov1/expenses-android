package com.evgurov.expenses.domain.bank

import com.evgurov.expenses.domain.model.Kopecks
import com.evgurov.expenses.domain.model.TransactionKind

/** Банк, чьи уведомления мы читаем: пакет приложения и название счёта в «Расходах». */
data class Bank(val packageName: String, val accountName: String)

/** Операция, найденная в уведомлении банка. */
data class BankOperation(
    val bank: Bank,
    val kind: TransactionKind,
    val amount: Kopecks,
    /** Магазин или отправитель — как написал банк, для категории и подписи. */
    val merchant: String,
    val type: OperationType,
)

enum class OperationType { PURCHASE, TRANSFER, SALARY, CASHBACK, LOAN_PAYMENT, FEE, CASH, OTHER }

/**
 * Разбор пуш-уведомлений банков. Тексты у банков разные и меняются,
 * поэтому ищем общие признаки: слово операции, сумму в рублях, название магазина.
 * Всё, что не похоже на операцию (реклама, коды входа), пропускаем.
 */
object BankNotificationParser {
    val banks = listOf(
        Bank("com.idamob.tinkoff.android", "Т-Банк"),
        Bank("ru.sberbankmobile", "Сбер"),
        Bank("ru.vtb24.mobilebanking.android", "ВТБ"),
        Bank("ru.alfabank.mobile.android", "Альфа-Банк"),
        Bank("ru.ozon.fintech.finance", "Озон Банк"),
        Bank("com.yandex.bank", "Яндекс Банк"),
        Bank("ru.raiffeisennews", "Райффайзен"),
        Bank("ru.gazprombank.android.mobilebank.app", "Газпромбанк"),
        Bank("ru.psbank.online", "ПСБ"),
        Bank("ru.sovcomcard.halva.v1", "Халва"),
        Bank("com.openbank", "Открытие"),
        Bank("ru.pochta.bank", "Почта Банк"),
        Bank("ru.mts.money", "МТС Банк"),
    )

    fun bankFor(packageName: String): Bank? = banks.firstOrNull { it.packageName == packageName }

    private val money = Regex(
        """([+\-−–]?)\s?(\d{1,3}(?:[\s  ]\d{3})+|\d+)(?:[.,](\d{1,2}))?\s?(₽|р\.?(?!\p{L})|руб\.?|rub|rur)""",
        RegexOption.IGNORE_CASE,
    )

    /** Слова перед суммой, после которых идёт не операция, а остаток. */
    private val balanceWords = Regex("""(баланс|доступно|остаток|лимит|на сч[её]те|итого)[^.\d]{0,12}$""", RegexOption.IGNORE_CASE)

    private val skipWords = listOf(
        "код", "пароль", "войти", "вход в", "одобрен", "предлагаем", "акци", "скидк", "розыгрыш",
        "не прошл", "отклонен", "отказ", "недостаточно", "напоминаем", "не забудьте", "будет списан", "спишется",
    )

    private val incomeWords = listOf(
        "зачислен", "поступлен", "пополнен", "входящий перевод", "перевод от", "зарплат", "аванс",
        "возврат", "кэшбэк", "кешбэк", "cashback", "начислен", "получен",
    )
    private val expenseWords = listOf(
        "покупк", "оплат", "списан", "снятие", "перевод", "платёж", "платеж", "погашени", "отправлен",
    )

    private val ownTransferWords = listOf(
        "внутренний перевод на договор", "перевод с договора", "между своими", "между счетами", "копилк",
        "на свой счет", "на свой счёт", "себе в другой банк", "со своего счета", "со своего счёта",
    )

    /** Слова перед остатком на счёте. Лимит и долг по кредитке — не свои деньги, их не берём. */
    private val ownBalanceWords = Regex("""(баланс|доступно|остаток|на сч[её]те)[^.\d]{0,12}$""", RegexOption.IGNORE_CASE)
    private val notOwnMoney = Regex("""лимит|задолж|кредит|долг""", RegexOption.IGNORE_CASE)

    /** Остаток на счёте после операции: «Доступно 12 345 ₽», «Баланс: 5 432.10р». Null — если банк его не написал. */
    fun balance(title: String, text: String): Kopecks? {
        val full = "$title. $text".replace('\n', ' ').trim()
        val match = money.findAll(full).firstOrNull { m ->
            val before = full.substring(0, m.range.first).takeLast(30)
            ownBalanceWords.containsMatchIn(before) && !notOwnMoney.containsMatchIn(before)
        } ?: return null
        val amount = kopecks(match) ?: return null
        return if (match.groupValues[1].isNotEmpty() && match.groupValues[1] != "+") -amount else amount
    }

    private fun kopecks(match: MatchResult): Kopecks? {
        val rubles = match.groupValues[2].filter(Char::isDigit).toLongOrNull() ?: return null
        return rubles * 100 + match.groupValues[3].padEnd(2, '0').toLong()
    }

    /** Перевод между своими счетами: это не трата и не доход, такие операции не учитываем. */
    fun isOwnTransfer(text: String): Boolean = text.lowercase().let { lower -> ownTransferWords.any { it in lower } }

    fun parse(packageName: String, title: String, text: String): BankOperation? {
        val bank = bankFor(packageName) ?: return null
        val full = "$title. $text".replace('\n', ' ').trim()
        val lower = full.lowercase()
        if (skipWords.any { it in lower } || isOwnTransfer(lower)) return null

        val match = money.findAll(full).firstOrNull { m -> !balanceWords.containsMatchIn(full.substring(0, m.range.first)) } ?: return null
        val amount = kopecks(match) ?: return null
        if (amount <= 0) return null

        val sign = match.groupValues[1]
        val income = when {
            sign == "+" -> true
            sign.isNotEmpty() -> false
            incomeWords.any { it in lower } && !lower.contains("перевод на") -> true
            expenseWords.any { it in lower } -> false
            else -> return null
        }
        val type = typeOf(lower)
        return BankOperation(
            bank = bank,
            kind = if (income) TransactionKind.INCOME else TransactionKind.EXPENSE,
            amount = amount,
            merchant = merchant(full, match.range.last + 1),
            type = type,
        )
    }

    /** Тип операции по словам в тексте (текст в нижнем регистре). */
    fun typeOf(lower: String): OperationType = when {
        "зарплат" in lower || "аванс" in lower || "от организации" in lower -> OperationType.SALARY
        "кэшбэк" in lower || "кешбэк" in lower || "cashback" in lower || "возврат" in lower -> OperationType.CASHBACK
        "комисси" in lower -> OperationType.FEE
        "снятие наличных" in lower -> OperationType.CASH
        "досрочное погашение" in lower || "оплата долями" in lower || "dolyame" in lower || "yandex split" in lower ->
            OperationType.LOAN_PAYMENT
        "кредит" in lower && ("погашени" in lower || "платеж" in lower || "платёж" in lower || "списан" in lower) -> OperationType.LOAN_PAYMENT
        "перевод" in lower || "пополнен" in lower || "сбп" in lower -> OperationType.TRANSFER
        "покупк" in lower || "оплат" in lower -> OperationType.PURCHASE
        else -> OperationType.OTHER
    }

    /** Магазин обычно идёт сразу после суммы: «450 ₽. PYATEROCHKA 1234. Доступно…» или «… в Пятёрочке». */
    private fun merchant(text: String, afterAmount: Int): String {
        val service = Regex("""^(карта|карты|счет|счёт|mir|visa|mastercard|баланс|доступно|остаток|в\s\d|\*)""", RegexOption.IGNORE_CASE)
        val tail = text.substring(afterAmount)
            .split(Regex("""[.;]\s+|\n"""))
            .map { it.substringBefore(" Баланс").substringBefore(" Доступно").substringBefore(" Остаток").trim(' ', '.', ',', ':', '«', '»', '"', '\u00A0') }
            .map { it.removePrefix("в ").removePrefix("на ").removePrefix("от ").trim() }
            .firstOrNull { it.isNotBlank() && it.any(Char::isLetter) && !service.containsMatchIn(it) && it.length <= 60 }
        if (tail != null) return tail
        // «Покупка в Пятёрочке на 450 ₽» — магазин до суммы.
        val before = Regex("""\s(?:в|у)\s([^.,]{2,40}?)\s(?:на\s)?[+\-−–]?\d""").find(text)?.groupValues?.get(1)
        return before?.trim().orEmpty()
    }
}
