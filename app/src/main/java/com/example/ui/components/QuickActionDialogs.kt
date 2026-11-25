package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.data.model.AdminFeeCalculator
import com.example.data.model.AppSettings
import com.example.data.model.Seller
import com.example.data.model.SellerStatus
import com.example.data.repository.ProductDetailItem
import com.example.data.repository.SellerDetailItem
import com.example.ui.theme.LocalLapakColors
import com.example.ui.viewmodel.LapakViewModel

// Quick popup: "+ Terima Titipan" (Dashboard) & "Tambah Stok" (Penyetor card)
@Composable
fun QuickDepositDialog(
    sellers: List<Seller>,
    productsWithDetails: List<ProductDetailItem>,
    preselectedSellerId: Long? = null,
    onConfirm: (sellerId: Long, productId: Long, qty: Int, unit: String, sellingPrice: Double, sellerShare: Double, notes: String) -> Unit,
    onDismiss: () -> Unit
) {
    val lapakColors = LocalLapakColors.current
    var selectedSellerId by remember {
        mutableStateOf(preselectedSellerId ?: sellers.firstOrNull()?.id ?: 0L)
    }

    val sellerProducts = remember(productsWithDetails, selectedSellerId) {
        productsWithDetails.filter { it.product.sellerId == selectedSellerId }
    }

    var selectedProductId by remember(selectedSellerId, sellerProducts) {
        mutableStateOf(sellerProducts.firstOrNull()?.product?.id ?: 0L)
    }
    var qtyText by remember { mutableStateOf("10") }
    var notes by remember { mutableStateOf("Titipan harian") }
    var allowLeaveDeposit by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val selectedSeller = sellers.firstOrNull { it.id == selectedSellerId }
    val selectedProductDetail = sellerProducts.firstOrNull { it.product.id == selectedProductId }
    val isSellerLeave = selectedSeller?.status == SellerStatus.LIBUR

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.AddBox,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(8.dp))
                Text("Terima Titipan / Tambah Stok", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                if (sellers.isEmpty()) {
                    Text(
                        text = "Belum ada penyetor terdaftar. Silakan tambah penyetor terlebih dahulu di tab Penyetor.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    // 1. Seller Picker
                    Text("Pilih Penyetor:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    var expSeller by remember { mutableStateOf(false) }
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { expSeller = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = selectedSeller?.name ?: "Pilih Penyetor",
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.Start,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                        }
                        DropdownMenu(expanded = expSeller, onDismissRequest = { expSeller = false }) {
                            sellers.forEach { s ->
                                DropdownMenuItem(
                                    text = { Text(s.name) },
                                    onClick = {
                                        selectedSellerId = s.id
                                        allowLeaveDeposit = false
                                        error = null
                                        expSeller = false
                                    }
                                )
                            }
                        }
                    }

                    if (isSellerLeave) {
                        Spacer(Modifier.height(8.dp))
                        Surface(
                            color = lapakColors.warningBg,
                            border = BorderStroke(1.dp, lapakColors.warningBorder),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = "Penyetor sedang LIBUR. Centang di bawah jika tetap menerima titipan khusus.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = lapakColors.warningText
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(
                                        checked = allowLeaveDeposit,
                                        onCheckedChange = { allowLeaveDeposit = it }
                                    )
                                    Text("Izinkan titipan khusus", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    // 2. Product Picker
                    Text("Pilih Produk:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    if (sellerProducts.isEmpty()) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Penyetor ini belum memiliki produk. Tambahkan produk terlebih dahulu di tab Jualan.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    } else {
                        var expProd by remember { mutableStateOf(false) }
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = { expProd = true },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                val label = selectedProductDetail?.let {
                                    "${it.product.name} (Sisa ${it.currentStock} ${it.product.unit})"
                                } ?: "Pilih Produk"
                                Text(
                                    text = label,
                                    modifier = Modifier.weight(1f),
                                    textAlign = TextAlign.Start,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                            }
                            DropdownMenu(expanded = expProd, onDismissRequest = { expProd = false }) {
                                sellerProducts.forEach { prod ->
                                    DropdownMenuItem(
                                        text = {
                                            Text("${prod.product.name} • Sisa ${prod.currentStock} ${prod.product.unit}")
                                        },
                                        onClick = {
                                            selectedProductId = prod.product.id
                                            error = null
                                            expProd = false
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(10.dp))

                        // Quick quantity chips
                        Text("Jumlah Tambahan Stok:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(5, 10, 15, 20).forEach { quickQty ->
                                FilterChip(
                                    selected = qtyText == quickQty.toString(),
                                    onClick = { qtyText = quickQty.toString(); error = null },
                                    label = { Text("+$quickQty") }
                                )
                            }
                        }

                        Spacer(Modifier.height(6.dp))

                        OutlinedTextField(
                            value = qtyText,
                            onValueChange = { qtyText = it.filter { c -> c.isDigit() }; error = null },
                            label = { Text("Jumlah (${selectedProductDetail?.product?.unit ?: "pcs"})") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp)
                        )

                        Spacer(Modifier.height(8.dp))

                        OutlinedTextField(
                            value = notes,
                            onValueChange = { notes = it },
                            label = { Text("Catatan (Opsional)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp)
                        )
                    }

                    if (error != null) {
                        Spacer(Modifier.height(6.dp))
                        Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val prod = selectedProductDetail
                    val qty = qtyText.toIntOrNull() ?: 0
                    if (selectedSeller == null) {
                        error = "Pilih penyetor terlebih dahulu"
                    } else if (isSellerLeave && !allowLeaveDeposit) {
                        error = "Centang izin titipan khusus untuk penyetor libur"
                    } else if (prod == null) {
                        error = "Pilih produk yang akan ditambah stoknya"
                    } else if (qty <= 0) {
                        error = "Jumlah stok harus lebih dari 0"
                    } else {
                        onConfirm(
                            selectedSeller.id,
                            prod.product.id,
                            qty,
                            prod.product.unit,
                            prod.product.sellingPrice,
                            prod.product.sellerShare,
                            notes.trim()
                        )
                    }
                },
                enabled = sellerProducts.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier.testTag("confirm_quick_deposit_button")
            ) {
                Text("Simpan Titipan")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal")
            }
        }
    )
}

// Quick popup: "Bayar Setoran" picker from Dashboard
@Composable
fun QuickPayoutPickerDialog(
    sellersWithStats: List<SellerDetailItem>,
    onSelectSeller: (SellerDetailItem) -> Unit,
    onDismiss: () -> Unit
) {
    val lapakColors = LocalLapakColors.current
    val sorted = remember(sellersWithStats) {
        sellersWithStats.sortedByDescending { it.remainingBalance }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, lapakColors.cardBorder),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Bayar Setoran ke Penjual",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Tutup")
                    }
                }

                Text(
                    text = "Pilih penyetor yang ingin dicatat pembayaran setorannya:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(12.dp))

                if (sorted.isEmpty()) {
                    Text(
                        text = "Belum ada penyetor terdaftar.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        sorted.forEach { detail ->
                            val hasUnpaid = detail.remainingBalance > 0
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = if (hasUnpaid) lapakColors.payoutCardBg else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                ),
                                border = BorderStroke(
                                    1.dp,
                                    if (hasUnpaid) lapakColors.payoutCardBorder else lapakColors.cardBorder
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectSeller(detail) }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                        Text(
                                            text = detail.seller.name,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "${detail.totalSoldQty} terjual • ${detail.paymentStatus}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = LapakViewModel.formatRupiah(detail.remainingBalance),
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = if (hasUnpaid) lapakColors.payoutCardAccent else MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = if (hasUnpaid) "Bayar >" else "Lunas",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = if (hasUnpaid) lapakColors.payoutCardAccent else MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Tutup")
                }
            }
        }
    }
}

// Popup: "Link Web Penyetor" per seller card
@Composable
fun VendorWebLinkDialog(
    seller: Seller,
    appSettings: AppSettings,
    portalUrl: String,
    shareText: String,
    isSyncing: Boolean,
    syncStatusText: String = "",
    isSyncedOk: Boolean = false,
    onSyncNow: () -> Unit,
    onCopyLink: () -> Unit,
    onCopyToken: () -> Unit = {},
    onShareWhatsApp: () -> Unit,
    onDismiss: () -> Unit
) {
    val lapakColors = LocalLapakColors.current
    val (feeType, feeValue) = AdminFeeCalculator.resolveEffectiveFee(seller, appSettings)
    val feeLabel = AdminFeeCalculator.formatTariffLabel(feeType, feeValue)

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, lapakColors.cardBorder),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Language,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Link Web Penyetor",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Tutup")
                    }
                }

                Text(
                    text = seller.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                if (syncStatusText.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSyncedOk) lapakColors.stockSafeBg else lapakColors.warningBg,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isSyncedOk) Icons.Default.CloudDone else Icons.Default.CloudSync,
                                contentDescription = null,
                                tint = if (isSyncedOk) lapakColors.stockSafeText else lapakColors.warningText,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = syncStatusText,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isSyncedOk) lapakColors.stockSafeText else lapakColors.warningText
                            )
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = lapakColors.omzetCardBg,
                    border = BorderStroke(1.dp, lapakColors.omzetCardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Kode Penyetor", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(seller.vendorCode, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.ExtraBold, color = lapakColors.omzetCardAccent)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Token Kirim", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable { onCopyToken() }
                            ) {
                                Text(
                                    text = seller.writeToken.take(10) + "...",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(Modifier.width(4.dp))
                                Icon(
                                    Icons.Default.ContentCopy,
                                    contentDescription = "Salin Token Kirim",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Tarif Biaya Admin", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(feeLabel, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                Text(
                    text = "Tautan Khusus Penyetor (Sudah Termasuk Token):",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = portalUrl,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(10.dp)
                    )
                }

                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Penyetor bisa menambah/mengubah produk, melihat rincian uang bersih otomatis, dan mengirim stok harian langsung ke aplikasi kasir ini secara real-time.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(16.dp))

                Button(
                    onClick = onShareWhatsApp,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("share_vendor_wa_button")
                ) {
                    Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Kirim Link via WhatsApp", fontWeight = FontWeight.Bold)
                }

                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onCopyLink,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .testTag("copy_vendor_link_button")
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Salin Link", maxLines = 1)
                    }

                    OutlinedButton(
                        onClick = onSyncNow,
                        enabled = !isSyncing,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                    ) {
                        if (isSyncing) {
                            CircularProgressIndicator(modifier = Modifier.size(15.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.CloudSync, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                        Spacer(Modifier.width(6.dp))
                        Text("Sinkronkan", maxLines = 1)
                    }
                }
            }
        }
    }
}
