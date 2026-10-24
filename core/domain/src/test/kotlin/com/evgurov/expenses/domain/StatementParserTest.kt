package com.evgurov.expenses.domain

import com.evgurov.expenses.domain.bank.CategoryGuesser
import com.evgurov.expenses.domain.bank.StatementParser
import com.evgurov.expenses.domain.model.TransactionKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class StatementParserTest {
    // Строки в том виде, как их отдаёт PdfBox из выписки Т-Банка (данные вымышленные).
    private val text = """
        24.10.26
        ИВАНОВ ИВАН ИВАНОВИЧ
        Выписка по договору №1234567890
        Баланс на 18.09.26 36 913.36 ₽
        •     Поступления 150 000.00 ₽
        Операции по карте № 220070******0000 ИВАНОВ ИВАН
        Расходы: 77 000.00 ₽
        Дата и время Дата Сумма Сумма
        операции обработки Описание операции в валюте счёта
        17.09.26 18.09.26 Внутренний перевод на договор 1 935.00 ₽ 1 935.00 ₽
        8400000000
        17.09.26 06:12 18.09.26 Оплата в "SKURATOV COFFEE" g Samara 310.00 ₽ 310.00 ₽
        RU
        17.09.26 19:00 18.09.26 Оплата в VKUSVILL Moskva RUS 1 425.50 ₽ 1 425.50 ₽
        18.09.26 18.09.26 Пополнение. Система быстрых платежей + 141 517.00 ₽ + 141 517.00 ₽
        18.09.26 18.09.26 Досрочное погашение Задолженности по 70 600.00 ₽ 70 600.00 ₽
        Кредиту  MOSCOW RU
        18.09.26 10:59 18.09.26 Оплата в METRO STORE 1032 SAMARA 5 018.85 ₽ 5 018.85 ₽
        RUS
        21.09.26 21.09.26 Пополнение через СБП. Перевод средств + 18 109.00 ₽ + 18 109.00 ₽
        от организации
        02.10.26 02.10.26 Комиссия за внешний перевод на 17.00 ₽ 17.00 ₽
        mBank.perevod
        17.10.26 22:43 17.10.26 Кэшбэк за обычные покупки + 1 674.00 ₽ + 1 674.00 ₽
    """.trimIndent()

    @Test
    fun `reads tbank statement rows with categories`() {
        val rows = StatementParser.parse(text)!!.rows
        assertEquals(9, rows.size)
        val coffee = rows[1]
        assertEquals(LocalDate.of(2026, 9, 17), coffee.date)
        assertEquals("06:12", coffee.time)
        assertEquals(310_00L, coffee.operation.amount)
        assertEquals("SKURATOV COFFEE", coffee.description)
        assertEquals("Кафе и рестораны", CategoryGuesser.guess(coffee.operation))

        val categories = rows.map { CategoryGuesser.guess(it.operation) }
        assertEquals(
            listOf(
                "Переводы", "Кафе и рестораны", "Продукты", "Переводы", "Платежи по кредитам", "Продукты",
                "Зарплата", "Услуги банка", "Проценты и кешбэк",
            ),
            categories,
        )
        assertEquals(TransactionKind.INCOME, rows[3].operation.kind)
        assertEquals(141_517_00L, rows[3].operation.amount)
        assertEquals(1_425_50L, rows[2].operation.amount)
    }

    @Test
    fun `ignores other documents`() {
        assertNull(StatementParser.parse("График платежей по кредиту\n18.09.26 7 844.00 ₽"))
    }
}
