package com.evgurov.expenses.ui.categories

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.evgurov.expenses.ui.components.appFieldColors
import com.evgurov.expenses.ui.components.PillChip
import com.evgurov.expenses.domain.model.Category
import com.evgurov.expenses.domain.model.TransactionKind
import com.evgurov.expenses.ui.components.ConfirmDeleteDialog
import com.evgurov.expenses.ui.components.label

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(viewModel: CategoriesViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var showAdd by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<Category?>(null) }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbar.showSnackbar(it) }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Категории") }) },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAdd = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Добавить категорию")
            }
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            section("Расходы", state.expense) { toDelete = it }
            section("Доходы", state.income) { toDelete = it }
        }
    }

    if (showAdd) {
        AddCategoryDialog(
            onConfirm = { name, kind ->
                viewModel.add(name, kind)
                showAdd = false
            },
            onDismiss = { showAdd = false },
        )
    }

    toDelete?.let { category ->
        ConfirmDeleteDialog(
            title = "Удалить категорию «${category.name}»?",
            text = "Категорию, в которой есть операции, удалить нельзя.",
            onConfirm = {
                viewModel.delete(category)
                toDelete = null
            },
            onDismiss = { toDelete = null },
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.section(
    title: String,
    categories: List<Category>,
    onClick: (Category) -> Unit,
) {
    item(key = "header-$title") {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
        )
    }
    items(categories, key = { it.id }) { category ->
        ListItem(
            headlineContent = { Text(category.name) },
            modifier = Modifier.clickable { onClick(category) },
        )
        HorizontalDivider()
    }
}

@Composable
private fun AddCategoryDialog(
    onConfirm: (String, TransactionKind) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(TransactionKind.EXPENSE) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Новая категория") },
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
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TransactionKind.entries.forEach {
                        PillChip(selected = kind == it, onClick = { kind = it }, label = it.label)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name, kind) }, enabled = name.isNotBlank()) { Text("Добавить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}
