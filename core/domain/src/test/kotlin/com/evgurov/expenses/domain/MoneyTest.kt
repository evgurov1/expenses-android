package com.evgurov.expenses.domain

import com.evgurov.expenses.domain.model.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoneyTest {
    @Test
    fun `formats with thousand separators and kopecks`() {
        assertEquals("1 234,56 ₽", Money.format(123_456))
        assertEquals("50 ₽", Money.format(5_000))
        assertEquals("0,05 ₽", Money.format(5))
        assertEquals("−1 000 000 ₽", Money.format(-100_000_000))
    }

    @Test
    fun `parses common user input`() {
        assertEquals(123_450L, Money.parse("1 234,5"))
        assertEquals(123_450L, Money.parse("1234.50"))
        assertEquals(100_000L, Money.parse("1000"))
        assertEquals(100_000L, Money.parse("1 000 ₽".replace(" ₽", "")))
        assertEquals(10L, Money.parse("0,1"))
    }

    @Test
    fun `rejects garbage and extra precision`() {
        assertNull(Money.parse(""))
        assertNull(Money.parse("abc"))
        assertNull(Money.parse("1,234"))
        assertNull(Money.parse("1.2.3"))
    }

    @Test
    fun `format and parse round trip`() {
        listOf(0L, 1L, 99L, 100L, 123_456_789L).forEach {
            assertEquals(it, Money.parse(Money.format(it)))
        }
    }
}
