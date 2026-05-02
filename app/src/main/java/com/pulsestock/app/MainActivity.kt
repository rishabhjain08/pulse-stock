package com.pulsestock.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import com.pulsestock.app.ui.SettingsScreen
import com.pulsestock.app.ui.accounts.AccountsScreen
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
}

@Composable
private fun MainScreen() {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

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
            }
        },
    ) { innerPadding ->
        // consumeWindowInsets prevents inner Scaffolds from double-counting system bar insets
        // that the outer Scaffold already handled in innerPadding.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .consumeWindowInsets(innerPadding),
        ) {
            when (selectedTab) {
                0 -> SettingsScreen(modifier = Modifier.padding(innerPadding))
                1 -> AccountsScreen(modifier = Modifier.padding(innerPadding))
            }
        }
    }
}
