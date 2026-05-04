package com.pulsestock.app.ui.finances

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pulsestock.app.data.poarvault.ExpenseWithLinks
import com.pulsestock.app.data.poarvault.PlaidTransaction
import com.pulsestock.app.data.poarvault.SplitwiseExpense
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReconcileScreen(
    vm: FinancesViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)

    val state by vm.uiState.collectAsState()
    val snackbarState = remember { SnackbarHostState() }
    val currencyFmt = remember { NumberFormat.getCurrencyInstance(Locale.US) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(state.error) {
        val msg = state.error ?: return@LaunchedEffect
        snackbarState.showSnackbar(msg)
        vm.dismissError()
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarState) },
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Splitwise Expenses")
                        if (state.inboxCount > 0) {
                            Spacer(Modifier.width(8.dp))
                            Badge { Text(state.inboxCount.toString()) }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (state.isSyncing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp).padding(end = 4.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        IconButton(onClick = vm::sync) {
                            Icon(
                                Icons.Default.Sync,
                                contentDescription = "Sync",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
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
            // Filter toggle
            item {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = !state.showAll,
                        onClick = { if (state.showAll) vm.toggleShowAll() },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        label = {
                            val unlinkedCount = state.allWithLinks.count { it.isUnlinked || it.isPendingAutoMatch }
                            Text(if (unlinkedCount > 0) "To Link ($unlinkedCount)" else "To Link")
                        },
                    )
                    SegmentedButton(
                        selected = state.showAll,
                        onClick = { if (!state.showAll) vm.toggleShowAll() },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        label = { Text("All (${state.allWithLinks.size})") },
                    )
                }
            }

            if (state.displayedList.isEmpty()) {
                item {
                    Text(
                        text = if (state.showAll) "No Splitwise expenses loaded. Tap sync to fetch them."
                               else "All caught up! No expenses left to link.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            } else {
                items(state.displayedList, key = { it.expense.id }) { item ->
                    ExpenseCard(
                        item = item,
                        currencyFmt = currencyFmt,
                        onAccept = { vm.acceptMatch(item.expense.id) },
                        onReject = { vm.rejectMatch(item.expense.id) },
                        onLink = { vm.openLinkSheet(item) },
                        onDismiss = { vm.dismiss(item.expense.id) },
                        onUnlink = { plaidId -> vm.unlinkTransaction(item.expense.id, plaidId) },
                    )
                }

                item {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        if (state.isLoadingMore) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        } else {
                            TextButton(onClick = vm::loadMore) {
                                Text("Load older expenses")
                            }
                        }
                    }
                }
            }
        }

        val linkSheet = state.linkSheet
        if (linkSheet != null) {
            ModalBottomSheet(
                onDismissRequest = vm::closeLinkSheet,
                sheetState = sheetState,
            ) {
                LinkBottomSheet(
                    linkSheet = linkSheet,
                    currencyFmt = currencyFmt,
                    onLink = { plaidId -> vm.linkTransaction(linkSheet.expense.id, plaidId) },
                    onDismiss = vm::closeLinkSheet,
                )
            }
        }
    }
}

@Composable
private fun ExpenseCard(
    item: ExpenseWithLinks,
    currencyFmt: NumberFormat,
    onAccept: () -> Unit,
    onReject: () -> Unit,
    onLink: () -> Unit,
    onDismiss: () -> Unit,
    onUnlink: (String) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header row: description + amount
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.expense.description,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = item.expense.date,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = currencyFmt.format(item.expense.totalAmount),
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                )
            }

            when {
                // ⚡ Auto-match pending approval
                item.isPendingAutoMatch -> {
                    val matchedTx = item.linkedTransactions.firstOrNull()
                    if (matchedTx != null) {
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Bolt,
                                contentDescription = "Auto-matched",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = matchedTx.name,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = onReject) {
                                Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Reject")
                            }
                            Spacer(Modifier.width(4.dp))
                            Button(onClick = onAccept) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Accept")
                            }
                        }
                    }
                }

                // Reconciled: show linked transactions with unlink buttons
                item.isReconciled -> {
                    Spacer(Modifier.height(8.dp))
                    item.linkedTransactions.forEach { tx ->
                        LinkedTxRow(tx = tx, currencyFmt = currencyFmt, onUnlink = { onUnlink(tx.transactionId) })
                    }
                    Spacer(Modifier.height(4.dp))
                    TextButton(
                        onClick = onLink,
                        modifier = Modifier.align(Alignment.End),
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Add link…")
                    }
                }

                // Unlinked: standard Link / Dismiss actions
                else -> {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        OutlinedButton(onClick = onDismiss) { Text("Dismiss") }
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = onLink) { Text("Link…") }
                    }
                }
            }
        }
    }
}

@Composable
private fun LinkedTxRow(tx: PlaidTransaction, currencyFmt: NumberFormat, onUnlink: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = tx.name, style = MaterialTheme.typography.bodySmall)
            Text(
                text = tx.date,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = currencyFmt.format(tx.amount),
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
        )
        IconButton(onClick = onUnlink, modifier = Modifier.size(32.dp)) {
            Icon(
                Icons.Default.Close,
                contentDescription = "Unlink",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LinkBottomSheet(
    linkSheet: LinkSheetState,
    currencyFmt: NumberFormat,
    onLink: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val expense = linkSheet.expense
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 32.dp),
    ) {
        Text(
            text = "Link Transaction",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "${expense.description} · ${currencyFmt.format(expense.totalAmount)} · ${expense.date}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp, bottom = 12.dp),
        )

        if (linkSheet.suggested.isNotEmpty()) {
            Text(
                text = "Suggested",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            linkSheet.suggested.forEach { tx ->
                TxRow(tx = tx, currencyFmt = currencyFmt, onClick = { onLink(tx.transactionId) })
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        }

        Text(
            text = "All Recent",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        if (linkSheet.all.isEmpty()) {
            Text(
                text = "No transactions found. Sync first.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            linkSheet.all.forEach { tx ->
                TxRow(tx = tx, currencyFmt = currencyFmt, onClick = { onLink(tx.transactionId) })
            }
        }
    }
}

@Composable
private fun TxRow(tx: PlaidTransaction, currencyFmt: NumberFormat, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = tx.name, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = tx.date,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = currencyFmt.format(tx.amount),
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
        )
    }
}
