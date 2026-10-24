package com.evgurov.expenses

import android.app.Application
import com.evgurov.expenses.reminders.ReminderWorker
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class ExpensesApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ReminderWorker.schedule(this)
    }
}
