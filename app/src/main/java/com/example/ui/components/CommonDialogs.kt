package com.example.ui.components

import android.content.Context
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.model.*
import com.example.data.repository.ProductDetailItem
import com.example.data.repository.SellerDetailItem
import com.example.i18n.AppStrings
import com.example.ui.theme.*
import com.example.ui.viewmodel.LapakViewModel
import java.text.SimpleDateFormat
import java.util.*

import com.example.util.ReceiptPrinter

// 1. NOTA PELANGGAN (STRICT PRIVACY: NO SELLER NAME APPEARS ANYWHERE!)
@Composable
fun ReceiptCustomerDialog(
    transaction: LapakTransaction,
    items: List<TransactionItem>,
    shopProfile: ShopProfile,
    lang: String,
    onDismiss: () -> Unit,
    onPrint: () -> Unit,
    onShareWhatsApp: (String) -> Unit,
    onShareGeneral: (String) -> Unit,
    onCopy: (String) -> Unit
) {
    val context = LocalContext.current
    val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
    val dateStr = sdf.format(Date(transaction.timestamp))

    val receiptText = remember(transaction, items, shopProfile) {
        val sb = StringBuilder()
        sb.append("================================\n")
        sb.append("${shopProfile.shopName.uppercase()}\n")
        sb.append("${shopProfile.address}\n")
        if (shopProfile.whatsappNumber.isNotEmpty()) {
            sb.append("WA: ${shopProfile.whatsappNumber}\n")
        }
        sb.append("================================\n")
        sb.append("No. TRX : ${transaction.transactionNumber}\n")
        sb.append("Waktu   : $dateStr\n")
        sb.append("Metode  : ${transaction.paymentMethod}\n")
        sb.append("--------------------------------\n")
        for (item in items) {
            sb.append("${item.productName}\n")
            sb.append("  ${item.quantity} x ${LapakViewModel.formatRupiah(item.unitPrice)} = ${LapakViewModel.formatRupiah(item.subtotal)}\n")
        }
        sb.append("--------------------------------\n")
        sb.append("TOTAL   : ${LapakViewModel.formatRupiah(transaction.totalAmount)}\n")
        sb.append("BAYAR   : ${LapakViewModel.formatRupiah(transaction.cashReceived)}\n")
        sb.append("KEMBALI : ${LapakViewModel.formatRupiah(transaction.changeGiven)}\n")
        sb.append("================================\n")
        sb.append("${shopProfile.footerMessage}\n")
        sb.append("================================\n")
        sb.toString()
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
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
                        text = AppStrings.get("receipt_customer_title", lang),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Paper receipt visual card
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = shopProfile.shopName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = shopProfile.address,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        if (shopProfile.whatsappNumber.isNotEmpty()) {
                            Text(
                                text = "WhatsApp: ${shopProfile.whatsappNumber}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 12.dp),
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                        )

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text = "No. TRX", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(text = transaction.transactionNumber, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text = "Waktu", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(text = dateStr, style = MaterialTheme.typography.labelSmall)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text = "Metode", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(text = transaction.paymentMethod, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = EmeraldPrimary)
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 12.dp),
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                        )

                        // Items list (Strictly NO seller name)
                        items.forEach { itm ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = itm.productName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = "${itm.quantity} x ${LapakViewModel.formatRupiah(itm.unitPrice)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = LapakViewModel.formatRupiah(itm.subtotal),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 12.dp),
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                        )

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text = "TOTAL", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(text = LapakViewModel.formatRupiah(transaction.totalAmount), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = EmeraldPrimary)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text = "Uang Bayar", style = MaterialTheme.typography.bodySmall)
                            Text(text = LapakViewModel.formatRupiah(transaction.cashReceived), style = MaterialTheme.typography.bodySmall)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text = "Kembalian", style = MaterialTheme.typography.bodySmall)
                            Text(text = LapakViewModel.formatRupiah(transaction.changeGiven), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                        }

                        Spacer(Modifier.height(14.dp))
                        Text(
                            text = shopProfile.footerMessage,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Action buttons: Print Struk, Bagikan ke WhatsApp, Lainnya, Salin
                Button(
                    onClick = onPrint,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
                ) {
                    Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Print Struk Pembelian")
                }

                Spacer(Modifier.height(8.dp))

                OutlinedButton(
                    onClick = { onShareWhatsApp(receiptText) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Bagikan Struk ke WhatsApp")
                }

                Spacer(Modifier.height(8.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { onShareGeneral(receiptText) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Aplikasi Lain", maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = { onCopy(receiptText) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Salin", maxLines = 1)
                    }
                }
            }
        }
    }
}

// 1.5. BUKTI PEMBAYARAN HAK PENYETOR (SELLER PAYOUT RECEIPT MODAL: PRINT & SHARE)
@Composable
fun SellerPaymentReceiptDialog(
    payment: SellerPayment,
    seller: Seller,
    remainingBalance: Double,
    shopProfile: ShopProfile,
    lang: String,
    soldItems: List<com.example.data.model.SoldItemSummary> = emptyList(),
    onPrint: () -> Unit,
    onShareWhatsApp: (String) -> Unit,
    onShareGeneral: (String) -> Unit,
    onCopy: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sdf = SimpleDateFormat("dd MMMM yyyy HH:mm", Locale("id", "ID"))
    val dateStr = sdf.format(Date(payment.paymentDate))

    val receiptText = remember(payment, seller, shopProfile, remainingBalance, soldItems) {
        ReceiptPrinter.formatSellerPayoutReceiptText(payment, seller, shopProfile, remainingBalance, soldItems)
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
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
                        text = "Bukti Bayar Penyetor",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = EmeraldPrimary
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(Modifier.height(10.dp))

                // Paper voucher visual card
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = shopProfile.shopName.uppercase(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = shopProfile.address,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        if (shopProfile.whatsappNumber.isNotEmpty()) {
                            Text(
                                text = "WhatsApp Lapak: ${shopProfile.whatsappNumber}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 10.dp),
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                        )

                        Text(
                            text = "BUKTI PEMBAYARAN HAK PENYETOR",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "No: PAY-${payment.id}-${payment.paymentDate % 10000}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 10.dp),
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                        )

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text = "Tanggal & Waktu", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(text = dateStr, style = MaterialTheme.typography.labelSmall)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text = "Penyetor", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(text = seller.name, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = EmeraldPrimary)
                        }
                        if (seller.whatsappNumber.isNotEmpty()) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(text = "No. WhatsApp", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(text = seller.whatsappNumber, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text = "Metode Payout", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(text = payment.paymentMethod, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                        }
                        if (payment.notes.isNotEmpty()) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(text = "Catatan", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(text = payment.notes, style = MaterialTheme.typography.labelSmall)
                            }
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 10.dp),
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                        )

                        // Sold Items Section
                        if (soldItems.isNotEmpty()) {
                            Text(
                                text = "RINCIAN MENU TERJUAL:",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 6.dp)
                            )
                            soldItems.forEach { item ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.productName,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            text = "${item.quantity} ${item.unit} x ${LapakViewModel.formatRupiah(item.sellerSharePerUnit)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        text = LapakViewModel.formatRupiah(item.totalSellerShare),
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 10.dp),
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                            )
                        }

                        // Highlight Amount Paid
                        Card(
                            colors = CardDefaults.cardColors(containerColor = EmeraldContainer),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "JUMLAH DIBAYARKAN",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = EmeraldDark
                                )
                                Text(
                                    text = LapakViewModel.formatRupiah(payment.amount),
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = EmeraldDark
                                )
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text = "Sisa Hak Belum Dibayar:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                            Text(
                                text = LapakViewModel.formatRupiah(remainingBalance),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = if (remainingBalance <= 0) EmeraldPrimary else AmberTertiary
                            )
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 10.dp),
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                        )

                        // Signatures
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Penerima (Penyetor),", style = MaterialTheme.typography.labelSmall)
                                Spacer(Modifier.height(28.dp))
                                Text("( ${seller.name} )", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Admin Lapak,", style = MaterialTheme.typography.labelSmall)
                                Spacer(Modifier.height(28.dp))
                                Text("( ${shopProfile.ownerName} )", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Action buttons: Print Struk, Bagikan ke WhatsApp, Aplikasi Lain, Salin
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onPrint,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
                    ) {
                        Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Print Struk")
                    }

                    Button(
                        onClick = { onShareWhatsApp(receiptText) },
                        modifier = Modifier.weight(1.2f),
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldDark)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Share Struk WA")
                    }
                }

                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { onShareGeneral(receiptText) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Aplikasi Lain", maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = { onCopy(receiptText) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Salin", maxLines = 1)
                    }
                }

                Spacer(Modifier.height(8.dp))

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Tutup")
                }
            }
        }
    }
}

// 2. DETAIL TRANSAKSI INTERNAL KHUSUS ADMIN (SHOWS SELLER NAME, SELLER SHARE, ADMIN PROFIT)
@Composable
fun TransactionAdminDetailDialog(
    transaction: LapakTransaction,
    items: List<TransactionItem>,
    lang: String,
    onDismiss: () -> Unit
) {
    val sdf = SimpleDateFormat("dd MMMM yyyy HH:mm:ss", Locale.getDefault())

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
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
                        text = AppStrings.get("admin_detail_title", lang),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = AmberContainer),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Security, contentDescription = null, tint = OnAmberContainer, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = AppStrings.get("admin_detail_notice", lang),
                            style = MaterialTheme.typography.labelSmall,
                            color = OnAmberContainer
                        )
                    }
                }

                Text(
                    text = "No. Transaksi: ${transaction.transactionNumber}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Waktu: ${sdf.format(Date(transaction.timestamp))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Metode Pembayaran: ${transaction.paymentMethod}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (transaction.isCancelled) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "STATUS: DIBATALKAN (${transaction.cancellationReason ?: "-"})",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                Text(
                    text = "Rincian Item & Hak Penyetor:",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(Modifier.height(8.dp))

                items.forEach { itm ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = itm.productName,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${itm.quantity} ${itm.unit}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            // Internal Seller Name Tag
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                                Icon(Icons.Default.Person, contentDescription = null, tint = EmeraldPrimary, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = "Penyetor: ${itm.sellerName}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = EmeraldPrimary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Harga Jual:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${LapakViewModel.formatRupiah(itm.unitPrice)} (${LapakViewModel.formatRupiah(itm.subtotal)})", style = MaterialTheme.typography.labelSmall)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Hak Penyetor:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${LapakViewModel.formatRupiah(itm.sellerSharePerUnit)} x ${itm.quantity} = ${LapakViewModel.formatRupiah(itm.totalSellerShare)}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Laba Admin:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${LapakViewModel.formatRupiah(itm.adminProfitPerUnit)} x ${itm.quantity} = ${LapakViewModel.formatRupiah(itm.totalAdminProfit)}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = TealSecondary)
                            }
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                // Summary Totals
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Total Omzet Transaksi:", style = MaterialTheme.typography.bodyMedium)
                    Text(LapakViewModel.formatRupiah(transaction.totalAmount), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Total Hak Penyetor:", style = MaterialTheme.typography.bodyMedium)
                    Text(LapakViewModel.formatRupiah(transaction.totalSellerShare), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = AmberTertiary)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Total Laba Admin:", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                    Text(LapakViewModel.formatRupiah(transaction.totalAdminProfit), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = EmeraldPrimary)
                }

                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Tutup Detail")
                }
            }
        }
    }
}

// 3. BAGIKAN STOK TERSISA MODAL (DUA PILIHAN: SEMUA PENYETOR vs PER PENYETOR)
@Composable
fun ShareStockReportDialog(
    sellers: List<Seller>,
    viewModel: LapakViewModel,
    lang: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var isAllSellers by remember { mutableStateOf(true) }
    var selectedSellerId by remember { mutableStateOf(sellers.firstOrNull()?.id) }

    val reportText = remember(isAllSellers, selectedSellerId, sellers) {
        viewModel.generateStockReportText(isAllSellers, selectedSellerId)
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
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
                        text = AppStrings.get("share_stock_title", lang),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = EmeraldPrimary
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Dua Pilihan Wajib: Semua Penyetor vs Per Penyetor
                Text(
                    text = "Pilih Format Laporan:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )

                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = isAllSellers,
                        onClick = { isAllSellers = true }
                    )
                    Text(
                        text = AppStrings.get("share_option_all", lang),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.clickable { isAllSellers = true }
                    )

                    Spacer(Modifier.width(16.dp))

                    RadioButton(
                        selected = !isAllSellers,
                        onClick = { isAllSellers = false }
                    )
                    Text(
                        text = AppStrings.get("share_option_per_seller", lang),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.clickable { isAllSellers = false }
                    )
                }

                // If Per Penyetor is selected, show seller dropdown / picker
                if (!isAllSellers) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = AppStrings.get("share_select_seller", lang),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    var expanded by remember { mutableStateOf(false) }
                    val currentSellerName = sellers.firstOrNull { it.id == selectedSellerId }?.name ?: "Pilih Penyetor"

                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { expanded = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(currentSellerName, modifier = Modifier.weight(1f), textAlign = TextAlign.Start)
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                        }

                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            sellers.forEach { s ->
                                DropdownMenuItem(
                                    text = { Text(s.name) },
                                    onClick = {
                                        selectedSellerId = s.id
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Message preview
                Text(
                    text = AppStrings.get("share_preview_label", lang),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(4.dp))

                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                ) {
                    Text(
                        text = reportText,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp)
                    )
                }

                Spacer(Modifier.height(16.dp))

                // Action buttons: WhatsApp, General share, Copy
                Button(
                    onClick = {
                        val targetSellerWa = if (!isAllSellers) {
                            sellers.firstOrNull { it.id == selectedSellerId }?.whatsappNumber
                        } else null
                        viewModel.shareTextToWhatsApp(context, reportText, targetSellerWa)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
                ) {
                    Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(AppStrings.get("share_to_whatsapp", lang))
                }

                Spacer(Modifier.height(8.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { viewModel.shareTextGeneral(context, reportText) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(AppStrings.get("share_via_other", lang), maxLines = 1)
                    }

                    OutlinedButton(
                        onClick = { viewModel.copyToClipboard(context, reportText) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(AppStrings.get("share_copy_text", lang), maxLines = 1)
                    }
                }
            }
        }
    }
}

// 4. BATALKAN TRANSAKSI MODAL
@Composable
fun CancelTransactionDialog(
    transaction: LapakTransaction,
    lang: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var reason by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(AppStrings.get("history_cancel_dialog_title", lang), fontWeight = FontWeight.Bold)
        },
        text = {
            Column {
                Text(
                    text = AppStrings.get("history_cancel_dialog_desc", lang),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = reason,
                    onValueChange = {
                        reason = it
                        if (it.isNotEmpty()) error = null
                    },
                    label = { Text("Alasan Pembatalan") },
                    placeholder = { Text(AppStrings.get("history_cancel_reason_hint", lang)) },
                    modifier = Modifier.fillMaxWidth(),
                    isError = error != null
                )
                if (error != null) {
                    Text(
                        text = error!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (reason.isBlank()) {
                        error = "Harap masukkan alasan pembatalan"
                    } else {
                        onConfirm(reason)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Batalkan & Pulihkan Stok")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal")
            }
        }
    )
}

// 5. ATUR STATUS LIBUR PENYETOR
@Composable
fun SellerLeaveDialog(
    seller: Seller,
    lang: String,
    onConfirm: (Long?, Long?, String?) -> Unit,
    onDismiss: () -> Unit
) {
    var reason by remember { mutableStateOf(seller.leaveReason ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(AppStrings.get("sellers_dialog_leave_title", lang), fontWeight = FontWeight.Bold)
        },
        text = {
            Column {
                Text(
                    text = "Penyetor: ${seller.name}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Ketika status LIBUR aktif, setoran baru secara default akan ditolak, namun stok lama tetap dapat dijual di kasir.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Alasan / Catatan Libur (Opsional)") },
                    placeholder = { Text(AppStrings.get("sellers_leave_reason_hint", lang)) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val now = System.currentTimeMillis()
                    onConfirm(now, null, reason.ifBlank { null })
                },
                colors = ButtonDefaults.buttonColors(containerColor = AmberTertiary)
            ) {
                Text("Set Status Libur")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Tutup")
            }
        }
    )
}

// 6. CATAT PEMBAYARAN HAK KE PENYETOR
@Composable
fun SellerPaymentDialog(
    sellerDetail: SellerDetailItem,
    lang: String,
    onConfirm: (Double, String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var amountText by remember { mutableStateOf(sellerDetail.remainingBalance.toInt().toString()) }
    var method by remember { mutableStateOf("Tunai") }
    var notes by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(AppStrings.get("sellers_dialog_payment_title", lang), fontWeight = FontWeight.Bold)
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = "Penyetor: ${sellerDetail.seller.name}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Sisa Hak Belum Dibayar: ${LapakViewModel.formatRupiah(sellerDetail.remainingBalance)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = AmberTertiary,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = amountText,
                    onValueChange = {
                        amountText = it.filter { char -> char.isDigit() }
                        error = null
                    },
                    label = { Text("Jumlah Pembayaran (Rp)") },
                    modifier = Modifier.fillMaxWidth(),
                    isError = error != null
                )
                if (error != null) {
                    Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                }

                Spacer(Modifier.height(10.dp))
                Text("Metode Pembayaran:", style = MaterialTheme.typography.labelSmall)
                Row(modifier = Modifier.fillMaxWidth()) {
                    listOf("Tunai", "Transfer Bank").forEach { m ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 12.dp)) {
                            RadioButton(selected = method == m, onClick = { method = m })
                            Text(m, style = MaterialTheme.typography.bodySmall, modifier = Modifier.clickable { method = m })
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Catatan / Keterangan Pembayaran") },
                    placeholder = { Text("Misal: Payout mingguan 1-7 Nov") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(10.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = EmeraldContainer.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Print, contentDescription = null, tint = EmeraldDark, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Struk bukti pembayaran dapat langsung dicetak (Print) dan dibagikan (Share) ke WhatsApp setelah disimpan.",
                            style = MaterialTheme.typography.labelSmall,
                            color = EmeraldDark
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amt = amountText.toDoubleOrNull() ?: 0.0
                    if (amt <= 0) {
                        error = "Masukkan jumlah pembayaran yang valid"
                    } else {
                        onConfirm(amt, notes, method)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
            ) {
                Text("Simpan Pembayaran")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal")
            }
        }
    )
}

// 7. TAMBAH MASTER PRODUK BARU
@Composable
fun AddProductDialog(
    sellers: List<Seller>,
    preselectedSellerId: Long?,
    lang: String,
    onConfirm: (Long, String, String, String, Double, Double, Int) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedSellerId by remember { mutableStateOf(preselectedSellerId ?: sellers.firstOrNull()?.id ?: 0L) }
    var name by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Makanan") }
    var unit by remember { mutableStateOf("pcs") }
    var sellingPriceText by remember { mutableStateOf("") }
    var sellerShareText by remember { mutableStateOf("") }
    var initialStockText by remember { mutableStateOf("10") }
    var error by remember { mutableStateOf<String?>(null) }

    val sellingPrice = sellingPriceText.toDoubleOrNull() ?: 0.0
    val sellerShare = sellerShareText.toDoubleOrNull() ?: 0.0
    val adminProfit = sellingPrice - sellerShare

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(AppStrings.get("prod_add_new", lang), fontWeight = FontWeight.Bold)
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                // Seller Selector
                Text("Pilih Penyetor:", style = MaterialTheme.typography.labelSmall)
                var exp by remember { mutableStateOf(false) }
                val currentSellerName = sellers.firstOrNull { it.id == selectedSellerId }?.name ?: "Pilih Penyetor"

                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { exp = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(currentSellerName, modifier = Modifier.weight(1f), textAlign = TextAlign.Start)
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                    }
                    DropdownMenu(expanded = exp, onDismissRequest = { exp = false }) {
                        sellers.forEach { s ->
                            DropdownMenuItem(text = { Text(s.name) }, onClick = { selectedSellerId = s.id; exp = false })
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nama Produk") },
                    placeholder = { Text("Contoh: Risoles Rogout Sapi") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(8.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = category,
                        onValueChange = { category = it },
                        label = { Text("Kategori") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = unit,
                        onValueChange = { unit = it },
                        label = { Text("Satuan") },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = sellingPriceText,
                    onValueChange = { sellingPriceText = it.filter { c -> c.isDigit() } },
                    label = { Text("Harga Jual ke Pembeli (Rp)") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = sellerShareText,
                    onValueChange = { sellerShareText = it.filter { c -> c.isDigit() } },
                    label = { Text("Hak Penyetor per Unit (Rp)") },
                    modifier = Modifier.fillMaxWidth()
                )

                if (sellingPrice > 0 || sellerShare > 0) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Keuntungan Admin per unit: ${LapakViewModel.formatRupiah(adminProfit)}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (adminProfit >= 0) EmeraldPrimary else MaterialTheme.colorScheme.error
                    )
                }

                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = initialStockText,
                    onValueChange = { initialStockText = it.filter { c -> c.isDigit() } },
                    label = { Text("Jumlah Stok Awal Dititipkan") },
                    modifier = Modifier.fillMaxWidth()
                )

                if (error != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (selectedSellerId == 0L) {
                        error = "Pilih penyetor terlebih dahulu"
                    } else if (name.isBlank()) {
                        error = "Nama produk tidak boleh kosong"
                    } else if (sellingPrice <= 0) {
                        error = "Harga jual harus lebih dari 0"
                    } else if (sellerShare <= 0) {
                        error = "Hak penyetor harus lebih dari 0"
                    } else {
                        val initStock = initialStockText.toIntOrNull() ?: 0
                        onConfirm(selectedSellerId, name, category, unit, sellingPrice, sellerShare, initStock)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
            ) {
                Text("Simpan Produk")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal")
            }
        }
    )
}

// 8. SETOR STOK BARU (CONSIGNMENT DEPOSIT)
@Composable
fun DepositStockDialog(
    productDetail: ProductDetailItem,
    lang: String,
    onConfirm: (Int, Double, Double, String) -> Unit,
    onDismiss: () -> Unit
) {
    var qtyText by remember { mutableStateOf("10") }
    var sellingPriceText by remember { mutableStateOf(productDetail.product.sellingPrice.toInt().toString()) }
    var sellerShareText by remember { mutableStateOf(productDetail.product.sellerShare.toInt().toString()) }
    var notes by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    val isSellerLeave = productDetail.seller.status == SellerStatus.LIBUR
    var allowLeaveDeposit by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Setoran Stok Produk", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = productDetail.product.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Penyetor: ${productDetail.seller.name}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Stok saat ini: ${productDetail.currentStock} ${productDetail.product.unit}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = EmeraldPrimary
                )

                if (isSellerLeave) {
                    Spacer(Modifier.height(8.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = AmberContainer)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = AppStrings.get("sellers_leave_warning_deposit", lang),
                                style = MaterialTheme.typography.labelSmall,
                                color = OnAmberContainer
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = allowLeaveDeposit,
                                    onCheckedChange = { allowLeaveDeposit = it }
                                )
                                Text("Izinkan setoran khusus ini", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                OutlinedTextField(
                    value = qtyText,
                    onValueChange = { qtyText = it.filter { c -> c.isDigit() } },
                    label = { Text("Jumlah Tambahan Setoran (${productDetail.product.unit})") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = sellingPriceText,
                    onValueChange = { sellingPriceText = it.filter { c -> c.isDigit() } },
                    label = { Text("Harga Jual (Rp)") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = sellerShareText,
                    onValueChange = { sellerShareText = it.filter { c -> c.isDigit() } },
                    label = { Text("Hak Penyetor per Unit (Rp)") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Catatan Setoran") },
                    placeholder = { Text("Contoh: Titipan sore fresh") },
                    modifier = Modifier.fillMaxWidth()
                )

                if (error != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (isSellerLeave && !allowLeaveDeposit) {
                        error = "Penyetor sedang libur. Centang persetujuan setoran khusus untuk melanjutkan."
                        return@Button
                    }
                    val qty = qtyText.toIntOrNull() ?: 0
                    val sp = sellingPriceText.toDoubleOrNull() ?: 0.0
                    val ss = sellerShareText.toDoubleOrNull() ?: 0.0
                    if (qty <= 0) {
                        error = "Jumlah setoran harus lebih dari 0"
                    } else if (sp <= 0 || ss <= 0) {
                        error = "Harga jual dan hak penyetor harus valid"
                    } else {
                        onConfirm(qty, sp, ss, notes)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
            ) {
                Text("Simpan Setoran")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal")
            }
        }
    )
}

// 9. DIALOG PEMILIHAN PENYETOR KETIKA PRODUK DENGAN NAMA SAMA ADA DARI BEBERAPA PENYETOR
@Composable
fun SelectSellerLotDialog(
    matchingProducts: List<ProductDetailItem>,
    lang: String,
    onSelect: (ProductDetailItem) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = AppStrings.get("cashier_select_seller_lot", lang),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = EmeraldPrimary
                )

                Spacer(Modifier.height(6.dp))

                Text(
                    text = AppStrings.get("cashier_shared_product_notice", lang),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(14.dp))

                matchingProducts.forEach { item ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable { onSelect(item) },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.seller.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Harga: ${LapakViewModel.formatRupiah(item.product.sellingPrice)} (Hak: ${LapakViewModel.formatRupiah(item.product.sellerShare)})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Stok Tersedia: ${item.currentStock} ${item.product.unit}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (item.currentStock > 0) EmeraldPrimary else MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = EmeraldPrimary)
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Batal")
                }
            }
        }
    }
}

// 10. TAMBAH BIAYA OPERASIONAL
@Composable
fun AddExpenseDialog(
    lang: String,
    onConfirm: (String, Double, String) -> Unit,
    onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Tambah Biaya Operasional", fontWeight = FontWeight.Bold)
        },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Jenis Biaya / Keperluan") },
                    placeholder = { Text("Contoh: Kantong Kresek, Es Batu, Listrik") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it.filter { c -> c.isDigit() } },
                    label = { Text("Nominal Biaya (Rp)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Catatan / Keterangan (Opsional)") },
                    modifier = Modifier.fillMaxWidth()
                )
                if (error != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amt = amountText.toDoubleOrNull() ?: 0.0
                    if (title.isBlank()) {
                        error = "Keperluan biaya tidak boleh kosong"
                    } else if (amt <= 0) {
                        error = "Nominal biaya harus lebih dari 0"
                    } else {
                        onConfirm(title, amt, notes)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
            ) {
                Text("Simpan Biaya")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal")
            }
        }
    )
}
