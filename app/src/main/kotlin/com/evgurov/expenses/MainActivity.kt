package com.evgurov.expenses

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.evgurov.expenses.ui.ExpensesNavHost
import com.evgurov.expenses.ui.theme.ExpensesTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ExpensesTheme {
                ExpensesNavHost()
            }
        }
    }
}
