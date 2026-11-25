package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.*
import com.example.i18n.AppStrings
import com.example.ui.auth.GoogleAuthHelper
import com.example.ui.components.*
import com.example.ui.theme.LocalLapakColors
import com.example.ui.viewmodel.AppScreen
import com.example.ui.viewmodel.LapakViewModel
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
    val products by viewModel.productsWithDetails.collectAsState()
    val isSyncingCloud by viewModel.isSyncingCloud.collectAsState()
    val lastCloudSyncAt by viewModel.lastCloudSyncAt.collectAsState()

    // Accordion state: null means all collapsed initially for ultra-clean scannability
    var expandedSection by remember { mutableStateOf<String?>(null) }
    fun toggleSection(key: String) {
        expandedSection = if (expandedSection == key) null else key
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

    // Zero-permission Photo Picker for Shop Logo
    val logoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                val saved = copyPickedImageToInternal(context, uri, "shop_logo.jpg")
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

    // Zero-permission Photo Picker for Default Poster Background
    val posterBgPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                val saved = copyPickedImageToInternal(context, uri, "poster_default_bg.jpg")
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

    val signedInEmail = remember { Firebase.auth.currentUser?.email ?: "Admin Lapak" }
    val syncTimeFormatter = remember { SimpleDateFormat("dd MMM HH:mm:ss", Locale("id", "ID")) }

    BackHandler {
        viewModel.navigateTo(AppScreen.DASHBOARD)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.navigationBars,
        topBar = {
            LapakTopHeader(
                title = AppStrings.get("settings_title", lang),
                subtitle = "Profil lapak, komisi, promosi & panduan",
                paletteKey = shopProfile.headerBgColorKey,
                showBackButton = true,
                onBackClick = { viewModel.navigateTo(AppScreen.DASHBOARD) }
            )
        },
        modifier = modifier
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 14.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // =========================================================================
            // 1. PROFIL LAPAK
            // =========================================================================
            item {
                val selectedPalette = LapakHeaderPalettes.resolve(selectedHeaderBgKey)
                SettingsAccordionCard(
                    title = "Profil Lapak",
                    subtitle = "${shopProfile.shopName} • ${selectedPalette.label}",
                    icon = Icons.Default.Storefront,
                    expanded = expandedSection == "profile",
                    onToggle = { toggleSection("profile") },
                    testTag = "accordion_profil_lapak"
                ) {
                    // Live mini preview of Dashboard header style & shop icon
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Brush.horizontalGradient(listOf(selectedPalette.lightStart, selectedPalette.lightEnd)))
                            .padding(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ShopAvatarBadge(
                                iconKey = selectedIconKey,
                                logoUri = localLogoPath,
                                size = 40.dp
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = shopName.ifBlank { "Lapak Kita" },
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = waNumber.ifBlank { "Pratinjau Header & Poster" },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.85f),
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    OutlinedTextField(
                        value = shopName,
                        onValueChange = { shopName = it },
                        label = { Text(AppStrings.get("settings_shop_name", lang)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = ownerName,
                        onValueChange = { ownerName = it },
                        label = { Text(AppStrings.get("settings_owner_name", lang)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = waNumber,
                        onValueChange = { waNumber = it },
                        label = { Text(AppStrings.get("settings_wa_number", lang)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
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
                    Text(
                        text = "Pilihan Ikon / Foto Lapak",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(6.dp))
                    val iconChoices = listOf(
                        Triple(ShopIconKey.STORE, "Warung", Icons.Default.Storefront),
                        Triple(ShopIconKey.BAKERY, "Kue", Icons.Default.BakeryDining),
                        Triple(ShopIconKey.CAFE, "Kafe", Icons.Default.LocalCafe),
                        Triple(ShopIconKey.BASKET, "Keranjang", Icons.Default.ShoppingBasket),
                        Triple(ShopIconKey.RESTAURANT, "Kuliner", Icons.Default.Restaurant)
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(iconChoices) { (key, label, icon) ->
                            FilterChip(
                                selected = selectedIconKey == key,
                                onClick = { selectedIconKey = key },
                                leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary
                                )
                            )
                        }
                        item {
                            FilterChip(
                                selected = selectedIconKey == ShopIconKey.CUSTOM_PHOTO,
                                onClick = {
                                    logoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                leadingIcon = { Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                label = { Text("Upload Foto", style = MaterialTheme.typography.labelSmall) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary
                                )
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Warna Background Header & Profil",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(LapakHeaderPalettes.all) { palette ->
                            val isSelected = selectedHeaderBgKey == palette.key
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { selectedHeaderBgKey = palette.key }
                                    .padding(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(Brush.linearGradient(listOf(palette.lightStart, palette.lightEnd)))
                                        .border(
                                            width = if (isSelected) 3.dp else 1.dp,
                                            color = if (isSelected) MaterialTheme.colorScheme.onSurface else lapakColors.cardBorder,
                                            shape = CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isSelected) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = "Dipilih",
                                            tint = Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    text = palette.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(14.dp))
                    Button(
                        onClick = {
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
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 46.dp)
                            .testTag("save_shop_profile_button")
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Simpan Profil Lapak", fontWeight = FontWeight.Bold)
                    }
                }
            }

            // =========================================================================
            // 2. BIAYA ADMIN / KOMISI LAPAK
            // =========================================================================
            item {
                val activeTariffLabel = AdminFeeCalculator.formatTariffLabel(appSettings.adminFeeType, appSettings.adminFeeValue)
                SettingsAccordionCard(
                    title = "Biaya Admin / Komisi Lapak",
                    subtitle = "Tarif aktif: $activeTariffLabel",
                    icon = Icons.Default.Percent,
                    expanded = expandedSection == "admin_fee",
                    onToggle = { toggleSection("admin_fee") },
                    testTag = "accordion_biaya_admin"
                ) {
                    Text(
                        text = "Otomatis menghitung Hak Penyetor & Untung Lapak saat menambah produk. Anda juga bisa atur tarif khusus per penyetor di halaman Penyetor.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))

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

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        val presets = if (selectedFeeType == AdminFeeType.FIXED) {
                            listOf("1000" to "Rp 1.000", "1500" to "Rp 1.500", "2000" to "Rp 2.000", "3000" to "Rp 3.000")
                        } else {
                            listOf("10" to "10%", "15" to "15%", "20" to "20%", "25" to "25%")
                        }
                        items(presets) { (valStr, label) ->
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

                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            val value = feeValueText.toDoubleOrNull()
                            if (value == null || value < 0 || (selectedFeeType == AdminFeeType.PERCENTAGE && value > 100)) {
                                Toast.makeText(context, "Masukkan nominal/persentase tarif yang valid", Toast.LENGTH_SHORT).show()
                            } else {
                                viewModel.updateAdminFeeSettings(selectedFeeType, value, appSettings.webPortalBaseUrl) {
                                    Toast.makeText(context, "Tarif admin disimpan & disinkronkan!", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 46.dp)
                            .testTag("save_admin_fee_button")
                    ) {
                        Text("Simpan Tarif Biaya Admin", fontWeight = FontWeight.Bold)
                    }
                }
            }

            // =========================================================================
            // 3. LINK PORTAL WEB PENYETOR
            // =========================================================================
            item {
                val syncStatusSub = if (isSyncingCloud) {
                    "Sedang menyinkronkan ke cloud..."
                } else if (lastCloudSyncAt != null) {
                    "Firestore aktif • ${syncTimeFormatter.format(Date(lastCloudSyncAt!!))}"
                } else {
                    appSettings.webPortalBaseUrl
                }
                SettingsAccordionCard(
                    title = "Link Portal Web Penyetor",
                    subtitle = syncStatusSub,
                    icon = Icons.Default.CloudSync,
                    expanded = expandedSection == "web_portal",
                    onToggle = { toggleSection("web_portal") },
                    testTag = "accordion_web_portal"
                ) {
                    Surface(
                        color = lapakColors.omzetCardBg,
                        border = BorderStroke(1.dp, lapakColors.omzetCardBorder),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isSyncingCloud) Icons.Default.Sync else Icons.Default.CloudDone,
                                contentDescription = null,
                                tint = lapakColors.omzetCardAccent,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isSyncingCloud) "Menyinkronkan Firestore..." else "Sinkronisasi Firestore Real-Time Aktif",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = lapakColors.omzetCardAccent
                                )
                                Text(
                                    text = if (lastCloudSyncAt != null) {
                                        "Sinkron terakhir: ${syncTimeFormatter.format(Date(lastCloudSyncAt!!))}"
                                    } else {
                                        "Siap menerima update produk & stok harian dari penjual"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    OutlinedTextField(
                        value = webBaseUrlText,
                        onValueChange = { webBaseUrlText = it },
                        label = { Text("URL Dasar Portal Web Penyetor") },
                        supportingText = { Text("Domain permanen: https://lapakkita1.vercel.app") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp)
                    )

                    if (!connectionTestMessage.isNullOrBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Surface(
                            color = if (connectionTestOk == true) lapakColors.stockSafeBg else lapakColors.dangerBg,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = connectionTestMessage!!,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (connectionTestOk == true) lapakColors.stockSafeText else lapakColors.dangerText,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = {
                            viewModel.updateWebPortalBaseUrl(webBaseUrlText) { savedUrl ->
                                webBaseUrlText = savedUrl
                                Toast.makeText(context, "URL portal disimpan & disinkronkan!", Toast.LENGTH_SHORT).show()
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 44.dp)
                    ) {
                        Text("Simpan URL Portal", fontWeight = FontWeight.Bold)
                    }

                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                viewModel.syncAllVendorsToCloud { ok, msg ->
                                    connectionTestOk = ok
                                    connectionTestMessage = msg
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                }
                            },
                            enabled = !isSyncingCloud,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Sinkronkan", maxLines = 1, style = MaterialTheme.typography.labelMedium)
                        }

                        OutlinedButton(
                            onClick = {
                                viewModel.testCloudConnection { ok, msg ->
                                    connectionTestOk = ok
                                    connectionTestMessage = msg
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                }
                            },
                            enabled = !isSyncingCloud,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("btn_test_cloud_connection")
                        ) {
                            if (isSyncingCloud) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.WifiTethering, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                            Spacer(Modifier.width(4.dp))
                            Text("Uji Koneksi", maxLines = 1, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }

            // =========================================================================
            // 4. KUSTOMISASI PROMOSI
            // =========================================================================
            item {
                val activeTemplateLabel = if (appSettings.posterTemplate == PosterTemplate.CUSTOM_IMAGE) {
                    "Gambar Galeri"
                } else {
                    PromoPosterStyles.resolve(appSettings.posterTemplate).label
                }
                SettingsAccordionCard(
                    title = "Kustomisasi Promosi",
                    subtitle = "Template: $activeTemplateLabel",
                    icon = Icons.Default.AutoAwesome,
                    expanded = expandedSection == "promo",
                    onToggle = { toggleSection("promo") },
                    testTag = "accordion_kustomisasi_promosi"
                ) {
                    Text(
                        text = "Template Background Default Poster",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(6.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(PromoPosterStyles.allTemplates) { spec ->
                            FilterChip(
                                selected = promoTemplate == spec.key,
                                onClick = { promoTemplate = spec.key },
                                label = { Text(spec.label, style = MaterialTheme.typography.labelSmall) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                )
                            )
                        }
                        item {
                            FilterChip(
                                selected = promoTemplate == PosterTemplate.CUSTOM_IMAGE,
                                onClick = {
                                    posterBgPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                leadingIcon = { Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                label = { Text("Gambar Galeri", style = MaterialTheme.typography.labelSmall) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary
                                )
                            )
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = promoIntro,
                        onValueChange = { promoIntro = it },
                        label = { Text("Teks Ajakan Pembuka Poster") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = promoOutro,
                        onValueChange = { promoOutro = it },
                        label = { Text("Teks Ajakan Penutup Poster") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp)
                    )

                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "Tampilan Default Elemen Poster",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    SettingsToggleRow(
                        label = "Tampilkan Ikon / Logo Lapak",
                        checked = promoShowLogo,
                        onCheckedChange = { promoShowLogo = it }
                    )
                    SettingsToggleRow(
                        label = "Tampilkan Nama Lapak",
                        checked = promoShowShopName,
                        onCheckedChange = { promoShowShopName = it }
                    )
                    SettingsToggleRow(
                        label = "Tampilkan Nomor WA & Alamat",
                        checked = promoShowContact,
                        onCheckedChange = { promoShowContact = it }
                    )

                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
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
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("save_promo_settings_button")
                        ) {
                            Text("Simpan", fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = {
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
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Pratinjau", maxLines = 1)
                        }
                    }
                }
            }

            // =========================================================================
            // 5. BIAYA OPERASIONAL
            // =========================================================================
            item {
                val totalExpenses = remember(expenses) { expenses.sumOf { it.amount } }
                SettingsAccordionCard(
                    title = "Biaya Operasional",
                    subtitle = "${expenses.size} catatan • Total ${LapakViewModel.formatRupiah(totalExpenses)}",
                    icon = Icons.Default.ReceiptLong,
                    expanded = expandedSection == "expenses",
                    onToggle = { toggleSection("expenses") },
                    testTag = "accordion_biaya_operasional"
                ) {
                    Text(
                        text = "Catat pengeluaran rutin/harian lapak (plastik, kresek, listrik, kebersihan) agar otomatis mengurangi Untung Lapak di Dashboard.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))

                    Button(
                        onClick = { showAddExpenseDialog = true },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.AddCircleOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Tambah Biaya Operasional", fontWeight = FontWeight.Bold)
                    }

                    Spacer(Modifier.height(10.dp))

                    if (expenses.isEmpty()) {
                        Text(
                            text = "Belum ada biaya operasional tercatat.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    } else {
                        expenses.take(10).forEach { exp ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 5.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                    Text(exp.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                    if (exp.notes.isNotEmpty()) {
                                        Text(exp.notes, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = LapakViewModel.formatRupiah(exp.amount),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    IconButton(
                                        onClick = { viewModel.deleteExpense(exp) },
                                        modifier = Modifier.size(30.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.DeleteOutline,
                                            contentDescription = "Hapus Biaya",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(17.dp)
                                        )
                                    }
                                }
                            }
                            HorizontalDivider(color = lapakColors.cardBorder.copy(alpha = 0.6f))
                        }
                    }
                }
            }

            // =========================================================================
            // 6. CADANGAN & PEMULIHAN (BACKUP & RESTORE)
            // =========================================================================
            item {
                SettingsAccordionCard(
                    title = "Cadangan & Pemulihan",
                    subtitle = "Ekspor JSON / kirim ke WA & pulihkan saat ganti HP",
                    icon = Icons.Default.Backup,
                    expanded = expandedSection == "backup",
                    onToggle = { toggleSection("backup") },
                    testTag = "accordion_backup_restore"
                ) {
                    Text(
                        text = "Simpan cadangan seluruh data penyetor, produk, dan riwayat transaksi dalam format JSON agar aman saat berganti perangkat.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                viewModel.exportBackup { json ->
                                    viewModel.copyToClipboard(context, json)
                                    Toast.makeText(context, "Cadangan JSON disalin ke clipboard!", Toast.LENGTH_LONG).show()
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(5.dp))
                            Text("Salin JSON", maxLines = 1, style = MaterialTheme.typography.labelMedium)
                        }

                        FilledTonalButton(
                            onClick = {
                                viewModel.exportBackup { json ->
                                    viewModel.shareTextToWhatsApp(context, json)
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = lapakColors.whatsappBg,
                                contentColor = lapakColors.whatsappText
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(5.dp))
                            Text("Kirim ke WA", maxLines = 1, style = MaterialTheme.typography.labelMedium)
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { showRestorePrompt = true },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Pulihkan Data (Impor JSON)", fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            // =========================================================================
            // 7. TEMA & BAHASA
            // =========================================================================
            item {
                val themeLabel = when (themeMode) {
                    ThemeMode.LIGHT -> "Mode Terang Soft"
                    ThemeMode.DARK -> "Mode Gelap Soft"
                    else -> "Ikuti Sistem"
                }
                val langLabel = if (lang == "en") "English" else "Bahasa Indonesia"
                SettingsAccordionCard(
                    title = "Tema & Bahasa",
                    subtitle = "$themeLabel • $langLabel",
                    icon = Icons.Default.Palette,
                    expanded = expandedSection == "theme_lang",
                    onToggle = { toggleSection("theme_lang") },
                    testTag = "accordion_tema_bahasa"
                ) {
                    Text(
                        text = "Pilihan Tema Tampilan",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val themeOptions = listOf(
                            Triple(ThemeMode.SYSTEM, "Sistem", Icons.Default.BrightnessAuto),
                            Triple(ThemeMode.LIGHT, "Terang", Icons.Default.LightMode),
                            Triple(ThemeMode.DARK, "Gelap", Icons.Default.DarkMode)
                        )
                        themeOptions.forEach { (modeKey, label, icon) ->
                            FilterChip(
                                selected = themeMode == modeKey,
                                onClick = { viewModel.setThemeMode(modeKey) },
                                leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                label = { Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1) },
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

                    Text(
                        text = "Pilihan Bahasa Aplikasi",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = lang == "id",
                            onClick = { viewModel.setLanguage("id") },
                            label = { Text("Bahasa Indonesia", style = MaterialTheme.typography.labelSmall, maxLines = 1) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = lang == "en",
                            onClick = { viewModel.setLanguage("en") },
                            label = { Text("English", style = MaterialTheme.typography.labelSmall, maxLines = 1) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // =========================================================================
            // 8. TUTORIAL & PANDUAN LENGKAP PENGGUNAAN
            // =========================================================================
            item {
                SettingsAccordionCard(
                    title = "Tutorial & Panduan Penggunaan",
                    subtitle = "4 alur lengkap dari persiapan hingga bayar setoran",
                    icon = Icons.Default.MenuBook,
                    expanded = expandedSection == "tutorial",
                    onToggle = { toggleSection("tutorial") },
                    testTag = "accordion_tutorial_panduan"
                ) {
                    Text(
                        text = "Panduan langkah demi langkah yang ramah untuk pemula:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))

                    val quickSummaries = listOf(
                        "Alur 1" to "Persiapan Awal, Tarif Komisi & Daftarkan Penyetor",
                        "Alur 2" to "Terima Titipan Harian & Update Stok (Web / Manual)",
                        "Alur 3" to "Buat Poster Promosi Pelanggan & Layani Kasir",
                        "Alur 4" to "Tutup Lapak, Rekap Untung & Bayar Setoran Penjual"
                    )
                    quickSummaries.forEach { (badge, desc) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 3.dp)
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = badge,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = desc,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { showTutorialDialog = true },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("btn_open_tutorial_guide")
                    ) {
                        Icon(Icons.Default.PlayLesson, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Buka Panduan Lengkap (4 Alur)", fontWeight = FontWeight.Bold)
                    }
                }
            }

            // =========================================================================
            // COMPACT FOOTER CARDS: AKUN ADMIN GOOGLE & TENTANG APLIKASI
            // =========================================================================
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
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text("Akun Admin Terhubung", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(
                                text = signedInEmail,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
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
                            .padding(14.dp),
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
                                Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(19.dp))
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
        }
    }

    // Dialogs
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

@Composable
private fun SettingsAccordionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    expanded: Boolean,
    onToggle: () -> Unit,
    testTag: String,
    content: @Composable ColumnScope.() -> Unit
) {
    val lapakColors = LocalLapakColors.current
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "AccordionChevronRotation"
    )

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            width = 1.dp,
            color = if (expanded) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f) else lapakColors.cardBorder
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(horizontal = 14.dp, vertical = 14.dp)
                    .testTag(testTag),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(
                            if (expanded) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.primaryContainer
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (expanded) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
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
                Icon(
                    imageVector = Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Tutup" else "Buka",
                    tint = if (expanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(22.dp)
                        .rotate(arrowRotation)
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 14.dp, end = 14.dp, bottom = 16.dp)
                ) {
                    HorizontalDivider(
                        color = lapakColors.cardBorder,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    content()
                }
            }
        }
    }
}

@Composable
private fun SettingsToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}
