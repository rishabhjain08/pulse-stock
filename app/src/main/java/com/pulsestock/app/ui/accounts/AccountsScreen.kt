package com.pulsestock.app.ui.accounts

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.plaid.link.FastOpenPlaidLink
import com.pulsestock.app.PulseLog
import com.plaid.link.Plaid
import com.plaid.link.configuration.LinkTokenConfiguration
import com.plaid.link.result.LinkExit
import com.plaid.link.result.LinkSuccess
import com.pulsestock.app.data.poarvault.AccountEntity
import com.pulsestock.app.data.poarvault.InstitutionWithAccounts
import java.text.NumberFormat
import java.util.Locale

@Composable
fun AccountsScreen(modifier: Modifier = Modifier) {
    val vm: AccountsViewModel = viewModel()
    val state by vm.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarState = remember { SnackbarHostState() }
    val currencyFmt = remember { NumberFormat.getCurrencyInstance(Locale.US) }

    val launcher = rememberLauncherForActivityResult(FastOpenPlaidLink()) { result ->
        when (result) {
            is LinkSuccess -> {
                val institution = result.metadata.institution
                    ?: return@rememberLauncherForActivityResult
                vm.onLinkSuccess(result.publicToken, institution.id, institution.name)
            }
            is LinkExit -> Unit
        }
    }

    LaunchedEffect(Unit) {
        vm.linkToken.collect { token ->
            val config = LinkTokenConfiguration.Builder().token(token).build()
            launcher.launch(Plaid.create(context.applicationContext as android.app.Application, config))
        }
    }

    LaunchedEffect(Unit) {
        vm.launchUrl.collect { url ->
            PulseLog.d("AccountsScreen", "launching OAuth URL via Custom Tab: $url")
            // Custom Tab routes the pulsestock:// callback URI back to the app via the
            // Android intent system. A plain ACTION_VIEW intent opens Chrome, which blocks
            // the redirect to custom schemes and shows a 404.
            CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(url))
        }
    }

    LaunchedEffect(state.error) {
        val msg = state.error ?: return@LaunchedEffect
        snackbarState.showSnackbar(msg)
        vm.dismissError()
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarState) },
        floatingActionButton = {
            FloatingActionButton(onClick = vm::requestLinkToken) {
                Icon(Icons.Default.Add, contentDescription = "Connect a bank")
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        if (state.isInitialLoad) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = innerPadding.calculateTopPadding() + 8.dp,
                    bottom = innerPadding.calculateBottomPadding() + 8.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (state.institutions.isNotEmpty()) {
                    item {
                        SyncRow(isSyncing = state.isSyncing, onSync = vm::sync)
                    }
                }
                if (state.institutions.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AccountBalance,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = "No banks connected",
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Text(
                                    text = "Tap + to connect your first account",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                } else {
                    items(state.institutions, key = { it.institution.institutionId }) { iwa ->
                        val id = iwa.institution.institutionId
                        InstitutionCard(
                            iwa = iwa,
                            currencyFmt = currencyFmt,
                            isSyncing = state.isSyncing || id in state.syncingIds,
                            onSync = { vm.syncInstitution(id) },
                            onDisconnect = { vm.disconnect(id) },
                        )
                    }
                }
                item {
                    SplitwiseCard(
                        isConnected = state.isSplitwiseConnected,
                        isConnecting = state.isSplitwiseConnecting,
                        onConnect = vm::connectSplitwise,
                        onDisconnect = vm::disconnectSplitwise,
                    )
                }
            }
        }
    }
}

@Composable
private fun SplitwiseCard(
    isConnected: Boolean,
    isConnecting: Boolean,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Splitwise",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(4.dp))
            if (isConnected) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "Connected",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    OutlinedButton(onClick = onDisconnect) {
                        Text("Disconnect")
                    }
                }
            } else {
                Text(
                    text = "Link your Splitwise account to reconcile shared expenses with credit card transactions.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onConnect,
                    enabled = !isConnecting,
                ) {
                    if (isConnecting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Text("Connect Splitwise")
                    }
                }
            }
        }
    }
}

@Composable
private fun SyncRow(isSyncing: Boolean, onSync: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End,
    ) {
        if (isSyncing) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        } else {
            IconButton(onClick = onSync) {
                Icon(
                    imageVector = Icons.Default.Sync,
                    contentDescription = "Sync balances",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            text = if (isSyncing) "Syncing…" else "Sync balances",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 4.dp),
        )
    }
}

@Composable
private fun InstitutionCard(
    iwa: InstitutionWithAccounts,
    currencyFmt: NumberFormat,
    isSyncing: Boolean,
    onSync: () -> Unit,
    onDisconnect: () -> Unit,
) {
    val lastSyncedMs = iwa.accounts.maxOfOrNull { it.lastRefreshed } ?: 0L

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.Default.AccountBalance,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = iwa.institution.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 8.dp).weight(1f),
                )
                if (isSyncing) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(horizontal = 12.dp).size(20.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    IconButton(onClick = onSync) {
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = "Sync ${iwa.institution.name}",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                IconButton(onClick = onDisconnect) {
                    Icon(
                        imageVector = Icons.Default.LinkOff,
                        contentDescription = "Disconnect ${iwa.institution.name}",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Text(
                text = "Last synced: ${formatSyncTime(lastSyncedMs)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 32.dp, bottom = 4.dp),
            )

            if (iwa.accounts.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                iwa.accounts.forEachIndexed { index, account ->
                    AccountRow(account, currencyFmt)
                    if (index < iwa.accounts.lastIndex) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    }
                }
            }
        }
    }
}

private fun formatSyncTime(timestamp: Long): String {
    if (timestamp == 0L) return "Never"
    val diff = System.currentTimeMillis() - timestamp
    return when {
        diff < 60_000L -> "Just now"
        diff < 3_600_000L -> "${diff / 60_000}m ago"
        diff < 86_400_000L -> "${diff / 3_600_000}h ago"
        else -> "${diff / 86_400_000}d ago"
    }
}

@Composable
private fun AccountRow(account: AccountEntity, currencyFmt: NumberFormat) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = account.name,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = buildString {
                    append(account.type.replaceFirstChar { it.uppercase() })
                    if (!account.subtype.isNullOrBlank()) append(" · ${account.subtype}")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            val current = account.currentBalance
            if (current != null) {
                Text(
                    text = currencyFmt.format(current),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
            }
            val available = account.availableBalance
            if (available != null && available != current) {
                Text(
                    text = "${currencyFmt.format(available)} avail",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
