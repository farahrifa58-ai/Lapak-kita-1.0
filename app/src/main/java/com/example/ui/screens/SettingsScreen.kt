package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.config.LicenseStorage
import com.example.data.model.*
import com.example.i18n.AppStrings
import com.example.ui.auth.GoogleAuthHelper
import com.example.ui.components.*
import com.example.ui.viewmodel.AppScreen
import com.example.ui.viewmodel.LapakViewModel
import com.example.util.GoogleDriveBackupHelper
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun SettingsScreen(
    viewModel: LapakViewModel,
    activeLicenseCode: String? = null,
    onSignedOut: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val safeUidKey = remember(viewModel.currentUserId) {
        LicenseStorage.sanitizeUidForStorageKey(viewModel.currentUserId)
    }

    val registeredLicenseCode = remember(activeLicenseCode, viewModel.currentUserId) {
        activeLicenseCode ?: LicenseStorage(context).getActivatedLicenseCode(viewModel.currentUserId)
    }

    val lang by viewModel.currentLanguage.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val appSettings by viewModel.appSettings.collectAsState()
    val shopProfile by viewModel.shopProfile.collectAsState()
    val expenses by viewModel.allExpenses.collectAsState()
    val products by viewModel.productsWithDetails.collectAsState()
    val isSyncingCloud by viewModel.isSyncingCloud.collectAsState()
    val lastCloudSyncAt by viewModel.lastCloudSyncAt.collectAsState()
    val connectedDevicesCount by viewModel.connectedDevicesCount.collectAsState()
    val isOwner by viewModel.isOwner.collectAsState()
    val isCloudLoaded by viewModel.isCloudLoaded.collectAsState()
    val isTodayClosed by viewModel.isTodayClosed.collectAsState()
    val canAccessExpenses = isOwner || appSettings.cashierShowExpenses

    // Dedicated full-screen sub-page navigation (null = main Pengaturan card list)
    var activeSubPage by remember { mutableStateOf<SettingsSubPage?>(null) }
    var isBackingUpDrive by remember { mutableStateOf(false) }

    LaunchedEffect(isOwner, canAccessExpenses, activeSubPage) {
        val blocked = mutableSetOf<SettingsSubPage>()
        if (!isOwner) {
            blocked.add(SettingsSubPage.BIAYA_ADMIN)
            blocked.add(SettingsSubPage.CADANGAN)
            blocked.add(SettingsSubPage.AUTO_CLOSING)
            blocked.add(SettingsSubPage.KUSTOMISASI_PENGGUNA)
        }
        if (!canAccessExpenses) {
            blocked.add(SettingsSubPage.BIAYA_OPERASIONAL)
        }
        if (activeSubPage in blocked) {
            activeSubPage = null
        }
    }

    // 1. Profil Lapak Form State
    var shopName by remember(shopProfile) { mutableStateOf(shopProfile.shopName) }
    var ownerName by remember(shopProfile) { mutableStateOf(shopProfile.ownerName) }
    var waNumber by remember(shopProfile) { mutableStateOf(shopProfile.whatsappNumber) }
    var address by remember(shopProfile) { mutableStateOf(shopProfile.address) }
    var footerNote by remember(shopProfile) { mutableStateOf(shopProfile.footerMessage) }
    var selectedIconKey by remember(shopProfile.shopIconKey) { mutableStateOf(shopProfile.shopIconKey) }
    var selectedHeaderBgKey by remember(shopProfile.headerBgColorKey) { mutableStateOf(shopProfile.headerBgColorKey) }
    var localLogoPath by remember(shopProfile.logoUri) { mutableStateOf(shopProfile.logoUri) }

    // 2. Biaya Admin Form State
    var selectedFeeType by remember(appSettings.adminFeeType) { mutableStateOf(appSettings.adminFeeType) }
    var feeValueText by remember(appSettings.adminFeeValue, appSettings.adminFeeType) {
        mutableStateOf(
            if (appSettings.adminFeeValue % 1.0 == 0.0) appSettings.adminFeeValue.toLong().toString()
            else appSettings.adminFeeValue.toString()
        )
    }
    val parsedFeeVal = feeValueText.toDoubleOrNull() ?: 0.0
    val previewCalc = remember(selectedFeeType, parsedFeeVal) {
        AdminFeeCalculator.calculate(10_000.0, selectedFeeType, parsedFeeVal)
    }

    // 3. Portal Web Penyetor State
    var webBaseUrlText by remember(appSettings.webPortalBaseUrl) { mutableStateOf(appSettings.webPortalBaseUrl) }
    var connectionTestMessage by remember { mutableStateOf<String?>(null) }
    var connectionTestOk by remember { mutableStateOf<Boolean?>(null) }

    // 4. Kustomisasi Promosi State
    var promoTemplate by remember(appSettings.posterTemplate) { mutableStateOf(appSettings.posterTemplate) }
    var promoIntro by remember(appSettings.posterIntroText) { mutableStateOf(appSettings.posterIntroText) }
    var promoOutro by remember(appSettings.posterOutroText) { mutableStateOf(appSettings.posterOutroText) }
    var promoShowLogo by remember(appSettings.posterShowLogo) { mutableStateOf(appSettings.posterShowLogo) }
    var promoShowShopName by remember(appSettings.posterShowShopName) { mutableStateOf(appSettings.posterShowShopName) }
    var promoShowContact by remember(appSettings.posterShowContact) { mutableStateOf(appSettings.posterShowContact) }
    var promoCustomBgPath by remember(appSettings.posterCustomBgUri) { mutableStateOf(appSettings.posterCustomBgUri) }

    // 5. Reset Stok Harian State
    var autoResetEnabled by remember(appSettings.autoResetStockEnabled) { mutableStateOf(appSettings.autoResetStockEnabled) }
    var autoResetHour by remember(appSettings.autoResetHour) { mutableStateOf(appSettings.autoResetHour) }
    var showConfirmResetStockDialog by remember { mutableStateOf(false) }
    var isResettingStock by remember { mutableStateOf(false) }

    // 6. Auto Tutup Buku Harian State
    var autoClosingEnabled by remember(appSettings.autoClosingEnabled) { mutableStateOf(appSettings.autoClosingEnabled) }
    var autoClosingHour by remember(appSettings.autoClosingHour) { mutableStateOf(appSettings.autoClosingHour) }
    var autoClosingMinute by remember(appSettings.autoClosingMinute) { mutableStateOf(appSettings.autoClosingMinute) }
    var autoClosingLeftoverMode by remember(appSettings.autoClosingLeftoverMode) { mutableStateOf(appSettings.autoClosingLeftoverMode) }
    var showManualClosingDialog by remember { mutableStateOf(false) }
    var isRunningManualClosing by remember { mutableStateOf(false) }

    // 7. Kustomisasi Pengguna (HP Kasir) State
    var cashierShowOmzet by remember(appSettings.cashierShowOmzet) { mutableStateOf(appSettings.cashierShowOmzet) }
    var cashierShowProfit by remember(appSettings.cashierShowProfit) { mutableStateOf(appSettings.cashierShowProfit) }
    var cashierShowExpenses by remember(appSettings.cashierShowExpenses) { mutableStateOf(appSettings.cashierShowExpenses) }

    // 8. Kustomisasi Print & Printer Bluetooth State
    var printerName by remember(appSettings.printerName) { mutableStateOf(appSettings.printerName) }
    var printerAddress by remember(appSettings.printerAddress) { mutableStateOf(appSettings.printerAddress) }
    var printerPaperSize by remember(appSettings.printerPaperSize) { mutableStateOf(appSettings.printerPaperSize) }

    // Zero-permission Photo Picker for Shop Logo (strictly UID-scoped file name)
    val logoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                val saved = copyPickedImageToInternal(context, uri, "lapakkita_${safeUidKey}_shop_logo.jpg")
                withContext(Dispatchers.Main) {
                    if (saved != null) {
                        localLogoPath = saved
                        selectedIconKey = ShopIconKey.CUSTOM_PHOTO
                        Toast.makeText(context, "Foto logo dipilih! Tekan Simpan Profil.", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Gagal memuat foto logo", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    // Zero-permission Photo Picker for Default Poster Background (strictly UID-scoped file name)
    val posterBgPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                val saved = copyPickedImageToInternal(context, uri, "lapakkita_${safeUidKey}_poster_default_bg.jpg")
                withContext(Dispatchers.Main) {
                    if (saved != null) {
                        promoCustomBgPath = saved
                        promoTemplate = PosterTemplate.CUSTOM_IMAGE
                        Toast.makeText(context, "Background poster dipilih! Tekan Simpan.", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Gagal memuat gambar galeri", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    // Dialog states
    var showAddExpenseDialog by remember { mutableStateOf(false) }
    var showPromoPreviewDialog by remember { mutableStateOf(false) }
    var showTutorialDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showRestorePrompt by remember { mutableStateOf(false) }
    var jsonInputForRestore by remember { mutableStateOf("") }

    val signedInEmail = remember(viewModel.currentUserId) {
        Firebase.auth.currentUser?.email ?: "Admin Lapak"
    }
    val syncTimeFormatter = remember { SimpleDateFormat("dd MMM HH:mm:ss", Locale("id", "ID")) }

    // Google Drive Backup (SAF CreateDocument — zero storage permissions, natively saves to Google Drive)
    val saveToDriveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            isBackingUpDrive = true
            viewModel.exportBackup { json ->
                scope.launch {
                    val res = GoogleDriveBackupHelper.writeJsonToUri(context, uri, json)
                    isBackingUpDrive = false
                    res.fold(
                        onSuccess = {
                            viewModel.recordGoogleDriveBackupSuccess(signedInEmail)
                            Toast.makeText(
                                context,
                                "Cadangan berhasil disimpan ke Google Drive!",
                                Toast.LENGTH_LONG
                            ).show()
                        },
                        onFailure = { err ->
                            Toast.makeText(
                                context,
                                err.message ?: "Gagal menyimpan cadangan ke Google Drive",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    )
                }
            }
        }
    }

    // Google Drive Restore (SAF OpenDocument — natively opens JSON backup from Google Drive)
    val restoreFromDriveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            isBackingUpDrive = true
            scope.launch {
                val res = GoogleDriveBackupHelper.readJsonFromUri(context, uri)
                res.fold(
                    onSuccess = { json ->
                        viewModel.importBackup(json) { _, msg ->
                            isBackingUpDrive = false
                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                        }
                    },
                    onFailure = { err ->
                        isBackingUpDrive = false
                        Toast.makeText(
                            context,
                            err.message ?: "Gagal membaca file cadangan Google Drive",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                )
            }
        }
    }

    BackHandler {
        if (activeSubPage != null) {
            activeSubPage = null
        } else {
            viewModel.navigateTo(AppScreen.DASHBOARD)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.navigationBars,
        topBar = {
            val currentSub = activeSubPage
            LapakTopHeader(
                title = currentSub?.headerTitle ?: AppStrings.get("settings_title", lang),
                subtitle = currentSub?.headerSubtitle ?: "Profil lapak, komisi, promosi & cloud",
                paletteKey = shopProfile.headerBgColorKey,
                showBackButton = true,
                onBackClick = {
                    if (activeSubPage != null) {
                        activeSubPage = null
                    } else {
                        viewModel.navigateTo(AppScreen.DASHBOARD)
                    }
                }
            )
        },
        modifier = modifier
    ) { paddingValues ->
        AnimatedContent(
            targetState = activeSubPage,
            transitionSpec = {
                val forward = targetState != null
                if (forward) {
                    (slideInHorizontally(animationSpec = tween(220)) { it / 3 } + fadeIn(tween(180)))
                        .togetherWith(slideOutHorizontally(animationSpec = tween(200)) { -it / 6 } + fadeOut(tween(160)))
                } else {
                    (slideInHorizontally(animationSpec = tween(220)) { -it / 3 } + fadeIn(tween(180)))
                        .togetherWith(slideOutHorizontally(animationSpec = tween(200)) { it / 6 } + fadeOut(tween(160)))
                }
            },
            label = "SettingsSubPageTransition",
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) { subPage ->
            if (subPage == null) {
                // =====================================================================
                // MAIN PENGATURAN MENU LIST (COMPACT NEUMORPHIC CARDS WITH `>` ARROW)
                // =====================================================================
                val selectedPalette = LapakHeaderPalettes.resolve(selectedHeaderBgKey)
                val activeTariffLabel = AdminFeeCalculator.formatTariffLabel(appSettings.adminFeeType, appSettings.adminFeeValue)
                val syncStatusSub = if (isSyncingCloud) {
                    "Sedang menyinkronkan ke cloud..."
                } else if (lastCloudSyncAt != null) {
                    "Firestore aktif • ${syncTimeFormatter.format(Date(lastCloudSyncAt!!))}"
                } else {
                    appSettings.webPortalBaseUrl
                }
                val activeTemplateLabel = if (appSettings.posterTemplate == PosterTemplate.CUSTOM_IMAGE) {
                    "Gambar Galeri"
                } else {
                    PromoPosterStyles.resolve(appSettings.posterTemplate).label
                }
                val formattedResetHour = OperationalCycleHelper.formatResetHour(autoResetHour)
                val resetCardSubtitle = if (autoResetEnabled) {
                    "Aktif • Otomatis pukul $formattedResetHour WIB"
                } else {
                    "Nonaktif • Reset dilakukan manual"
                }
                val totalExpenses = remember(expenses) { expenses.sumOf { it.amount } }
                val themeLabel = when (themeMode) {
                    ThemeMode.LIGHT -> "Mode Terang Soft"
                    ThemeMode.DARK -> "Mode Gelap Soft"
                    else -> "Ikuti Sistem"
                }
                val langLabel = if (lang == "en") "English" else "Bahasa Indonesia"
                val clampedDevices = connectedDevicesCount.coerceIn(0, 2)

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(top = 14.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // 1. Profil Lapak
                    item {
                        SettingsMenuRowCard(
                            title = "Profil Lapak",
                            subtitle = "${shopProfile.shopName} • ${selectedPalette.label}",
                            icon = Icons.Default.Storefront,
                            onClick = { activeSubPage = SettingsSubPage.PROFIL_LAPAK },
                            testTag = "menu_profil_lapak"
                        )
                    }

                    // 2. Biaya Admin / Komisi Lapak (Owner only)
                    if (isOwner) {
                        item {
                            SettingsMenuRowCard(
                                title = "Biaya Admin / Komisi Lapak",
                                subtitle = "Tarif aktif: $activeTariffLabel",
                                icon = Icons.Default.Percent,
                                onClick = { activeSubPage = SettingsSubPage.BIAYA_ADMIN },
                                testTag = "menu_biaya_admin"
                            )
                        }
                    }

                    // 3. Link Portal Web Penyetor
                    item {
                        SettingsMenuRowCard(
                            title = "Link Portal Web Penyetor",
                            subtitle = syncStatusSub,
                            icon = Icons.Default.CloudSync,
                            onClick = { activeSubPage = SettingsSubPage.WEB_PORTAL },
                            testTag = "menu_web_portal"
                        )
                    }

                    // 4. Kustomisasi Promosi
                    item {
                        SettingsMenuRowCard(
                            title = "Kustomisasi Promosi",
                            subtitle = "Template: $activeTemplateLabel",
                            icon = Icons.Default.AutoAwesome,
                            onClick = { activeSubPage = SettingsSubPage.PROMOSI },
                            testTag = "menu_kustomisasi_promosi"
                        )
                    }

                    // 5. Auto Tutup Buku Harian (Owner only)
                    if (isOwner) {
                        item {
                            val timeLabel = DailyClosingHelper.formatTime(
                                appSettings.autoClosingHour,
                                appSettings.autoClosingMinute
                            )
                            val autoClosingSub = if (appSettings.autoClosingEnabled) {
                                "Aktif • $timeLabel WIB • ${AutoClosingLeftoverMode.shortLabel(appSettings.autoClosingLeftoverMode)}"
                            } else {
                                "Nonaktif • Tutup buku dilakukan manual"
                            }
                            SettingsMenuRowCard(
                                title = "Auto Tutup Buku Harian",
                                subtitle = autoClosingSub,
                                icon = Icons.Default.EventRepeat,
                                onClick = { activeSubPage = SettingsSubPage.AUTO_CLOSING },
                                testTag = "menu_auto_closing"
                            )
                        }
                    }

                    // 6. Kustomisasi Pengguna (HP Kasir / Karyawan — Owner only)
                    if (isOwner) {
                        item {
                            val enabledModules = buildList {
                                if (appSettings.cashierShowOmzet) add("Omset")
                                if (appSettings.cashierShowProfit) add("Untung")
                                if (appSettings.cashierShowExpenses) add("Operasional")
                            }
                            val cashierSub = if (enabledModules.isEmpty()) {
                                "Kasir: Omset, Untung & Operasional disembunyikan"
                            } else {
                                "Tampil di Kasir: ${enabledModules.joinToString(", ")}"
                            }
                            SettingsMenuRowCard(
                                title = "Kustomisasi Pengguna (Kasir)",
                                subtitle = cashierSub,
                                icon = Icons.Default.ManageAccounts,
                                onClick = { activeSubPage = SettingsSubPage.KUSTOMISASI_PENGGUNA },
                                testTag = "menu_kustomisasi_pengguna"
                            )
                        }
                    }

                    // 7. Kustomisasi Print & Printer Bluetooth (Owner & Kasir)
                    item {
                        val activePrinterLabel = appSettings.printerName.ifBlank { "Printer Sistem / PDF" }
                        SettingsMenuRowCard(
                            title = "Kustomisasi Print & Printer",
                            subtitle = "$activePrinterLabel • ${PrinterPaperSize.label(appSettings.printerPaperSize)}",
                            icon = Icons.Default.Print,
                            onClick = { activeSubPage = SettingsSubPage.PRINTER_SETTINGS },
                            testTag = "menu_printer_settings"
                        )
                    }

                    // 8. Reset Stok Harian
                    item {
                        SettingsMenuRowCard(
                            title = "Reset Stok Harian",
                            subtitle = resetCardSubtitle,
                            icon = Icons.Default.Schedule,
                            onClick = { activeSubPage = SettingsSubPage.RESET_STOK },
                            testTag = "menu_reset_stok_harian"
                        )
                    }

                    // 9. Biaya Operasional (Owner or Cashier when enabled)
                    if (canAccessExpenses) {
                        item {
                            SettingsMenuRowCard(
                                title = "Biaya Operasional",
                                subtitle = "${expenses.size} catatan • Total ${LapakViewModel.formatRupiah(totalExpenses)}",
                                icon = Icons.Default.ReceiptLong,
                                onClick = { activeSubPage = SettingsSubPage.BIAYA_OPERASIONAL },
                                testTag = "menu_biaya_operasional"
                            )
                        }
                    }

                    // 10. Cadangan & Pemulihan (Owner only — Google Drive & JSON)
                    if (isOwner) {
                        item {
                            SettingsMenuRowCard(
                                title = "Cadangan & Pemulihan",
                                subtitle = "Cadangkan & pulihkan via Google Drive atau file JSON",
                                icon = Icons.Default.CloudUpload,
                                onClick = { activeSubPage = SettingsSubPage.CADANGAN },
                                testTag = "menu_backup_restore"
                            )
                        }
                    }

                    // 8. Tema & Bahasa
                    item {
                        SettingsMenuRowCard(
                            title = "Tema & Bahasa",
                            subtitle = "$themeLabel • $langLabel",
                            icon = Icons.Default.Palette,
                            onClick = { activeSubPage = SettingsSubPage.TEMA_BAHASA },
                            testTag = "menu_tema_bahasa"
                        )
                    }

                    // 9. Tutorial & Panduan Penggunaan
                    item {
                        SettingsMenuRowCard(
                            title = "Tutorial & Panduan Penggunaan",
                            subtitle = "4 alur lengkap dari persiapan hingga bayar setoran",
                            icon = Icons.Default.MenuBook,
                            onClick = { activeSubPage = SettingsSubPage.TUTORIAL },
                            testTag = "menu_tutorial_panduan"
                        )
                    }

                    // 10. Status Akun & Sinkronisasi Cloud
                    item {
                        val roleLabel = if (isOwner) "Owner" else "Kasir"
                        SettingsMenuRowCard(
                            title = "Status Akun & Sinkronisasi Cloud",
                            subtitle = "$signedInEmail • $roleLabel • $clampedDevices/2 HP",
                            icon = Icons.Default.CloudDone,
                            onClick = { activeSubPage = SettingsSubPage.AKUN_CLOUD },
                            testTag = "menu_status_akun_cloud"
                        )
                    }

                    // Compact About Application row
                    item {
                        SettingsMenuRowCard(
                            title = AppStrings.get("settings_about_title", lang),
                            subtitle = "Lapak Kita • ${AppStrings.get("settings_version", lang)}",
                            icon = Icons.Default.Info,
                            onClick = { showAboutDialog = true },
                            testTag = "menu_tentang_aplikasi"
                        )
                    }
                }
            } else {
                // =====================================================================
                // DEDICATED FULL-SCREEN SUB-PAGE CONTENT
                // =====================================================================
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(top = 14.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        when (subPage) {
                            SettingsSubPage.PROFIL_LAPAK -> {
                                ProfilLapakSubPage(
                                    lang = lang,
                                    shopName = shopName,
                                    onShopNameChange = { shopName = it },
                                    ownerName = ownerName,
                                    onOwnerNameChange = { ownerName = it },
                                    waNumber = waNumber,
                                    onWaNumberChange = { waNumber = it },
                                    address = address,
                                    onAddressChange = { address = it },
                                    footerNote = footerNote,
                                    onFooterNoteChange = { footerNote = it },
                                    selectedIconKey = selectedIconKey,
                                    onSelectedIconKeyChange = { selectedIconKey = it },
                                    selectedHeaderBgKey = selectedHeaderBgKey,
                                    onSelectedHeaderBgKeyChange = { selectedHeaderBgKey = it },
                                    localLogoPath = localLogoPath,
                                    onPickLogoPhoto = {
                                        logoPickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                    onSaveProfile = {
                                        viewModel.updateProfile(
                                            name = shopName,
                                            owner = ownerName,
                                            wa = waNumber,
                                            address = address,
                                            footer = footerNote,
                                            shopIconKey = selectedIconKey,
                                            headerBgColorKey = selectedHeaderBgKey,
                                            logoUri = localLogoPath
                                        )
                                        Toast.makeText(context, "Profil & tampilan lapak berhasil disimpan!", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }

                            SettingsSubPage.BIAYA_ADMIN -> {
                                BiayaAdminSubPage(
                                    selectedFeeType = selectedFeeType,
                                    onSelectFeeType = { type ->
                                        selectedFeeType = type
                                        if (type == AdminFeeType.FIXED && parsedFeeVal <= 100.0) {
                                            feeValueText = "2000"
                                        } else if (type == AdminFeeType.PERCENTAGE && parsedFeeVal > 100.0) {
                                            feeValueText = "15"
                                        }
                                    },
                                    feeValueText = feeValueText,
                                    onFeeValueTextChange = { feeValueText = it },
                                    previewCalc = previewCalc,
                                    onSaveAdminFee = {
                                        val value = feeValueText.toDoubleOrNull()
                                        if (value == null || value < 0 || (selectedFeeType == AdminFeeType.PERCENTAGE && value > 100)) {
                                            Toast.makeText(context, "Masukkan nominal/persentase tarif yang valid", Toast.LENGTH_SHORT).show()
                                        } else {
                                            viewModel.updateAdminFeeSettings(selectedFeeType, value, appSettings.webPortalBaseUrl) {
                                                Toast.makeText(context, "Tarif admin disimpan & disinkronkan!", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                )
                            }

                            SettingsSubPage.WEB_PORTAL -> {
                                WebPortalSubPage(
                                    isSyncingCloud = isSyncingCloud,
                                    lastCloudSyncAt = lastCloudSyncAt,
                                    syncTimeFormatter = syncTimeFormatter,
                                    webBaseUrlText = webBaseUrlText,
                                    onWebBaseUrlChange = { webBaseUrlText = it },
                                    connectionTestMessage = connectionTestMessage,
                                    connectionTestOk = connectionTestOk,
                                    onSaveUrl = {
                                        viewModel.updateWebPortalBaseUrl(webBaseUrlText) { savedUrl ->
                                            webBaseUrlText = savedUrl
                                            Toast.makeText(context, "URL portal disimpan & disinkronkan!", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    onSyncAllVendors = {
                                        viewModel.syncAllVendorsToCloud { ok, msg ->
                                            connectionTestOk = ok
                                            connectionTestMessage = msg
                                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    onTestConnection = {
                                        viewModel.testCloudConnection { ok, msg ->
                                            connectionTestOk = ok
                                            connectionTestMessage = msg
                                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                )
                            }

                            SettingsSubPage.PROMOSI -> {
                                PromosiSubPage(
                                    promoTemplate = promoTemplate,
                                    onPromoTemplateChange = { promoTemplate = it },
                                    onPickPosterBg = {
                                        posterBgPickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                    promoIntro = promoIntro,
                                    onPromoIntroChange = { promoIntro = it },
                                    promoOutro = promoOutro,
                                    onPromoOutroChange = { promoOutro = it },
                                    promoShowLogo = promoShowLogo,
                                    onPromoShowLogoChange = { promoShowLogo = it },
                                    promoShowShopName = promoShowShopName,
                                    onPromoShowShopNameChange = { promoShowShopName = it },
                                    promoShowContact = promoShowContact,
                                    onPromoShowContactChange = { promoShowContact = it },
                                    onSavePromo = {
                                        viewModel.updatePromoSettings(
                                            template = promoTemplate,
                                            introText = promoIntro,
                                            outroText = promoOutro,
                                            showLogo = promoShowLogo,
                                            showShopName = promoShowShopName,
                                            showContact = promoShowContact,
                                            customBgUri = promoCustomBgPath
                                        ) {
                                            Toast.makeText(context, "Pengaturan promosi disimpan!", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    onPreviewPromo = {
                                        viewModel.updatePromoSettings(
                                            template = promoTemplate,
                                            introText = promoIntro,
                                            outroText = promoOutro,
                                            showLogo = promoShowLogo,
                                            showShopName = promoShowShopName,
                                            showContact = promoShowContact,
                                            customBgUri = promoCustomBgPath
                                        ) {
                                            showPromoPreviewDialog = true
                                        }
                                    }
                                )
                            }

                            SettingsSubPage.AUTO_CLOSING -> {
                                AutoClosingSubPage(
                                    enabled = autoClosingEnabled,
                                    onEnabledChange = { autoClosingEnabled = it },
                                    hour = autoClosingHour,
                                    minute = autoClosingMinute,
                                    onTimeChange = { h, m ->
                                        autoClosingHour = h
                                        autoClosingMinute = m
                                    },
                                    leftoverMode = autoClosingLeftoverMode,
                                    onLeftoverModeChange = { autoClosingLeftoverMode = it },
                                    lastClosedDate = appSettings.lastClosedDate,
                                    isTodayClosed = isTodayClosed,
                                    onSave = {
                                        viewModel.updateAutoClosingSettings(
                                            enabled = autoClosingEnabled,
                                            hour = autoClosingHour,
                                            minute = autoClosingMinute,
                                            leftoverMode = autoClosingLeftoverMode
                                        ) { _, msg ->
                                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                        }
                                    },
                                    onRequestManualCloseToday = {
                                        showManualClosingDialog = true
                                    }
                                )
                            }

                            SettingsSubPage.KUSTOMISASI_PENGGUNA -> {
                                CashierCustomizationSubPage(
                                    showOmzet = cashierShowOmzet,
                                    onShowOmzetChange = { cashierShowOmzet = it },
                                    showProfit = cashierShowProfit,
                                    onShowProfitChange = { cashierShowProfit = it },
                                    showExpenses = cashierShowExpenses,
                                    onShowExpensesChange = { cashierShowExpenses = it },
                                    onSave = {
                                        viewModel.updateCashierVisibilitySettings(
                                            showOmzet = cashierShowOmzet,
                                            showProfit = cashierShowProfit,
                                            showExpenses = cashierShowExpenses
                                        ) { _, msg ->
                                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                        }
                                    }
                                )
                            }

                            SettingsSubPage.PRINTER_SETTINGS -> {
                                PrinterSettingsSubPage(
                                    selectedPrinterName = printerName,
                                    selectedPrinterAddress = printerAddress,
                                    selectedPaperSize = printerPaperSize,
                                    onSelectPrinter = { name, addr ->
                                        printerName = name
                                        printerAddress = addr
                                    },
                                    onSelectPaperSize = { size ->
                                        printerPaperSize = size
                                    },
                                    onSave = {
                                        viewModel.updatePrinterSettings(
                                            printerName = printerName,
                                            printerAddress = printerAddress,
                                            paperSize = printerPaperSize
                                        ) { _, msg ->
                                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    onTestPrint = { onFinished ->
                                        viewModel.testPrintReceipt(
                                            context = context,
                                            printerName = printerName,
                                            printerAddress = printerAddress,
                                            paperSize = printerPaperSize,
                                            onResult = onFinished
                                        )
                                    }
                                )
                            }

                            SettingsSubPage.RESET_STOK -> {
                                val totalActiveProductsCount = remember(products) { products.count { it.currentStock > 0 } }
                                val totalActiveStockQty = remember(products) { products.sumOf { it.currentStock } }
                                ResetStokSubPage(
                                    autoResetEnabled = autoResetEnabled,
                                    onToggleAutoReset = { checked ->
                                        autoResetEnabled = checked
                                        val formattedHour = OperationalCycleHelper.formatResetHour(autoResetHour)
                                        viewModel.updateDailyStockResetSettings(
                                            enabled = checked,
                                            resetHour = autoResetHour
                                        ) {
                                            Toast.makeText(
                                                context,
                                                if (checked) "Auto-Reset Stok Harian pukul $formattedHour WIB diaktifkan"
                                                else "Auto-Reset Stok Harian dinonaktifkan",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    },
                                    autoResetHour = autoResetHour,
                                    onChangeResetHour = { newHour ->
                                        autoResetHour = newHour
                                        viewModel.updateDailyStockResetSettings(autoResetEnabled, newHour) {
                                            Toast.makeText(
                                                context,
                                                "Jam reset diatur ke ${OperationalCycleHelper.formatResetHour(newHour)} WIB",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    },
                                    totalActiveProductsCount = totalActiveProductsCount,
                                    totalActiveStockQty = totalActiveStockQty,
                                    isResettingStock = isResettingStock,
                                    onRequestManualReset = { showConfirmResetStockDialog = true }
                                )
                            }

                            SettingsSubPage.BIAYA_OPERASIONAL -> {
                                BiayaOperasionalSubPage(
                                    expenses = expenses,
                                    onAddExpenseClick = { showAddExpenseDialog = true },
                                    onDeleteExpense = { viewModel.deleteExpense(it) }
                                )
                            }

                            SettingsSubPage.CADANGAN -> {
                                CadanganSubPage(
                                    lastBackupDate = appSettings.lastBackupDate,
                                    driveAccountEmail = appSettings.driveAccountEmail ?: signedInEmail,
                                    isBackingUpDrive = isBackingUpDrive,
                                    syncTimeFormatter = syncTimeFormatter,
                                    onSaveToGoogleDrive = {
                                        saveToDriveLauncher.launch(
                                            GoogleDriveBackupHelper.defaultBackupFileName(shopProfile.shopName)
                                        )
                                    },
                                    onQuickUploadToDrive = {
                                        isBackingUpDrive = true
                                        viewModel.exportBackup { json ->
                                            scope.launch {
                                                val res = GoogleDriveBackupHelper.shareBackupFileToGoogleDrive(
                                                    context = context,
                                                    json = json,
                                                    shopName = shopProfile.shopName
                                                )
                                                isBackingUpDrive = false
                                                res.fold(
                                                    onSuccess = { fileName ->
                                                        viewModel.recordGoogleDriveBackupSuccess(signedInEmail)
                                                        Toast.makeText(
                                                            context,
                                                            "Membuka Google Drive untuk menyimpan $fileName",
                                                            Toast.LENGTH_LONG
                                                        ).show()
                                                    },
                                                    onFailure = { err ->
                                                        Toast.makeText(
                                                            context,
                                                            err.message ?: "Gagal membuka aplikasi Google Drive",
                                                            Toast.LENGTH_LONG
                                                        ).show()
                                                    }
                                                )
                                            }
                                        }
                                    },
                                    onRestoreFromGoogleDrive = {
                                        restoreFromDriveLauncher.launch(
                                            arrayOf("application/json", "text/plain", "*/*")
                                        )
                                    },
                                    onCopyJson = {
                                        viewModel.exportBackup { json ->
                                            viewModel.copyToClipboard(context, json)
                                            Toast.makeText(context, "Cadangan JSON disalin ke clipboard!", Toast.LENGTH_LONG).show()
                                        }
                                    },
                                    onShareToWhatsApp = {
                                        viewModel.exportBackup { json ->
                                            viewModel.shareTextToWhatsApp(context, json)
                                        }
                                    },
                                    onOpenRestoreDialog = { showRestorePrompt = true }
                                )
                            }

                            SettingsSubPage.TEMA_BAHASA -> {
                                TemaBahasaSubPage(
                                    themeMode = themeMode,
                                    onSelectTheme = { viewModel.setThemeMode(it) },
                                    lang = lang,
                                    onSelectLanguage = { viewModel.setLanguage(it) }
                                )
                            }

                            SettingsSubPage.TUTORIAL -> {
                                TutorialSubPage(
                                    onOpenTutorialGuide = { showTutorialDialog = true }
                                )
                            }

                            SettingsSubPage.AKUN_CLOUD -> {
                                AccountCloudSyncSubPage(
                                    signedInEmail = signedInEmail,
                                    registeredLicenseCode = registeredLicenseCode,
                                    connectedDevicesCount = connectedDevicesCount,
                                    isOwner = isOwner,
                                    isCloudLoaded = isCloudLoaded,
                                    isSyncingCloud = isSyncingCloud,
                                    lastCloudSyncAt = lastCloudSyncAt,
                                    syncTimeFormatter = syncTimeFormatter,
                                    onSignOutClick = {
                                        onSignedOut()
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // =========================================================================
    // DIALOGS
    // =========================================================================
    if (showTutorialDialog) {
        TutorialGuideDialog(onDismiss = { showTutorialDialog = false })
    }

    if (showPromoPreviewDialog) {
        PromoPosterDialog(
            shopProfile = shopProfile,
            appSettings = appSettings,
            productsWithDetails = products,
            onDismiss = { showPromoPreviewDialog = false }
        )
    }

    if (showManualClosingDialog) {
        ManualDailyClosingDialog(
            defaultLeftoverMode = autoClosingLeftoverMode,
            isProcessing = isRunningManualClosing,
            onConfirm = { chosenMode ->
                isRunningManualClosing = true
                viewModel.runManualDailyClosing(leftoverModeOverride = chosenMode) { _, msg ->
                    isRunningManualClosing = false
                    showManualClosingDialog = false
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                }
            },
            onDismiss = { showManualClosingDialog = false }
        )
    }

    if (showConfirmResetStockDialog) {
        val activeProdCount = products.count { it.currentStock > 0 }
        val activeQtySum = products.sumOf { it.currentStock }
        AlertDialog(
            onDismissRequest = { if (!isResettingStock) showConfirmResetStockDialog = false },
            icon = {
                Icon(
                    Icons.Default.RestartAlt,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = {
                Text(
                    text = "Reset Semua Stok ke 0?",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = if (activeProdCount > 0) {
                        "Sebanyak $activeProdCount produk ($activeQtySum total sisa stok) akan diarsipkan ke riwayat harian lalu direset menjadi 0 di aplikasi dan Portal Web Penyetor. Data transaksi penjualan & setoran tidak akan terhapus."
                    } else {
                        "Semua stok produk saat ini sudah 0. Tetap jalankan sinkronisasi reset stok ke Portal Web Penyetor?"
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        isResettingStock = true
                        viewModel.resetAllStocksManually { result ->
                            isResettingStock = false
                            showConfirmResetStockDialog = false
                            val msg = if (result.resetProductsCount > 0) {
                                "Berhasil! ${result.resetProductsCount} produk (${result.totalArchivedQty} stok) diarsipkan & direset ke 0."
                            } else {
                                "Semua stok produk sudah 0 & disinkronkan."
                            }
                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                        }
                    },
                    enabled = !isResettingStock,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    modifier = Modifier.testTag("confirm_manual_reset_stock_button")
                ) {
                    Text(if (isResettingStock) "Mereset..." else "Ya, Reset ke 0", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showConfirmResetStockDialog = false },
                    enabled = !isResettingStock
                ) {
                    Text("Batal")
                }
            }
        )
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
            title = {
                Text(
                    text = "Lapak Kita",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.testTag("about_lapak_kita_title")
                )
            },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_lapak_logo),
                        contentDescription = "Logo Lapak Kita",
                        modifier = Modifier
                            .size(110.dp)
                            .clip(RoundedCornerShape(22.dp)),
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
                        text = "By Rafline Dev",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    if (!registeredLicenseCode.isNullOrBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "Lisensi Aktif: $registeredLicenseCode",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
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
