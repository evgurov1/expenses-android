package com.evgurov.expenses.ui.loans

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.evgurov.expenses.assistant.AssistantAvailability
import com.evgurov.expenses.assistant.LocalAssistant
import com.evgurov.expenses.assistant.ModelDownload
import com.evgurov.expenses.domain.model.Kopecks
import com.evgurov.expenses.domain.repository.IncomeRepository
import com.evgurov.expenses.domain.repository.LoanRepository
import com.evgurov.expenses.domain.repository.TransactionRepository
import com.evgurov.expenses.domain.usecase.AdvisorPrompt
import com.evgurov.expenses.domain.usecase.ChatTurn
import com.evgurov.expenses.domain.usecase.PaydaySchedule
import com.evgurov.expenses.domain.usecase.PayoffPlanner
import com.evgurov.expenses.domain.usecase.monthlyIncome
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

data class ChatMessage(val text: String, val fromUser: Boolean, val pending: Boolean = false)

data class AssistantUiState(
    val availability: AssistantAvailability = AssistantAvailability.CHECKING,
    val downloadProgress: Float? = null,
    val downloadError: String? = null,
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

    private val prefs = context.getSharedPreferences("assistant", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(AssistantUiState())
    val state: StateFlow<AssistantUiState> = _state.asStateFlow()

    private val history = mutableListOf<ChatTurn>()

    /** Мы спросили дни зарплаты: следующий короткий ответ вроде «5 и 20» — это они. */
    private var awaitingPaydays = false
    private var job: Job? = null

    /** Доплата в месяц, от которой считаются советы, если в вопросе нет своей суммы. */
    var extraMonthly: Kopecks = 10_000_00

    init {
        viewModelScope.launch { _state.update { it.copy(availability = assistant.availability()) } }
    }

    fun editDraft(text: String) = _state.update { it.copy(draft = text) }

    fun download() {
        _state.update { it.copy(availability = AssistantAvailability.DOWNLOADING, downloadError = null, downloadProgress = 0f) }
        viewModelScope.launch {
            assistant.download().collect { event ->
                when (event) {
                    is ModelDownload.Progress -> _state.update {
                        it.copy(downloadProgress = if (event.totalBytes > 0) event.downloadedBytes.toFloat() / event.totalBytes else null)
                    }
                    ModelDownload.Done -> _state.update {
                        it.copy(availability = AssistantAvailability.READY, downloadProgress = null)
                    }
                    is ModelDownload.Failed -> _state.update {
                        it.copy(availability = AssistantAvailability.DOWNLOADABLE, downloadError = event.message, downloadProgress = null)
                    }
                }
            }
        }
    }

    fun ask(question: String = _state.value.draft) {
        val q = question.trim()
        if (q.isEmpty() || _state.value.busy) return
        _state.update {
            it.copy(
                draft = "",
                busy = true,
                messages = it.messages + ChatMessage(q, fromUser = true) + ChatMessage("", fromUser = false, pending = true),
            )
        }
        job = viewModelScope.launch {
            val days = PaydaySchedule.paydaysFromText(q, expectingDays = awaitingPaydays)
            if (AdvisorPrompt.asksForSchedule(q) || days.isNotEmpty()) {
                // График считаем сами: модель путает даты и суммы, а здесь нужна точность до рубля.
                val text = runCatching { scheduleAnswer(q) }.getOrElse { e -> "Не получилось составить график: ${e.message}" }
                history += ChatTurn(q, text)
                finish(text)
                return@launch
            }
            awaitingPaydays = false
            val prompt = runCatching { buildPrompt(q) }.getOrElse { e ->
                finish("Не получилось собрать данные по кредитам: ${e.message}")
                return@launch
            }
            val answer = StringBuilder()
            runCatching {
                assistant.answer(prompt).collect { chunk ->
                    // Поток может отдавать и новые куски, и весь текст целиком — поддерживаем оба варианта.
                    if (answer.isNotEmpty() && chunk.startsWith(answer)) answer.setLength(0)
                    answer.append(chunk)
                    replaceLast(answer.toString().trim(), pending = true)
                }
            }.onFailure { e ->
                finish("Модель не ответила: ${e.message ?: e.javaClass.simpleName}")
                return@launch
            }
            val text = answer.toString().trim().ifEmpty { "Не нашёл, что ответить. Попробуйте переформулировать вопрос." }
            history += ChatTurn(q, text)
            finish(text)
        }
    }

    private suspend fun scheduleAnswer(question: String): String {
        PaydaySchedule.paydaysFromText(question, expectingDays = awaitingPaydays).takeIf { it.isNotEmpty() }?.let { days ->
            prefs.edit().putString(PAYDAYS, days.joinToString(",")).apply()
        }
        val paydays = prefs.getString(PAYDAYS, null).orEmpty().split(",").mapNotNull { it.trim().toIntOrNull() }
        // Без дней зарплаты сразу показываем график по датам списания и спрашиваем дни.
        awaitingPaydays = paydays.isEmpty()
        val today = LocalDate.now()
        val loanList = loans.observeAll().first()
        val extra = AdvisorPrompt.extraFromQuestion(question) ?: extraMonthly
        val advice = PayoffPlanner.advise(loanList, extra, today = today)
        val schedule = PaydaySchedule.build(advice.plan, paydays, today)
        if (schedule.isEmpty()) return "Нет действующих кредитов — расписывать нечего."
        return PaydaySchedule.describe(schedule, paydays, loanList, extra, advice.order.firstOrNull(), today)
    }

    private suspend fun buildPrompt(question: String): String {
        val today = LocalDate.now()
        val loanList = loans.observeAll().first()
        val income = monthlyIncome(incomes.observeAll().first(), transactions.observeAll().first(), YearMonth.from(today)).amount
        val advice = PayoffPlanner.advise(loanList, extraMonthly, income = income, today = today)
        val whatIf = AdvisorPrompt.extraFromQuestion(question)
            ?.takeIf { it != extraMonthly }
            ?.let { it to PayoffPlanner.advise(loanList, it, income = income, today = today) }
        val facts = AdvisorPrompt.facts(loanList, advice, extraMonthly, income, today, whatIf)
        return AdvisorPrompt.build(facts, history, question)
    }

    private companion object {
        const val PAYDAYS = "paydays"
    }

    private fun replaceLast(text: String, pending: Boolean) = _state.update {
        it.copy(messages = it.messages.dropLast(1) + ChatMessage(text, fromUser = false, pending = pending))
    }

    private fun finish(text: String) {
        replaceLast(text, pending = false)
        _state.update { it.copy(busy = false) }
    }
}
