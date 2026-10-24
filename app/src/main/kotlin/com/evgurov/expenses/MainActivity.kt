package com.evgurov.expenses

import android.os.Bundle
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.evgurov.expenses.ui.ExpensesNavHost
import com.evgurov.expenses.ui.theme.ExpensesTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Тема всегда светлая, поэтому значки в системных панелях всегда тёмные.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        setContent {
            ExpensesTheme {
                ExpensesNavHost()
            }
        }
    }
}
