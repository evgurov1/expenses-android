package com.evgurov.expenses.ui.transactions

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.evgurov.expenses.domain.model.Money
import com.evgurov.expenses.domain.model.TransactionKind
import com.evgurov.expenses.ui.components.AppCard
import com.evgurov.expenses.ui.components.PillChip

// Цвета диаграммы — исключение из монохромной темы: категории нужно различать.
// Порядок фиксированный, проверен на различимость при дальтонизме.
private val SliceColors = listOf(
    Color(0xFF2A78D6),
    Color(0xFFEB6834),
    Color(0xFF1BAF7A),
    Color(0xFFEDA100),
    Color(0xFFE87BA4),
    Color(0xFF6250D6),
)
private val RestColor = Color(0xFFB8BCC4)

/** Крупные категории — своим цветом, мелкие — серым и на кольце одной долей. Подписи у всех свои. */
private fun sliceColor(index: Int) = SliceColors.getOrElse(index) { RestColor }

/** Диаграмма-кольцо по категориям, как в Т-Банке: под ней категории с суммами, нажатие фильтрует список. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryChart(
    kind: TransactionKind,
    slices: List<CategorySlice>,
    selected: CategorySlice?,
    onKind: (TransactionKind) -> Unit,
    onSlice: (CategorySlice) -> Unit,
    modifier: Modifier = Modifier,
) {
    AppCard(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillChip(kind == TransactionKind.EXPENSE, { onKind(TransactionKind.EXPENSE) }, "Траты")
            PillChip(kind == TransactionKind.INCOME, { onKind(TransactionKind.INCOME) }, "Поступления")
        }
        if (slices.isEmpty()) {
            Text(
                if (kind == TransactionKind.EXPENSE) "Трат за этот период нет." else "Поступлений за этот период нет.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp),
            )
            return@AppCard
        }
        val total = slices.sumOf { it.amount }
        Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
            Donut(slices, selected, Modifier.size(200.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    selected?.name ?: if (kind == TransactionKind.EXPENSE) "Всего трат" else "Всего поступлений",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(130.dp),
                )
                Text(
                    Money.format(selected?.amount ?: total),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                selected?.let {
                    Text(
                        "${percent(it.amount, total)} %",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            slices.forEachIndexed { index, slice ->
                SliceChip(slice, sliceColor(index), slice == selected) { onSlice(slice) }
            }
        }
    }
}

@Composable
private fun Donut(slices: List<CategorySlice>, selected: CategorySlice?, modifier: Modifier) {
    val total = slices.sumOf { it.amount }.toFloat()
    Canvas(modifier) {
        val width = 28.dp.toPx()
        val inset = width / 2 + 4.dp.toPx()
        val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
        // Мелкие категории (без своего цвета) рисуем одной серой долей.
        val big = slices.take(SliceColors.size)
        val small = slices.drop(SliceColors.size)
        val arcs = big.mapIndexed { i, s -> Triple(s.amount, sliceColor(i), s == selected) } +
            if (small.isEmpty()) emptyList() else listOf(Triple(small.sumOf { it.amount }, RestColor, selected in small))
        val gap = if (arcs.size > 1) Math.toDegrees((2.dp.toPx() / (arcSize.width / 2)).toDouble()).toFloat() else 0f
        var start = -90f
        arcs.forEach { (amount, color, isSelected) ->
            val sweep = amount / total * 360f
            val dimmed = selected != null && !isSelected
            drawArc(
                color = color.copy(alpha = if (dimmed) 0.25f else 1f),
                startAngle = start + gap / 2,
                sweepAngle = (sweep - gap).coerceAtLeast(0.5f),
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = if (isSelected) width + 6.dp.toPx() else width),
            )
            start += sweep
        }
    }
}

@Composable
private fun SliceChip(slice: CategorySlice, color: Color, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (selected) colors.primary else colors.surfaceContainerLowest,
        border = BorderStroke(1.dp, if (selected) colors.primary else colors.outlineVariant),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Box(Modifier.size(10.dp).background(color, CircleShape))
            Spacer(Modifier.width(8.dp))
            Text(
                "${slice.name}  ${Money.format(slice.amount)}",
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) colors.onPrimary else colors.onSurface,
            )
        }
    }
}

private fun percent(part: Long, total: Long): String =
    if (total == 0L) "0" else "%.0f".format(part * 100.0 / total).let { if (it == "0") "<1" else it }
