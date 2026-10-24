package com.evgurov.expenses.ui.loans

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.evgurov.expenses.assistant.AssistantAvailability
import com.evgurov.expenses.assistant.LocalAssistant
import com.evgurov.expenses.assistant.ModelDownload
import com.evgurov.expenses.domain.assistant.AgentMessage
import com.evgurov.expenses.domain.assistant.AgentPrompt
import com.evgurov.expenses.domain.assistant.LoanTools
import com.evgurov.expenses.domain.assistant.Role
import com.evgurov.expenses.domain.repository.IncomeRepository
import com.evgurov.expenses.domain.repository.LoanRepository
import com.evgurov.expenses.domain.repository.TransactionRepository
import com.evgurov.expenses.domain.usecase.monthlyIncome
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

data class ChatMessage(
    val text: String,
    val fromUser: Boolean,
    val pending: Boolean = false,
    /** Что помощник делает сейчас: «Составляю график…». */
    val status: String? = null,
)

data class AssistantUiState(
    val availability: AssistantAvailability = AssistantAvailability.CHECKING,
    val downloadProgress: Float? = null,
    val error: String? = null,
    val messages: List<ChatMessage> = emptyList(),
    val draft: String = "",
    val busy: Boolean = false,
)

@HiltViewModel
class AssistantViewModel @Inject constructor(
    private val assistant: LocalAssistant,
    private val loans: LoanRepository,
    private val incomes: IncomeRepository,
    private val transactions: TransactionRepository,
    @ApplicationContext context: Context,
) : ViewModel() {

    private val _state = MutableStateFlow(AssistantUiState())
    val state: StateFlow<AssistantUiState> = _state.asStateFlow()

    private val prefs = context.getSharedPreferences("assistant", Context.MODE_PRIVATE)

    /** Переписка для модели: вопросы, ответы, вызовы инструментов и их результаты. */
    private val history = mutableListOf<AgentMessage>()

    init {
        when (val availability = assistant.availability()) {
            AssistantAvailability.LOADING, AssistantAvailability.READY -> loadModel()
            AssistantAvailability.DOWNLOADING -> download()
            else -> _state.update { it.copy(availability = availability) }
        }
    }

    fun editDraft(text: String) = _state.update { it.copy(draft = text) }

    fun download() {
        _state.update { it.copy(availability = AssistantAvailability.DOWNLOADING, error = null, downloadProgress = 0f) }
        viewModelScope.launch {
            assistant.download().collect { event ->
                when (event) {
                    is ModelDownload.Progress -> _state.update {
                        it.copy(downloadProgress = if (event.totalBytes > 0) event.downloadedBytes.toFloat() / event.totalBytes else null)
                    }
                    ModelDownload.Done -> loadModel()
                    is ModelDownload.Failed -> _state.update {
                        it.copy(availability = AssistantAvailability.DOWNLOADABLE, error = event.message, downloadProgress = null)
                    }
                }
            }
        }
    }

    private fun loadModel() {
        _state.update { it.copy(availability = AssistantAvailability.LOADING, downloadProgress = null) }
        viewModelScope.launch {
            val ok = runCatching { assistant.load() }.getOrDefault(false)
            if (!ok) {
                _state.update { it.copy(availability = AssistantAvailability.UNAVAILABLE, error = "Не удалось загрузить модель в память.") }
                return@launch
            }
            _state.update { it.copy(availability = AssistantAvailability.READY) }
            // Пока пользователь читает экран, заранее считаем системный запрос — первый ответ придёт быстрее.
            runCatching {
                val system = AgentPrompt.system(tools().facts())
                assistant.generate("<|im_start|>system\n$system${AgentPrompt.END}\n", maxTokens = 0).collect {}
            }
        }
    }

    fun ask(question: String = _state.value.draft) {
        val q = question.trim()
        if (q.isEmpty() || _state.value.busy || _state.value.availability != AssistantAvailability.READY) return
        _state.update {
            it.copy(
                draft = "",
                busy = true,
                messages = it.messages + ChatMessage(q, fromUser = true) +
                    ChatMessage("", fromUser = false, pending = true, status = "Думаю…"),
            )
        }
        viewModelScope.launch {
            val answer = runCatching { runAgent(q) }.getOrElse { e ->
                if (e is LocalAssistant.PromptTooLong) {
                    "Вопрос получился слишком длинным для модели. Попробуйте сформулировать его короче."
                } else {
                    "Не получилось ответить: ${e.message ?: e.javaClass.simpleName}"
                }
            }
            replaceLast(answer.ifBlank { "Не нашёл, что ответить. Попробуйте переформулировать вопрос." }, pending = false)
            _state.update { it.copy(busy = false) }
        }
    }

    /**
     * Цикл агента: модель либо отвечает текстом, либо вызывает инструмент.
     * Инструмент считает приложение, результат возвращается модели, и она продолжает.
     */
    private suspend fun runAgent(question: String): String {
        val tools = tools()
        val system = AgentPrompt.system(tools.facts())
        val turn = mutableListOf(AgentMessage(Role.USER, question))
        var lastToolText = ""
        repeat(MAX_STEPS) {
            val output = StringBuilder()
            generateFitting(system, turn) { chunk ->
                output.append(chunk)
                val text = output.toString().trimStart()
                // Вызов инструмента пользователю не показываем — только то, что модель пишет человеку.
                if (!text.startsWith("<") && !"<tool_call>".startsWith(text)) {
                    replaceLast(AgentPrompt.visibleText(text), pending = true)
                }
            }
            val raw = output.toString().trim()
            val call = AgentPrompt.parseToolCall(raw)
            if (call == null) {
                turn += AgentMessage(Role.ASSISTANT, raw)
                history += turn
                return AgentPrompt.visibleText(raw)
            }
            replaceLast("", pending = true, status = statusFor(call.name))
            val result = tools.execute(call)
            result.paydays?.let { prefs.edit().putString(KEY_PAYDAYS, it.joinToString(",")).apply() }
            turn += AgentMessage(Role.ASSISTANT, raw)
            // В память модели кладём только начало длинного результата: целый график на год её переполняет.
            turn += AgentMessage(Role.TOOL, compact(result.text))
            lastToolText = result.text
            if (result.showToUser) {
                // График показываем как есть: пересказ длинного списка медленный и может исказить цифры.
                turn += AgentMessage(Role.ASSISTANT, "Показал график пользователю целиком.")
                history += turn
                return result.text
            }
        }
        history += turn
        return lastToolText
    }

    /**
     * Генерация с запасом по длине: если переписка не помещается в память модели,
     * забываем самые старые сообщения, а не начинаем разговор заново.
     */
    private suspend fun generateFitting(system: String, turn: List<AgentMessage>, onChunk: (String) -> Unit) {
        while (true) {
            try {
                assistant.generate(AgentPrompt.render(system, history.takeLast(HISTORY) + turn)).collect(onChunk)
                return
            } catch (e: LocalAssistant.PromptTooLong) {
                if (history.isEmpty()) throw e
                repeat(maxOf(2, history.size / 2).coerceAtMost(history.size)) { history.removeAt(0) }
                // Переписка должна начинаться с вопроса пользователя.
                while (history.isNotEmpty() && history.first().role != Role.USER) history.removeAt(0)
            }
        }
    }

    private fun compact(text: String): String =
        if (text.length <= TOOL_MEMORY) text else text.take(TOOL_MEMORY).substringBeforeLast('\n') + "\n… (дальше — то же по следующим месяцам)"

    private suspend fun tools(): LoanTools {
        val today = LocalDate.now()
        val loanList = loans.observeAll().first()
        val income = monthlyIncome(incomes.observeAll().first(), transactions.observeAll().first(), YearMonth.from(today)).amount
        val paydays = prefs.getString(KEY_PAYDAYS, null).orEmpty().split(",").mapNotNull { it.trim().toIntOrNull() }
        return LoanTools(loanList, income, paydays, today)
    }

    private fun statusFor(tool: String) = when (tool) {
        "payoff_plan" -> "Считаю план погашения…"
        "compare_strategies" -> "Сравниваю стратегии…"
        "payment_schedule" -> "Составляю график…"
        "save_paydays" -> "Запоминаю дни зарплаты…"
        else -> "Считаю…"
    }

    private fun replaceLast(text: String, pending: Boolean, status: String? = null) = _state.update {
        it.copy(messages = it.messages.dropLast(1) + ChatMessage(text, fromUser = false, pending = pending, status = status))
    }

    private companion object {
        const val KEY_PAYDAYS = "paydays"
        const val MAX_STEPS = 4
        const val HISTORY = 12
        const val TOOL_MEMORY = 1200
    }
}
