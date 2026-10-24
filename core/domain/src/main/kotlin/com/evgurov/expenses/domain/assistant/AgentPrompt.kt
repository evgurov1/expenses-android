package com.evgurov.expenses.domain.assistant

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

enum class Role { USER, ASSISTANT, TOOL }

data class AgentMessage(val role: Role, val content: String)

data class ToolCall(val name: String, val arguments: JsonObject)

/**
 * Запрос к Qwen3 в формате ChatML с описанием инструментов.
 * Модель сама выбирает инструмент, приложение его выполняет, и модель объясняет результат.
 */
object AgentPrompt {
    const val END = "<|im_end|>"

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private const val RULES = """Ты — помощник по кредитам в приложении «Расходы». Работаешь прямо в телефоне пользователя, без интернета.
Отвечай по-русски, коротко и по делу: 2–5 предложений. Отвечай именно на последний вопрос.
Цифры бери только из раздела «Данные» или из результатов инструментов. Не придумывай суммы, ставки и даты и не считай сам: ни одного числа, которого там нет.
Вопросы про досрочное погашение, сроки закрытия с доплатой, экономию или нужную сумму доплаты — сначала вызови payoff_plan (если сумма не названа, возьми 10000), потом отвечай по его результату.
Вопросы «сколько доплачивать, чтобы закрыть за N месяцев» и «закрою ли я всё или один кредит за N месяцев» — extra_for_target.
Сравнение стратегий — compare_strategies, график или расписание платежей — payment_schedule.
Если пользователь называет дни зарплаты, сохрани их инструментом save_paydays и сразу построй график.
Если данных не хватает, задай один уточняющий вопрос.
Не советуй брать новые кредиты и займы, кроме рефинансирования под меньшую ставку. Ты не финансовый консультант."""

    private val TOOLS = listOf(
        """{"type":"function","function":{"name":"payoff_plan","description":"Посчитать план досрочного погашения: сколько сэкономит доплата, когда закроется каждый кредит и все вместе.","parameters":{"type":"object","properties":{"extra_monthly":{"type":"number","description":"Доплата сверх обязательных платежей, рублей в месяц"},"lump_sum":{"type":"number","description":"Разовый досрочный платёж сейчас, рублей"},"strategy":{"type":"string","enum":["avalanche","snowball"],"description":"avalanche — сначала самая высокая ставка, snowball — сначала самый маленький остаток"}},"required":["extra_monthly"]}}}""",
        """{"type":"function","function":{"name":"extra_for_target","description":"Сколько нужно доплачивать в месяц, чтобы закрыть все кредиты или один выбранный кредит за заданное число месяцев.","parameters":{"type":"object","properties":{"months":{"type":"integer","description":"За сколько месяцев закрыть"},"loan":{"type":"string","description":"Название кредита, если речь об одном кредите; пусто — все кредиты"}},"required":["months"]}}}""",
        """{"type":"function","function":{"name":"compare_strategies","description":"Сравнить стратегии «лавина» и «снежный ком» при одной и той же доплате.","parameters":{"type":"object","properties":{"extra_monthly":{"type":"number","description":"Доплата, рублей в месяц"}},"required":["extra_monthly"]}}}""",
        """{"type":"function","function":{"name":"payment_schedule","description":"Построить график платежей на ближайшие месяцы: что, сколько и с какой зарплаты платить, включая досрочку.","parameters":{"type":"object","properties":{"extra_monthly":{"type":"number","description":"Доплата, рублей в месяц; 0 — только обязательные платежи"},"months":{"type":"integer","description":"На сколько месяцев, от 1 до 12"}},"required":[]}}}""",
        """{"type":"function","function":{"name":"save_paydays","description":"Запомнить, в какие числа месяца приходит зарплата или аванс.","parameters":{"type":"object","properties":{"days":{"type":"array","items":{"type":"integer"},"description":"Числа месяца, например [5, 20]"}},"required":["days"]}}}""",
    )

    fun system(facts: String): String = buildString {
        appendLine(RULES)
        appendLine()
        appendLine("Данные:")
        appendLine(facts)
        appendLine()
        appendLine("# Tools")
        appendLine()
        appendLine("You may call one or more functions to assist with the user query.")
        appendLine()
        appendLine("You are provided with function signatures within <tools></tools> XML tags:")
        appendLine("<tools>")
        TOOLS.forEach { appendLine(it) }
        appendLine("</tools>")
        appendLine()
        appendLine("For each function call, return a json object with function name and arguments within <tool_call></tool_call> XML tags:")
        appendLine("<tool_call>")
        appendLine("""{"name": <function-name>, "arguments": <args-json-object>}""")
        append("</tool_call>")
    }

    /** Полный запрос: система, переписка и начало ответа ассистента. */
    fun render(system: String, messages: List<AgentMessage>): String = buildString {
        append("<|im_start|>system\n").append(system).append(END).append('\n')
        for (message in messages) {
            when (message.role) {
                Role.USER -> append("<|im_start|>user\n").append(message.content)
                Role.ASSISTANT -> append("<|im_start|>assistant\n").append(message.content)
                Role.TOOL -> append("<|im_start|>user\n<tool_response>\n").append(message.content).append("\n</tool_response>")
            }
            append(END).append('\n')
        }
        append("<|im_start|>assistant\n")
    }

    /** Первый вызов инструмента в ответе модели или null, если модель ответила текстом. */
    fun parseToolCall(output: String): ToolCall? {
        val start = output.indexOf("<tool_call>").takeIf { it >= 0 } ?: return null
        val body = output.substring(start + "<tool_call>".length).substringBefore("</tool_call>").trim()
        val obj = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return null
        val name = obj["name"]?.jsonPrimitive?.contentOrNull ?: return null
        val args = when (val raw = obj["arguments"]) {
            is JsonObject -> raw
            null -> JsonObject(emptyMap())
            // Иногда модель кладёт аргументы строкой с JSON внутри.
            else -> runCatching { json.parseToJsonElement(raw.jsonPrimitive.content).jsonObject }.getOrDefault(JsonObject(emptyMap()))
        }
        return ToolCall(name, args)
    }

    /** Текст ответа без служебных тегов — на случай, если модель смешала текст и вызов. */
    fun visibleText(output: String): String =
        output.substringBefore("<tool_call>").replace(END, "").trim()
}
