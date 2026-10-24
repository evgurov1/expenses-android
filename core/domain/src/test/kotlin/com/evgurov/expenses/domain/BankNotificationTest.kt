package com.evgurov.expenses.domain

import com.evgurov.expenses.domain.bank.BankNotificationParser
import com.evgurov.expenses.domain.bank.CategoryGuesser
import com.evgurov.expenses.domain.bank.OperationType
import com.evgurov.expenses.domain.model.TransactionKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BankNotificationTest {
    private val tbank = "com.idamob.tinkoff.android"

    private fun parse(pkg: String, title: String, text: String) = BankNotificationParser.parse(pkg, title, text)

    @Test
    fun `reads purchases with merchant and skips balance`() {
        val op = parse(tbank, "Покупка", "Покупка, карта *1234. 450,50 ₽. PYATEROCHKA 1234. Доступно 12 345 ₽")!!
        assertEquals(TransactionKind.EXPENSE, op.kind)
        assertEquals(450_50L, op.amount)
        assertEquals("PYATEROCHKA 1234", op.merchant)
        assertEquals("Продукты", CategoryGuesser.guess(op))

        val sber = parse("ru.sberbankmobile", "СберБанк", "MIR-1234 14:05 Покупка 1 299р KFC Баланс: 5 432.10р")!!
        assertEquals(1_299_00L, sber.amount)
        assertEquals("Кафе и рестораны", CategoryGuesser.guess(sber))

        val vtb = parse("ru.vtb24.mobilebanking.android", "ВТБ", "Списание 350.00 RUB. Карта *5678. YANDEX.GO. Баланс 10000.00 RUB")!!
        assertEquals(350_00L, vtb.amount)
        assertEquals("Транспорт", CategoryGuesser.guess(vtb))

        val signed = parse("com.yandex.bank", "−2 490 ₽", "Wildberries")!!
        assertEquals(TransactionKind.EXPENSE, signed.kind)
        assertEquals("Покупки онлайн", CategoryGuesser.guess(signed))
    }

    @Test
    fun `reads income, salary, transfers and loan payments`() {
        val salary = parse(tbank, "Пополнение", "Зачисление зарплаты. 85 000 ₽. ООО Ромашка. Доступно 90 000 ₽")!!
        assertEquals(TransactionKind.INCOME, salary.kind)
        assertEquals(OperationType.SALARY, salary.type)
        assertEquals("Зарплата", CategoryGuesser.guess(salary))

        val transferIn = parse("ru.ozon.fintech.finance", "Озон Банк", "Входящий перевод 5 000 ₽ от Ивана И.")!!
        assertEquals(TransactionKind.INCOME, transferIn.kind)
        assertEquals("Переводы", CategoryGuesser.guess(transferIn))

        val plus = parse("com.yandex.bank", "+1 000 ₽", "Перевод от Марии")!!
        assertEquals(TransactionKind.INCOME, plus.kind)

        val loan = parse("ru.vtb24.mobilebanking.android", "ВТБ", "Погашение кредита 11 149,08 ₽. Остаток задолженности 243 594,39 ₽")!!
        assertEquals(11_149_08L, loan.amount)
        assertEquals("Платежи по кредитам", CategoryGuesser.guess(loan))
    }

    @Test
    fun `ignores codes, ads, declines and other apps`() {
        assertNull(parse(tbank, "Код", "Код для входа: 1234. Никому не сообщайте"))
        assertNull(parse(tbank, "Кэшбэк до 30%", "Скидки на покупки до 5 000 ₽"))
        assertNull(parse(tbank, "Операция отклонена", "Покупка 450 ₽ не прошла: недостаточно средств"))
        assertNull(parse("ru.ozon.app.android", "Ozon", "Покупка 450 ₽"))
        assertNull(parse(tbank, "Напоминание", "Завтра будет списан платёж 7 844 ₽"))
    }

    @Test
    fun `remembers user choice for a merchant`() {
        val op = parse(tbank, "Покупка", "Покупка, карта *1234. 700 ₽. IP IVANOV 77. Доступно 1 000 ₽")!!
        assertEquals("Прочее", CategoryGuesser.guess(op))
        val learned = mapOf(CategoryGuesser.merchantKey("IP IVANOV 12") to "Кафе и рестораны")
        assertEquals("Кафе и рестораны", CategoryGuesser.guess(op, learned))
    }
}
