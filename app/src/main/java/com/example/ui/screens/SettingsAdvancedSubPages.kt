package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.AutoClosingLeftoverMode
import com.example.data.model.DailyClosingHelper
import com.example.data.model.PrinterPaperSize
import com.example.ui.theme.LocalLapakColors
import com.example.util.BluetoothPrinterDevice
import com.example.util.BluetoothThermalPrinter

// =============================================================================
// AUTO TUTUP BUKU HARIAN SUB-PAGE
// =============================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AutoClosingSubPage(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    hour: Int,
    minute: Int,
    onTimeChange: (Int, Int) -> Unit,
    leftoverMode: String,
    onLeftoverModeChange: (String) -> Unit,
    lastClosedDate: String,
    isTodayClosed: Boolean,
    onSave: () -> Unit,
    onRequestManualCloseToday: () -> Unit
) {
    val lapakColors = LocalLapakColors.current
    var showTimePickerDialog by remember { mutableStateOf(false) }
    val formattedTime = DailyClosingHelper.formatTime(hour, minute)

    SettingsSubPageCard {
        // Info & status banner
        Surface(
            color = lapakColors.omzetCardBg,
            border = BorderStroke(1.dp, lapakColors.omzetCardBorder),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.EventRepeat,
                        contentDescription = null,
                        tint = lapakColors.omzetCardAccent,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Auto Tutup Buku Harian (Anti-Gagal)",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = lapakColors.omzetCardAccent
                    )
                }
                Text(
                    text = "Otomatis mencatat arsip permanen harian (Masuk, Terjual, Sisa/Retur) tepat pada jam tutup lapak. Jika HP mati atau aplikasi tertutup, sistem otomatis melakukan tutup buku susulan saat aplikasi dibuka kembali.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (lastClosedDate.isNotBlank()) {
                    Text(
                        text = "Tutup buku terakhir tercatat: ${DailyClosingHelper.formatDisplayDate(lastClosedDate)}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = lapakColors.omzetCardAccent
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // Toggle Aktif / Nonaktif
        SettingsToggleRow(
            label = "Aktifkan Auto Tutup Buku Otomatis",
            checked = enabled,
            onCheckedChange = onEnabledChange
        )

        Spacer(Modifier.height(12.dp))

        // Time Picker section
        Text(
            text = "Jadwal Jam Auto Tutup Buku (WIB)",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(8.dp))

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable { showTimePickerDialog = true }
                .testTag("btn_open_auto_closing_time_picker")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 54.dp)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AccessTime,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "$formattedTime WIB",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Ketuk untuk mengubah jam & menit",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                FilledTonalButton(
                    onClick = { showTimePickerDialog = true },
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text("Ubah Jam", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        val presets = listOf(17 to 0, 20 to 0, 21 to 0, 22 to 0, 23 to 0)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(presets) { (ph, pm) ->
                FilterChip(
                    selected = hour == ph && minute == pm,
                    onClick = { onTimeChange(ph, pm) },
                    label = { Text(DailyClosingHelper.formatTime(ph, pm), style = MaterialTheme.typography.labelSmall) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // Leftover stock policy
        Text(
            text = "Aturan Otomatis untuk Produk Sisa",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(8.dp))

        LeftoverPolicyOptionCard(
            title = "Reset Sisa ke 0 (Dikembalikan ke Penyetor)",
            subtitle = "Cocok untuk kue basah/makanan harian. Sisa barang dicatat di arsip lalu stok direset ke 0.",
            selected = leftoverMode == AutoClosingLeftoverMode.RESET_ZERO,
            onClick = { onLeftoverModeChange(AutoClosingLeftoverMode.RESET_ZERO) },
            testTag = "option_leftover_reset_zero"
        )
        Spacer(Modifier.height(8.dp))
        LeftoverPolicyOptionCard(
            title = "Bawa Sisa ke Stok Besok (Carry Over)",
            subtitle = "Cocok untuk snack kering/tahan lama. Sisa stok dicatat di arsip & tetap tersedia besok.",
            selected = leftoverMode == AutoClosingLeftoverMode.CARRY_OVER,
            onClick = { onLeftoverModeChange(AutoClosingLeftoverMode.CARRY_OVER) },
            testTag = "option_leftover_carry_over"
        )

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = onSave,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .testTag("btn_save_auto_closing")
        ) {
            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Simpan Jadwal Auto Tutup Buku", fontWeight = FontWeight.Bold)
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp), color = lapakColors.cardBorder)

        if (isTodayClosed) {
            Surface(
                color = lapakColors.stockSafeBg,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = lapakColors.stockSafeText,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Buku hari ini sudah ditutup. Jadwal otomatis malam ini akan otomatis dilewati.",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = lapakColors.stockSafeText
                    )
                }
            }
        } else {
            OutlinedButton(
                onClick = onRequestManualCloseToday,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.2.dp, MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("btn_manual_close_from_settings")
            ) {
                Icon(Icons.Default.TaskAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Tutup Buku Hari Ini Sekarang", fontWeight = FontWeight.Bold)
            }
        }
    }

    if (showTimePickerDialog) {
        val timePickerState = rememberTimePickerState(
            initialHour = hour.coerceIn(0, 23),
            initialMinute = minute.coerceIn(0, 59),
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showTimePickerDialog = false },
            title = {
                Text(
                    text = "Pilih Jam Auto Tutup Buku (WIB)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    TimePicker(state = timePickerState)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onTimeChange(timePickerState.hour, timePickerState.minute)
                        showTimePickerDialog = false
                    },
                    modifier = Modifier.testTag("btn_confirm_time_picker")
                ) {
                    Text("Pilih Jam", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimePickerDialog = false }) {
                    Text("Batal")
                }
            }
        )
    }
}

@Composable
private fun LeftoverPolicyOptionCard(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    val lapakColors = LocalLapakColors.current
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else lapakColors.cardBorder
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = selected,
                onClick = onClick
            )
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// =============================================================================
// KUSTOMISASI PENGGUNA (TAMPILAN HP KASIR / KARYAWAN) SUB-PAGE
// =============================================================================
@Composable
internal fun CashierCustomizationSubPage(
    showOmzet: Boolean,
    onShowOmzetChange: (Boolean) -> Unit,
    showProfit: Boolean,
    onShowProfitChange: (Boolean) -> Unit,
    showExpenses: Boolean,
    onShowExpensesChange: (Boolean) -> Unit,
    onSave: () -> Unit
) {
    val lapakColors = LocalLapakColors.current

    SettingsSubPageCard {
        Surface(
            color = lapakColors.omzetCardBg,
            border = BorderStroke(1.dp, lapakColors.omzetCardBorder),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.ManageAccounts,
                    contentDescription = null,
                    tint = lapakColors.omzetCardAccent,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Kontrol Tampilan HP Kasir / Karyawan",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = lapakColors.omzetCardAccent
                    )
                    Text(
                        text = "Atur fitur keuangan apa saja yang boleh dilihat oleh perangkat ke-2 (Kasir/Karyawan). Perubahan langsung sinkron real-time ke HP Kasir.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        CashierToggleDetailRow(
            title = "Tampilkan Fitur Omset",
            subtitle = "Menampilkan kartu Total Omset & grafik omset di Beranda serta label Omzet di Riwayat HP Kasir.",
            checked = showOmzet,
            onCheckedChange = onShowOmzetChange,
            testTag = "toggle_cashier_show_omzet"
        )
        HorizontalDivider(color = lapakColors.cardBorder, modifier = Modifier.padding(vertical = 6.dp))

        CashierToggleDetailRow(
            title = "Tampilkan Fitur Untung Lapak",
            subtitle = "Menampilkan kartu Untung Bersih Lapak, komisi produk terlaris, dan untung per transaksi di HP Kasir.",
            checked = showProfit,
            onCheckedChange = onShowProfitChange,
            testTag = "toggle_cashier_show_profit"
        )
        HorizontalDivider(color = lapakColors.cardBorder, modifier = Modifier.padding(vertical = 6.dp))

        CashierToggleDetailRow(
            title = "Tampilkan Fitur Pengeluaran Operasional",
            subtitle = "Menampilkan kartu Biaya Operasional di Beranda & akses menu Biaya Operasional di Pengaturan HP Kasir.",
            checked = showExpenses,
            onCheckedChange = onShowExpensesChange,
            testTag = "toggle_cashier_show_expenses"
        )

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = onSave,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .testTag("btn_save_cashier_customization")
        ) {
            Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Simpan & Sinkronkan ke HP Kasir", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun CashierToggleDetailRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 6.dp)
            .testTag(testTag),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

// =============================================================================
// KUSTOMISASI PRINT & PRINTER BLUETOOTH SUB-PAGE
// =============================================================================
@Composable
internal fun PrinterSettingsSubPage(
    selectedPrinterName: String,
    selectedPrinterAddress: String,
    selectedPaperSize: String,
    onSelectPrinter: (name: String, address: String) -> Unit,
    onSelectPaperSize: (String) -> Unit,
    onSave: () -> Unit,
    onTestPrint: (onFinished: (Boolean, String) -> Unit) -> Unit
) {
    val context = LocalContext.current
    val lapakColors = LocalLapakColors.current

    var hasBtPermission by remember {
        mutableStateOf(BluetoothThermalPrinter.hasBluetoothConnectPermission(context))
    }
    var pairedDevices by remember { mutableStateOf<List<BluetoothPrinterDevice>>(emptyList()) }
    var btStatusError by remember { mutableStateOf<String?>(null) }
    var isTestingPrint by remember { mutableStateOf(false) }
    var testPrintFeedback by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

    fun refreshPairedDevices() {
        hasBtPermission = BluetoothThermalPrinter.hasBluetoothConnectPermission(context)
        if (!hasBtPermission) {
            pairedDevices = emptyList()
            btStatusError = "Izin perangkat terdekat (Bluetooth) diperlukan untuk melihat printer yang tersambung."
            return
        }
        val res = BluetoothThermalPrinter.getPairedDevices(context)
        res.fold(
            onSuccess = { devices ->
                pairedDevices = devices
                btStatusError = if (devices.isEmpty()) {
                    "Belum ada perangkat Bluetooth terpasang. Pasangkan (Pair) printer thermal di Pengaturan Bluetooth HP terlebih dahulu."
                } else null
            },
            onFailure = { err ->
                pairedDevices = emptyList()
                btStatusError = err.message ?: "Gagal membaca perangkat Bluetooth."
            }
        )
    }

    val btPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasBtPermission = granted
        refreshPairedDevices()
    }

    LaunchedEffect(Unit) {
        refreshPairedDevices()
    }

    SettingsSubPageCard {
        // Active printer summary
        Surface(
            color = lapakColors.omzetCardBg,
            border = BorderStroke(1.dp, lapakColors.omzetCardBorder),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Print,
                        contentDescription = null,
                        tint = lapakColors.omzetCardAccent,
                        modifier = Modifier.size(21.dp)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (selectedPrinterAddress.isBlank()) "Printer Sistem / PDF"
                        else selectedPrinterName.ifBlank { "Printer Bluetooth" },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = lapakColors.omzetCardAccent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (selectedPrinterAddress.isBlank()) {
                            "Ukuran: ${PrinterPaperSize.label(selectedPaperSize)}"
                        } else {
                            "MAC: $selectedPrinterAddress • ${PrinterPaperSize.label(selectedPaperSize)}"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // Paper Size Selector
        Text(
            text = "Ukuran Kertas Struk",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val paperOptions = listOf(
                Triple(PrinterPaperSize.MM_58, "58 mm", "32 kol"),
                Triple(PrinterPaperSize.MM_80, "80 mm", "48 kol"),
                Triple(PrinterPaperSize.A4, "A4 / PDF", "Standar")
            )
            paperOptions.forEach { (code, title, desc) ->
                val isSelected = selectedPaperSize == code
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    border = BorderStroke(
                        1.dp,
                        if (isSelected) MaterialTheme.colorScheme.primary else lapakColors.cardBorder
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 56.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onSelectPaperSize(code) }
                        .testTag("paper_size_${code.lowercase()}")
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = desc,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Bluetooth Printer List Header + Actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Perangkat Printer Tersedia",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !hasBtPermission) {
                            btPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
                        } else {
                            refreshPairedDevices()
                        }
                    },
                    modifier = Modifier.testTag("btn_refresh_bluetooth_printers")
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Segarkan", style = MaterialTheme.typography.labelSmall)
                }
                TextButton(
                    onClick = {
                        runCatching {
                            val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            context.startActivity(intent)
                        }
                    }
                ) {
                    Icon(Icons.Default.Bluetooth, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Bluetooth HP", style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        // Default System Printer option
        PrinterDeviceRowCard(
            name = "Printer Sistem Android / Simpan PDF",
            subtitle = "Gunakan layanan cetak bawaan HP (Wi-Fi / PDF)",
            isBluetooth = false,
            selected = selectedPrinterAddress.isBlank(),
            onClick = { onSelectPrinter("", "") },
            testTag = "printer_option_system"
        )

        Spacer(Modifier.height(8.dp))

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !hasBtPermission) {
            OutlinedButton(
                onClick = { btPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT) },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("btn_grant_bluetooth_permission")
            ) {
                Icon(Icons.Default.BluetoothSearching, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Izinkan Akses Printer Bluetooth", fontWeight = FontWeight.Bold)
            }
        } else if (!btStatusError.isNullOrBlank()) {
            Surface(
                color = lapakColors.warningBg,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = btStatusError!!,
                    style = MaterialTheme.typography.labelSmall,
                    color = lapakColors.warningText,
                    modifier = Modifier.padding(10.dp)
                )
            }
        }

        if (pairedDevices.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                pairedDevices.forEach { device ->
                    PrinterDeviceRowCard(
                        name = device.name,
                        subtitle = "Bluetooth • ${device.address}",
                        isBluetooth = true,
                        selected = selectedPrinterAddress.equals(device.address, ignoreCase = true),
                        onClick = { onSelectPrinter(device.name, device.address) },
                        testTag = "printer_bt_${device.address}"
                    )
                }
            }
        }

        testPrintFeedback?.let { (ok, msg) ->
            Spacer(Modifier.height(10.dp))
            Surface(
                color = if (ok) lapakColors.stockSafeBg else lapakColors.dangerBg,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = msg,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (ok) lapakColors.stockSafeText else lapakColors.dangerText,
                    modifier = Modifier.padding(10.dp)
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onSave,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .testTag("btn_save_printer_settings")
            ) {
                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Simpan", fontWeight = FontWeight.Bold)
            }

            OutlinedButton(
                onClick = {
                    if (isTestingPrint) return@OutlinedButton
                    isTestingPrint = true
                    testPrintFeedback = null
                    onTestPrint { ok, msg ->
                        isTestingPrint = false
                        testPrintFeedback = ok to msg
                    }
                },
                enabled = !isTestingPrint,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.2.dp, MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .testTag("btn_test_print")
            ) {
                if (isTestingPrint) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(6.dp))
                Text("Tes Cetak", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun PrinterDeviceRowCard(
    name: String,
    subtitle: String,
    isBluetooth: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    val lapakColors = LocalLapakColors.current
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
        else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else lapakColors.cardBorder
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 54.dp)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isBluetooth) Icons.Default.BluetoothConnected else Icons.Default.Print,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (selected) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Dipilih",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
