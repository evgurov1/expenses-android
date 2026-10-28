package com.evgurov.expenses.domain.bank

import com.evgurov.expenses.domain.model.Kopecks
import com.evgurov.expenses.domain.model.TransactionKind
import java.time.LocalDate

/** Операция со скриншота списка операций банка. */
data class ScreenshotRow(
    val date: LocalDate,
    /** Как подписал банк: магазин, человек или «Себе в другой банк». */
    val name: String,
    val amount: Kopecks,
    val kind: TransactionKind,
    /** Категория банка из второй строки («Переводы», «Маркетплейсы»), если распознали. */
    val bankCategory: String?,
    /** Карта или счёт из второй строки («Black», «Платинум»). */
    val card: String?,
)

/**
 * Разбор распознанного текста скриншота со списком операций банковского приложения:
 *
 *     Сегодня                          −8 891 ₽
 *     Виталий К.                      +22 000 ₽
 *     Переводы                            Black
 *
 * Строка операции — название и сумма в конце, под ней категория банка и карта.
 * Заголовок дня («Сегодня», «Вчера», «22 октября») задаёт дату для строк ниже.
 */
object ScreenshotParser {
    private val months = listOf(
        "январ", "феврал", "март", "апрел", "ма", "июн", "июл", "август", "сентябр", "октябр", "ноябр", "декабр",
    )

    // Распознавание читает «₽» как P, Р, р или «?», а иногда теряет его вовсе.
    private val amountAtEnd = Regex("""([+\-−–—]{0,2})\s?(\d{1,3}(?:[  ]\d{3})+|\d+)(?:[.,](\d{1,2}))?\s?(₽|P|Р|р|\?)?$""")
    private val dayMonth = Regex("""(?<!\d)(\d{1,2})\s+([а-яё]+)(?:\s+(\d{4}))?""", RegexOption.IGNORE_CASE)

    /** Подписи и плашки, в которых есть суммы, но это не операции. */
    private val noise = listOf("рассрочк", "траты", "доходы", "можно распределить", "кэшбэк до", "лимит", "баланс", "всего")

    /** Категории банков → категории приложения. Сверяем по началу подписи. */
    private val bankCategories = listOf(
        "Переводы" to CategoryGuesser.TRANSFERS,
        "Штраф" to "Налоги и штрафы",
        "Налог" to "Налоги и штрафы",
        "Госуслуг" to "Налоги и штрафы",
        "Маркетплейс" to "Покупки онлайн",
        "Онлайн" to "Покупки онлайн",
        "Услуги банка" to "Услуги банка",
        "Мобильн" to "Связь и интернет",
        "Связь" to "Связь и интернет",
        "Интернет" to "Связь и интернет",
        "Супермаркет" to "Продукты",
        "Продукт" to "Продукты",
        "Фастфуд" to "Кафе и рестораны",
        "Ресторан" to "Кафе и рестораны",
        "Кафе" to "Кафе и рестораны",
        "Доставка еды" to CategoryGuesser.FOOD_DELIVERY,
        "Такси" to "Транспорт",
        "Транспорт" to "Транспорт",
        "Топливо" to "Транспорт",
        "Азс" to "Транспорт",
        "Автоуслуг" to "Транспорт",
        "Каршеринг" to "Транспорт",
        "Аптек" to "Здоровье",
        "Медицин" to "Здоровье",
        "Здоровье" to "Здоровье",
        "Одежда" to "Одежда",
        "Дом и ремонт" to "Дом и ремонт",
        "Красота" to "Красота и спорт",
        "Спорт" to "Красота и спорт",
        "Развлечени" to "Развлечения",
        "Кино" to "Развлечения",
        "Подписк" to "Развлечения",
        "Путешеств" to "Путешествия",
        "Авиабилет" to "Путешествия",
        "Отел" to "Путешествия",
        "Жкх" to "Жильё и ЖКХ",
        "Коммунальн" to "Жильё и ЖКХ",
        "Наличные" to "Наличные",
        "Кредит" to "Платежи по кредитам",
        "Зарплат" to "Зарплата",
        "Кэшбэк" to "Проценты и кешбэк",
        "Проценты" to "Проценты и кешбэк",
    )

    fun parse(text: String, today: LocalDate): List<ScreenshotRow> {
        val lines = text.lines().map(::clean).filter { it.isNotEmpty() }
        val rows = mutableListOf<ScreenshotRow>()
        var date = today
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            val header = dayHeader(line, today)
            if (header != null) {
                date = header
                i++
                continue
            }
            val row = row(line, date)
            if (row == null) {
                i++
                continue
            }
            // Вторая строка: категория банка и карта. Если там снова сумма — это уже следующая операция.
            val next = lines.getOrNull(i + 1)
            if (next != null && amountAtEnd.find(next)?.groupValues?.get(2).isNullOrEmpty() && dayHeader(next, today) == null) {
                val (category, card) = categoryAndCard(next)
                rows += row.copy(bankCategory = category, card = card)
                i += 2
            } else {
                rows += row
                i++
            }
        }
        return rows
    }

    /** Наша категория по подписи банка или null, если подпись незнакомая. */
    fun categoryFor(bankCategory: String?): String? {
        val lower = bankCategory?.lowercase() ?: return null
        return bankCategories.firstOrNull { (prefix, _) -> lower.startsWith(prefix.lowercase()) }?.second
    }

    private fun row(line: String, date: LocalDate): ScreenshotRow? {
        val match = amountAtEnd.find(line) ?: return null
        // Без знака и без рубля число в конце строки — скорее мусор от иконок, а не сумма.
        if (match.groupValues[1].isEmpty() && match.groupValues[4].isEmpty()) return null
        val name = line.substring(0, match.range.first).trim(' ', ',', ':', '·')
        val lower = name.lowercase()
        if (name.count(Char::isLetter) < 2 || noise.any { it in lower }) return null
        val rubles = match.groupValues[2].filter(Char::isDigit).toLongOrNull() ?: return null
        val amount = rubles * 100 + match.groupValues[3].padEnd(2, '0').toLong()
        if (amount <= 0) return null
        val kind = if ('+' in match.groupValues[1]) TransactionKind.INCOME else TransactionKind.EXPENSE
        return ScreenshotRow(date, name, amount, kind, bankCategory = null, card = null)
    }

    /**
     * «Маркетплейсы Дебетовая карта», «Переводы Black», «Услуги банка Платинум»: карта — в конце строки
     * (одно слово или «… карта»), всё, что перед ней, — категория банка.
     */
    private fun categoryAndCard(line: String): Pair<String?, String?> {
        val words = line.split(' ')
        if (words.size == 1) return line to null
        val cardWords = if (words.last().lowercase() in setOf("карта", "карты", "счёт", "счет") && words.size > 2) 2 else 1
        return words.dropLast(cardWords).joinToString(" ") to words.takeLast(cardWords).joinToString(" ")
    }

    private fun dayHeader(line: String, today: LocalDate): LocalDate? {
        val lower = line.lowercase()
        if (lower.startsWith("сегодня")) return today
        if (lower.startsWith("вчера")) return today.minusDays(1)
        val m = dayMonth.find(lower) ?: return null
        // Заголовок дня короткий: «22 октября», «22 октября, вторник», «Вторник, 22 октября −1 200 ₽».
        if (m.range.first > 14) return null
        val month = months.indexOfFirst { m.groupValues[2].startsWith(it) && (it != "ма" || m.groupValues[2] == "мая") }
        if (month < 0) return null
        val day = m.groupValues[1].toInt()
        val year = m.groupValues[3].toIntOrNull()
        val date = runCatching { LocalDate.of(year ?: today.year, month + 1, day) }.getOrNull() ?: return null
        // Год не указан, а дата в будущем — значит, прошлый год.
        return if (year == null && date > today) date.minusYears(1) else date
    }

    /** Убираем мусор от иконок слева: «© », «= », «е » и двойные пробелы. */
    private fun clean(line: String): String =
        line.replace(' ', ' ').replace(Regex("""\s+"""), " ").trim()
            .replace(Regex("""^(?:[^\p{L}\d+\-−–—]+|[\p{L}]{1}\s)+(?=\p{L}{2})"""), "")
            .trim()
}
