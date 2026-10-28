package com.evgurov.expenses.ui.loans

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.evgurov.expenses.assistant.AssistantAvailability
import com.evgurov.expenses.ui.components.AppCard
import com.evgurov.expenses.ui.components.PillChip
import com.evgurov.expenses.ui.components.appFieldColors

private val suggestions = listOf(
    "Какой кредит гасить первым?",
    "Что если доплачивать 15 000 в месяц?",
    "Стоит ли рефинансировать?",
    "Когда я закрою все кредиты?",
    "Построй график платежей",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantScreen(
    onBack: () -> Unit,
    viewModel: AssistantViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    LaunchedEffect(state.messages.size, state.messages.lastOrNull()?.text?.length) {
        if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.size)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Помощник") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
            )
        },
        bottomBar = {
            if (state.availability == AssistantAvailability.READY) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.background)
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    OutlinedTextField(
                        colors = appFieldColors(),
                        value = state.draft,
                        onValueChange = viewModel::editDraft,
                        placeholder = { Text("Спросите про ваши кредиты") },
                        shape = RoundedCornerShape(24.dp),
                        maxLines = 4,
                        modifier = Modifier.weight(1f),
                    )
                    FilledIconButton(
                        onClick = { viewModel.ask() },
                        enabled = !state.busy && state.draft.isNotBlank(),
                        modifier = Modifier.padding(start = 8.dp).size(48.dp),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Отправить")
                    }
                }
            }
        },
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { Header(state, onDownload = viewModel::download, onAsk = viewModel::ask) }
            itemsIndexed(state.messages) { index, message -> Bubble(message) { confirm -> viewModel.resolveAction(index, confirm) } }
        }
    }
}

@Composable
private fun Header(state: AssistantUiState, onDownload: () -> Unit, onAsk: (String) -> Unit) {
    AppCard {
        Text(
            "Отвечает модель Qwen3 4B прямо в телефоне: вопросы и данные о кредитах никуда не отправляются. " +
                "Помощник сам запускает расчёты приложения — график, досрочку, сравнение стратегий.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        when (state.availability) {
            AssistantAvailability.CHECKING -> Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 16.dp),
            ) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Text("Проверяю модель…", modifier = Modifier.padding(start = 12.dp))
            }
            AssistantAvailability.DOWNLOADABLE -> {
                Text(
                    "Модель нужно один раз скачать: 2,5 ГБ с сайта Hugging Face, лучше по Wi-Fi. " +
                        "Скачивается только сам файл модели, ваши данные не передаются. Дальше всё работает без интернета.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 12.dp),
                )
                state.error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
                }
                Button(onClick = onDownload, modifier = Modifier.fillMaxWidth().padding(top = 16.dp).height(48.dp)) {
                    Text("Скачать модель")
                }
            }
            AssistantAvailability.DOWNLOADING -> {
                Text("Скачиваю модель…", modifier = Modifier.padding(top = 12.dp))
                val progress = state.downloadProgress
                if (progress != null) {
                    LinearProgressIndicator(
                        progress = { progress },
                        strokeCap = StrokeCap.Round,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                } else {
                    LinearProgressIndicator(strokeCap = StrokeCap.Round, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                }
            }
            AssistantAvailability.LOADING -> Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 16.dp),
            ) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Text("Загружаю модель в память…", modifier = Modifier.padding(start = 12.dp))
            }
            AssistantAvailability.UNAVAILABLE -> Text(
                (state.error ?: "Модель недоступна.") + " Советы и план погашения есть на экране «Как закрыть быстрее».",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 12.dp),
            )
            AssistantAvailability.READY -> if (state.warming) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 16.dp)) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text(
                        "Готовлю помощника: читаю данные о кредитах. Можно уже задать вопрос — отвечу, как закончу.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }
                if (state.messages.isEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 16.dp)) {
                        suggestions.forEach { q -> PillChip(selected = false, onClick = { onAsk(q) }, label = q) }
                    }
                }
            } else if (state.messages.isEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 16.dp)) {
                    suggestions.forEach { q -> PillChip(selected = false, onClick = { onAsk(q) }, label = q) }
                }
            }
        }
    }
}

@Composable
private fun Bubble(message: ChatMessage, onAction: (Boolean) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(Modifier.fillMaxWidth(), contentAlignment = if (message.fromUser) Alignment.CenterEnd else Alignment.CenterStart) {
        Box(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .background(
                    if (message.fromUser) colors.primary else colors.surfaceContainerLowest,
                    RoundedCornerShape(18.dp),
                )
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            if (message.pending && message.text.isEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    message.status?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceVariant,
                            modifier = Modifier.padding(start = 10.dp),
                        )
                    }
                }
            } else {
                Column {
                    Text(
                        message.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (message.fromUser) colors.onPrimary else colors.onSurface,
                    )
                    if (message.action != null) {
                        when (message.actionState) {
                            ActionState.WAITING -> Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(top = 12.dp),
                            ) {
                                Button(onClick = { onAction(true) }) { Text("Подтвердить") }
                                OutlinedButton(onClick = { onAction(false) }) { Text("Отмена") }
                            }
                            ActionState.SAVED -> Text(
                                "Сохранено",
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                            ActionState.CANCELLED -> Text(
                                "Отменено",
                                style = MaterialTheme.typography.labelLarge,
                                color = colors.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
