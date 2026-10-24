package com.evgurov.expenses.domain

import com.evgurov.expenses.domain.importing.LoanDocumentParser
import com.evgurov.expenses.domain.model.Loan
import com.evgurov.expenses.domain.model.LoanSource
import com.evgurov.expenses.domain.model.LoanType
import com.evgurov.expenses.domain.usecase.LoanMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/**
 * Тексты собраны по образцу графика платежей ТБанка (как его отдаёт pdftotext / PdfBox / OCR),
 * но все данные вымышленные: ФИО, договор, суммы.
 */
class LoanDocumentParserTest {
    private val fmt = DateTimeFormatter.ofPattern("dd.MM.yyyy")

    /** Вымышленный кредит: 500 000 ₽ под 24% на 24 месяца. */
    private val source = Loan(
        type = LoanType.CONSUMER,
        name = "",
        principal = 500_000_00,
        annualRate = 24.0,
        termMonths = 24,
        issueDate = LocalDate.of(2026, 3, 15),
    )
    private val schedule = LoanMath.schedule(source)

    private fun money(kop: Long, sep: String = " ", dec: String = "."): String {
        val rub = (kop / 100).toString().reversed().chunked(3).joinToString(sep).reversed()
        return "$rub$dec%02d".format(kop % 100)
    }

    private fun tbankLayout(): String = buildString {
        appendLine("                     АКЦИОНЕРНОЕ ОБЩЕСТВО «ТБАНК»")
        appendLine("Информационный график регулярных платежей")
        appendLine("№ 1a2b3c4d                                                      24.10.2026")
        appendLine("Иванов Иван Иванович")
        appendLine("Номер договора: 0100000001")
        appendLine("Продукт: Кредит")
        appendLine("Дата заключения договора: 15.03.2026 г.")
        appendLine("Сумма кредита: 500 000.00 (₽)")
        appendLine("График платежей")
        appendLine("                       Дата       Регулярный       Платеж по     Начисленные                      Кредитные        Остаток")
        appendLine(" Номер платежа                                                               Страховка")
        appendLine("                     платежа        платеж          кредиту       проценты                         каникулы     задолженности")
        appendLine("Досрочный платеж 20.03.2026        10 000.00 ₽     9 000.00 ₽     1 000.00 ₽       0.00 ₽          0.00 ₽      491 000.00 ₽")
        schedule.forEach { item ->
            append("        ${item.number}           ${item.dueDate.format(fmt)}     ${money(item.payment)} ₽     ")
            append("${money(item.principalPart)} ₽     ${money(item.interestPart)} ₽       0.00 ₽          0.00 ₽      ")
            appendLine("${money(item.balanceAfter)} ₽")
        }
        appendLine("АО «ТБанк» универсальная лицензия Банка России № 2673, к/с 30101810145250000974")
        appendLine("БИК 044525974 ИНН 7710140679 КПП 77130100")
    }

    @Test
    fun `parses tbank schedule layout`() {
        val parsed = LoanDocumentParser.parse(tbankLayout())
        assertEquals("ТБанк", parsed.bank)
        assertEquals("0100000001", parsed.contractNumber)
        assertEquals(LocalDate.of(2026, 3, 15), parsed.contractDate)
        assertEquals(500_000_00L, parsed.principal)
        assertEquals(24, parsed.termMonths)
        assertEquals(schedule.first().payment, parsed.regularPayment)
        assertEquals(24, parsed.schedule.size)
        assertEquals(1, parsed.earlyPayments.size)
        assertEquals(schedule, parsed.schedule)
        assertTrue(parsed.rateIsEstimated)
        assertEquals(24.0, parsed.annualRate!!, 0.6)
    }

    @Test
    fun `parses collapsed whitespace like PdfBox output`() {
        val text = tbankLayout().lines().joinToString("\n") { it.trim().replace(Regex(" {2,}"), " ") }
        val parsed = LoanDocumentParser.parse(text)
        assertEquals(schedule, parsed.schedule)
        assertEquals(500_000_00L, parsed.principal)
    }

    @Test
    fun `parses OCR-like text without currency signs and with commas`() {
        val text = buildString {
            appendLine("График платежей")
            schedule.forEach { item ->
                appendLine(
                    "${item.number} ${item.dueDate.format(fmt)} ${money(item.payment, dec = ",")} " +
                        "${money(item.principalPart, dec = ",")} ${money(item.interestPart, dec = ",")} 0,00 0,00 " +
                        money(item.balanceAfter, dec = ","),
                )
            }
        }
        val parsed = LoanDocumentParser.parse(text)
        assertEquals(schedule, parsed.schedule)
        assertEquals(24, parsed.termMonths)
        assertNull(parsed.bank)
        assertEquals(500_000_00L, parsed.principal)
    }

    @Test
    fun `reads stated rate, payment and term from contract text`() {
        val text = """
            ПАО Сбербанк
            Индивидуальные условия договора потребительского кредита № 92-123456 от 01.02.2026
            Сумма кредита 300 000,00 рублей
            Срок возврата кредита 36 мес.
            Процентная ставка 19,9 % годовых
            Ежемесячный платеж: 11 135,52 руб. не позднее 1 числа месяца
        """.trimIndent()
        val parsed = LoanDocumentParser.parse(text)
        assertEquals("Сбербанк", parsed.bank)
        assertEquals("92-123456", parsed.contractNumber)
        assertEquals(LocalDate.of(2026, 2, 1), parsed.contractDate)
        assertEquals(300_000_00L, parsed.principal)
        assertEquals(19.9, parsed.annualRate!!, 0.001)
        assertTrue(!parsed.rateIsEstimated)
        assertEquals(11_135_52L, parsed.regularPayment)
        assertEquals(36, parsed.termMonths)
    }

    @Test
    fun `payment label does not grab a date`() {
        val parsed = LoanDocumentParser.parse("Ежемесячный платеж до 27.07.2026: 70 600 ₽")
        assertEquals(70_600_00L, parsed.regularPayment)
    }

    @Test
    fun `imported loan uses bank schedule for balance and burden`() {
        val loan = LoanDocumentParser.parse(tbankLayout()).toLoan(LoanSource.CONTRACT)
        assertEquals("ТБанк", loan.bank)
        assertEquals(24, loan.bankSchedule.size)
        assertEquals(schedule.first().payment, LoanMath.regularPayment(loan))
        val afterThird = schedule[2]
        assertEquals(afterThird.balanceAfter, LoanMath.balanceOn(loan, afterThird.dueDate))
        assertEquals(schedule.last().dueDate, LoanMath.endDate(loan))
        assertNotNull(LoanMath.paymentInMonth(loan, YearMonth.from(schedule[5].dueDate)))
    }

    @Test
    fun `garbage gives empty result`() {
        assertTrue(LoanDocumentParser.parse("Привет, это не кредит").isEmpty)
    }

    @Test
    fun `app screenshot without schedule gives payment and debt`() {
        val text = """
            Кредит наличными
            Т-Банк
            Ежемесячный платеж 12 500 ₽
            Осталось выплатить 245 300,50 ₽
            Ставка 21,9%
        """.trimIndent()
        val parsed = LoanDocumentParser.parse(text)
        assertEquals("ТБанк", parsed.bank)
        assertEquals(12_500_00L, parsed.regularPayment)
        assertEquals(245_300_50L, parsed.currentDebt)
        assertEquals(21.9, parsed.annualRate!!, 0.001)
        val loan = parsed.toLoan(LoanSource.PHOTO)
        assertEquals(LoanType.CONSUMER, loan.type)
        assertEquals(245_300_50L, loan.currentBalance)
    }

    @Test
    fun `tbank app loan screen from OCR`() {
        // Вывод Tesseract (rus, fast) по скриншоту экрана кредита в приложении Т-Банка.
        val text = """
            11:30 ${'$'} 4${'$'} © 100)

            < Кредит наличными

            Осталось выплатить

            197 884,71Р

            Ещё 58 платежей График >
            =:

            2 ноября спишем с основного счёта

            7844Р

            7; Пополните счёт заранее — автоматически спишем
            платёж до 20:00 мск

            Баланс основного счёта 94,35 Р

            © Погасить кредит
        """.trimIndent()
        val parsed = LoanDocumentParser.parse(text, today = LocalDate.of(2026, 10, 24))
        assertEquals(197_884_71L, parsed.currentDebt)
        assertEquals(7_844_00L, parsed.regularPayment)
        assertEquals(58, parsed.remainingPayments)
        assertEquals(LocalDate.of(2026, 11, 2), parsed.nextPaymentDate)
        assertTrue(parsed.rateIsEstimated)
        assertEquals(41.0, parsed.annualRate!!, 3.0)

        val loan = parsed.toLoan(LoanSource.PHOTO)
        assertEquals(197_884_71L, loan.principal)
        assertEquals(58, loan.termMonths)
        assertEquals(LocalDate.of(2026, 10, 2), loan.issueDate)
        assertEquals(LocalDate.of(2031, 8, 2), LoanMath.endDate(loan))
        assertEquals(7_844_00L, LoanMath.paymentInMonth(loan, YearMonth.of(2026, 11)))
    }

    @Test
    fun `next payment date in january rolls to next year`() {
        val parsed = LoanDocumentParser.parse("15 января спишем\n12 000 ₽", today = LocalDate.of(2026, 12, 20))
        assertEquals(LocalDate.of(2027, 1, 15), parsed.nextPaymentDate)
        assertEquals(12_000_00L, parsed.regularPayment)
    }

    @Test
    fun `credit card screenshot becomes a card`() {
        val parsed = LoanDocumentParser.parse("Альфа-Банк\nКредитный лимит 150 000 ₽\nТекущая задолженность 32 000 ₽")
        assertTrue(parsed.isCard)
        val loan = parsed.toLoan(LoanSource.PHOTO)
        assertEquals(LoanType.CREDIT_CARD, loan.type)
        assertEquals(150_000_00L, loan.creditLimit)
        assertEquals(32_000_00L, loan.currentBalance)
        assertEquals(7_500_00L, LoanMath.regularPayment(loan))
    }
}
