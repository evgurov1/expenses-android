package com.evgurov.expenses.ui.transactions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.evgurov.expenses.domain.model.Money
import com.evgurov.expenses.ui.accounts.AccountWithBalance
import com.evgurov.expenses.ui.components.AppCard
import com.evgurov.expenses.ui.components.RussianLocale
import java.time.LocalDate
import java.time.format.TextStyle

/** Сколько денег сейчас по счетам и приблизительный прогноз трат до конца месяца. */
@Composable
fun MoneyNowCard(overview: MoneyOverview, onAccount: (AccountWithBalance) -> Unit) {
    if (overview.accounts.isEmpty() && overview.forecast == null) return
    AppCard(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp)) {
        Text("Сейчас на счетах", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(Money.format(overview.total), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
        overview.accounts.forEach { item ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clickable { onAccount(item) }.padding(vertical = 8.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(item.account.name, style = MaterialTheme.typography.bodyLarge)
                    if (item.account.knownBalance == null) {
                        Text(
                            "Нажмите, чтобы указать точный остаток",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text(Money.format(item.balance), style = MaterialTheme.typography.bodyLarge)
            }
        }

        overview.forecast?.let { f ->
            val month = LocalDate.now().month.getDisplayName(TextStyle.FULL_STANDALONE, RussianLocale).lowercase(RussianLocale)
            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("Прогноз трат на $month", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Text("≈ ${Money.format(roundToHundreds(f.forecast))}", style = MaterialTheme.typography.titleMedium)
            }
            Text(
                "Уже потрачено ${Money.format(f.spent)}, в среднем ${Money.format(roundToHundreds(f.dailyRate))} в день " +
                    "без платежей по кредитам.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            if (overview.accounts.isNotEmpty()) {
                val left = overview.total - (f.forecast - f.spent)
                Text(
                    "К концу месяца останется ≈ ${Money.format(roundToHundreds(left))}, если не считать новых поступлений.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

/** Прогноз приблизительный — копейки и десятки рублей в нём только мешают. */
private fun roundToHundreds(kopecks: Long): Long = Math.round(kopecks / 10_000.0) * 10_000
