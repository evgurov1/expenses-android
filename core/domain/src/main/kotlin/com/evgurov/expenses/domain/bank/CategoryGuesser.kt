package com.evgurov.expenses.domain.bank

import com.evgurov.expenses.domain.model.TransactionKind

/**
 * Категория для операции из банка. Сначала — то, что пользователь сам выбрал для этого магазина раньше,
 * потом — словарь известных магазинов, потом — тип операции.
 */
object CategoryGuesser {
    const val TRANSFERS = "Переводы"
    const val OTHER_EXPENSE = "Прочее"
    const val OTHER_INCOME = "Прочие доходы"
    const val FOOD_DELIVERY = "Доставка еды"

    private val expenseRules: List<Pair<String, List<String>>> = listOf(
        // Доставка — раньше продуктов и кафе: пользователь хочет видеть её отдельно.
        FOOD_DELIVERY to listOf(
            "самокат", "samokat", "лавка", "lavka", "яндекс еда", "yandex eda", "eda.yandex", "yandex.eda", "yandex food",
            "delivery club", "perekrest", "перекрест", "vprok", "впрок", "kuper", "купер", "sbermarket", "сбермаркет",
            "utkonos", "утконос", "вкусвилл", "vkusvill",
        ),
        "Продукты" to listOf(
            "ovosch", "овощ", "nuts", "kolbas", "колбас", "rybn", "рыбн", "myaso", "мясо", "hleb", "хлеб", "bakery", "пекарн",
            "metro store", "magazin", "магазин", "produkt",
            "пятерочк", "pyaterochk", "магнит", "magnit", "лента", "lenta", "ашан", "auchan", "дикси", "dixy", "spar", " окей", "okey", "кэш энд керри", "metro cash", "o'key", "metro c&c", "globus",
            "глобус", "fix price", "светофор", "верный", "verny", "азбука вкуса",
            "av daily", "чижик", "chizhik", "бристоль", "bristol", "красное&белое", "krasnoe", "producty", "продукт",
        ),
        "Кафе и рестораны" to listOf(
            "kafe", "ramen", "espresso", "bistro", "бистро", "stolovaya", "столов", "bar ", "pub ",
            "kfc", "rostic", "burger", "бургер", "вкусно", "vkusno", "mcdonald", "coffee", "кофе", "cofix",
            "шоколадниц", "shokoladnitsa", "додо", "dodo", "pizza", "пицц", "cafe", "кафе", "restoran", "ресторан",
            "суши", "sushi", "теремок", "teremok", "stars coffee",
        ),
        "Транспорт" to listOf(
            "avtozapchast", "автозапчаст", "azk", "азк", " auto ", "carprice",
            "azs", "gazpromneft", "avtodor", "carwash", "автомойк", "мойк", "wash",
            "metro", "метро", "mosmetro", "тройка", "troika", "taxi", "такси", "yandex go", "yandex.go", "uber",
            "citymobil", "ситимобил", "азс", "lukoil", "лукойл", "роснефть", "rosneft", "gazprom neft", "газпромнефть",
            "shell", "татнефть", "tatneft", "ржд", "rzd", "аэрофлот", "aeroflot", "победа", "pobeda", " s7 ", "whoosh",
            "urent", "делимобиль", "delimobil", "parking", "парковк", "мцд", "автобус", "транспорт",
        ),
        "Покупки онлайн" to listOf(
            "ozon", "озон", "wildberries", "вайлдберриз", "wb.ru", "yandex market", "яндекс маркет", "market.yandex",
            "aliexpress", "алиэкспресс", "sdek", "сдэк", "cdek", "dpd", " pek ", "пэк", "boxberry", "pochta", "почта россии",
            "рамочному договору", "м.видео", "mvideo", " dns ", "eldorado", "эльдорадо", "lamoda", "ламода", "мегамаркет", "megamarket", "avito", "авито",
        ),
        "Связь и интернет" to listOf(
            "мтс", "mts", "билайн", "beeline", "мегафон", "megafon", "tele2", "теле2", "t2 ", "ростелеком",
            "rostelecom", "yota", "timeweb", "sota", "hosting", "vpn", "дом.ру", "dom.ru", "т-мобайл", "t-mobile",
        ),
        "Здоровье" to listOf(
            "аптек", "apteka", "ригла", "rigla", "36.6", "здравсити", "zdravcity", "eapteka", "горздрав", "gorzdrav",
            "клиник", "clinic", "стоматолог", "медси", "medsi", "инвитро", "invitro", "гемотест", "gemotest",
        ),
        "Жильё и ЖКХ" to listOf("жкх", "zhkh", "gkh", "мосэнерго", "mosenergo", "энергосбыт", "водоканал", "жку", "квартплат"),
        "Налоги и штрафы" to listOf("gosuslugi", "госуслуг", "гибдд", "gibdd", "штраф", "фнс", "налог", "nalog"),
        "Дом и ремонт" to listOf(
            "lemana", "леман", "leroy", "леруа", " obi ", "petrovich", "петрович", "ikea", "икеа", "hoff", "askona", "аскона",
            "castorama", "максидом", "maxidom", "строител",
        ),
        "Одежда" to listOf("zara", "h&m", "uniqlo", "gloria jeans", "глория", "sportmaster", "спортмастер", "befree", "ostin", "остин", "love republic", "zolla", "золла"),
        "Путешествия" to listOf(
            "travel", "bankair", "avia", "авиа", "booking", "ostrovok", "островок", "sutochno", "hotel", "отель", "camp",
        ),
        "Красота и спорт" to listOf(
            "gym", "fitnes", "фитнес", "nails", "barber", "барбер", "салон", "gold apple", "золотое яблоко", "letu", "летуаль",
            "beauty", "spa ", "sport", "спорт",
        ),
        "Развлечения" to listOf(
            "кино", "cinema", "kinopoisk", "кинопоиск", " ivi ", "okko", "spotify", "steam", "playstation", "яндекс плюс",
            "yandex plus", "plus.yandex", "театр", "музей", "boosty", "apple.com", "google play", "games", "игр",
        ),
    )

    /** Название магазина без номера точки и лишних знаков: по нему запоминаем выбор пользователя. */
    fun merchantKey(merchant: String): String =
        merchant.lowercase()
            .replace('ё', 'е')
            .replace(Regex("""[\d*#№]+"""), " ")
            .replace(Regex("""[^\p{L}&.' ]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()

    /** Категория по словарю магазинов, без учёта типа операции и выбора пользователя. */
    fun byMerchant(merchant: String): String? {
        val key = merchantKey(merchant)
        return expenseRules.firstOrNull { (_, words) -> words.any { it in " $key " } }?.first
    }

    /**
     * @param learned выбор пользователя: ключ магазина → название категории.
     * @return название категории; приложение создаст её, если такой ещё нет.
     */
    fun guess(operation: BankOperation, learned: Map<String, String> = emptyMap()): String {
        val key = merchantKey(operation.merchant)
        if (key.isNotEmpty()) learned[key]?.let { return it }
        return when (operation.kind) {
            TransactionKind.INCOME -> when (operation.type) {
                OperationType.SALARY -> "Зарплата"
                OperationType.CASHBACK -> "Проценты и кешбэк"
                OperationType.TRANSFER -> TRANSFERS
                else -> OTHER_INCOME
            }
            TransactionKind.EXPENSE -> when (operation.type) {
                OperationType.LOAN_PAYMENT -> "Платежи по кредитам"
                OperationType.FEE -> "Услуги банка"
                OperationType.CASH -> "Наличные"
                else -> expenseRules.firstOrNull { (_, words) -> words.any { it in " $key " } }?.first
                    ?: if (operation.type == OperationType.TRANSFER) TRANSFERS else OTHER_EXPENSE
            }
        }
    }
}
