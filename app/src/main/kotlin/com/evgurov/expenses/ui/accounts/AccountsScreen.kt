package com.evgurov.expenses.ui.accounts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.evgurov.expenses.ui.components.appFieldColors
import com.evgurov.expenses.ui.components.PillChip
import com.evgurov.expenses.domain.model.Account
import com.evgurov.expenses.domain.model.AccountType
import com.evgurov.expenses.domain.model.Kopecks
import com.evgurov.expenses.domain.model.Money
import com.evgurov.expenses.ui.components.ConfirmDeleteDialog
import com.evgurov.expenses.ui.components.label

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsScreen(viewModel: AccountsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showAdd by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<Account?>(null) }
    var editing by remember { mutableStateOf<AccountWithBalance?>(null) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Счета") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAdd = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Добавить счёт")
            }
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item {
                ListItem(
                    headlineContent = { Text("Всего") },
                    trailingContent = {
                        Text(Money.format(state.total), style = MaterialTheme.typography.titleMedium)
                    },
                )
                HorizontalDivider()
            }
            items(state.accounts, key = { it.account.id }) { item ->
                ListItem(
                    headlineContent = { Text(item.account.name) },
                    supportingContent = { Text(item.account.type.label) },
                    trailingContent = { Text(Money.format(item.balance)) },
                    modifier = Modifier.clickable { editing = item },
                )
                HorizontalDivider()
            }
        }
    }

    if (showAdd) {
        AddAccountDialog(
            onConfirm = { name, type, balance ->
                viewModel.add(name, type, balance)
                showAdd = false
            },
            onDismiss = { showAdd = false },
        )
    }

    editing?.let { item ->
        BalanceDialog(
            account = item.account,
            current = item.balance,
            onSave = {
                viewModel.setBalance(item.account, it)
                editing = null
            },
            onDismiss = { editing = null },
            onDelete = {
                editing = null
                toDelete = item.account
            },
        )
    }

    toDelete?.let { account ->
        ConfirmDeleteDialog(
            title = "Удалить счёт «${account.name}»?",
            text = "Вместе со счётом удалятся все его операции.",
            onConfirm = {
                viewModel.delete(account)
                toDelete = null
            },
            onDismiss = { toDelete = null },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddAccountDialog(
    onConfirm: (String, AccountType, Kopecks) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(AccountType.CARD) }
    var balanceText by remember { mutableStateOf("") }
    val balance = if (balanceText.isBlank()) 0L else Money.parse(balanceText)
    val valid = name.isNotBlank() && balance != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Новый счёт") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    colors = appFieldColors(),
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Название") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AccountType.entries.forEach {
                        PillChip(selected = type == it, onClick = { type = it }, label = it.label)
                    }
                }
                OutlinedTextField(
                    colors = appFieldColors(),
                    value = balanceText,
                    onValueChange = { balanceText = it },
                    label = { Text("Начальный остаток, ₽") },
                    singleLine = true,
                    isError = balance == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name, type, balance ?: 0L) }, enabled = valid) { Text("Добавить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}
