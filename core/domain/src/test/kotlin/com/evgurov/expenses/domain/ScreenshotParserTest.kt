package com.evgurov.expenses.domain

import com.evgurov.expenses.domain.bank.ScreenshotParser
import com.evgurov.expenses.domain.model.TransactionKind
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class ScreenshotParserTest {
    private val today = LocalDate.of(2026, 10, 24)

    // Текст, который Tesseract выдаёт по скриншоту списка операций (имена вымышленные).
    private val text = """
        < = Операции а C6
        Счета и карты Ут
        364 076 P 379 424 P
        Траты Доходы
        Сегодня
        Иван П. +22 000 P
        Переводы Black
        Можно распределить по счетам Ha разные цели
        Штрафы ГИБДД -562,5 Р
        = Штрафы Платинум
        Себе в другой банк -300 P
        Переводы Black
        WILDBERRIES -1360?
        Маркетплейсы Дебетовая карта
        МегаФон -500 P
        о Мобильна.  Рассрочки на 350 871,44 Р Black
        22 октября −1 200 ₽
        SAMOKAT -1 200 ₽
        Супермаркеты Black
    """.trimIndent()

    @Test
    fun `reads rows with dates, signs and bank categories`() {
        val rows = ScreenshotParser.parse(text, today)
        assertEquals(listOf("Иван П.", "Штрафы ГИБДД", "Себе в другой банк", "WILDBERRIES", "МегаФон", "SAMOKAT"), rows.map { it.name })
        assertEquals(TransactionKind.INCOME, rows[0].kind)
        assertEquals(22_000_00L, rows[0].amount)
        assertEquals("Переводы", rows[0].bankCategory)
        assertEquals("Black", rows[0].card)
        assertEquals(562_50L, rows[1].amount)
        assertEquals("Налоги и штрафы", ScreenshotParser.categoryFor(rows[1].bankCategory))
        assertEquals(TransactionKind.EXPENSE, rows[3].kind)
        assertEquals(1_360_00L, rows[3].amount)
        assertEquals("Дебетовая карта", rows[3].card)
        assertEquals("Покупки онлайн", ScreenshotParser.categoryFor(rows[3].bankCategory))
        assertEquals(today, rows[4].date)
        assertEquals(LocalDate.of(2026, 10, 22), rows[5].date)
        assertEquals("Продукты", ScreenshotParser.categoryFor(rows[5].bankCategory))
    }

    @Test
    fun `date without year in the future is last year`() {
        val rows = ScreenshotParser.parse("28 декабря\nOZON -990 ₽\nМаркетплейсы Black", today)
        assertEquals(LocalDate.of(2025, 12, 28), rows.single().date)
    }
}
