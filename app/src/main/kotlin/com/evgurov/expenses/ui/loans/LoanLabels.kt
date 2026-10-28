package com.evgurov.expenses.ui.loans

import androidx.compose.ui.graphics.Color
import com.evgurov.expenses.domain.model.LoanType
import com.evgurov.expenses.domain.model.PaymentType
import com.evgurov.expenses.domain.usecase.BurdenZone

val LoanType.label: String
    get() = when (this) {
        LoanType.CONSUMER -> "Потребительский"
        LoanType.MORTGAGE -> "Ипотека"
        LoanType.CAR -> "Автокредит"
        LoanType.CREDIT_CARD -> "Кредитная карта"
        LoanType.MICROLOAN -> "Микрозайм"
        LoanType.OTHER -> "Другой"
    }

val PaymentType.label: String
    get() = when (this) {
        PaymentType.ANNUITY -> "Аннуитетный"
        PaymentType.DIFFERENTIATED -> "Дифференцированный"
    }

val BurdenZone.color: Color
    get() = when (this) {
        BurdenZone.LOW -> Color(0xFF2E7D32)
        BurdenZone.MODERATE -> Color(0xFFF9A825)
        BurdenZone.HIGH -> Color(0xFFEF6C00)
        BurdenZone.CRITICAL -> Color(0xFFC62828)
    }

val BurdenZone.title: String
    get() = when (this) {
        BurdenZone.LOW -> "Комфортная нагрузка"
        BurdenZone.MODERATE -> "Умеренная нагрузка"
        BurdenZone.HIGH -> "Высокая нагрузка"
        BurdenZone.CRITICAL -> "Критическая нагрузка"
    }

val BurdenZone.hint: String
    get() = when (this) {
        BurdenZone.LOW -> "До 30% дохода. Банки охотно дают новые кредиты."
        BurdenZone.MODERATE -> "30–50% дохода. Новый кредит одобрят не везде."
        BurdenZone.HIGH -> "50–80% дохода. Банки сильно ограничены в выдаче таким заёмщикам."
        BurdenZone.CRITICAL -> "Больше 80% дохода. Стоит подумать о рефинансировании или досрочном погашении."
    }

/** «1 год 3 мес.», «8 мес.» */
fun monthsTitle(months: Int): String {
    val years = months / 12
    val rest = months % 12
    return listOfNotNull(
        if (years > 0) "$years ${yearsWord(years)}" else null,
        if (rest > 0 || years == 0) "$rest мес." else null,
    ).joinToString(" ")
}

private fun yearsWord(n: Int): String {
    val mod100 = n % 100
    val mod10 = n % 10
    return when {
        mod100 in 11..14 -> "лет"
        mod10 == 1 -> "год"
        mod10 in 2..4 -> "года"
        else -> "лет"
    }
}

fun parseRate(text: String): Double? = text.trim().replace(',', '.').removeSuffix("%").trim().toDoubleOrNull()
    ?.takeIf { it in 0.0..1000.0 }

/** 12.0 -> "12%", 9.9 -> "9,9%" */
fun formatRate(rate: Double): String =
    java.math.BigDecimal.valueOf(rate).stripTrailingZeros().toPlainString().replace('.', ',') + "%"
