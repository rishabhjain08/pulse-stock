package com.pulsestock.app.ui.accounts

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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

    // Entrance animation trigger
    var contentVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { contentVisible = true }

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
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        // Entrance: fade in + slide up — 10% offset for subtlety, spring for natural feel.
        AnimatedVisibility(
            visible = contentVisible,
            enter = fadeIn() + slideInVertically(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
                initialOffsetY = { it / 10 },
            ),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = innerPadding.calculateTopPadding() + 12.dp,
                    bottom = innerPadding.calculateBottomPadding() + 16.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // ── Connected Services — shown first so it's always discoverable ─
                item { AccountsSectionHeader(title = "Connected Services") }

                item {
                    SplitwiseCard(
                        isConnected = state.isSplitwiseConnected,
                        isConnecting = state.isSplitwiseConnecting,
                        onConnect = vm::connectSplitwise,
                        onDisconnect = vm::disconnectSplitwise,
                    )
                }

                // ── Bank Accounts ────────────────────────────────────────────────
                item {
                    Spacer(Modifier.height(4.dp))
                    AccountsSectionHeader(
                        title = "Bank Accounts",
                        trailing = {
                            if (state.institutions.isNotEmpty()) {
                                if (state.isSyncing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                    )
                                } else {
                                    // M3 IconButton provides a 48dp×48dp touch target by default.
                                    IconButton(
                                        onClick = vm::sync,
                                        modifier = Modifier.semantics {
                                            contentDescription = "Sync all accounts"
                                        },
                                    ) {
                                        Icon(
                                            Icons.Default.Sync,
                                            // contentDescription carried by parent semantics block.
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }
                                }
                            }
                        },
                    )
                }

                items(state.institutions, key = { it.institution.institutionId }) { iwa ->
                    val id = iwa.institution.institutionId
                    InstitutionCard(
                        iwa = iwa,
                        currencyFmt = currencyFmt,
                        isSyncing = id in state.syncingIds,
                        onSync = { vm.syncInstitution(id) },
                        onDisconnect = { vm.disconnect(id) },
                    )
                }

                item {
                    AddBankRow(onClick = vm::requestLinkToken)
                }
            }
        }
    }
}

// ── Section header ─────────────────────────────────────────────────────────────

@Composable
private fun AccountsSectionHeader(
    title: String,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
        )
        trailing()
    }
}

// ── Add bank CTA row ──────────────────────────────────────────────────────────

@Composable
private fun AddBankRow(onClick: () -> Unit) {
    // Card(onClick=...) provides the correct M3 state-layer ripple on interactive tap.
    // secondaryContainer draws attention to the CTA without being as assertive as primaryContainer.
    // shapes.medium = 12dp — this is a list-level action row, not a section container.
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                // Decorative — action described by adjacent text label.
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = "Connect bank account",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

// ── Splitwise service card ────────────────────────────────────────────────────

@Composable
private fun SplitwiseCard(
    isConnected: Boolean,
    isConnecting: Boolean,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
) {
    // shapes.large = 16dp — SplitwiseCard is a standalone section-level service card.
    // Tonal elevation 0dp; containerColor provides all needed differentiation from background.
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Splitwise",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (isConnected) "Connected" else "Track shared expense reimbursements",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isConnected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(12.dp))
            if (isConnected) {
                OutlinedButton(onClick = onDisconnect) {
                    Text("Disconnect")
                }
            } else {
                Button(onClick = onConnect, enabled = !isConnecting) {
                    if (isConnecting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Text("Connect")
                    }
                }
            }
        }
    }
}

// ── Institution card ──────────────────────────────────────────────────────────

@Composable
private fun InstitutionCard(
    iwa: InstitutionWithAccounts,
    currencyFmt: NumberFormat,
    isSyncing: Boolean,
    onSync: () -> Unit,
    onDisconnect: () -> Unit,
) {
    val lastSyncedMs = iwa.accounts.maxOfOrNull { it.lastRefreshed } ?: 0L

    // shapes.large = 16dp — InstitutionCard is a section container holding multiple account rows.
    // Tonal elevation 0dp; surfaceContainerLow provides the needed lift over the page background.
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.Default.AccountBalance,
                    // Decorative — institution name text is the accessible label.
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = iwa.institution.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .weight(1f),
                )
                if (isSyncing) {
                    // Spinner replaces both action buttons during sync to prevent double-taps.
                    CircularProgressIndicator(
                        modifier = Modifier
                            .padding(horizontal = 10.dp)
                            .size(18.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    // M3 IconButton = 48dp×48dp touch target by default — no explicit size needed.
                    IconButton(onClick = onSync) {
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = "Sync ${iwa.institution.name}",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                IconButton(onClick = onDisconnect) {
                    Icon(
                        imageVector = Icons.Default.LinkOff,
                        contentDescription = "Disconnect ${iwa.institution.name}",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            Text(
                text = "Last synced: ${formatSyncTime(lastSyncedMs)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 28.dp, top = 2.dp, bottom = 4.dp),
            )

            if (iwa.accounts.isNotEmpty()) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
                iwa.accounts.forEachIndexed { index, account ->
                    AccountRow(account, currencyFmt)
                    if (index < iwa.accounts.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 4.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                        )
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

// ── Account balance row ────────────────────────────────────────────────────────

@Composable
private fun AccountRow(account: AccountEntity, currencyFmt: NumberFormat) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = account.name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
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
                    color = MaterialTheme.colorScheme.onSurface,
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
