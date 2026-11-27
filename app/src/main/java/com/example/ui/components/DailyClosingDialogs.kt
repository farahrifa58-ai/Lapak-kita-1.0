package com.example.ui.components

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AutoClosingLeftoverMode
import com.example.data.model.DailyClosingArchive
import com.example.data.model.DailyClosingHelper
import com.example.data.model.DailyClosingTriggerType
import com.example.data.repository.LapakRepository
import com.example.ui.theme.LocalLapakColors
import com.example.ui.viewmodel.LapakViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ManualDailyClosingDialog(
    defaultLeftoverMode: String,
    isProcessing: Boolean,
    onConfirm: (leftoverMode: String) -> Unit,
    onDismiss: () -> Unit
) {
    val todayLabel = remember { DailyClosingHelper.formatDisplayDate(DailyClosingHelper.todayDateString()) }
    var selectedMode by remember(defaultLeftoverMode) { mutableStateOf(defaultLeftoverMode) }

    AlertDialog(
        onDismissRequest = { if (!isProcessing) onDismiss() },
        icon = {
            Icon(
                imageVector = Icons.Default.TaskAlt,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = "Tutup Buku Hari Ini?",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Tanggal $todayLabel akan disimpan permanen ke Arsip Tutup Buku lengkap dengan rincian Masuk, Terjual, dan Sisa/Retur per produk & penyetor. Jadwal Auto Tutup Buku malam ini akan otomatis dilewati.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    text = "Pilih aturan untuk sisa barang hari ini:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (selectedMode == AutoClosingLeftoverMode.RESET_ZERO)
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { selectedMode = AutoClosingLeftoverMode.RESET_ZERO }
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedMode == AutoClosingLeftoverMode.RESET_ZERO,
                            onClick = { selectedMode = AutoClosingLeftoverMode.RESET_ZERO }
                        )
                        Spacer(Modifier.width(6.dp))
                        Column {
                            Text("Reset Sisa ke 0 (Retur Penyetor)", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                            Text("Sisa stok hari ini dikembalikan ke penyetor", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (selectedMode == AutoClosingLeftoverMode.CARRY_OVER)
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { selectedMode = AutoClosingLeftoverMode.CARRY_OVER }
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedMode == AutoClosingLeftoverMode.CARRY_OVER,
                            onClick = { selectedMode = AutoClosingLeftoverMode.CARRY_OVER }
                        )
                        Spacer(Modifier.width(6.dp))
                        Column {
                            Text("Bawa Sisa ke Stok Besok (Carry Over)", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                            Text("Sisa barang tetap tersedia untuk jualan besok", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(selectedMode) },
                enabled = !isProcessing,
                modifier = Modifier.testTag("btn_confirm_manual_closing")
            ) {
                if (isProcessing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Text(if (isProcessing) "Memproses..." else "Ya, Tutup Buku", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isProcessing
            ) {
                Text("Batal")
            }
        }
    )
}

@Composable
fun DailyClosingArchiveDetailDialog(
    archive: DailyClosingArchive,
    onDismiss: () -> Unit
) {
    val lapakColors = LocalLapakColors.current
    val itemsBreakdown = remember(archive.itemsBreakdownJson) {
        LapakRepository.decodeClosingItemsJson(archive.itemsBreakdownJson)
    }
    val closedAtStr = remember(archive.closedAtMillis) {
        SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("id", "ID")).apply {
            timeZone = TimeZone.getTimeZone("Asia/Jakarta")
        }.format(Date(archive.closedAtMillis))
    }
    val isAuto = archive.triggerType == DailyClosingTriggerType.AUTO

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = DailyClosingHelper.formatDisplayDate(archive.closingDate),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.weight(1f)
                    )
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isAuto) MaterialTheme.colorScheme.primaryContainer else lapakColors.stockSafeBg
                    ) {
                        Text(
                            text = if (isAuto) "AUTO" else "MANUAL",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (isAuto) MaterialTheme.colorScheme.primary else lapakColors.stockSafeText,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
                Text(
                    text = "Dicatat: $closedAtStr WIB • ${AutoClosingLeftoverMode.shortLabel(archive.leftoverMode)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 440.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Summary box
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = lapakColors.omzetCardBg,
                    border = BorderStroke(1.dp, lapakColors.omzetCardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        ArchiveSummaryLine("Total Transaksi", "${archive.totalTransactions} trx")
                        ArchiveSummaryLine(
                            "Arus Barang (Masuk / Terjual / Sisa)",
                            "${archive.totalItemsIn} / ${archive.totalItemsSold} / ${archive.totalItemsLeftover}"
                        )
                        HorizontalDivider(color = lapakColors.omzetCardBorder)
                        ArchiveSummaryLine("Total Omzet", LapakViewModel.formatRupiah(archive.totalOmzet))
                        ArchiveSummaryLine("Hak Penyetor", LapakViewModel.formatRupiah(archive.totalSellerShare))
                        ArchiveSummaryLine("Biaya Operasional", LapakViewModel.formatRupiah(archive.totalOperatingExpenses))
                        ArchiveSummaryLine("Untung Bersih Lapak", LapakViewModel.formatRupiah(archive.netProfit), highlight = true)
                    }
                }

                // Table header
                Text(
                    text = "Rincian Produk & Penyetor (${itemsBreakdown.size})",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )

                if (itemsBreakdown.isEmpty()) {
                    Text(
                        text = "Tidak ada pergerakan produk pada tanggal ini.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Text("Produk / Penyetor", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(2.2f))
                        Text("Masuk", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.weight(0.75f))
                        Text("Jual", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.weight(0.75f))
                        Text("Sisa", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.weight(0.75f))
                    }

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(itemsBreakdown) { item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 6.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(2.2f).padding(end = 4.dp)) {
                                    Text(
                                        text = item.productName,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 2,
                                        softWrap = true,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = item.sellerName,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Text(
                                    text = "${item.qtyIn}",
                                    style = MaterialTheme.typography.labelMedium,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.weight(0.75f)
                                )
                                Text(
                                    text = "${item.qtySold}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = lapakColors.stockSafeText,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.weight(0.75f)
                                )
                                Text(
                                    text = "${item.qtyLeftover}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (item.qtyLeftover > 0) lapakColors.warningText else MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.weight(0.75f)
                                )
                            }
                            HorizontalDivider(color = lapakColors.cardBorder.copy(alpha = 0.5f))
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Tutup", fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
private fun ArchiveSummaryLine(
    label: String,
    value: String,
    highlight: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (highlight) FontWeight.ExtraBold else FontWeight.Normal,
            color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
    }
}
