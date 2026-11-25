package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.AdminFeeCalculator
import com.example.data.model.AdminFeeType
import com.example.data.model.ThemeMode
import com.example.i18n.AppStrings
import com.example.ui.auth.GoogleAuthHelper
import com.example.ui.components.AddExpenseDialog
import com.example.ui.theme.LocalLapakColors
import com.example.ui.viewmodel.AppScreen
import com.example.ui.viewmodel.LapakViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: LapakViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lapakColors = LocalLapakColors.current

    val lang by viewModel.currentLanguage.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val appSettings by viewModel.appSettings.collectAsState()
    val shopProfile by viewModel.shopProfile.collectAsState()
    val expenses by viewModel.allExpenses.collectAsState()
    val isSyncingCloud by viewModel.isSyncingCloud.collectAsState()
    val lastCloudSyncAt by viewModel.lastCloudSyncAt.collectAsState()

    var shopName by remember(shopProfile) { mutableStateOf(shopProfile.shopName) }
    var ownerName by remember(shopProfile) { mutableStateOf(shopProfile.ownerName) }
    var waNumber by remember(shopProfile) { mutableStateOf(shopProfile.whatsappNumber) }
    var address by remember(shopProfile) { mutableStateOf(shopProfile.address) }
    var footerNote by remember(shopProfile) { mutableStateOf(shopProfile.footerMessage) }

    // Admin Fee Tariff State
    var selectedFeeType by remember(appSettings.adminFeeType) { mutableStateOf(appSettings.adminFeeType) }
    var feeValueText by remember(appSettings.adminFeeValue, appSettings.adminFeeType) {
        mutableStateOf(
            if (appSettings.adminFeeValue % 1.0 == 0.0) appSettings.adminFeeValue.toLong().toString()
            else appSettings.adminFeeValue.toString()
        )
    }
    var webBaseUrlText by remember(appSettings.webPortalBaseUrl) { mutableStateOf(appSettings.webPortalBaseUrl) }

    val parsedFeeVal = feeValueText.toDoubleOrNull() ?: 0.0
    val previewCalc = remember(selectedFeeType, parsedFeeVal) {
        AdminFeeCalculator.calculate(10_000.0, selectedFeeType, parsedFeeVal)
    }

    var showAddExpenseDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showRestorePrompt by remember { mutableStateOf(false) }
    var jsonInputForRestore by remember { mutableStateOf("") }

    val signedInEmail = remember { Firebase.auth.currentUser?.email ?: "Admin Lapak" }

    BackHandler {
        viewModel.navigateTo(AppScreen.DASHBOARD)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = AppStrings.get("settings_title", lang),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { viewModel.navigateTo(AppScreen.DASHBOARD) }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Kembali",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. TEMA TAMPILAN (Ikuti Sistem / Terang / Gelap) & BAHASA
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, lapakColors.cardBorder),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = AppStrings.get("settings_theme_title", lang),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Palet Soft Emerald & Warm Neutral lembut di mata untuk siang maupun malam.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val themeOptions = listOf(
                                Triple(ThemeMode.SYSTEM, "Ikuti Sistem", Icons.Default.BrightnessAuto),
                                Triple(ThemeMode.LIGHT, "Terang", Icons.Default.LightMode),
                                Triple(ThemeMode.DARK, "Gelap", Icons.Default.DarkMode)
                            )
                            themeOptions.forEach { (modeKey, label, icon) ->
                                val selected = themeMode == modeKey
                                FilterChip(
                                    selected = selected,
                                    onClick = { viewModel.setThemeMode(modeKey) },
                                    leadingIcon = {
                                        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
                                    },
                                    label = {
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.labelSmall,
                                            maxLines = 1
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                        selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("theme_option_${modeKey.lowercase()}")
                                )
                            }
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = lapakColors.cardBorder)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Language, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = AppStrings.get("settings_lang_title", lang),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                FilterChip(
                                    selected = lang == "id",
                                    onClick = { viewModel.setLanguage("id") },
                                    label = { Text("ID") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                    )
                                )
                                FilterChip(
                                    selected = lang == "en",
                                    onClick = { viewModel.setLanguage("en") },
                                    label = { Text("EN") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // 2. TARIF BIAYA ADMIN / KOMISI LAPAK + SINKRONISASI WEB PENYETOR
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, lapakColors.cardBorder),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Percent, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Tarif Biaya Admin / Komisi Lapak",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Otomatis menghitung Hak Penyetor & Untung Lapak saat menambah produk dan tampil real-time di Web Penyetor.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(Modifier.height(12.dp))

                        // Type selector: Nominal Tetap vs Persentase
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = selectedFeeType == AdminFeeType.FIXED,
                                onClick = {
                                    selectedFeeType = AdminFeeType.FIXED
                                    if (parsedFeeVal <= 100.0) feeValueText = "2000"
                                },
                                label = { Text("Nominal Tetap (Rp)", maxLines = 1) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("fee_type_fixed")
                            )
                            FilterChip(
                                selected = selectedFeeType == AdminFeeType.PERCENTAGE,
                                onClick = {
                                    selectedFeeType = AdminFeeType.PERCENTAGE
                                    if (parsedFeeVal > 100.0) feeValueText = "15"
                                },
                                label = { Text("Persentase (%)", maxLines = 1) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("fee_type_percentage")
                            )
                        }

                        Spacer(Modifier.height(8.dp))

                        // Quick Preset Chips
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val presets = if (selectedFeeType == AdminFeeType.FIXED) {
                                listOf("1000" to "Rp 1.000", "1500" to "Rp 1.500", "2000" to "Rp 2.000", "3000" to "Rp 3.000")
                            } else {
                                listOf("10" to "10%", "15" to "15%", "20" to "20%", "25" to "25%")
                            }
                            presets.forEach { (valStr, label) ->
                                SuggestionChip(
                                    onClick = { feeValueText = valStr },
                                    label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        OutlinedTextField(
                            value = feeValueText,
                            onValueChange = { feeValueText = it.filter { c -> c.isDigit() || c == '.' } },
                            label = {
                                Text(
                                    if (selectedFeeType == AdminFeeType.PERCENTAGE) "Persentase Komisi (%)"
                                    else "Potongan Biaya Admin per Item (Rp)"
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp)
                        )

                        Spacer(Modifier.height(8.dp))

                        // Live Calculation Preview Box
                        Surface(
                            color = lapakColors.omzetCardBg,
                            border = BorderStroke(1.dp, lapakColors.omzetCardBorder),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = "Simulasi Produk Harga Jual Rp 10.000:",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = lapakColors.omzetCardAccent
                                )
                                Text(
                                    text = "• Biaya Admin / Untung Lapak: ${LapakViewModel.formatRupiah(previewCalc.adminFee)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "• Uang Bersih Hak Penyetor: ${LapakViewModel.formatRupiah(previewCalc.sellerShare)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = lapakColors.omzetCardAccent
                                )
                            }
                        }

                        Spacer(Modifier.height(10.dp))

                        OutlinedTextField(
                            value = webBaseUrlText,
                            onValueChange = { webBaseUrlText = it },
                            label = { Text("URL Portal Web Penyetor") },
                            supportingText = { Text("Tautan dasar untuk halaman ?kode=LPK-XXXXXX") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp)
                        )

                        Spacer(Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    val value = feeValueText.toDoubleOrNull()
                                    if (value == null || value < 0 || (selectedFeeType == AdminFeeType.PERCENTAGE && value > 100)) {
                                        Toast.makeText(context, "Masukkan nominal/persentase tarif yang valid", Toast.LENGTH_SHORT).show()
                                    } else {
                                        viewModel.updateAdminFeeSettings(selectedFeeType, value, webBaseUrlText) {
                                            Toast.makeText(context, "Tarif admin disimpan & disinkronkan ke Web Penyetor!", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("save_admin_fee_button")
                            ) {
                                Text("Simpan Tarif")
                            }

                            OutlinedButton(
                                onClick = {
                                    viewModel.syncAllVendorsToCloud { _, msg ->
                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                    }
                                },
                                enabled = !isSyncingCloud,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                if (isSyncingCloud) {
                                    CircularProgressIndicator(modifier = Modifier.size(15.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Default.CloudSync, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                                Spacer(Modifier.width(5.dp))
                                Text("Sinkron Web", maxLines = 1)
                            }
                        }

                        if (lastCloudSyncAt != null) {
                            val sdf = remember { SimpleDateFormat("dd MMM HH:mm:ss", Locale("id", "ID")) }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "Sinkronisasi terakhir: ${sdf.format(Date(lastCloudSyncAt!!))}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // 3. PROFIL LAPAK
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, lapakColors.cardBorder),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Store, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = AppStrings.get("settings_shop_profile", lang),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(Modifier.height(12.dp))

                        OutlinedTextField(
                            value = shopName,
                            onValueChange = { shopName = it },
                            label = { Text(AppStrings.get("settings_shop_name", lang)) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = ownerName,
                            onValueChange = { ownerName = it },
                            label = { Text(AppStrings.get("settings_owner_name", lang)) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = waNumber,
                            onValueChange = { waNumber = it },
                            label = { Text(AppStrings.get("settings_wa_number", lang)) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = address,
                            onValueChange = { address = it },
                            label = { Text(AppStrings.get("settings_address", lang)) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = footerNote,
                            onValueChange = { footerNote = it },
                            label = { Text(AppStrings.get("settings_footer_note", lang)) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        )

                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = {
                                viewModel.updateProfile(shopName, ownerName, waNumber, address, footerNote)
                                Toast.makeText(context, "Profil lapak berhasil diperbarui!", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(AppStrings.get("btn_save", lang))
                        }
                    }
                }
            }

            // 4. BIAYA OPERASIONAL
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, lapakColors.cardBorder),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.ReceiptLong, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = AppStrings.get("settings_expenses_title", lang),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Total: ${LapakViewModel.formatRupiah(expenses.sumOf { it.amount })}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                            IconButton(onClick = { showAddExpenseDialog = true }) {
                                Icon(Icons.Default.AddCircle, contentDescription = "Tambah Biaya", tint = MaterialTheme.colorScheme.primary)
                            }
                        }

                        if (expenses.isEmpty()) {
                            Text(
                                text = "Belum ada biaya operasional tercatat.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        } else {
                            Spacer(Modifier.height(8.dp))
                            expenses.take(5).forEach { exp ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(exp.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                        if (exp.notes.isNotEmpty()) {
                                            Text(exp.notes, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(LapakViewModel.formatRupiah(exp.amount), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                        IconButton(onClick = { viewModel.deleteExpense(exp) }, modifier = Modifier.size(28.dp)) {
                                            Icon(Icons.Default.DeleteOutline, contentDescription = "Hapus", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 5. BACKUP & PEMULIHAN DATA (JSON)
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, lapakColors.cardBorder),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Backup, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = AppStrings.get("settings_backup_title", lang),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    viewModel.exportBackup { json ->
                                        viewModel.copyToClipboard(context, json)
                                        Toast.makeText(context, "Data cadangan JSON disalin ke clipboard!", Toast.LENGTH_LONG).show()
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Cadangkan", maxLines = 1, style = MaterialTheme.typography.labelSmall)
                            }

                            OutlinedButton(
                                onClick = { showRestorePrompt = true },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Pulihkan", maxLines = 1, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }

            // 6. AKUN ADMIN GOOGLE & KELUAR
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, lapakColors.cardBorder),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text("Akun Admin Terhubung", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(
                                text = signedInEmail,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    GoogleAuthHelper.signOut(context)
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.testTag("sign_out_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Keluar", maxLines = 1)
                        }
                    }
                }
            }

            // 7. TENTANG APLIKASI
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, lapakColors.cardBorder),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showAboutDialog = true }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = AppStrings.get("settings_about_title", lang),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Lapak Kita • ${AppStrings.get("settings_version", lang)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            item {
                Spacer(Modifier.height(30.dp))
            }
        }
    }

    if (showAddExpenseDialog) {
        AddExpenseDialog(
            lang = lang,
            onConfirm = { title, amt, notes ->
                viewModel.addExpense(title, amt, notes) {
                    showAddExpenseDialog = false
                    Toast.makeText(context, "Biaya operasional berhasil dicatat!", Toast.LENGTH_SHORT).show()
                }
            },
            onDismiss = { showAddExpenseDialog = false }
        )
    }

    if (showRestorePrompt) {
        AlertDialog(
            onDismissRequest = { showRestorePrompt = false },
            title = { Text(AppStrings.get("settings_restore_btn", lang), fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        text = "Tempelkan teks JSON cadangan untuk memulihkan seluruh data lapak:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = jsonInputForRestore,
                        onValueChange = { jsonInputForRestore = it },
                        placeholder = { Text("{\"version\":1, \"shopProfile\":...}") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.importBackup(jsonInputForRestore) { _, msg ->
                            showRestorePrompt = false
                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Pulihkan")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestorePrompt = false }) {
                    Text("Batal")
                }
            }
        )
    }

    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = { Text("Lapak Kita", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.lapak_banner_1794055340379),
                        contentDescription = "Lapak Banner",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = AppStrings.get("settings_about_desc", lang),
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "Developer: Rafline Dev",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { showAboutDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Tutup")
                }
            }
        )
    }
}
