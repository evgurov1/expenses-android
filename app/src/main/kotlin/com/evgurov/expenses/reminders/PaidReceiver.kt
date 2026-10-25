package com.evgurov.expenses.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat

/** Кнопка «Уже оплатил» в напоминании: запоминаем платежи и убираем уведомление. */
class PaidReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val keys = intent.getStringArrayExtra(EXTRA_KEYS).orEmpty().toList()
        PaidPayments.add(context, keys)
        NotificationManagerCompat.from(context).cancel(intent.getIntExtra(EXTRA_NOTIFICATION, 0))
    }

    companion object {
        const val EXTRA_KEYS = "keys"
        const val EXTRA_NOTIFICATION = "notification"
    }
}

/** Платежи, которые пользователь отметил оплаченными. Хранятся на телефоне. */
object PaidPayments {
    private const val PREFS = "reminders"
    private const val PAID = "paid"

    fun all(context: Context): Set<String> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getStringSet(PAID, emptySet()).orEmpty()

    fun add(context: Context, keys: List<String>) {
        if (keys.isEmpty()) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().putStringSet(PAID, all(context) + keys).apply()
    }
}
