package com.evgurov.expenses.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.evgurov.expenses.MainActivity
import com.evgurov.expenses.R
import com.evgurov.expenses.domain.repository.LoanRepository
import com.evgurov.expenses.domain.model.TransactionKind
import com.evgurov.expenses.domain.repository.CategoryRepository
import com.evgurov.expenses.domain.repository.TransactionRepository
import com.evgurov.expenses.domain.usecase.MadePayment
import com.evgurov.expenses.domain.usecase.PaymentReminders
import com.evgurov.expenses.settings.PaydayStore
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/** Раз в день утром проверяет платежи и показывает напоминания. Всё считается на телефоне. */
class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun loans(): LoanRepository
        fun paydays(): PaydayStore
        fun transactions(): TransactionRepository
        fun categories(): CategoryRepository
    }

    override suspend fun doWork(): Result {
        val deps = EntryPointAccessors.fromApplication(applicationContext, Deps::class.java)
        val today = LocalDate.now()
        // Платежи по кредитам за последние полтора месяца: если платёж уже прошёл, о нём не напоминаем.
        val loanCategories = deps.categories().observeAll().first().filter { it.name == LOAN_PAYMENTS }.map { it.id }.toSet()
        val made = deps.transactions().observeBetween(today.minusDays(45), today).first()
            .filter { it.kind == TransactionKind.EXPENSE && it.categoryId in loanCategories }
            .map { MadePayment(it.date, it.amount) }
        val reminders = PaymentReminders.forDay(
            deps.loans().observeAll().first(),
            deps.paydays().paydays.value,
            today,
            paidKeys = PaidPayments.all(applicationContext),
            made = made,
        )
        reminders.forEach { show(applicationContext, it.id, it.title, it.text, it.paidKeys) }
        return Result.success()
    }

    companion object {
        private const val CHANNEL = "payments"
        private const val WORK = "payment-reminders"
        private const val HOUR = 9
        private const val LOAN_PAYMENTS = "Платежи по кредитам"

        /** Ежедневная проверка в 9 утра; повторный вызов ничего не меняет. */
        fun schedule(context: Context) {
            val now = LocalDateTime.now()
            var next = now.toLocalDate().atTime(HOUR, 0)
            if (!next.isAfter(now)) next = next.plusDays(1)
            val request = PeriodicWorkRequestBuilder<ReminderWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(Duration.between(now, next).toMinutes(), TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun show(context: Context, id: Int, title: String, text: String, paidKeys: List<String> = emptyList()) {
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
            val manager = context.getSystemService(NotificationManager::class.java)
            if (Build.VERSION.SDK_INT >= 26) {
                manager.createNotificationChannel(
                    NotificationChannel(CHANNEL, "Платежи по кредитам", NotificationManager.IMPORTANCE_DEFAULT),
                )
            }
            val open = PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE,
            )
            val builder = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_launcher_monochrome)
                .setContentTitle(title)
                .setContentText(text.lineSequence().first())
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(open)
                .setAutoCancel(true)
            if (paidKeys.isNotEmpty()) {
                val paid = PendingIntent.getBroadcast(
                    context, id,
                    Intent(context, PaidReceiver::class.java)
                        .putExtra(PaidReceiver.EXTRA_KEYS, paidKeys.toTypedArray())
                        .putExtra(PaidReceiver.EXTRA_NOTIFICATION, id),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
                builder.addAction(0, "Уже оплатил", paid)
            }
            NotificationManagerCompat.from(context).notify(id, builder.build())
        }
    }
}
