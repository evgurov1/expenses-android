package com.evgurov.expenses.settings

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Дни месяца, когда приходит зарплата: по ним помощник строит график, а напоминания говорят, что отложить. */
@Singleton
class PaydayStore @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("assistant", Context.MODE_PRIVATE)
    private val _paydays = MutableStateFlow(read())
    val paydays: StateFlow<List<Int>> = _paydays.asStateFlow()

    fun set(days: List<Int>) {
        val clean = days.filter { it in 1..31 }.distinct().sorted()
        prefs.edit().putString(KEY, clean.joinToString(",")).apply()
        _paydays.value = clean
    }

    private fun read(): List<Int> =
        prefs.getString(KEY, null).orEmpty().split(",").mapNotNull { it.trim().toIntOrNull() }

    private companion object {
        const val KEY = "paydays"
    }
}
