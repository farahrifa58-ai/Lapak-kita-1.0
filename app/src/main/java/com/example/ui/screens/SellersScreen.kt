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
    val isOwner by viewModel.isOwner.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf("Semua") }

    // Dialog states
    var showAddSellerDialog by remember { mutableStateOf(false) }
    var leaveTargetSeller by remember { mutableStateOf<Seller?>(null) }
    var paymentTargetSellerDetail by remember { mutableStateOf<SellerDetailItem?>(null) }
    var stockDetailTargetSeller by remember { mutableStateOf<SellerDetailItem?>(null) }
    var addStockTargetSellerId by remember { mutableStateOf<Long?>(null) }
    var editStockTargetProduct by remember { mutableStateOf<ProductDetailItem?>(null) }
    var webLinkTargetSeller by remember { mutableStateOf<Seller?>(null) }
    var showShareAllLinksDialog by remember { mutableStateOf(false) }
    var editTargetSeller by remember { mutableStateOf<Seller?>(null) }
    var deleteTargetSeller by remember { mutableStateOf<Seller?>(null) }
    var addProductTargetSellerId by remember { mutableStateOf<Long?>(null) }
    var editTargetProduct by remember { mutableStateOf<ProductDetailItem?>(null) }
    var deleteTargetProduct by remember { mutableStateOf<ProductDetailItem?>(null) }

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
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            LapakTopHeader(
                title = AppStrings.get("sellers_title", lang),
                subtitle = "${sellersWithStats.size} penyetor terdaftar • Web Penyetor aktif",
                paletteKey = shopProfile.headerBgColorKey
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
            contentPadding = PaddingValues(top = 12.dp, bottom = 116.dp), // Generous bottom space so FAB never covers last card buttons
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

            // Bulk Share All Active Vendor Links to WhatsApp Group Button
            item {
                val activeCount = remember(rawSellers) { rawSellers.count { it.status == SellerStatus.AKTIF } }
                FilledTonalButton(
                    onClick = { showShareAllLinksDialog = true },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = lapakColors.whatsappBg,
                        contentColor = lapakColors.whatsappText
                    ),
                    border = BorderStroke(1.dp, lapakColors.whatsappText.copy(alpha = 0.28f)),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 11.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("btn_share_all_vendor_links")
                ) {
                    Icon(
                        imageVector = Icons.Default.Groups,
                        contentDescription = null,
                        modifier = Modifier.size(19.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Bagikan Link Semua Penyetor ($activeCount Aktif)",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = null,
                        modifier = Modifier.size(17.dp)
                    )
                }
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
                        isOwner = isOwner,
                        onSetLeave = { leaveTargetSeller = item.seller },
                        onReactivate = {
                            viewModel.reactivateSeller(item.seller.id)
                            Toast.makeText(context, "${item.seller.name} telah diaktifkan kembali!", Toast.LENGTH_SHORT).show()
                        },
                        onRecordPayment = { paymentTargetSellerDetail = item },
                        onAddStock = { addStockTargetSellerId = item.seller.id },
                        onEditStock = { prodItem -> editStockTargetProduct = prodItem },
                        onOpenWebLink = { webLinkTargetSeller = item.seller },
                        onViewReceipt = { viewModel.showLatestPaymentReceipt(item) },
                        onViewStock = { stockDetailTargetSeller = item },
                        onEdit = { editTargetSeller = item.seller },
                        onDelete = { deleteTargetSeller = item.seller },
                        onAddProduct = { addProductTargetSellerId = item.seller.id },
                        onEditProduct = { prodItem -> editTargetProduct = prodItem },
                        onDeleteProduct = { prodItem -> deleteTargetProduct = prodItem }
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

    // Admin Full Stock Editor Dialog (increase, decrease, or set exact stock)
    editStockTargetProduct?.let { prodDetail ->
        EditStockDialog(
            productDetail = prodDetail,
            onConfirm = { newStock, reason ->
                viewModel.adjustProductStock(
                    sellerId = prodDetail.seller.id,
                    productId = prodDetail.product.id,
                    targetStock = newStock,
                    reason = reason,
                    onSuccess = { finalStock ->
                        editStockTargetProduct = null
                        Toast.makeText(
                            context,
                            "Stok ${prodDetail.product.name} diperbarui menjadi $finalStock ${prodDetail.product.unit}",
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    onError = { err -> Toast.makeText(context, err, Toast.LENGTH_LONG).show() }
                )
            },
            onDismiss = { editStockTargetProduct = null }
        )
    }

    // Vendor Web Link Dialog ("Link Web Penyetor") — manual sync only (no auto-write on open)
    webLinkTargetSeller?.let { selectedSeller ->
        // Always read freshest seller row (in case vendorCode/writeToken was just generated)
        val freshSeller = remember(selectedSeller.id, rawSellers) {
            rawSellers.firstOrNull { it.id == selectedSeller.id } ?: selectedSeller
        }
        val portalUrl = remember(freshSeller, appSettings) { viewModel.buildVendorPortalUrl(freshSeller) }
        val shareText = remember(freshSeller, appSettings, shopProfile) { viewModel.buildVendorPortalShareText(freshSeller) }
        var syncStatusLabel by remember(freshSeller.id) { mutableStateOf("Tekan 'Sinkronkan' jika ingin memperbarui data di Web Portal") }
        var isSyncedOk by remember(freshSeller.id) { mutableStateOf(true) }

        VendorWebLinkDialog(
            seller = freshSeller,
            appSettings = appSettings,
            portalUrl = portalUrl,
            shareText = shareText,
            isSyncing = isSyncingCloud,
            syncStatusText = syncStatusLabel,
            isSyncedOk = isSyncedOk,
            onSyncNow = {
                syncStatusLabel = "Menyinkronkan data & token ke Web Portal..."
                viewModel.syncSingleSellerToCloud(freshSeller.id, showSuccessFeedback = true) { ok, msg ->
                    isSyncedOk = ok
                    syncStatusLabel = if (ok) {
                        "Tersinkron ke Web Portal"
                    } else {
                        "Gagal sinkron: $msg"
                    }
                }
            },
            onCopyLink = {
                viewModel.copyToClipboard(context, portalUrl)
            },
            onCopyToken = {
                viewModel.copyToClipboard(context, freshSeller.writeToken)
            },
            onShareWhatsApp = {
                viewModel.shareTextToWhatsApp(context, shareText, freshSeller.whatsappNumber)
            },
            onDismiss = { webLinkTargetSeller = null }
        )
    }

    // Bulk Share All Active Vendor Links Dialog (Grup WA) — manual sync only (no auto-write on open)
    if (showShareAllLinksDialog) {
        val activeSellerCount = remember(rawSellers) { rawSellers.count { it.status == SellerStatus.AKTIF } }
        val bulkMessageText = remember(rawSellers, appSettings, shopProfile) {
            viewModel.buildAllActiveVendorLinksShareText(rawSellers)
        }
        var bulkSyncStatus by remember { mutableStateOf("Tekan 'Sinkronkan' jika ingin mengirim pembaruan terbaru ke Web Portal") }
        var bulkSyncedOk by remember { mutableStateOf(true) }

        ShareAllVendorLinksDialog(
            activeSellerCount = activeSellerCount,
            messageText = bulkMessageText,
            isSyncing = isSyncingCloud,
            syncStatusText = bulkSyncStatus,
            isSyncedOk = bulkSyncedOk,
            onSyncNow = {
                bulkSyncStatus = "Menyinkronkan semua token penyetor ke Cloud..."
                viewModel.syncAllVendorsToCloud { ok, msg ->
                    bulkSyncedOk = ok
                    bulkSyncStatus = if (ok) "Semua link & token penyetor aktif tersinkron di Cloud" else msg
                }
            },
            onShareWhatsAppGroup = {
                viewModel.shareTextToWhatsApp(context, bulkMessageText, null)
            },
            onCopyAllLinks = {
                viewModel.copyToClipboard(context, bulkMessageText)
                Toast.makeText(context, "Daftar semua link penyetor disalin!", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showShareAllLinksDialog = false }
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
    stockDetailTargetSeller?.let { selectedDetail ->
        val freshSellerDetail = remember(sellersWithStats, selectedDetail.seller.id) {
            sellersWithStats.firstOrNull { it.seller.id == selectedDetail.seller.id } ?: selectedDetail
        }
        val sellerProducts = remember(allProductsWithDetails, freshSellerDetail.seller.id) {
            allProductsWithDetails.filter { it.product.sellerId == freshSellerDetail.seller.id }
        }
        SellerStockDetailDialog(
            sellerDetail = freshSellerDetail,
            productsWithStock = sellerProducts,
            lang = lang,
            isOwner = isOwner,
            onEditStock = { prodItem -> editStockTargetProduct = prodItem },
            onEditProduct = { prodItem -> editTargetProduct = prodItem },
            onDeleteProduct = { prodItem -> deleteTargetProduct = prodItem },
            onAddProduct = { addProductTargetSellerId = freshSellerDetail.seller.id },
            onDismiss = { stockDetailTargetSeller = null }
        )
    }

    // Add Product Dialog directly from SellersScreen
    addProductTargetSellerId?.let { targetSellerId ->
        AddProductDialog(
            sellers = rawSellers,
            preselectedSellerId = targetSellerId,
            appSettings = appSettings,
            lang = lang,
            onConfirm = { sellerId, name, cat, unit, sp, ss, initStock ->
                viewModel.addProduct(sellerId, name, cat, unit, sp, ss, initStock) {
                    addProductTargetSellerId = null
                }
            },
            onDismiss = { addProductTargetSellerId = null }
        )
    }

    // Edit Product Dialog directly from SellersScreen
    editTargetProduct?.let { prodDetail ->
        EditProductDialog(
            product = prodDetail.product,
            seller = prodDetail.seller,
            appSettings = appSettings,
            lang = lang,
            onConfirm = { updated ->
                viewModel.updateProduct(updated) {
                    editTargetProduct = null
                }
            },
            onDismiss = { editTargetProduct = null }
        )
    }

    // Delete Product Dialog directly from SellersScreen
    deleteTargetProduct?.let { prodDetail ->
        DeleteConfirmDialog(
            title = "Hapus Produk",
            message = "Apakah Anda yakin ingin menghapus produk '${prodDetail.product.name}' milik ${prodDetail.seller.name}? Perubahan akan langsung disinkronkan ke Web Portal.",
            onConfirm = {
                viewModel.deleteProduct(prodDetail.product) {
                    deleteTargetProduct = null
                }
            },
            onDismiss = { deleteTargetProduct = null }
        )
    }
}

@Composable
fun SellerCard(
    item: SellerDetailItem,
    productsWithStock: List<ProductDetailItem>,
    appSettings: AppSettings,
    lang: String,
    isOwner: Boolean = true,
    onSetLeave: () -> Unit,
    onReactivate: () -> Unit,
    onRecordPayment: () -> Unit,
    onAddStock: () -> Unit,
    onEditStock: (ProductDetailItem) -> Unit,
    onOpenWebLink: () -> Unit,
    onViewReceipt: () -> Unit,
    onViewStock: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onAddProduct: () -> Unit,
    onEditProduct: (ProductDetailItem) -> Unit,
    onDeleteProduct: (ProductDetailItem) -> Unit
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
            if (isOwner && item.remainingBalance > 0) lapakColors.warningBorder else lapakColors.cardBorder
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
                    if (isOwner) {
                        Text(
                            text = "Tarif Admin: ${AdminFeeCalculator.formatTariffLabel(feeType, feeValue)}" +
                                if (item.seller.customAdminFeeType != null) " (Khusus)" else "",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
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
                            if (isOwner) {
                                DropdownMenuItem(
                                    text = { Text("Bukti / Struk Setoran") },
                                    leadingIcon = { Icon(Icons.Default.ReceiptLong, contentDescription = null) },
                                    onClick = { showOverflowMenu = false; onViewReceipt() }
                                )
                            }
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
                                text = { Text("Tambah Produk Baru") },
                                leadingIcon = { Icon(Icons.Default.AddCircleOutline, contentDescription = null) },
                                onClick = { showOverflowMenu = false; onAddProduct() }
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

                if (isOwner) {
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
                } else {
                    TextButton(onClick = onViewStock) {
                        Text("Kelola Stok >", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Expandable inline preview of seller products (hides profit/commission for Kasir)
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
                    text = if (isExpandedInline) {
                        "Sembunyikan Rincian Menu"
                    } else if (isOwner) {
                        "Lihat ${item.products.size} Menu & Bagi Hasil"
                    } else {
                        "Lihat ${item.products.size} Menu & Stok"
                    },
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
                                    Column(modifier = Modifier.weight(1f).padding(end = 6.dp)) {
                                        Text(
                                            text = prodItem.product.name,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 3,
                                            softWrap = true,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = if (isOwner) {
                                                "Jual ${LapakViewModel.formatRupiah(prodItem.product.sellingPrice)} • Hak ${LapakViewModel.formatRupiah(prodItem.product.sellerShare)} • Komisi ${LapakViewModel.formatRupiah(prodItem.product.adminProfitPerUnit)}"
                                            } else {
                                                "Harga Jual ${LapakViewModel.formatRupiah(prodItem.product.sellingPrice)} / ${prodItem.product.unit}"
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Surface(
                                            onClick = { onEditStock(prodItem) },
                                            color = if (prodItem.currentStock > 0) lapakColors.stockSafeBg else lapakColors.stockEmptyBg,
                                            shape = RoundedCornerShape(6.dp),
                                            modifier = Modifier.testTag("edit_seller_stock_${prodItem.product.id}")
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = if (prodItem.currentStock > 0) "${prodItem.currentStock} ${prodItem.product.unit}" else "Habis",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (prodItem.currentStock > 0) lapakColors.stockSafeText else lapakColors.stockEmptyText
                                                )
                                                Spacer(Modifier.width(3.dp))
                                                Icon(
                                                    imageVector = Icons.Default.Tune,
                                                    contentDescription = "Edit Stok ${prodItem.product.name}",
                                                    tint = if (prodItem.currentStock > 0) lapakColors.stockSafeText else lapakColors.stockEmptyText,
                                                    modifier = Modifier.size(12.dp)
                                                )
                                            }
                                        }
                                        IconButton(
                                            onClick = { onEditProduct(prodItem) },
                                            modifier = Modifier
                                                .size(36.dp)
                                                .testTag("edit_seller_product_${prodItem.product.id}")
                                        ) {
                                            Icon(
                                                Icons.Default.Edit,
                                                contentDescription = "Edit Produk ${prodItem.product.name}",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(17.dp)
                                            )
                                        }
                                        IconButton(
                                            onClick = { onDeleteProduct(prodItem) },
                                            modifier = Modifier
                                                .size(36.dp)
                                                .testTag("delete_seller_product_${prodItem.product.id}")
                                        ) {
                                            Icon(
                                                Icons.Default.DeleteOutline,
                                                contentDescription = "Hapus Produk ${prodItem.product.name}",
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(17.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(2.dp))
                        OutlinedButton(
                            onClick = onAddProduct,
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 38.dp)
                                .testTag("add_product_for_seller_${item.seller.id}")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "Tambah Produk untuk ${item.seller.name}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // Primary Action Row: "Bayar Setoran" (Owner only) + "Tambah Stok"
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isOwner) {
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
