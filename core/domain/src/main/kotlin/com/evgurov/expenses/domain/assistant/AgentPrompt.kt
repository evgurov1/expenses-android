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

    // Порядок важен: правила и инструменты не меняются и считаются моделью один раз (кэш сохраняется в файл),
    // а данные о кредитах идут в конце — при запуске пересчитываются только они.
    private const val RULES = """Ты — помощник по кредитам в приложении «Расходы», работаешь в телефоне без интернета.
Отвечай по-русски, коротко: 2–5 предложений, именно на последний вопрос.
Числа бери только из «Данных» и результатов инструментов. Сам не считай и не придумывай суммы, ставки и даты.
Какой инструмент вызвать:
- досрочка, экономия, когда закроется с доплатой — payoff_plan (нет суммы — 10000);
- сколько доплачивать, чтобы закрыть всё или один кредит за N месяцев — extra_for_target;
- лавина или снежный ком — compare_strategies;
- график, расписание, что платить с какой зарплаты — payment_schedule;
- один кредит — loan_details; ПДН в будущем — burden_forecast;
- пользователь внёс досрочный платёж — record_early_payment (уменьшил платёж — mode reduce_payment, иначе reduce_term);
- назвал свой доход или зарплату в месяц — set_income;
  эти два действия приложение сохранит после подтверждения пользователем, не пиши, что уже сохранено;
- сам назвал дни зарплаты — save_paydays, потом payment_schedule. Дни не выдумывай: неизвестны — строй график без них.
Не хватает данных — задай один вопрос.
Полезно знать: проценты идут на остаток каждый день, поэтому досрочку выгоднее вносить раньше, удобно в день зарплаты. Многие банки списывают досрочку в дату платежа: деньги держать на счёте и подать заявку в приложении банка. Лучше уменьшать срок, а не платёж. Внесённую досрочку отмечают здесь: экран кредита → «Внести досрочный платёж».
Не советуй новые кредиты, кроме рефинансирования под меньшую ставку. Ты не финансовый консультант."""

    private fun tool(name: String, description: String, properties: String, required: String = "") =
        """{"type":"function","function":{"name":"$name","description":"$description","parameters":{"type":"object","properties":{$properties},"required":[$required]}}}"""

    private const val EXTRA = """"extra_monthly":{"type":"number","description":"Доплата, ₽ в месяц"}"""
    private const val LOAN = """"loan":{"type":"string","description":"Название кредита"}"""

    private val TOOLS = listOf(
        tool(
            "payoff_plan", "План досрочного погашения: экономия и когда закроется каждый кредит.",
            """$EXTRA,"lump_sum":{"type":"number","description":"Разовый платёж сейчас, ₽"},"strategy":{"type":"string","enum":["avalanche","snowball"]}""",
            "\"extra_monthly\"",
        ),
        tool(
            "extra_for_target", "Сколько доплачивать, чтобы закрыть все кредиты или один за N месяцев.",
            """"months":{"type":"integer"},"loan":{"type":"string","description":"Пусто — все кредиты"}""", "\"months\"",
        ),
        tool("compare_strategies", "Сравнить лавину и снежный ком.", EXTRA, "\"extra_monthly\""),
        tool("payment_schedule", "График платежей по зарплатам на ближайшие месяцы.", """$EXTRA,"months":{"type":"integer"}"""),
        tool(
            "record_early_payment", "Отметить досрочный платёж, уже внесённый в банке.",
            """$LOAN,"amount":{"type":"number"},"mode":{"type":"string","enum":["reduce_term","reduce_payment"],"description":"reduce_payment — если уменьшил платёж"},"date":{"type":"string","description":"ГГГГ-ММ-ДД, по умолчанию сегодня"}""",
            "\"loan\",\"amount\"",
        ),
        tool("set_income", "Сохранить доход пользователя в месяц.", """"monthly_income":{"type":"number"}""", "\"monthly_income\""),
        tool("loan_details", "Остаток, ближайшие платежи и проценты по одному кредиту.", LOAN, "\"loan\""),
        tool("burden_forecast", "Когда ПДН опустится ниже 80, 50 и 30%.", EXTRA),
        tool("save_paydays", "Запомнить дни зарплаты.", """"days":{"type":"array","items":{"type":"integer"}}""", "\"days\""),
    )

    /** Неизменная часть системного запроса: правила и инструменты. */
    val stablePart: String = buildString {
        appendLine(RULES)
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
        appendLine("</tool_call>")
        appendLine()
        appendLine("Данные:")
    }

    fun system(facts: String): String = stablePart + facts.trim()

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
