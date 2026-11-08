package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.LapakTransaction
import com.example.data.model.TransactionItem
import com.example.i18n.AppStrings
import com.example.ui.components.CancelTransactionDialog
import com.example.ui.components.ReceiptCustomerDialog
import com.example.ui.components.TransactionAdminDetailDialog
import com.example.ui.theme.*
import com.example.ui.viewmodel.LapakViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(
    viewModel: LapakViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lang by viewModel.currentLanguage.collectAsState()
    val shopProfile by viewModel.shopProfile.collectAsState()
    val transactions by viewModel.allTransactions.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedMethod by remember { mutableStateOf("Semua") }

    // Dialog states
    var customerReceiptTarget by remember { mutableStateOf<LapakTransaction?>(null) }
    var adminDetailTarget by remember { mutableStateOf<LapakTransaction?>(null) }
    var cancelTarget by remember { mutableStateOf<LapakTransaction?>(null) }

    val filteredTransactions = remember(transactions, searchQuery, selectedMethod) {
        transactions.filter { trx ->
            val matchQuery = searchQuery.isBlank() ||
                trx.transactionNumber.contains(searchQuery, ignoreCase = true)
            val matchMethod = selectedMethod == "Semua" || trx.paymentMethod == selectedMethod
            matchQuery && matchMethod
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = AppStrings.get("history_title", lang),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = EmeraldPrimary
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = EmeraldContainer,
                    titleContentColor = EmeraldPrimary
                )
            )
        },
        modifier = modifier
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Search Bar
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text(AppStrings.get("history_search_hint", lang)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )
            }

            // Payment Method Filter Chips
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("Semua", "Tunai", "QRIS", "Transfer").forEach { method ->
                        FilterChip(
                            selected = selectedMethod == method,
                            onClick = { selectedMethod = method },
                            label = { Text(method, style = MaterialTheme.typography.labelSmall) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = EmeraldPrimary,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }
            }

            // Transaction History List
            if (filteredTransactions.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.ReceiptLong,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "Belum ada transaksi",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(filteredTransactions) { trx ->
                    TransactionCard(
                        trx = trx,
                        lang = lang,
                        onOpenCustomerReceipt = { customerReceiptTarget = trx },
                        onOpenAdminDetail = { adminDetailTarget = trx },
                        onCancel = { cancelTarget = trx }
                    )
                }
            }

            item {
                Spacer(Modifier.height(30.dp))
            }
        }
    }

    // Customer Receipt Dialog (NO SELLER NAME)
    customerReceiptTarget?.let { trx ->
        val itemsFlow = remember(trx.id) { viewModel.getTransactionItems(trx.id) }
        val items by itemsFlow.collectAsState(initial = emptyList())

        ReceiptCustomerDialog(
            transaction = trx,
            items = items,
            shopProfile = shopProfile,
            lang = lang,
            onDismiss = { customerReceiptTarget = null },
            onPrint = { viewModel.printCustomerReceipt(context, trx, items) },
            onShareWhatsApp = { text -> viewModel.shareTextToWhatsApp(context, text) },
            onShareGeneral = { text -> viewModel.shareTextGeneral(context, text) },
            onCopy = { text -> viewModel.copyToClipboard(context, text) }
        )
    }

    // Admin Internal Detail Dialog (SHOWS SELLER NAME & ADMIN PROFIT)
    adminDetailTarget?.let { trx ->
        val itemsFlow = remember(trx.id) { viewModel.getTransactionItems(trx.id) }
        val items by itemsFlow.collectAsState(initial = emptyList())

        TransactionAdminDetailDialog(
            transaction = trx,
            items = items,
            lang = lang,
            onDismiss = { adminDetailTarget = null }
        )
    }

    // Cancellation Dialog
    cancelTarget?.let { trx ->
        CancelTransactionDialog(
            transaction = trx,
            lang = lang,
            onConfirm = { reason ->
                viewModel.cancelTransaction(
                    trxId = trx.id,
                    reason = reason,
                    onSuccess = {
                        cancelTarget = null
                        Toast.makeText(context, "Transaksi berhasil dibatalkan & stok dipulihkan!", Toast.LENGTH_SHORT).show()
                    },
                    onError = { err ->
                        Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                    }
                )
            },
            onDismiss = { cancelTarget = null }
        )
    }
}

@Composable
fun TransactionCard(
    trx: LapakTransaction,
    lang: String,
    onOpenCustomerReceipt: () -> Unit,
    onOpenAdminDetail: () -> Unit,
    onCancel: () -> Unit
) {
    val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header Row: TRX Number & Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = trx.transactionNumber,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = sdf.format(Date(trx.timestamp)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (trx.isCancelled) {
                    Box(
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = AppStrings.get("history_cancelled", lang),
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .background(EmeraldContainer, RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = trx.paymentMethod,
                            color = EmeraldDark,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // Breakdown Summary: Total Omzet, Hak Penyetor, Laba Admin
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                    .padding(10.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Total Omzet:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = LapakViewModel.formatRupiah(trx.totalAmount),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${trx.totalItemsCount} item",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Column {
                    Text("Hak Penyetor:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = LapakViewModel.formatRupiah(trx.totalSellerShare),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = AmberTertiary
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text("Laba Admin:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = LapakViewModel.formatRupiah(trx.totalAdminProfit),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = EmeraldPrimary
                    )
                }
            }

            if (trx.isCancelled && !trx.cancellationReason.isNullOrBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Alasan Batal: ${trx.cancellationReason}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(Modifier.height(12.dp))

            // Action Buttons Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onOpenCustomerReceipt,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Receipt, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(AppStrings.get("history_btn_receipt_customer", lang), maxLines = 1, style = MaterialTheme.typography.labelSmall)
                }

                Button(
                    onClick = onOpenAdminDetail,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
                ) {
                    Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(AppStrings.get("history_btn_detail_admin", lang), maxLines = 1, style = MaterialTheme.typography.labelSmall)
                }

                if (!trx.isCancelled) {
                    IconButton(
                        onClick = onCancel,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            Icons.Default.Cancel,
                            contentDescription = "Batalkan",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}
