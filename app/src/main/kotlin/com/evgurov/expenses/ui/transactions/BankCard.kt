package com.evgurov.expenses.ui.transactions

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.evgurov.expenses.bank.SkippedNotification
import com.evgurov.expenses.domain.model.Category
import com.evgurov.expenses.domain.model.Money
import com.evgurov.expenses.domain.model.Transaction
import com.evgurov.expenses.domain.model.TransactionKind
import com.evgurov.expenses.ui.components.AppCard
import com.evgurov.expenses.ui.components.PillChip

/** Включение загрузки операций из уведомлений банков. */
@Composable
fun BankCard(
    enabled: Boolean,
    skipped: List<SkippedNotification>,
    statementStatus: String?,
    onPickStatement: () -> Unit,
    onClearSkipped: () -> Unit,
) {
    val context = LocalContext.current
    var showSkipped by remember { mutableStateOf(false) }
    AppCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Text("Операции из банков", style = MaterialTheme.typography.titleMedium)
        if (!enabled) {
            Text(
                "Приложение может само добавлять покупки и поступления из уведомлений Т-Банка, Сбера, ВТБ, Альфы, " +
                    "Озон Банка и Яндекс Банка и раскладывать их по категориям. Читаются только уведомления банков, " +
                    "всё остаётся на телефоне.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
            Button(
                onClick = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            ) { Text("Включить доступ к уведомлениям") }
            Text(
                "В списке выберите «Расходы». Если переключатель серый: Настройки → Приложения → Расходы → ⋮ → " +
                    "«Разрешить ограниченные настройки».",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        } else {
            Text(
                "Включено. Новые операции добавятся сами, прошлые уведомления банки не хранят. " +
                    "Нажмите на операцию, чтобы сменить категорию: в следующий раз для этого магазина выберу её же.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
            if (skipped.isNotEmpty()) {
                TextButton(onClick = { showSkipped = true }, modifier = Modifier.padding(top = 4.dp)) {
                    Text("Не распознал уведомлений: ${skipped.size}")
                }
            }
        }
        OutlinedButton(onClick = onPickStatement, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
            Text("Загрузить выписку (PDF)")
        }
        Text(
            statementStatus ?: "Прошлые операции — из выписки: в Т-Банке нажмите на карту → «Реквизиты и выписки», " +
                "выберите период и PDF. Повторная загрузка не создаёт дублей.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
    if (showSkipped) {
        AlertDialog(
            onDismissRequest = { showSkipped = false },
            title = { Text("Не распознал") },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        "В этих уведомлениях есть сумма, но я не понял, покупка это или поступление. " +
                            "Такие операции добавьте вручную.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    skipped.forEach { Text("${it.date} · ${it.bank}\n${it.text}", style = MaterialTheme.typography.bodyMedium) }
                }
            },
            confirmButton = { TextButton(onClick = { showSkipped = false }) { Text("Закрыть") } },
            dismissButton = {
                TextButton(onClick = {
                    onClearSkipped()
                    showSkipped = false
                }) { Text("Очистить") }
            },
        )
    }
}

/** Выбор категории для операции; отсюда же можно удалить операцию. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryDialog(
    transaction: Transaction,
    categories: List<Category>,
    onPick: (Long) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                (if (transaction.kind == TransactionKind.INCOME) "+" else "−") + Money.format(transaction.amount) +
                    transaction.note.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty(),
            )
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Категория", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(bottom = 8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    categories.forEach { category ->
                        PillChip(selected = category.id == transaction.categoryId, onClick = { onPick(category.id) }, label = category.name)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Готово") } },
        dismissButton = { TextButton(onClick = onDelete) { Text("Удалить") } },
    )
}
