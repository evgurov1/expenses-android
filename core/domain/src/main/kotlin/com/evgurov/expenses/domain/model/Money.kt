package com.evgurov.expenses.domain.model

import java.math.BigDecimal
import java.math.RoundingMode

object Money {
    private const val NBSP = ' '

    /** 123456 -> "1 234,56 ₽", -5000 -> "−50 ₽". */
    fun format(amount: Kopecks, currencySymbol: String = "₽"): String {
        val abs = kotlin.math.abs(amount)
        val rubles = abs / 100
        val kopecks = abs % 100
        val grouped = rubles.toString().reversed().chunked(3).joinToString(NBSP.toString()).reversed()
        val fraction = if (kopecks == 0L) "" else ",%02d".format(kopecks)
        val sign = if (amount < 0) "−" else ""
        return "$sign$grouped$fraction$NBSP$currencySymbol"
    }

    /**
     * Разбирает ввод пользователя: "1 234,5", "1234.50", "1000".
     * Возвращает null, если строка не похожа на сумму или в ней больше двух знаков после запятой.
     */
    fun parse(input: String): Kopecks? {
        val normalized = input
            .replace(NBSP.toString(), "")
            .replace(" ", "")
            .replace(',', '.')
            .removeSuffix("₽")
        if (normalized.isEmpty() || !normalized.matches(Regex("""-?\d+(\.\d{0,2})?"""))) return null
        return BigDecimal(normalized)
            .setScale(2, RoundingMode.UNNECESSARY)
            .movePointRight(2)
            .longValueExact()
    }
}
