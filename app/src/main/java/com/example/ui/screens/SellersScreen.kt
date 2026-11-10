package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Seller
import com.example.data.model.SellerStatus
import com.example.data.repository.ProductDetailItem
import com.example.data.repository.SellerDetailItem
import com.example.i18n.AppStrings
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.LapakViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SellersScreen(
    viewModel: LapakViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lang by viewModel.currentLanguage.collectAsState()
    val shopProfile by viewModel.shopProfile.collectAsState()
    val sellersWithStats by viewModel.sellersWithStats.collectAsState()
    val allProductsWithDetails by viewModel.productsWithDetails.collectAsState()
    val payoutReceiptState by viewModel.lastSellerPaymentReceipt.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf("Semua") }

    // Dialog states
    var showAddSellerDialog by remember { mutableStateOf(false) }
    var leaveTargetSeller by remember { mutableStateOf<Seller?>(null) }
    var paymentTargetSellerDetail by remember { mutableStateOf<SellerDetailItem?>(null) }
    var stockDetailTargetSeller by remember { mutableStateOf<SellerDetailItem?>(null) }
    var editTargetSeller by remember { mutableStateOf<Seller?>(null) }
    var deleteTargetSeller by remember { mutableStateOf<Seller?>(null) }

    val filteredSellers = remember(sellersWithStats, searchQuery, statusFilter) {
        sellersWithStats.filter { item ->
            val matchQuery = searchQuery.isBlank() ||
                item.seller.name.contains(searchQuery, ignoreCase = true) ||
                item.seller.whatsappNumber.contains(searchQuery, ignoreCase = true)

            val matchStatus = when (statusFilter) {
                "Aktif" -> item.seller.status == SellerStatus.AKTIF
                "Libur" -> item.seller.status == SellerStatus.LIBUR
                "Nonaktif" -> item.seller.status == SellerStatus.NONAKTIF
                else -> true
            }

            matchQuery && matchStatus
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = AppStrings.get("sellers_title", lang),
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
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddSellerDialog = true },
                containerColor = EmeraldPrimary,
                contentColor = Color.White,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.size(50.dp)
            ) {
                Icon(
                    Icons.Default.PersonAdd,
                    contentDescription = AppStrings.get("sellers_btn_add", lang),
                    modifier = Modifier.size(26.dp)
                )
            }
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
                    placeholder = { Text(AppStrings.get("sellers_search_hint", lang)) },
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

            // Status Filter Chips
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("Semua", "Aktif", "Libur", "Nonaktif").forEach { status ->
                        FilterChip(
                            selected = statusFilter == status,
                            onClick = { statusFilter = status },
                            label = { Text(status, style = MaterialTheme.typography.labelSmall) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = EmeraldPrimary,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }
            }

            // Sellers List
            if (filteredSellers.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.PeopleOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "Tidak ada penyetor yang cocok",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(filteredSellers) { item ->
                    val sellerProducts = remember(allProductsWithDetails, item.seller.id) {
                        allProductsWithDetails.filter { it.product.sellerId == item.seller.id }
                    }
                    SellerCard(
                        item = item,
                        productsWithStock = sellerProducts,
                        lang = lang,
                        onSetLeave = { leaveTargetSeller = item.seller },
                        onReactivate = {
                            viewModel.reactivateSeller(item.seller.id)
                            Toast.makeText(context, "${item.seller.name} telah diaktifkan kembali!", Toast.LENGTH_SHORT).show()
                        },
                        onRecordPayment = { paymentTargetSellerDetail = item },
                        onViewReceipt = { viewModel.showLatestPaymentReceipt(item) },
                        onViewStock = { stockDetailTargetSeller = item },
                        onEdit = { editTargetSeller = item.seller },
                        onDelete = { deleteTargetSeller = item.seller }
                    )
                }
            }

            item {
                Spacer(Modifier.height(80.dp)) // Space for FAB
            }
        }
    }

    // Add Seller Dialog
    if (showAddSellerDialog) {
        var name by remember { mutableStateOf("") }
        var wa by remember { mutableStateOf("") }
        var address by remember { mutableStateOf("") }
        var notes by remember { mutableStateOf("") }
        var err by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showAddSellerDialog = false },
            title = { Text(AppStrings.get("sellers_btn_add", lang), fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Nama Penyetor") },
                        placeholder = { Text("Contoh: Dapur Mama Farida") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = wa,
                        onValueChange = { wa = it },
                        label = { Text("Nomor WhatsApp") },
                        placeholder = { Text("Contoh: 08123456789") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = address,
                        onValueChange = { address = it },
                        label = { Text("Alamat / Keterangan") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Catatan") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (err != null) {
                        Spacer(Modifier.height(6.dp))
                        Text(err!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (name.isBlank()) {
                            err = "Nama penyetor tidak boleh kosong"
                        } else {
                            viewModel.addSeller(name, wa, address, notes) {
                                showAddSellerDialog = false
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
                ) {
                    Text("Simpan")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddSellerDialog = false }) {
                    Text("Batal")
                }
            }
        )
    }

    // Seller Leave Dialog
    leaveTargetSeller?.let { seller ->
        SellerLeaveDialog(
            seller = seller,
            lang = lang,
            onConfirm = { start, end, reason ->
                viewModel.setSellerLeaveStatus(
                    sellerId = seller.id,
                    newStatus = SellerStatus.LIBUR,
                    startDate = start,
                    endDate = end,
                    reason = reason
                )
                leaveTargetSeller = null
                Toast.makeText(context, "${seller.name} telah diatur status LIBUR", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { leaveTargetSeller = null }
        )
    }

    var soldItemsForPayment by remember { mutableStateOf<List<com.example.data.model.SoldItemSummary>>(emptyList()) }

    LaunchedEffect(paymentTargetSellerDetail) {
        val target = paymentTargetSellerDetail
        if (target != null) {
            soldItemsForPayment = viewModel.getSoldItemsSummary(target.seller.id)
        } else {
            soldItemsForPayment = emptyList()
        }
    }

    // Payout Settlement Dialog
    paymentTargetSellerDetail?.let { sellerDetail ->
        SellerPaymentDialog(
            sellerDetail = sellerDetail,
            lang = lang,
            soldItems = soldItemsForPayment,
            onConfirm = { amount, notes, method ->
                viewModel.recordSellerPayment(
                    sellerId = sellerDetail.seller.id,
                    amount = amount,
                    notes = notes,
                    method = method
                ) {
                    paymentTargetSellerDetail = null
                    Toast.makeText(context, "Pembayaran hak ${LapakViewModel.formatRupiah(amount)} berhasil dicatat!", Toast.LENGTH_SHORT).show()
                }
            },
            onDismiss = { paymentTargetSellerDetail = null }
        )
    }

    // Seller Payment Receipt Dialog (Print & Share Payout Voucher)
    payoutReceiptState?.let { state ->
        SellerPaymentReceiptDialog(
            payment = state.payment,
            seller = state.seller,
            remainingBalance = state.remainingBalance,
            shopProfile = shopProfile,
            lang = lang,
            soldItems = state.soldItems,
            onPrint = {
                viewModel.printSellerPayoutReceipt(
                    context = context,
                    payment = state.payment,
                    seller = state.seller,
                    remainingBalance = state.remainingBalance,
                    soldItems = state.soldItems
                )
            },
            onShareWhatsApp = { text ->
                viewModel.shareTextToWhatsApp(context, text, state.seller.whatsappNumber)
            },
            onShareGeneral = { text ->
                viewModel.shareTextGeneral(context, text)
            },
            onCopy = { text ->
                viewModel.copyToClipboard(context, text)
            },
            onDismiss = {
                viewModel.clearSellerPaymentReceipt()
            }
        )
    }

    // Edit Seller Dialog
    editTargetSeller?.let { seller ->
        EditSellerDialog(
            seller = seller,
            lang = lang,
            onConfirm = { updated ->
                viewModel.updateSeller(updated) {
                    editTargetSeller = null
                    Toast.makeText(context, "Data penyetor ${updated.name} berhasil diperbarui", Toast.LENGTH_SHORT).show()
                }
            },
            onDismiss = { editTargetSeller = null }
        )
    }

    // Delete Seller Dialog
    deleteTargetSeller?.let { seller ->
        DeleteConfirmDialog(
            title = "Hapus Penyetor",
            message = "Apakah Anda yakin ingin menghapus penyetor '${seller.name}'? Produk dan data terkait akan ikut terhapus.",
            onConfirm = {
                viewModel.deleteSeller(seller) {
                    deleteTargetSeller = null
                    Toast.makeText(context, "Penyetor ${seller.name} berhasil dihapus", Toast.LENGTH_SHORT).show()
                }
            },
            onDismiss = { deleteTargetSeller = null }
        )
    }

    // Seller Stock Detail Dialog
    stockDetailTargetSeller?.let { sellerDetail ->
        val sellerProducts = remember(allProductsWithDetails, sellerDetail.seller.id) {
            allProductsWithDetails.filter { it.product.sellerId == sellerDetail.seller.id }
        }
        SellerStockDetailDialog(
            sellerDetail = sellerDetail,
            productsWithStock = sellerProducts,
            lang = lang,
            onDismiss = { stockDetailTargetSeller = null }
        )
    }
}

@Composable
fun SellerCard(
    item: SellerDetailItem,
    productsWithStock: List<ProductDetailItem>,
    lang: String,
    onSetLeave: () -> Unit,
    onReactivate: () -> Unit,
    onRecordPayment: () -> Unit,
    onViewReceipt: () -> Unit,
    onViewStock: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var isExpandedInline by remember { mutableStateOf(false) }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = if (item.remainingBalance > 0)
            BorderStroke(1.dp, if (item.paymentStatus == "Belum Dibayar") Color(0xFFFFCDD2) else Color(0xFFFFE0B2))
        else null,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header Row: Seller Name, WhatsApp, Status Badge, Edit & Delete Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 6.dp)) {
                    Text(
                        text = item.seller.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (item.seller.whatsappNumber.isNotEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                            Icon(Icons.Default.Phone, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(13.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = item.seller.whatsappNumber,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    StatusBadge(status = item.seller.status)

                    // Prominent alert badge if there is unpaid remaining balance
                    if (item.remainingBalance > 0) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (item.paymentStatus == "Belum Dibayar") Color(0xFFFFEBEE) else Color(0xFFFFF3E0),
                            border = BorderStroke(1.dp, if (item.paymentStatus == "Belum Dibayar") Color(0xFFFFCDD2) else Color(0xFFFFE0B2))
                        ) {
                            Text(
                                text = if (item.paymentStatus == "Belum Dibayar") "Belum Bayar" else "Sisa Hak",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (item.paymentStatus == "Belum Dibayar") Color(0xFFD32F2F) else Color(0xFFE65100),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Edit Penyetor",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Default.DeleteOutline,
                            contentDescription = "Hapus Penyetor",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // Leave information if on leave
            if (item.seller.status == SellerStatus.LIBUR) {
                Spacer(Modifier.height(8.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = TealContainer.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(
                            text = "Status: Libur (${item.seller.leaveReason ?: "Sedang libur sementara"})",
                            style = MaterialTheme.typography.labelSmall,
                            color = OnTealContainer,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Stok lama tetap dapat dijual, setoran baru ditolak secara default.",
                            style = MaterialTheme.typography.labelSmall,
                            color = OnTealContainer
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // Operational & Financial Metrics Grid
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                    .padding(10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Stok Aktif: Clickable card/button to open dialog and view products & stock
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(EmeraldContainer.copy(alpha = 0.65f))
                        .clickable(onClick = onViewStock)
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            "Stok Aktif:",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Icon(
                            Icons.Default.Visibility,
                            contentDescription = "Buka Stok",
                            tint = EmeraldDark,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                    Text(
                        text = "${item.totalStock} item (${item.products.size} menu)",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = EmeraldDark
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            "Terjual: ${item.totalSoldQty}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            " • Buka ↗",
                            style = MaterialTheme.typography.labelSmall,
                            color = EmeraldDark,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Column {
                    Text("Total Hak Terjual:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = LapakViewModel.formatRupiah(item.totalRights),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text("Dibayar: ${LapakViewModel.formatRupiah(item.totalPaid)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                // Sisa Hak column: Orange if Dibayar Sebagian, Merah if Belum Dibayar, Hijau if Lunas
                val sisaHakTextColor = when {
                    item.remainingBalance <= 0 || item.paymentStatus == "Lunas" -> EmeraldPrimary
                    item.paymentStatus == "Belum Dibayar" -> Color(0xFFD32F2F) // Merah
                    else -> Color(0xFFE65100) // Orange
                }
                val sisaHakContainer = when {
                    item.remainingBalance <= 0 || item.paymentStatus == "Lunas" -> EmeraldContainer.copy(alpha = 0.45f)
                    item.paymentStatus == "Belum Dibayar" -> Color(0xFFFFEBEE) // Merah muda lembut
                    else -> Color(0xFFFFF3E0) // Orange muda lembut
                }
                val sisaHakBorder = when {
                    item.remainingBalance <= 0 || item.paymentStatus == "Lunas" -> EmeraldLight.copy(alpha = 0.25f)
                    item.paymentStatus == "Belum Dibayar" -> Color(0xFFFFCDD2)
                    else -> Color(0xFFFFE0B2)
                }

                Column(
                    horizontalAlignment = Alignment.End,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(sisaHakContainer)
                        .border(1.dp, sisaHakBorder, RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Text(
                        "Sisa Hak:",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (item.remainingBalance > 0) sisaHakTextColor else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (item.remainingBalance > 0) FontWeight.Bold else FontWeight.Normal
                    )
                    Text(
                        text = LapakViewModel.formatRupiah(item.remainingBalance),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = sisaHakTextColor
                    )
                    Text(
                        text = item.paymentStatus,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = sisaHakTextColor
                    )
                }
            }

            // Quick toggle for inline viewing of menu and stock
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { isExpandedInline = !isExpandedInline }
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isExpandedInline) "Sembunyikan Rincian Menu" else "Lihat ${item.products.size} Menu & Stok",
                    style = MaterialTheme.typography.labelSmall,
                    color = EmeraldPrimary,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.width(4.dp))
                Icon(
                    imageVector = if (isExpandedInline) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = EmeraldPrimary,
                    modifier = Modifier.size(16.dp)
                )
            }

            // Inline accordion preview of products & stock
            AnimatedVisibility(visible = isExpandedInline) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = EmeraldContainer.copy(alpha = 0.35f)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp, bottom = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Daftar Menu & Stok (${productsWithStock.size} menu):",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = EmeraldDark
                            )
                            TextButton(
                                onClick = onViewStock,
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                            ) {
                                Text("Pop-up ↗", style = MaterialTheme.typography.labelSmall, color = EmeraldDark, fontWeight = FontWeight.Bold)
                            }
                        }

                        if (productsWithStock.isEmpty()) {
                            Text(
                                text = "Belum ada produk aktif.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                productsWithStock.forEach { prodItem ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                            Text(
                                                text = prodItem.product.name,
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                text = "Jual: ${LapakViewModel.formatRupiah(prodItem.product.sellingPrice)} | Hak: ${LapakViewModel.formatRupiah(prodItem.product.sellerShare)}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Surface(
                                            color = if (prodItem.currentStock > 0) EmeraldContainer else MaterialTheme.colorScheme.surfaceVariant,
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = if (prodItem.currentStock > 0) "${prodItem.currentStock} ${prodItem.product.unit}" else "Habis",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = if (prodItem.currentStock > 0) EmeraldDark else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onRecordPayment,
                    modifier = Modifier.weight(1.3f),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = when {
                            item.remainingBalance <= 0 || item.paymentStatus == "Lunas" -> EmeraldPrimary
                            item.paymentStatus == "Belum Dibayar" -> Color(0xFFD32F2F) // Merah
                            else -> Color(0xFFE65100) // Orange
                        }
                    )
                ) {
                    Icon(Icons.Default.Payment, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = if (item.remainingBalance > 0) "Bayar Hak" else AppStrings.get("sellers_btn_pay_settlement", lang),
                        maxLines = 1,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                OutlinedButton(
                    onClick = onViewReceipt,
                    modifier = Modifier.weight(0.9f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.ReceiptLong, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Struk", maxLines = 1, style = MaterialTheme.typography.labelSmall)
                }

                if (item.seller.status == SellerStatus.LIBUR) {
                    OutlinedButton(
                        onClick = onReactivate,
                        modifier = Modifier.weight(0.9f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(AppStrings.get("sellers_btn_reactivate", lang), maxLines = 1, style = MaterialTheme.typography.labelSmall)
                    }
                } else {
                    OutlinedButton(
                        onClick = onSetLeave,
                        modifier = Modifier.weight(0.9f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Set Libur", maxLines = 1, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}
