package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.*
import com.example.ui.components.HeaderActionIconButton
import com.example.ui.components.LapakTopHeader
import com.example.ui.theme.LocalLapakColors
import com.example.ui.viewmodel.AppScreen
import com.example.ui.viewmodel.LapakViewModel
import com.example.util.FinancialReportExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinancialReportScreen(
    viewModel: LapakViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lapakColors = LocalLapakColors.current
    val scope = rememberCoroutineScope()

    val shopProfile by viewModel.shopProfile.collectAsState()
    val periodFilter by viewModel.financialPeriodFilter.collectAsState()
    val report by viewModel.financialReport.collectAsState()
    val isSyncingFinancials by viewModel.isSyncingFinancials.collectAsState()

    var activeTab by remember { mutableIntStateOf(0) } // 0 = Buku Kas Kronologis, 1 = Rekap Setoran Penyetor
    var isExporting by remember { mutableStateOf(false) }
    var showMonthPickerDialog by remember { mutableStateOf(false) }
    var showDateRangeDialog by remember { mutableStateOf(false) }

    val wibTimeZone = remember { TimeZone.getTimeZone("Asia/Jakarta") }
    val rowDateFmt = remember {
        SimpleDateFormat("dd MMM yyyy • HH:mm", Locale("id", "ID")).apply { timeZone = wibTimeZone }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            LapakTopHeader(
                title = "Laporan Keuangan",
                subtitle = if (isSyncingFinancials) "Menyinkronkan ke cloud..." else report.periodLabel,
                paletteKey = shopProfile.headerBgColorKey,
                showBackButton = true,
                onBackClick = { viewModel.navigateTo(AppScreen.DASHBOARD) },
                actions = {
                    HeaderActionIconButton(
                        icon = Icons.Default.CloudSync,
                        contentDescription = "Sinkronkan Laporan ke Cloud",
                        onClick = {
                            if (!isSyncingFinancials) {
                                viewModel.syncAllFinancialDataWithCloud { ok, msg ->
                                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        modifier = Modifier.testTag("financial_sync_button")
                    )
                }
            )
        },
        modifier = modifier
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. Period Filter Chips
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(FinancialReportPeriod.entries) { period ->
                        val selected = periodFilter.period == period
                        FilterChip(
                            selected = selected,
                            onClick = {
                                when (period) {
                                    FinancialReportPeriod.CUSTOM_MONTH -> showMonthPickerDialog = true
                                    FinancialReportPeriod.CUSTOM_RANGE -> showDateRangeDialog = true
                                    else -> viewModel.setFinancialPeriodFilter(
                                        periodFilter.copy(period = period)
                                    )
                                }
                            },
                            label = {
                                Text(
                                    text = period.label,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            },
                            leadingIcon = if (selected) {
                                {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            } else null,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier.testTag("filter_period_${period.name.lowercase()}")
                        )
                    }
                }
            }

            // 2. Period Info Banner & Firestore Sync Badge
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, lapakColors.cardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DateRange,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Periode: ${report.periodLabel}",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Urutan kronologis: Terdahulu → Terbaru (${report.totalTransactionsCount} transaksi • ${report.totalItemsSold} item)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (isSyncingFinancials) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                        }
                    }
                }
            }

            // 3. Four Executive Financial Summary Cards
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        MetricCard(
                            title = "Total Omzet Kotor",
                            value = LapakViewModel.formatRupiah(report.totalGrossOmzet),
                            subtitle = "${report.totalItemsSold} item terjual",
                            icon = Icons.Default.Payments,
                            containerColor = lapakColors.omzetCardBg,
                            borderColor = lapakColors.omzetCardBorder,
                            contentColor = lapakColors.omzetCardAccent,
                            modifier = Modifier.weight(1f)
                        )
                        MetricCard(
                            title = "Untung Bersih Lapak",
                            value = LapakViewModel.formatRupiah(report.netLapakProfit),
                            subtitle = "Komisi: ${LapakViewModel.formatRupiah(report.totalAdminCommission)}",
                            icon = Icons.Default.TrendingUp,
                            containerColor = lapakColors.profitCardBg,
                            borderColor = lapakColors.profitCardBorder,
                            contentColor = lapakColors.profitCardAccent,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        MetricCard(
                            title = "Total Hak Penyetor",
                            value = LapakViewModel.formatRupiah(report.totalSellerShare),
                            subtitle = "${report.sellerRecapRows.size} dapur aktif",
                            icon = Icons.Default.Storefront,
                            containerColor = lapakColors.payoutCardBg,
                            borderColor = lapakColors.payoutCardBorder,
                            contentColor = lapakColors.payoutCardAccent,
                            modifier = Modifier.weight(1f)
                        )
                        MetricCard(
                            title = "Biaya Operasional",
                            value = LapakViewModel.formatRupiah(report.totalOperatingExpenses),
                            subtitle = "Pengeluaran lapak",
                            icon = Icons.Default.ReceiptLong,
                            containerColor = lapakColors.expenseCardBg,
                            borderColor = lapakColors.expenseCardBorder,
                            contentColor = lapakColors.expenseCardAccent,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // 4. Export Excel (.xlsx) & PDF Action Card
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, lapakColors.cardBorder),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.FileDownload,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "Ekspor & Bagikan Laporan",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            if (isExporting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                            }
                        }

                        // Excel Row: Download + Share WA
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = {
                                    if (isExporting) return@Button
                                    isExporting = true
                                    scope.launch {
                                        val res = withContext(Dispatchers.IO) {
                                            FinancialReportExporter.saveExcelToDownloads(context, shopProfile, report)
                                        }
                                        isExporting = false
                                        if (res.isSuccess) {
                                            Toast.makeText(
                                                context,
                                                "Excel tersimpan di ${res.getOrNull()}",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        } else {
                                            Toast.makeText(
                                                context,
                                                "Gagal ekspor Excel: ${res.exceptionOrNull()?.message}",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    }
                                },
                                enabled = !isExporting,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("btn_download_excel")
                            ) {
                                Icon(Icons.Default.TableChart, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Download Excel (.xlsx)", fontWeight = FontWeight.Bold, maxLines = 1)
                            }

                            FilledTonalIconButton(
                                onClick = {
                                    if (isExporting) return@FilledTonalIconButton
                                    isExporting = true
                                    scope.launch {
                                        val res = withContext(Dispatchers.IO) {
                                            FinancialReportExporter.shareReportFile(
                                                context = context,
                                                shopProfile = shopProfile,
                                                report = report,
                                                isExcel = true,
                                                preferWhatsApp = true
                                            )
                                        }
                                        isExporting = false
                                        if (res.isFailure) {
                                            Toast.makeText(
                                                context,
                                                "Gagal membagikan Excel: ${res.exceptionOrNull()?.message}",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    }
                                },
                                enabled = !isExporting,
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("btn_share_excel")
                            ) {
                                Icon(Icons.Default.Share, contentDescription = "Bagikan Excel ke WhatsApp")
                            }
                        }

                        // PDF Row: Download + Share WA
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = {
                                    if (isExporting) return@OutlinedButton
                                    isExporting = true
                                    scope.launch {
                                        val res = withContext(Dispatchers.IO) {
                                            FinancialReportExporter.savePdfToDownloads(context, shopProfile, report)
                                        }
                                        isExporting = false
                                        if (res.isSuccess) {
                                            Toast.makeText(
                                                context,
                                                "PDF tersimpan di ${res.getOrNull()}",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        } else {
                                            Toast.makeText(
                                                context,
                                                "Gagal ekspor PDF: ${res.exceptionOrNull()?.message}",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    }
                                },
                                enabled = !isExporting,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.2.dp, MaterialTheme.colorScheme.primary),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("btn_download_pdf")
                            ) {
                                Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Download Laporan PDF", fontWeight = FontWeight.Bold, maxLines = 1)
                            }

                            FilledTonalIconButton(
                                onClick = {
                                    if (isExporting) return@FilledTonalIconButton
                                    isExporting = true
                                    scope.launch {
                                        val res = withContext(Dispatchers.IO) {
                                            FinancialReportExporter.shareReportFile(
                                                context = context,
                                                shopProfile = shopProfile,
                                                report = report,
                                                isExcel = false,
                                                preferWhatsApp = true
                                            )
                                        }
                                        isExporting = false
                                        if (res.isFailure) {
                                            Toast.makeText(
                                                context,
                                                "Gagal membagikan PDF: ${res.exceptionOrNull()?.message}",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    }
                                },
                                enabled = !isExporting,
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("btn_share_pdf")
                            ) {
                                Icon(Icons.Default.Share, contentDescription = "Bagikan PDF ke WhatsApp")
                            }
                        }

                        Text(
                            text = "File Excel berisi 2 sheet (Buku Kas & Rekap Penyetor) dengan lebar kolom otomatis. PDF siap cetak ukuran A4 lengkap dengan kolom tanda tangan.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 5. Segmented Switch: Buku Kas Kronologis vs Rekap Setoran Penyetor
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    ReportTabPill(
                        label = "Buku Kas (${report.ledgerRows.size})",
                        selected = activeTab == 0,
                        onClick = { activeTab = 0 },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("tab_financial_ledger")
                    )
                    ReportTabPill(
                        label = "Rekap Penyetor (${report.sellerRecapRows.size})",
                        selected = activeTab == 1,
                        onClick = { activeTab = 1 },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("tab_financial_sellers")
                    )
                }
            }

            // 6. Tab Content
            if (activeTab == 0) {
                if (report.ledgerRows.isEmpty()) {
                    item {
                        EmptyReportCard(
                            title = "Belum ada catatan pada periode ini",
                            subtitle = "Transaksi penjualan dan biaya operasional akan muncul otomatis secara kronologis (tanggal terdahulu ke terbaru)."
                        )
                    }
                } else {
                    items(report.ledgerRows, key = { "${it.number}_${it.timestampMillis}_${it.referenceCode}" }) { row ->
                        LedgerEntryCard(
                            row = row,
                            dateLabel = rowDateFmt.format(Date(row.timestampMillis))
                        )
                    }

                    // Bottom TOTAL Summary Card
                    item {
                        LedgerTotalFooterCard(report = report)
                    }
                }
            } else {
                if (report.sellerRecapRows.isEmpty()) {
                    item {
                        EmptyReportCard(
                            title = "Belum ada setoran penyetor pada periode ini",
                            subtitle = "Rekap hak penjualan, setoran dibayar, dan sisa tagihan setiap dapur penyetor akan tampil di sini."
                        )
                    }
                } else {
                    items(report.sellerRecapRows, key = { it.sellerId }) { sRow ->
                        SellerRecapCard(row = sRow)
                    }

                    item {
                        SellerRecapTotalFooterCard(rows = report.sellerRecapRows)
                    }
                }
            }
        }
    }

    // Month & Year Picker Dialog
    if (showMonthPickerDialog) {
        MonthYearPickerDialog(
            initialYear = periodFilter.selectedYear,
            initialMonth = periodFilter.selectedMonth,
            onDismiss = { showMonthPickerDialog = false },
            onConfirm = { year, month ->
                showMonthPickerDialog = false
                viewModel.setFinancialPeriodFilter(
                    periodFilter.copy(
                        period = FinancialReportPeriod.CUSTOM_MONTH,
                        selectedYear = year,
                        selectedMonth = month
                    )
                )
            }
        )
    }

    // Custom Date Range Picker Dialog
    if (showDateRangeDialog) {
        val dateRangePickerState = rememberDateRangePickerState(
            initialSelectedStartDateMillis = periodFilter.customStartMillis,
            initialSelectedEndDateMillis = periodFilter.customEndMillis
        )
        DatePickerDialog(
            onDismissRequest = { showDateRangeDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val start = dateRangePickerState.selectedStartDateMillis
                        val end = dateRangePickerState.selectedEndDateMillis ?: start
                        if (start != null && end != null) {
                            viewModel.setFinancialPeriodFilter(
                                periodFilter.copy(
                                    period = FinancialReportPeriod.CUSTOM_RANGE,
                                    customStartMillis = start,
                                    customEndMillis = end
                                )
                            )
                            showDateRangeDialog = false
                        } else {
                            Toast.makeText(context, "Pilih minimal satu tanggal", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("Terapkan", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDateRangeDialog = false }) {
                    Text("Batal")
                }
            }
        ) {
            DateRangePicker(
                state = dateRangePickerState,
                title = {
                    Text(
                        text = "Pilih Rentang Tanggal Laporan",
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                modifier = Modifier.heightIn(max = 480.dp)
            )
        }
    }
}

@Composable
private fun ReportTabPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
        modifier = modifier
            .heightIn(min = 42.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun LedgerEntryCard(
    row: FinancialLedgerRow,
    dateLabel: String
) {
    val lapakColors = LocalLapakColors.current
    val isExpense = row.type == FinancialLedgerEntryType.OPERATING_EXPENSE

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, if (isExpense) lapakColors.dangerBg else lapakColors.cardBorder),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(
                                if (isExpense) lapakColors.dangerBg else MaterialTheme.colorScheme.primaryContainer
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${row.number}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isExpense) lapakColors.dangerText else MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = row.description,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (isExpense) "$dateLabel WIB • Biaya Operasional" else "$dateLabel WIB • ${row.sellerName}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (!isExpense && row.qty > 0) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = "${row.qty}x",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            if (isExpense) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(lapakColors.dangerBg.copy(alpha = 0.5f))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Biaya Operasional Keluar",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = lapakColors.dangerText
                    )
                    Text(
                        text = "- ${LapakViewModel.formatRupiah(row.operatingExpense)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = lapakColors.dangerText
                    )
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Omzet Masuk", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = LapakViewModel.formatRupiah(row.omzetIn),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Hak Penyetor", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = LapakViewModel.formatRupiah(row.sellerShare),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = lapakColors.payoutCardAccent
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Komisi Lapak", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = LapakViewModel.formatRupiah(row.adminCommission),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LedgerTotalFooterCard(report: FinancialReportSnapshot) {
    val lapakColors = LocalLapakColors.current
    Card(
        colors = CardDefaults.cardColors(containerColor = lapakColors.omzetCardBg),
        border = BorderStroke(1.2.dp, lapakColors.omzetCardBorder),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "TOTAL KESELURUHAN PERIODE",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.ExtraBold,
                color = lapakColors.omzetCardAccent
            )
            HorizontalDivider(color = lapakColors.omzetCardBorder)
            TotalLine("Total Omzet Masuk", LapakViewModel.formatRupiah(report.totalGrossOmzet))
            TotalLine("Total Hak Penyetor", LapakViewModel.formatRupiah(report.totalSellerShare))
            TotalLine("Total Komisi Admin Lapak", LapakViewModel.formatRupiah(report.totalAdminCommission))
            TotalLine("Total Biaya Operasional", LapakViewModel.formatRupiah(report.totalOperatingExpenses))
            HorizontalDivider(color = lapakColors.omzetCardBorder)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "UNTUNG BERSIH LAPAK",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = lapakColors.omzetCardAccent
                )
                Text(
                    text = LapakViewModel.formatRupiah(report.netLapakProfit),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = lapakColors.omzetCardAccent
                )
            }
        }
    }
}

@Composable
private fun TotalLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun SellerRecapCard(row: SellerRecapRow) {
    val lapakColors = LocalLapakColors.current
    val (badgeBg, badgeText) = when (row.status) {
        "Lunas" -> lapakColors.stockSafeBg to lapakColors.stockSafeText
        "Dibayar Sebagian" -> lapakColors.warningBg to lapakColors.warningText
        else -> lapakColors.dangerBg to lapakColors.dangerText
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, lapakColors.cardBorder),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${row.number}. ${row.sellerName}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${row.totalItemsSold} item terjual",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(badgeBg)
                        .padding(horizontal = 9.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = row.status,
                        color = badgeText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Total Hak", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = LapakViewModel.formatRupiah(row.totalSellerShare),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Sudah Dibayar", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = LapakViewModel.formatRupiah(row.totalPaid),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Sisa Tagihan", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = LapakViewModel.formatRupiah(row.remainingUnpaid),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (row.remainingUnpaid > 0.0) lapakColors.dangerText else lapakColors.stockSafeText
                    )
                }
            }
        }
    }
}

@Composable
private fun SellerRecapTotalFooterCard(rows: List<SellerRecapRow>) {
    val lapakColors = LocalLapakColors.current
    val sumItems = rows.sumOf { it.totalItemsSold }
    val sumRights = rows.sumOf { it.totalSellerShare }
    val sumPaid = rows.sumOf { it.totalPaid }
    val sumUnpaid = rows.sumOf { it.remainingUnpaid }

    Card(
        colors = CardDefaults.cardColors(containerColor = lapakColors.payoutCardBg),
        border = BorderStroke(1.2.dp, lapakColors.payoutCardBorder),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "TOTAL REKAP SETORAN (${rows.size} PENYETOR)",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.ExtraBold,
                color = lapakColors.payoutCardAccent
            )
            HorizontalDivider(color = lapakColors.payoutCardBorder)
            TotalLine("Total Item Terjual", "$sumItems item")
            TotalLine("Total Hak Penyetor", LapakViewModel.formatRupiah(sumRights))
            TotalLine("Total Sudah Dibayar", LapakViewModel.formatRupiah(sumPaid))
            TotalLine("Total Sisa Belum Dibayar", LapakViewModel.formatRupiah(sumUnpaid))
        }
    }
}

@Composable
private fun EmptyReportCard(
    title: String,
    subtitle: String
) {
    val lapakColors = LocalLapakColors.current
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, lapakColors.cardBorder),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 32.dp, horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Assessment,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(44.dp)
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun MonthYearPickerDialog(
    initialYear: Int,
    initialMonth: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int) -> Unit
) {
    var selectedYear by remember { mutableIntStateOf(initialYear) }
    var selectedMonth by remember { mutableIntStateOf(initialMonth.coerceIn(0, 11)) }

    val months = remember {
        listOf(
            "Januari", "Februari", "Maret", "April", "Mei", "Juni",
            "Juli", "Agustus", "September", "Oktober", "November", "Desember"
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Pilih Bulan Laporan",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { selectedYear -= 1 }) {
                        Icon(Icons.Default.ChevronLeft, contentDescription = "Tahun Sebelumnya")
                    }
                    Text(
                        text = "$selectedYear",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold
                    )
                    IconButton(onClick = { selectedYear += 1 }) {
                        Icon(Icons.Default.ChevronRight, contentDescription = "Tahun Berikutnya")
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (rowIdx in 0 until 4) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            for (colIdx in 0 until 3) {
                                val mIndex = rowIdx * 3 + colIdx
                                val isSel = mIndex == selectedMonth
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 42.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable { selectedMonth = mIndex }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = months[mIndex].take(3),
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(selectedYear, selectedMonth) }) {
                Text("Terapkan")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal")
            }
        }
    )
}
