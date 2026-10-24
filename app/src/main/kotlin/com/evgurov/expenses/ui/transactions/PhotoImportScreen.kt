package com.evgurov.expenses.ui.transactions

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.evgurov.expenses.domain.model.Category
import com.evgurov.expenses.domain.model.Money
import com.evgurov.expenses.domain.model.TransactionKind
import com.evgurov.expenses.ui.components.PillChip
import com.evgurov.expenses.ui.components.SignedAmount
import com.evgurov.expenses.ui.components.appFieldColors
import com.evgurov.expenses.ui.components.label
import com.evgurov.expenses.ui.components.shortTitle
import java.time.Instant
import java.time.ZoneOffset

/** Операции со скриншотов банка: приложение распознаёт их, предлагает категории и просит проверить. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoImportScreen(
    onDone: () -> Unit,
    viewModel: PhotoImportViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val categories by viewModel.allCategories.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<PhotoDraft?>(null) }
    var asked by rememberSaveable { mutableStateOf(false) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(MAX_PHOTOS)) { uris ->
        if (uris.isEmpty() && state.drafts.isEmpty()) onDone() else viewModel.addPhotos(uris)
    }
    val launchPicker = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }

    // Сразу просим выбрать фото — ради этого экран и открыли.
    LaunchedEffect(Unit) {
        if (!asked) {
            asked = true
            launchPicker()
        }
    }
    LaunchedEffect(state.saved) { if (state.saved != null) onDone() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Операции с фото") },
                navigationIcon = {
                    IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад") }
                },
                actions = {
                    IconButton(onClick = launchPicker, enabled = !state.busy) {
                        Icon(Icons.Filled.AddPhotoAlternate, contentDescription = "Добавить фото")
                    }
                },
            )
        },
        bottomBar = {
            if (state.drafts.isNotEmpty()) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    if (state.withoutCategory > 0) {
                        Text(
                            "Выберите категорию у операций без неё: осталось ${state.withoutCategory}.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }
                    Button(
                        onClick = viewModel::save,
                        enabled = !state.busy && state.toSave.isNotEmpty() && state.withoutCategory == 0,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Добавить операций: ${state.toSave.size}") }
                }
            }
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 16.dp)) {
            if (state.busy || state.status != null) {
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(16.dp),
                    ) {
                        if (state.busy) CircularProgressIndicator(Modifier.padding(4.dp))
                        Text(state.status ?: "Сохраняю…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (state.drafts.isNotEmpty()) {
                item {
                    Text(
                        "Проверьте операции. Нажмите на строку, чтобы исправить сумму, дату или категорию.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
            items(state.drafts, key = { it.id }) { draft ->
                DraftRow(draft, onToggle = { viewModel.toggle(draft.id) }, onClick = { editing = draft })
                HorizontalDivider()
            }
        }
    }

    editing?.let { draft ->
        DraftDialog(
            draft = draft,
            categories = categories,
            onSave = {
                viewModel.update(it)
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun DraftRow(draft: PhotoDraft, onToggle: () -> Unit, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(end = 16.dp, top = 8.dp, bottom = 8.dp),
    ) {
        Checkbox(checked = draft.selected, onCheckedChange = { onToggle() })
        Column(Modifier.weight(1f)) {
            Text(draft.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                listOfNotNull(draft.date.shortTitle(), draft.card).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            when {
                draft.hint != null && !draft.selected -> Text(
                    draft.hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                draft.category == null -> Text(
                    "Выберите категорию",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                else -> Text(draft.category, style = MaterialTheme.typography.labelMedium)
            }
        }
        SignedAmount(draft.amount, draft.kind)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun DraftDialog(
    draft: PhotoDraft,
    categories: List<Category>,
    onSave: (PhotoDraft) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(draft.name) }
    var amountText by remember { mutableStateOf(Money.format(draft.amount, currencySymbol = "").trim()) }
    var kind by remember { mutableStateOf(draft.kind) }
    var category by remember { mutableStateOf(draft.category) }
    var date by remember { mutableStateOf(draft.date) }
    var pickDate by remember { mutableStateOf(false) }
    val amount = Money.parse(amountText)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Операция") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    colors = appFieldColors(),
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Название") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    colors = appFieldColors(),
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Сумма, ₽") },
                    singleLine = true,
                    isError = amount == null || amount <= 0,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                val kinds = listOf(TransactionKind.EXPENSE, TransactionKind.INCOME)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    kinds.forEachIndexed { index, k ->
                        SegmentedButton(
                            selected = kind == k,
                            onClick = {
                                if (kind != k) category = null
                                kind = k
                            },
                            shape = SegmentedButtonDefaults.itemShape(index, kinds.size),
                        ) { Text(k.label) }
                    }
                }
                OutlinedButton(onClick = { pickDate = true }) { Text(date.shortTitle()) }
                Text("Категория", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    categories.filter { it.kind == kind }.forEach { c ->
                        PillChip(selected = category == c.name, onClick = { category = c.name }, label = c.name)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = amount != null && amount > 0 && name.isNotBlank(),
                onClick = {
                    onSave(
                        draft.copy(
                            name = name.trim(),
                            amount = amount ?: draft.amount,
                            kind = kind,
                            category = category,
                            date = date,
                            selected = true,
                            categoryChosen = draft.categoryChosen || category != draft.category,
                        ),
                    )
                },
            ) { Text("Готово") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )

    if (pickDate) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    pickDate = false
                }) { Text("Готово") }
            },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text("Отмена") } },
        ) { DatePicker(state = pickerState) }
    }
}

private const val MAX_PHOTOS = 10
