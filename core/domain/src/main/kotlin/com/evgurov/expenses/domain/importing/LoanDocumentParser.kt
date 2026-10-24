package com.evgurov.expenses.domain.importing

import com.evgurov.expenses.domain.model.Kopecks
import com.evgurov.expenses.domain.model.Loan
import com.evgurov.expenses.domain.model.LoanSource
import com.evgurov.expenses.domain.model.LoanType
import com.evgurov.expenses.domain.model.ScheduleItem
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/** Строка графика из документа банка. Досрочные платежи идут без номера. */
data class ParsedPayment(
    val number: Int?,
    val date: LocalDate,
    val payment: Kopecks,
    val principalPart: Kopecks?,
    val interestPart: Kopecks?,
    val balanceAfter: Kopecks?,
) {
    val isEarly: Boolean get() = number == null
}

/** Что удалось достать из текста документа. Любое поле может отсутствовать — его заполнит пользователь. */
data class ParsedLoan(
    val bank: String? = null,
    val contractNumber: String? = null,
    val contractDate: LocalDate? = null,
    val principal: Kopecks? = null,
    val annualRate: Double? = null,
    val rateIsEstimated: Boolean = false,
    val regularPayment: Kopecks? = null,
    val termMonths: Int? = null,
    val currentDebt: Kopecks? = null,
    val creditLimit: Kopecks? = null,
    val payments: List<ParsedPayment> = emptyList(),
) {
    val isCard: Boolean get() = creditLimit != null && payments.isEmpty()

    val schedule: List<ScheduleItem>
        get() = payments.filter { !it.isEarly }.map {
            ScheduleItem(
                number = it.number!!,
                dueDate = it.date,
                payment = it.payment,
                principalPart = it.principalPart ?: 0,
                interestPart = it.interestPart ?: 0,
                balanceAfter = it.balanceAfter ?: 0,
            )
        }

    val earlyPayments: List<ParsedPayment> get() = payments.filter { it.isEarly }

    /** Найдено хоть что-то полезное для кредита. */
    val isEmpty: Boolean
        get() = principal == null && regularPayment == null && currentDebt == null && creditLimit == null && payments.isEmpty()

    fun toLoan(source: LoanSource): Loan {
        val bankName = bank.orEmpty()
        val schedule = schedule.takeIf { it.size >= 2 && it.all { item -> item.balanceAfter >= 0 } }.orEmpty()
        if (isCard) {
            return Loan(
                type = LoanType.CREDIT_CARD,
                name = listOf("Кредитная карта", bankName).filter { it.isNotBlank() }.joinToString(" "),
                bank = bankName,
                annualRate = annualRate ?: 0.0,
                creditLimit = creditLimit,
                currentBalance = currentDebt,
                source = source,
                note = contractNumber?.let { "Договор № $it" }.orEmpty(),
            )
        }
        return Loan(
            type = LoanType.CONSUMER,
            name = listOf("Кредит", bankName).filter { it.isNotBlank() }.joinToString(" "),
            bank = bankName,
            principal = principal ?: 0,
            annualRate = annualRate ?: 0.0,
            termMonths = termMonths ?: schedule.size,
            issueDate = contractDate ?: schedule.firstOrNull()?.dueDate?.minusMonths(1) ?: LocalDate.now(),
            monthlyPayment = regularPayment,
            currentBalance = currentDebt.takeIf { schedule.isEmpty() },
            source = source,
            note = contractNumber?.let { "Договор № $it" }.orEmpty(),
            bankSchedule = schedule,
        )
    }
}

/**
 * Разбирает текст графика платежей или договора, полученный из PDF или OCR.
 * Работает по структуре строк: дата и следующие за ней суммы, поэтому не зависит от конкретного банка.
 */
object LoanDocumentParser {
    private val dateRegex = Regex("""(?<!\d)(\d{2})[./](\d{2})[./](\d{4})(?!\d)""")

    /** «70 600.00», «1 867 455,17», «1867455.17». Пробелы могут быть неразрывными. */
    private val amountRegex = Regex("""(?<![\d.,])\d{1,3}(?:[  ]\d{3})+[.,]\d{2}(?!\d)|(?<![\d.,])\d+[.,]\d{2}(?!\d)""")

    private val dateFormat = DateTimeFormatter.ofPattern("dd.MM.yyyy")

    private val banks = listOf(
        "ТБанк" to listOf("тбанк", "т-банк", "тинькофф", "tbank", "tinkoff"),
        "Сбербанк" to listOf("сбербанк", "сбер ", "пао сбер"),
        "ВТБ" to listOf("втб", "vtb"),
        "Альфа-Банк" to listOf("альфа-банк", "альфа банк", "alfa"),
        "Газпромбанк" to listOf("газпромбанк"),
        "Совкомбанк" to listOf("совкомбанк"),
        "Райффайзенбанк" to listOf("райффайзен"),
        "Почта Банк" to listOf("почта банк"),
        "ОТП Банк" to listOf("отп банк", "otp"),
        "Хоум Банк" to listOf("хоум", "home credit"),
        "МТС Банк" to listOf("мтс банк"),
        "Росбанк" to listOf("росбанк"),
        "Ренессанс Кредит" to listOf("ренессанс"),
        "Уралсиб" to listOf("уралсиб"),
        "Ozon Банк" to listOf("озон банк", "ozon"),
        "Яндекс Банк" to listOf("яндекс банк"),
    )

    fun parse(text: String): ParsedLoan {
        val normalized = text.replace(' ', ' ').replace(' ', ' ')
        val payments = normalized.lines().mapNotNull(::parsePaymentLine).distinct()
        val numbered = payments.filter { !it.isEarly }
        val schedule = numbered.map { it.payment }
        val regular = labeledAmount(normalized, "(?:ежемесячн\\w*|регулярн\\w*)\\s+плат[её]ж\\w*")
            ?: schedule.filter { it > 0 }.let { list ->
                list.groupingBy { it }.eachCount().maxWithOrNull(compareBy({ it.value }, { it.key }))?.key
            }
        val principal = labeledAmount(normalized, "сумм\\w*\\s+(?:кредита|займа)")
            ?: payments.firstOrNull()?.let { p -> p.balanceAfter?.plus(p.principalPart ?: 0) }
        val statedRate = Regex("""(?iU)(\d{1,3}(?:[.,]\d{1,3})?)\s*%\s*годовых""")
            .find(normalized)?.groupValues?.get(1)?.replace(',', '.')?.toDoubleOrNull()
            ?: Regex("""(?iU)ставк\w*[^\d\n]{0,40}(\d{1,3}(?:[.,]\d{1,3})?)\s*%""")
                .find(normalized)?.groupValues?.get(1)?.replace(',', '.')?.toDoubleOrNull()
        val estimatedRate = if (statedRate == null) estimateRate(payments) else null
        return ParsedLoan(
            bank = detectBank(normalized),
            contractNumber = Regex("""(?iU)(?:номер\s+договора|договор\w*[^\n№]{0,60}№|№\s*договора)\s*:?\s*([\wА-Яа-я/-]*\d[\wА-Яа-я/-]*)""")
                .find(normalized)?.groupValues?.get(1),
            contractDate = Regex("""(?iU)(?:дата\s+(?:заключения|оформления)\s+договора|договор\w*[^\n]{0,80}?\sот)\s*:?\s*(\d{2}\.\d{2}\.\d{4})""")
                .find(normalized)?.groupValues?.get(1)?.let(::parseDate),
            principal = principal,
            annualRate = statedRate ?: estimatedRate,
            rateIsEstimated = statedRate == null && estimatedRate != null,
            regularPayment = regular,
            termMonths = numbered.mapNotNull { it.number }.maxOrNull()
                ?: Regex("""(?iU)срок\w*[^\d\n]{0,30}(\d{1,3})\s*(?:мес|month)""")
                    .find(normalized)?.groupValues?.get(1)?.toIntOrNull(),
            currentDebt = labeledAmount(
                normalized,
                "(?:остаток\\s+(?:основного\\s+)?(?:долга|задолженности)|осталось\\s+(?:выплатить|погасить)|текущ\\w*\\s+задолженност\\w*)",
            ),
            creditLimit = labeledAmount(normalized, "(?:кредитн\\w*\\s+)?лимит\\w*"),
            payments = payments,
        )
    }

    private fun parsePaymentLine(line: String): ParsedPayment? {
        val date = dateRegex.find(line) ?: return null
        val head = line.substring(0, date.range.first).trim()
        val tail = line.substring(date.range.last + 1)
        val amounts = amountRegex.findAll(tail).map { parseAmount(it.value) }.toList()
        if (amounts.size < 2) return null
        val parsedDate = parseDate(date.value) ?: return null
        val number = Regex("""^(\d{1,3})$""").find(head)?.groupValues?.get(1)?.toInt()
        val early = head.contains("осроч", ignoreCase = true)
        if (number == null && !early) return null
        return ParsedPayment(
            number = if (early) null else number,
            date = parsedDate,
            payment = amounts.first(),
            principalPart = amounts.getOrNull(1)?.takeIf { amounts.size >= 4 },
            interestPart = amounts.getOrNull(2)?.takeIf { amounts.size >= 4 },
            balanceAfter = amounts.last().takeIf { amounts.size >= 3 },
        )
    }

    /** Ставка по графику: проценты за период / (остаток × дней / 365), медиана по обычным платежам. */
    private fun estimateRate(payments: List<ParsedPayment>): Double? {
        val rates = payments.zipWithNext().mapNotNull { (prev, cur) ->
            val balance = prev.balanceAfter ?: return@mapNotNull null
            val interest = cur.interestPart ?: return@mapNotNull null
            val principalPart = cur.principalPart ?: return@mapNotNull null
            val days = ChronoUnit.DAYS.between(prev.date, cur.date)
            if (cur.isEarly || prev.isEarly || principalPart <= 0 || interest <= 0 || balance <= 0 || days !in 25..35) {
                return@mapNotNull null
            }
            interest.toDouble() / (balance.toDouble() * days / 365.0) * 100
        }.sorted()
        if (rates.size < 3) return null
        val median = rates[rates.size / 2]
        return Math.round(median * 10) / 10.0
    }

    private fun labeledAmount(text: String, label: String): Kopecks? =
        Regex("""(?iU)$label[^\n]{0,60}?(?<![\d.,])(\d{1,3}(?: \d{3})+(?:[.,]\d{2})?|\d+[.,]\d{2})(?![\d.,])""")
            .find(text)?.groupValues?.get(1)?.let(::parseAmount)

    private fun parseAmount(value: String): Kopecks {
        val clean = value.replace(" ", "").replace(',', '.')
        return if ('.' in clean) {
            val (rub, kop) = clean.split('.')
            rub.toLong() * 100 + kop.padEnd(2, '0').take(2).toLong()
        } else {
            clean.toLong() * 100
        }
    }

    private fun parseDate(value: String): LocalDate? =
        runCatching { LocalDate.parse(value.replace('/', '.'), dateFormat) }.getOrNull()

    private fun detectBank(text: String): String? {
        val lower = text.lowercase()
        return banks.firstOrNull { (_, keys) -> keys.any { it in lower } }?.first
    }
}
