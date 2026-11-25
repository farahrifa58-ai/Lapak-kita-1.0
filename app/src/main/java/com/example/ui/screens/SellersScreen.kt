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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.*
import com.example.data.repository.ProductDetailItem
import com.example.data.repository.SellerDetailItem
import com.example.i18n.AppStrings
import com.example.ui.components.*
import com.example.ui.theme.LocalLapakColors
import com.example.ui.viewmodel.LapakViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SellersScreen(
    viewModel: LapakViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lapakColors = LocalLapakColors.current
    val lang by viewModel.currentLanguage.collectAsState()
    val shopProfile by viewModel.shopProfile.collectAsState()
    val appSettings by viewModel.appSettings.collectAsState()
    val sellersWithStats by viewModel.sellersWithStats.collectAsState()
    val rawSellers by viewModel.rawSellers.collectAsState()
    val allProductsWithDetails by viewModel.productsWithDetails.collectAsState()
    val payoutReceiptState by viewModel.lastSellerPaymentReceipt.collectAsState()
    val isSyncingCloud by viewModel.isSyncingCloud.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf("Semua") }

    // Dialog states
    var showAddSellerDialog by remember { mutableStateOf(false) }
    var leaveTargetSeller by remember { mutableStateOf<Seller?>(null) }
    var paymentTargetSellerDetail by remember { mutableStateOf<SellerDetailItem?>(null) }
    var stockDetailTargetSeller by remember { mutableStateOf<SellerDetailItem?>(null) }
    var addStockTargetSellerId by remember { mutableStateOf<Long?>(null) }
    var webLinkTargetSeller by remember { mutableStateOf<Seller?>(null) }
    var editTargetSeller by remember { mutableStateOf<Seller?>(null) }
    var deleteTargetSeller by remember { mutableStateOf<Seller?>(null) }

    val filteredSellers = remember(sellersWithStats, searchQuery, statusFilter) {
        sellersWithStats.filter { item ->
            val matchQuery = searchQuery.isBlank() ||
                item.seller.name.contains(searchQuery, ignoreCase = true) ||
                item.seller.whatsappNumber.contains(searchQuery, ignoreCase = true) ||
                item.seller.vendorCode.contains(searchQuery, ignoreCase = true)

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
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = AppStrings.get("sellers_title", lang),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "${sellersWithStats.size} penyetor terdaftar • Web Penyetor aktif",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddSellerDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .size(54.dp)
                    .testTag("add_seller_fab")
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
            contentPadding = PaddingValues(bottom = 116.dp), // Generous bottom space so FAB never covers last card buttons
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
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedBorderColor = lapakColors.cardBorder
                    ),
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
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
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
                items(filteredSellers, key = { it.seller.id }) { item ->
                    val sellerProducts = remember(allProductsWithDetails, item.seller.id) {
                        allProductsWithDetails.filter { it.product.sellerId == item.seller.id }
                    }
                    SellerCard(
                        item = item,
                        productsWithStock = sellerProducts,
                        appSettings = appSettings,
                        lang = lang,
                        onSetLeave = { leaveTargetSeller = item.seller },
                        onReactivate = {
                            viewModel.reactivateSeller(item.seller.id)
                            Toast.makeText(context, "${item.seller.name} telah diaktifkan kembali!", Toast.LENGTH_SHORT).show()
                        },
                        onRecordPayment = { paymentTargetSellerDetail = item },
                        onAddStock = { addStockTargetSellerId = item.seller.id },
                        onOpenWebLink = { webLinkTargetSeller = item.seller },
                        onViewReceipt = { viewModel.showLatestPaymentReceipt(item) },
                        onViewStock = { stockDetailTargetSeller = item },
                        onEdit = { editTargetSeller = item.seller },
                        onDelete = { deleteTargetSeller = item.seller }
                    )
                }
            }
        }
    }

    // Add Seller Dialog (with optional custom admin fee tariff)
    if (showAddSellerDialog) {
        var name by remember { mutableStateOf("") }
        var wa by remember { mutableStateOf("") }
        var address by remember { mutableStateOf("") }
        var notes by remember { mutableStateOf("") }
        var useCustomFee by remember { mutableStateOf(false) }
        var customFeeType by remember { mutableStateOf(appSettings.adminFeeType) }
        var customFeeValueText by remember {
            mutableStateOf(appSettings.adminFeeValue.toLong().toString())
        }
        var err by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showAddSellerDialog = false },
            title = { Text(AppStrings.get("sellers_btn_add", lang), fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Nama Penyetor") },
                        placeholder = { Text("Contoh: Dapur Mama Farida") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = wa,
                        onValueChange = { wa = it.filter { c -> c.isDigit() || c == '+' } },
                        label = { Text("Nomor WhatsApp") },
                        placeholder = { Text("Contoh: 08123456789") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = address,
                        onValueChange = { address = it },
                        label = { Text("Alamat / Keterangan") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Catatan") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Tarif Admin Khusus", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            Text(
                                text = if (useCustomFee) "Atur potongan khusus" else "Ikuti tarif global lapak",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = useCustomFee, onCheckedChange = { useCustomFee = it })
                    }
                    if (useCustomFee) {
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = customFeeType == AdminFeeType.FIXED,
                                onClick = { customFeeType = AdminFeeType.FIXED },
                                label = { Text("Nominal (Rp)") }
                            )
                            FilterChip(
                                selected = customFeeType == AdminFeeType.PERCENTAGE,
                                onClick = { customFeeType = AdminFeeType.PERCENTAGE },
                                label = { Text("Persen (%)") }
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        OutlinedTextField(
                            value = customFeeValueText,
                            onValueChange = { customFeeValueText = it.filter { c -> c.isDigit() || c == '.' } },
                            label = {
                                Text(if (customFeeType == AdminFeeType.PERCENTAGE) "Persentase (%)" else "Potongan / Item (Rp)")
                            },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp)
                        )
                    }
                    if (err != null) {
                        Spacer(Modifier.height(6.dp))
                        Text(err!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val parsedFee = customFeeValueText.toDoubleOrNull()
                        if (name.isBlank()) {
                            err = "Nama penyetor tidak boleh kosong"
                        } else if (useCustomFee && (parsedFee == null || parsedFee < 0 || (customFeeType == AdminFeeType.PERCENTAGE && parsedFee > 100))) {
                            err = "Tarif biaya admin khusus tidak valid"
                        } else {
                            viewModel.addSeller(
                                name = name.trim(),
                                wa = wa.trim(),
                                address = address.trim(),
                                notes = notes.trim(),
                                customFeeType = if (useCustomFee) customFeeType else null,
                                customFeeValue = if (useCustomFee) parsedFee else null
                            ) {
                                showAddSellerDialog = false
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
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

    // Add Stock Dialog preselected for this seller ("Tambah Stok")
    addStockTargetSellerId?.let { sId ->
        QuickDepositDialog(
            sellers = rawSellers,
            productsWithDetails = allProductsWithDetails,
            preselectedSellerId = sId,
            onConfirm = { sellerId, productId, qty, unit, sp, ss, notes ->
                viewModel.depositStock(
                    sellerId = sellerId,
                    productId = productId,
                    quantity = qty,
                    unit = unit,
                    sellingPrice = sp,
                    sellerShare = ss,
                    notes = notes,
                    onSuccess = {
                        addStockTargetSellerId = null
                        Toast.makeText(context, "Stok +$qty $unit berhasil ditambahkan!", Toast.LENGTH_SHORT).show()
                    },
                    onError = { err -> Toast.makeText(context, err, Toast.LENGTH_LONG).show() }
                )
            },
            onDismiss = { addStockTargetSellerId = null }
        )
    }

    // Vendor Web Link Dialog ("Link Web Penyetor")
    webLinkTargetSeller?.let { seller ->
        val portalUrl = remember(seller, appSettings) { viewModel.buildVendorPortalUrl(seller) }
        val shareText = remember(seller, appSettings, shopProfile) { viewModel.buildVendorPortalShareText(seller) }

        VendorWebLinkDialog(
            seller = seller,
            appSettings = appSettings,
            portalUrl = portalUrl,
            shareText = shareText,
            isSyncing = isSyncingCloud,
            onSyncNow = {
                viewModel.syncSingleSellerToCloud(seller.id) { ok ->
                    Toast.makeText(
                        context,
                        if (ok) "Data ${seller.name} tersinkron ke Web Penyetor!" else "Gagal menyinkronkan ke cloud",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            },
            onCopyLink = {
                viewModel.copyToClipboard(context, portalUrl)
            },
            onShareWhatsApp = {
                viewModel.shareTextToWhatsApp(context, shareText, seller.whatsappNumber)
            },
            onDismiss = { webLinkTargetSeller = null }
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

    var soldItemsForPayment by remember { mutableStateOf<List<SoldItemSummary>>(emptyList()) }

    LaunchedEffect(paymentTargetSellerDetail) {
        val target = paymentTargetSellerDetail
        soldItemsForPayment = if (target != null) {
            viewModel.getSoldItemsSummary(target.seller.id)
        } else {
            emptyList()
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
                    Toast.makeText(context, "Setoran ${LapakViewModel.formatRupiah(amount)} berhasil dicatat!", Toast.LENGTH_SHORT).show()
                }
            },
            onDismiss = { paymentTargetSellerDetail = null }
        )
    }

    // Seller Payment Receipt Dialog
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
            onShareWhatsApp = { text -> viewModel.shareTextToWhatsApp(context, text, state.seller.whatsappNumber) },
            onShareGeneral = { text -> viewModel.shareTextGeneral(context, text) },
            onCopy = { text -> viewModel.copyToClipboard(context, text) },
            onDismiss = { viewModel.clearSellerPaymentReceipt() }
        )
    }

    // Edit Seller Dialog
    editTargetSeller?.let { seller ->
        EditSellerDialog(
            seller = seller,
            appSettings = appSettings,
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
    appSettings: AppSettings,
    lang: String,
    onSetLeave: () -> Unit,
    onReactivate: () -> Unit,
    onRecordPayment: () -> Unit,
    onAddStock: () -> Unit,
    onOpenWebLink: () -> Unit,
    onViewReceipt: () -> Unit,
    onViewStock: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val lapakColors = LocalLapakColors.current
    var isExpandedInline by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    val (feeType, feeValue) = AdminFeeCalculator.resolveEffectiveFee(item.seller, appSettings)

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(
            1.dp,
            if (item.remainingBalance > 0) lapakColors.warningBorder else lapakColors.cardBorder
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header Row: Seller Name, Vendor Code, Status Badge, Overflow Menu
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 6.dp)) {
                    Text(
                        text = item.seller.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(top = 3.dp)
                    ) {
                        if (item.seller.vendorCode.isNotBlank()) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = lapakColors.omzetCardBg
                            ) {
                                Text(
                                    text = item.seller.vendorCode,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = lapakColors.omzetCardAccent,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        if (item.seller.whatsappNumber.isNotEmpty()) {
                            Text(
                                text = item.seller.whatsappNumber,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Text(
                        text = "Tarif Admin: ${AdminFeeCalculator.formatTariffLabel(feeType, feeValue)}" +
                            if (item.seller.customAdminFeeType != null) " (Khusus)" else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusBadge(status = item.seller.status)
                    Box {
                        IconButton(
                            onClick = { showOverflowMenu = true },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = "Opsi Penyetor",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        DropdownMenu(expanded = showOverflowMenu, onDismissRequest = { showOverflowMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Bukti / Struk Setoran") },
                                leadingIcon = { Icon(Icons.Default.ReceiptLong, contentDescription = null) },
                                onClick = { showOverflowMenu = false; onViewReceipt() }
                            )
                            DropdownMenuItem(
                                text = { Text(if (item.seller.status == SellerStatus.LIBUR) "Aktifkan Penyetor" else "Atur Status Libur") },
                                leadingIcon = {
                                    Icon(
                                        if (item.seller.status == SellerStatus.LIBUR) Icons.Default.CheckCircle else Icons.Default.EventBusy,
                                        contentDescription = null
                                    )
                                },
                                onClick = {
                                    showOverflowMenu = false
                                    if (item.seller.status == SellerStatus.LIBUR) onReactivate() else onSetLeave()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Edit Penyetor & Tarif") },
                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                onClick = { showOverflowMenu = false; onEdit() }
                            )
                            DropdownMenuItem(
                                text = { Text("Hapus Penyetor", color = MaterialTheme.colorScheme.error) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.DeleteOutline,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                },
                                onClick = { showOverflowMenu = false; onDelete() }
                            )
                        }
                    }
                }
            }

            if (item.seller.status == SellerStatus.LIBUR) {
                Spacer(Modifier.height(8.dp))
                Surface(
                    color = lapakColors.warningBg,
                    border = BorderStroke(1.dp, lapakColors.warningBorder),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Sedang Libur: ${item.seller.leaveReason ?: "Sementara libur titipan"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = lapakColors.warningText,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // Metrics Strip: Stok Aktif | Hak Terjual | Sisa Setoran
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
                    .padding(10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onViewStock)
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text("Stok Aktif", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = "${item.totalStock} item (${item.products.size} menu)",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text("Terjual: ${item.totalSoldQty}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                Column {
                    Text("Total Hak", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = LapakViewModel.formatRupiah(item.totalRights),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text("Dibayar: ${LapakViewModel.formatRupiah(item.totalPaid)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                val hasUnpaid = item.remainingBalance > 0
                val sisaBg = if (!hasUnpaid) lapakColors.omzetCardBg else if (item.paymentStatus == "Belum Dibayar") lapakColors.dangerBg else lapakColors.warningBg
                val sisaText = if (!hasUnpaid) lapakColors.omzetCardAccent else if (item.paymentStatus == "Belum Dibayar") lapakColors.dangerText else lapakColors.warningText

                Column(
                    horizontalAlignment = Alignment.End,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(sisaBg)
                        .border(1.dp, sisaText.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                ) {
                    Text("Sisa Setoran", style = MaterialTheme.typography.labelSmall, color = sisaText, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = LapakViewModel.formatRupiah(item.remainingBalance),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = sisaText
                    )
                }
            }

            // Expandable inline preview of seller products (shows Harga Jual, Hak Penyetor & Laba Admin)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { isExpandedInline = !isExpandedInline }
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isExpandedInline) "Sembunyikan Rincian Menu" else "Lihat ${item.products.size} Menu & Bagi Hasil",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.width(4.dp))
                Icon(
                    imageVector = if (isExpandedInline) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
            }

            AnimatedVisibility(visible = isExpandedInline) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (productsWithStock.isEmpty()) {
                            Text(
                                text = "Belum ada produk terdaftar.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
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
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "Jual ${LapakViewModel.formatRupiah(prodItem.product.sellingPrice)} • Hak ${LapakViewModel.formatRupiah(prodItem.product.sellerShare)} • Laba ${LapakViewModel.formatRupiah(prodItem.product.adminProfitPerUnit)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Surface(
                                        color = if (prodItem.currentStock > 0) lapakColors.stockSafeBg else lapakColors.stockEmptyBg,
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = if (prodItem.currentStock > 0) "${prodItem.currentStock} ${prodItem.product.unit}" else "Habis",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = if (prodItem.currentStock > 0) lapakColors.stockSafeText else lapakColors.stockEmptyText,
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // Primary Action Row: "Bayar Setoran" + "Tambah Stok" (full clear Indonesian labels, no abbreviations)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onRecordPayment,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 42.dp),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (item.remainingBalance > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(Icons.Default.Payments, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = "Bayar Setoran",
                        maxLines = 1,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                OutlinedButton(
                    onClick = onAddStock,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 42.dp),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Default.AddBox, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = "Tambah Stok",
                        maxLines = 1,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // Secondary Action: WhatsApp-accented "Link Web Penyetor" button
            FilledTonalButton(
                onClick = onOpenWebLink,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = lapakColors.whatsappBg,
                    contentColor = lapakColors.whatsappText
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 42.dp)
                    .testTag("vendor_web_link_btn_${item.seller.id}")
            ) {
                Icon(Icons.Default.Forum, contentDescription = "WhatsApp Link Web Penyetor", modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "Link Web Penyetor (WhatsApp)",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp))
            }
        }
    }
}
