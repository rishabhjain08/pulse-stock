package com.pulsestock.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pulsestock.app.data.poarvault.SplitwiseAuthBus
import com.pulsestock.app.ui.SettingsScreen
import com.pulsestock.app.ui.accounts.AccountsScreen
import com.pulsestock.app.ui.finances.FinancesScreen
import com.pulsestock.app.ui.finances.FinancesViewModel
import com.pulsestock.app.ui.finances.ReconcileScreen
import com.pulsestock.app.ui.theme.PulseStockTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            PulseStockTheme {
                MainScreen()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        PulseLog.d("MainActivity", "onNewIntent action=${intent.action} data=${intent.data}")
        val uri = intent.data ?: run {
            PulseLog.w("MainActivity", "onNewIntent: no data URI")
            return
        }
        PulseLog.d("MainActivity", "deep link: scheme=${uri.scheme} host=${uri.host} path=${uri.path} query=${uri.query}")
        if (uri.scheme == "pulsestock" && uri.host == "splitwise") {
            val code = uri.getQueryParameter("code") ?: run {
                PulseLog.e("MainActivity", "Splitwise callback missing 'code' param — full URI: $uri")
                return
            }
            PulseLog.d("MainActivity", "Splitwise code received (${code.length} chars), delivering to bus")
            SplitwiseAuthBus.deliver(code)
        }
    }
}

@Composable
private fun MainScreen() {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var showReconcile by rememberSaveable { mutableStateOf(false) }
    val financesVm: FinancesViewModel = viewModel()
    val financesState by financesVm.uiState.collectAsState()

    if (showReconcile) {
        ReconcileScreen(
            vm = financesVm,
            onBack = { showReconcile = false },
        )
        return
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.ShowChart, contentDescription = null) },
                    label = { Text("Stocks") },
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.AccountBalance, contentDescription = null) },
                    label = { Text("Accounts") },
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = {
                        BadgedBox(badge = {
                            if (financesState.inboxCount > 0) {
                                Badge { Text(financesState.inboxCount.toString()) }
                            }
                        }) {
                            Icon(Icons.Default.CreditCard, contentDescription = null)
                        }
                    },
                    label = { Text("Finances") },
                )
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .consumeWindowInsets(innerPadding),
        ) {
            when (selectedTab) {
                0 -> SettingsScreen(modifier = Modifier.padding(innerPadding))
                1 -> AccountsScreen(modifier = Modifier.padding(innerPadding))
                2 -> FinancesScreen(
                    vm = financesVm,
                    onReconcile = { showReconcile = true },
                    modifier = Modifier.padding(innerPadding),
                )
            }
        }
    }
}
