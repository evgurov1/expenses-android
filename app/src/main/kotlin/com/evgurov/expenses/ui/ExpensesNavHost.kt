package com.evgurov.expenses.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.evgurov.expenses.ui.accounts.AccountsScreen
import com.evgurov.expenses.ui.categories.CategoriesScreen
import com.evgurov.expenses.ui.loans.AddLoanScreen
import com.evgurov.expenses.ui.loans.ImportLoanScreen
import com.evgurov.expenses.ui.loans.LoanDetailsScreen
import com.evgurov.expenses.ui.loans.AssistantScreen
import com.evgurov.expenses.ui.loans.LoansScreen
import com.evgurov.expenses.ui.loans.PayoffScreen
import com.evgurov.expenses.ui.transactions.AddTransactionScreen
import com.evgurov.expenses.ui.transactions.TransactionsScreen

private object Routes {
    const val LOANS = "loans"
    const val ADD_LOAN = "loans/add"
    const val IMPORT_LOAN = "loans/import"
    const val PAYOFF = "loans/payoff"
    const val ASSISTANT = "loans/assistant"
    const val LOAN_DETAILS = "loans/{loanId}"
    fun loanDetails(id: Long) = "loans/$id"
    const val TRANSACTIONS = "transactions"
    const val ACCOUNTS = "accounts"
    const val CATEGORIES = "categories"
    const val ADD_TRANSACTION = "transactions/add"
}

private data class TopLevel(val route: String, val label: String, val icon: ImageVector)

private val topLevel = listOf(
    TopLevel(Routes.LOANS, "Кредиты", Icons.Filled.CreditCard),
    TopLevel(Routes.TRANSACTIONS, "Операции", Icons.AutoMirrored.Filled.ReceiptLong),
    TopLevel(Routes.ACCOUNTS, "Счета", Icons.Filled.AccountBalanceWallet),
    TopLevel(Routes.CATEGORIES, "Категории", Icons.Filled.Category),
)

@Composable
fun ExpensesNavHost() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            if (topLevel.any { it.route == currentRoute }) {
                NavigationBar {
                    topLevel.forEach { item ->
                        NavigationBarItem(
                            selected = currentRoute == item.route,
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(item.icon, contentDescription = null) },
                            label = { Text(item.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.LOANS,
            modifier = Modifier.padding(bottom = padding.calculateBottomPadding()),
        ) {
            composable(Routes.LOANS) {
                LoansScreen(
                    onAddLoan = { navController.navigate(Routes.ADD_LOAN) },
                    onImportLoan = { navController.navigate(Routes.IMPORT_LOAN) },
                    onOpenLoan = { navController.navigate(Routes.loanDetails(it)) },
                    onPayoff = { navController.navigate(Routes.PAYOFF) },
                )
            }
            composable(Routes.PAYOFF) {
                PayoffScreen(
                    onBack = { navController.popBackStack() },
                    onAskAssistant = { navController.navigate(Routes.ASSISTANT) },
                )
            }
            composable(Routes.ASSISTANT) {
                AssistantScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.IMPORT_LOAN) {
                ImportLoanScreen(
                    onBack = { navController.popBackStack() },
                    onRecognized = {
                        navController.navigate(Routes.ADD_LOAN) {
                            popUpTo(Routes.IMPORT_LOAN) { inclusive = true }
                        }
                    },
                )
            }
            composable(Routes.ADD_LOAN) {
                AddLoanScreen(onDone = { navController.popBackStack() })
            }
            composable(
                Routes.LOAN_DETAILS,
                arguments = listOf(navArgument("loanId") { type = NavType.LongType }),
            ) {
                LoanDetailsScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.TRANSACTIONS) {
                TransactionsScreen(onAddClick = { navController.navigate(Routes.ADD_TRANSACTION) })
            }
            composable(Routes.ACCOUNTS) { AccountsScreen() }
            composable(Routes.CATEGORIES) { CategoriesScreen() }
            composable(Routes.ADD_TRANSACTION) {
                AddTransactionScreen(onDone = { navController.popBackStack() })
            }
        }
    }
}
