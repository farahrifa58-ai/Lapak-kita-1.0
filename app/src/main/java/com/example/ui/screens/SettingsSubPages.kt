package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.*
import com.example.i18n.AppStrings
import com.example.ui.components.LapakHeaderPalettes
import com.example.ui.components.NeuCard
import com.example.ui.components.NeuIconBadge
import com.example.ui.components.PromoPosterStyles
import com.example.ui.components.ShopAvatarBadge
import com.example.ui.theme.LocalLapakColors
import com.example.ui.viewmodel.LapakViewModel
import java.text.SimpleDateFormat
import java.util.Date

enum class SettingsSubPage(
    val headerTitle: String,
    val headerSubtitle: String
) {
    PROFIL_LAPAK("Profil Lapak", "Identitas usaha, logo & warna header"),
    BIAYA_ADMIN("Biaya Admin / Komisi Lapak", "Tarif default keuntungan lapak"),
    WEB_PORTAL("Link Portal Web Penyetor", "Sinkronisasi cloud & tautan penyetor"),
    PROMOSI("Kustomisasi Promosi", "Template poster & teks ajakan"),
    AUTO_CLOSING("Auto Tutup Buku Harian", "Jadwal tutup buku otomatis & aturan sisa stok"),
    KUSTOMISASI_PENGGUNA("Kustomisasi Pengguna (Kasir)", "Atur tampilan Omset, Untung & Operasional di HP Kasir"),
    PRINTER_SETTINGS("Kustomisasi Print & Printer", "Pilih printer Bluetooth & ukuran kertas struk"),
    RESET_STOK("Reset Stok Harian", "Siklus otomatis & reset manual"),
    BIAYA_OPERASIONAL("Biaya Operasional", "Catat pengeluaran rutin lapak"),
    CADANGAN("Cadangan & Pemulihan", "Google Drive & ekspor/impor file JSON"),
    TEMA_BAHASA("Tema & Bahasa", "Tampilan terang/gelap & bahasa"),
    TUTORIAL("Tutorial & Panduan Penggunaan", "4 alur lengkap operasional lapak"),
    AKUN_CLOUD("Status Akun & Sinkronisasi Cloud", "Akun Google, lisensi & perangkat 2 HP")
}

@Composable
internal fun SettingsMenuRowCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    testTag: String,
    accentColor: Color = MaterialTheme.colorScheme.primary
) {
    NeuCard(
        cornerRadius = 16.dp,
        shadowOffset = 5.dp,
        blurRadius = 10.dp,
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 68.dp)
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            NeuIconBadge(
                accentColor = accentColor,
                size = 42.dp
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(21.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Buka $title",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
internal fun SettingsSubPageCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    NeuCard(
        cornerRadius = 18.dp,
        shadowOffset = 5.dp,
        blurRadius = 11.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            content = content
        )
    }
}

@Composable
internal fun SettingsToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp)
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

// =============================================================================
// 1. PROFIL LAPAK SUB-PAGE
// =============================================================================
@Composable
internal fun ProfilLapakSubPage(
    lang: String,
    shopName: String,
    onShopNameChange: (String) -> Unit,
    ownerName: String,
    onOwnerNameChange: (String) -> Unit,
    waNumber: String,
    onWaNumberChange: (String) -> Unit,
    address: String,
    onAddressChange: (String) -> Unit,
    footerNote: String,
    onFooterNoteChange: (String) -> Unit,
    selectedIconKey: String,
    onSelectedIconKeyChange: (String) -> Unit,
    selectedHeaderBgKey: String,
    onSelectedHeaderBgKeyChange: (String) -> Unit,
    localLogoPath: String?,
    onPickLogoPhoto: () -> Unit,
    onSaveProfile: () -> Unit
) {
    val lapakColors = LocalLapakColors.current
    val selectedPalette = LapakHeaderPalettes.resolve(selectedHeaderBgKey)

    SettingsSubPageCard {
        // Live preview of Dashboard header style & shop icon
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Brush.horizontalGradient(listOf(selectedPalette.lightStart, selectedPalette.lightEnd)))
                .padding(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ShopAvatarBadge(
                    iconKey = selectedIconKey,
                    logoUri = localLogoPath,
                    size = 44.dp
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = shopName.ifBlank { "Lapak Kita" },
                        style = MaterialTheme.typography.titleMedium,
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

        Spacer(Modifier.height(14.dp))

        OutlinedTextField(
            value = shopName,
            onValueChange = onShopNameChange,
            label = { Text(AppStrings.get("settings_shop_name", lang)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(10.dp)
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = ownerName,
            onValueChange = onOwnerNameChange,
            label = { Text(AppStrings.get("settings_owner_name", lang)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(10.dp)
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = waNumber,
            onValueChange = onWaNumberChange,
            label = { Text(AppStrings.get("settings_wa_number", lang)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(10.dp)
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = address,
            onValueChange = onAddressChange,
            label = { Text(AppStrings.get("settings_address", lang)) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp)
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = footerNote,
            onValueChange = onFooterNoteChange,
            label = { Text(AppStrings.get("settings_footer_note", lang)) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp)
        )

        Spacer(Modifier.height(14.dp))
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
                    onClick = { onSelectedIconKeyChange(key) },
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
                    onClick = onPickLogoPhoto,
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

        Spacer(Modifier.height(14.dp))
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
                        .clickable { onSelectedHeaderBgKeyChange(palette.key) }
                        .padding(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
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

        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onSaveProfile,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .testTag("save_shop_profile_button")
        ) {
            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Simpan Profil Lapak", fontWeight = FontWeight.Bold)
        }
    }
}

// =============================================================================
// 2. BIAYA ADMIN / KOMISI LAPAK SUB-PAGE
// =============================================================================
@Composable
internal fun BiayaAdminSubPage(
    selectedFeeType: String,
    onSelectFeeType: (String) -> Unit,
    feeValueText: String,
    onFeeValueTextChange: (String) -> Unit,
    previewCalc: AdminFeeBreakdown,
    onSaveAdminFee: () -> Unit
) {
    val lapakColors = LocalLapakColors.current

    SettingsSubPageCard {
        Text(
            text = "Otomatis menghitung Hak Penyetor & Untung Lapak saat menambah produk. Anda juga bisa atur tarif khusus per penyetor di halaman Penyetor.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = selectedFeeType == AdminFeeType.FIXED,
                onClick = { onSelectFeeType(AdminFeeType.FIXED) },
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
                onClick = { onSelectFeeType(AdminFeeType.PERCENTAGE) },
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

        Spacer(Modifier.height(10.dp))

        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            val presets = if (selectedFeeType == AdminFeeType.FIXED) {
                listOf("1000" to "Rp 1.000", "1500" to "Rp 1.500", "2000" to "Rp 2.000", "3000" to "Rp 3.000")
            } else {
                listOf("10" to "10%", "15" to "15%", "20" to "20%", "25" to "25%")
            }
            items(presets) { (valStr, label) ->
                SuggestionChip(
                    onClick = { onFeeValueTextChange(valStr) },
                    label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        OutlinedTextField(
            value = feeValueText,
            onValueChange = { onFeeValueTextChange(it.filter { c -> c.isDigit() || c == '.' }) },
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

        Spacer(Modifier.height(12.dp))

        Surface(
            color = lapakColors.omzetCardBg,
            border = BorderStroke(1.dp, lapakColors.omzetCardBorder),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = "Simulasi Produk Harga Jual Rp 10.000:",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = lapakColors.omzetCardAccent
                )
                Text(
                    text = "• Biaya Admin / Untung Lapak: ${LapakViewModel.formatRupiah(previewCalc.adminFee)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "• Uang Bersih Hak Penyetor: ${LapakViewModel.formatRupiah(previewCalc.sellerShare)}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = lapakColors.omzetCardAccent
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onSaveAdminFee,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .testTag("save_admin_fee_button")
        ) {
            Text("Simpan Tarif Biaya Admin", fontWeight = FontWeight.Bold)
        }
    }
}

// =============================================================================
// 3. LINK PORTAL WEB PENYETOR SUB-PAGE
// =============================================================================
@Composable
internal fun WebPortalSubPage(
    isSyncingCloud: Boolean,
    lastCloudSyncAt: Long?,
    syncTimeFormatter: SimpleDateFormat,
    webBaseUrlText: String,
    onWebBaseUrlChange: (String) -> Unit,
    connectionTestMessage: String?,
    connectionTestOk: Boolean?,
    onSaveUrl: () -> Unit,
    onSyncAllVendors: () -> Unit,
    onTestConnection: () -> Unit
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
                    imageVector = if (isSyncingCloud) Icons.Default.Sync else Icons.Default.CloudDone,
                    contentDescription = null,
                    tint = lapakColors.omzetCardAccent,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isSyncingCloud) "Menyinkronkan Firestore..." else "Sinkronisasi Firestore Real-Time Aktif",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = lapakColors.omzetCardAccent
                    )
                    Text(
                        text = if (lastCloudSyncAt != null) {
                            "Sinkron terakhir: ${syncTimeFormatter.format(Date(lastCloudSyncAt))}"
                        } else {
                            "Siap menerima update produk & stok harian dari penjual"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = webBaseUrlText,
            onValueChange = onWebBaseUrlChange,
            label = { Text("URL Dasar Portal Web Penyetor") },
            supportingText = { Text("Domain permanen: https://lapakkita1.vercel.app") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(10.dp)
        )

        if (!connectionTestMessage.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            Surface(
                color = if (connectionTestOk == true) lapakColors.stockSafeBg else lapakColors.dangerBg,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = connectionTestMessage,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (connectionTestOk == true) lapakColors.stockSafeText else lapakColors.dangerText,
                    modifier = Modifier.padding(10.dp)
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onSaveUrl,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
        ) {
            Text("Simpan URL Portal", fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onSyncAllVendors,
                enabled = !isSyncingCloud,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
            ) {
                Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(5.dp))
                Text("Sinkronkan", maxLines = 1, style = MaterialTheme.typography.labelMedium)
            }

            OutlinedButton(
                onClick = onTestConnection,
                enabled = !isSyncingCloud,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .testTag("btn_test_cloud_connection")
            ) {
                if (isSyncingCloud) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.WifiTethering, contentDescription = null, modifier = Modifier.size(16.dp))
                }
                Spacer(Modifier.width(5.dp))
                Text("Uji Koneksi", maxLines = 1, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

// =============================================================================
// 4. KUSTOMISASI PROMOSI SUB-PAGE
// =============================================================================
@Composable
internal fun PromosiSubPage(
    promoTemplate: String,
    onPromoTemplateChange: (String) -> Unit,
    onPickPosterBg: () -> Unit,
    promoIntro: String,
    onPromoIntroChange: (String) -> Unit,
    promoOutro: String,
    onPromoOutroChange: (String) -> Unit,
    promoShowLogo: Boolean,
    onPromoShowLogoChange: (Boolean) -> Unit,
    promoShowShopName: Boolean,
    onPromoShowShopNameChange: (Boolean) -> Unit,
    promoShowContact: Boolean,
    onPromoShowContactChange: (Boolean) -> Unit,
    onSavePromo: () -> Unit,
    onPreviewPromo: () -> Unit
) {
    SettingsSubPageCard {
        Text(
            text = "Template Background Default Poster",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(PromoPosterStyles.allTemplates) { spec ->
                FilterChip(
                    selected = promoTemplate == spec.key,
                    onClick = { onPromoTemplateChange(spec.key) },
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
                    onClick = onPickPosterBg,
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

        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = promoIntro,
            onValueChange = onPromoIntroChange,
            label = { Text("Teks Ajakan Pembuka Poster") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(10.dp)
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = promoOutro,
            onValueChange = onPromoOutroChange,
            label = { Text("Teks Ajakan Penutup Poster") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(10.dp)
        )

        Spacer(Modifier.height(12.dp))
        Text(
            text = "Tampilan Default Elemen Poster",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold
        )
        SettingsToggleRow(
            label = "Tampilkan Ikon / Logo Lapak",
            checked = promoShowLogo,
            onCheckedChange = onPromoShowLogoChange
        )
        SettingsToggleRow(
            label = "Tampilkan Nama Lapak",
            checked = promoShowShopName,
            onCheckedChange = onPromoShowShopNameChange
        )
        SettingsToggleRow(
            label = "Tampilkan Nomor WA & Alamat",
            checked = promoShowContact,
            onCheckedChange = onPromoShowContactChange
        )

        Spacer(Modifier.height(14.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onSavePromo,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .testTag("save_promo_settings_button")
            ) {
                Text("Simpan", fontWeight = FontWeight.Bold)
            }

            OutlinedButton(
                onClick = onPreviewPromo,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
            ) {
                Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(5.dp))
                Text("Pratinjau", maxLines = 1)
            }
        }
    }
}

// =============================================================================
// 5. RESET STOK HARIAN SUB-PAGE
// =============================================================================
@Composable
internal fun ResetStokSubPage(
    autoResetEnabled: Boolean,
    onToggleAutoReset: (Boolean) -> Unit,
    autoResetHour: Int,
    onChangeResetHour: (Int) -> Unit,
    totalActiveProductsCount: Int,
    totalActiveStockQty: Int,
    isResettingStock: Boolean,
    onRequestManualReset: () -> Unit
) {
    val lapakColors = LocalLapakColors.current
    val formattedResetHour = OperationalCycleHelper.formatResetHour(autoResetHour)
    val cutoffMinuteBefore = OperationalCycleHelper.formatCutoffMinuteBefore(autoResetHour)
    val activeCycleId = OperationalCycleHelper.getOperationalCycleDate(autoResetHour)

    SettingsSubPageCard {
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
                Text(
                    text = "Siklus Operasional Aktif: $activeCycleId",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = lapakColors.omzetCardAccent
                )
                Text(
                    text = "Stok yang diinput mulai pukul $formattedResetHour WIB hingga malam hari tersimpan aman melewati tengah malam (00:00) dan baru diarsipkan lalu direset ke 0 setiap pukul $formattedResetHour WIB keesokan harinya (cut-off $cutoffMinuteBefore WIB).",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        SettingsToggleRow(
            label = "Otomatis Reset Stok ke 0 Setiap Pukul $formattedResetHour",
            checked = autoResetEnabled,
            onCheckedChange = onToggleAutoReset
        )

        Spacer(Modifier.height(10.dp))

        Text(
            text = "Jam Reset Harian (WIB)",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Jadwal reset setiap hari:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilledTonalIconButton(
                    onClick = { onChangeResetHour((autoResetHour + 23) % 24) },
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("btn_reset_hour_minus")
                ) {
                    Icon(Icons.Default.Remove, contentDescription = "Kurangi Jam Reset", modifier = Modifier.size(18.dp))
                }
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                ) {
                    Text(
                        text = "$formattedResetHour WIB",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                    )
                }
                FilledTonalIconButton(
                    onClick = { onChangeResetHour((autoResetHour + 1) % 24) },
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("btn_reset_hour_plus")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Tambah Jam Reset", modifier = Modifier.size(18.dp))
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            val hourPresets = listOf(16, 17, 18, 19, 20, 21, 22, 0)
            items(hourPresets) { h ->
                FilterChip(
                    selected = autoResetHour == h,
                    onClick = { onChangeResetHour(h) },
                    label = {
                        Text(
                            OperationalCycleHelper.formatResetHour(h),
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp), color = lapakColors.cardBorder)

        Text(
            text = "Stok aktif saat ini: $totalActiveProductsCount produk ($totalActiveStockQty total stok)",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(10.dp))

        OutlinedButton(
            onClick = onRequestManualReset,
            enabled = !isResettingStock,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.6f)),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error
            ),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .testTag("btn_manual_reset_stock_now")
        ) {
            if (isResettingStock) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(6.dp))
            Text("Reset Semua Stok ke 0 Sekarang", fontWeight = FontWeight.Bold)
        }
    }
}

// =============================================================================
// 6. BIAYA OPERASIONAL SUB-PAGE
// =============================================================================
@Composable
internal fun BiayaOperasionalSubPage(
    expenses: List<OperatingExpense>,
    onAddExpenseClick: () -> Unit,
    onDeleteExpense: (OperatingExpense) -> Unit
) {
    val lapakColors = LocalLapakColors.current
    val totalExpenses = expenses.sumOf { it.amount }

    SettingsSubPageCard {
        Surface(
            color = lapakColors.omzetCardBg,
            border = BorderStroke(1.dp, lapakColors.omzetCardBorder),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Total Biaya Operasional",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = LapakViewModel.formatRupiah(totalExpenses),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = lapakColors.omzetCardAccent
                    )
                }
                Text(
                    text = "${expenses.size} catatan",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = lapakColors.omzetCardAccent
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        Text(
            text = "Catat pengeluaran rutin/harian lapak (plastik, kresek, listrik, kebersihan) agar otomatis mengurangi Untung Lapak di Dashboard.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))

        Button(
            onClick = onAddExpenseClick,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .testTag("btn_add_operating_expense")
        ) {
            Icon(Icons.Default.AddCircleOutline, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Tambah Biaya Operasional", fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(14.dp))

        if (expenses.isEmpty()) {
            Text(
                text = "Belum ada biaya operasional tercatat.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 6.dp)
            )
        } else {
            expenses.forEach { exp ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp)
                    ) {
                        Text(exp.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        if (exp.notes.isNotEmpty()) {
                            Text(
                                exp.notes,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
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
                            onClick = { onDeleteExpense(exp) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Default.DeleteOutline,
                                contentDescription = "Hapus Biaya",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
                HorizontalDivider(color = lapakColors.cardBorder.copy(alpha = 0.6f))
            }
        }
    }
}

// =============================================================================
// 7. CADANGAN & PEMULIHAN SUB-PAGE (GOOGLE DRIVE + MANUAL JSON)
// =============================================================================
@Composable
internal fun CadanganSubPage(
    lastBackupDate: Long?,
    driveAccountEmail: String?,
    isBackingUpDrive: Boolean,
    syncTimeFormatter: SimpleDateFormat,
    onSaveToGoogleDrive: () -> Unit,
    onQuickUploadToDrive: () -> Unit,
    onRestoreFromGoogleDrive: () -> Unit,
    onCopyJson: () -> Unit,
    onShareToWhatsApp: () -> Unit,
    onOpenRestoreDialog: () -> Unit
) {
    val lapakColors = LocalLapakColors.current

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // Card 1: Google Drive Backup & Restore
        SettingsSubPageCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                NeuIconBadge(
                    accentColor = MaterialTheme.colorScheme.primary,
                    size = 44.dp
                ) {
                    Icon(
                        imageVector = Icons.Default.AddToDrive,
                        contentDescription = "Google Drive",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Cadangan Google Drive",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Simpan & pulihkan seluruh data lapak langsung ke Google Drive Anda tanpa izin penyimpanan tambahan.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Last Google Drive backup status banner
            Surface(
                color = if (lastBackupDate != null) lapakColors.omzetCardBg else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                border = BorderStroke(
                    1.dp,
                    if (lastBackupDate != null) lapakColors.omzetCardBorder else lapakColors.cardBorder
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("google_drive_backup_status")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (lastBackupDate != null) Icons.Default.CloudDone else Icons.Default.CloudQueue,
                        contentDescription = null,
                        tint = if (lastBackupDate != null) lapakColors.omzetCardAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (lastBackupDate != null) {
                                "Terakhir dicadangkan: ${syncTimeFormatter.format(Date(lastBackupDate))}"
                            } else {
                                "Belum pernah dicadangkan ke Google Drive"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (lastBackupDate != null) lapakColors.omzetCardAccent else MaterialTheme.colorScheme.onSurface
                        )
                        if (!driveAccountEmail.isNullOrBlank()) {
                            Text(
                                text = "Akun: $driveAccountEmail",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            Button(
                onClick = onSaveToGoogleDrive,
                enabled = !isBackingUpDrive,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("btn_backup_google_drive")
            ) {
                if (isBackingUpDrive) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Menyiapkan Cadangan...", fontWeight = FontWeight.Bold)
                } else {
                    Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Simpan Cadangan ke Google Drive", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(8.dp))

            FilledTonalButton(
                onClick = onQuickUploadToDrive,
                enabled = !isBackingUpDrive,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("btn_quick_share_google_drive")
            ) {
                Icon(Icons.Default.AddToDrive, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Upload Cepat ke Aplikasi Google Drive", fontWeight = FontWeight.SemiBold)
            }

            Spacer(Modifier.height(8.dp))

            OutlinedButton(
                onClick = onRestoreFromGoogleDrive,
                enabled = !isBackingUpDrive,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("btn_restore_google_drive")
            ) {
                Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Pulihkan dari File Google Drive", fontWeight = FontWeight.SemiBold)
            }
        }

        // Card 2: Manual JSON & WhatsApp Backup
        SettingsSubPageCard {
            Text(
                text = "Cadangan Manual (JSON & WhatsApp)",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Alternatif cadangan teks JSON untuk dikirim ke WhatsApp atau disalin manual saat berganti perangkat.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onCopyJson,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("Salin JSON", maxLines = 1, style = MaterialTheme.typography.labelMedium)
                }

                FilledTonalButton(
                    onClick = onShareToWhatsApp,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = lapakColors.whatsappBg,
                        contentColor = lapakColors.whatsappText
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("Kirim ke WA", maxLines = 1, style = MaterialTheme.typography.labelMedium)
                }
            }

            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = onOpenRestoreDialog,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
            ) {
                Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Pulihkan via Teks JSON", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// =============================================================================
// 8. TEMA & BAHASA SUB-PAGE
// =============================================================================
@Composable
internal fun TemaBahasaSubPage(
    themeMode: String,
    onSelectTheme: (String) -> Unit,
    lang: String,
    onSelectLanguage: (String) -> Unit
) {
    val lapakColors = LocalLapakColors.current

    SettingsSubPageCard {
        Text(
            text = "Pilihan Tema Tampilan",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
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
                    onClick = { onSelectTheme(modeKey) },
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

        HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp), color = lapakColors.cardBorder)

        Text(
            text = "Pilihan Bahasa Aplikasi",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = lang == "id",
                onClick = { onSelectLanguage("id") },
                label = { Text("Bahasa Indonesia", style = MaterialTheme.typography.labelSmall, maxLines = 1) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier.weight(1f)
            )
            FilterChip(
                selected = lang == "en",
                onClick = { onSelectLanguage("en") },
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

// =============================================================================
// 9. TUTORIAL & PANDUAN PENGGUNAAN SUB-PAGE
// =============================================================================
@Composable
internal fun TutorialSubPage(
    onOpenTutorialGuide: () -> Unit
) {
    SettingsSubPageCard {
        Text(
            text = "Panduan langkah demi langkah yang ramah untuk pemula:",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(10.dp))

        val quickSummaries = listOf(
            "Alur 1" to "Persiapan Awal, Tarif Komisi & Daftarkan Penyetor",
            "Alur 2" to "Terima Titipan Harian & Update Stok (Web / Manual)",
            "Alur 3" to "Buat Poster Promosi Pelanggan & Layani Kasir",
            "Alur 4" to "Tutup Lapak, Rekap Untung & Bayar Setoran Penjual"
        )
        quickSummaries.forEach { (badge, desc) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 5.dp)
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
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onOpenTutorialGuide,
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

// =============================================================================
// 10. STATUS AKUN & SINKRONISASI CLOUD SUB-PAGE (NEW!)
// =============================================================================
@Composable
internal fun AccountCloudSyncSubPage(
    signedInEmail: String,
    registeredLicenseCode: String?,
    connectedDevicesCount: Int,
    isOwner: Boolean = true,
    isCloudLoaded: Boolean = true,
    isSyncingCloud: Boolean,
    lastCloudSyncAt: Long?,
    syncTimeFormatter: SimpleDateFormat,
    onSignOutClick: () -> Unit
) {
    val lapakColors = LocalLapakColors.current
    val clampedDevices = connectedDevicesCount.coerceIn(0, 2)

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // Card 1: Akun Google & Lisensi Aktif
        SettingsSubPageCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                NeuIconBadge(
                    accentColor = MaterialTheme.colorScheme.primary,
                    size = 46.dp
                ) {
                    Icon(
                        imageVector = Icons.Default.AccountCircle,
                        contentDescription = "Akun Google",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(26.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Akun Google Login",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = signedInEmail,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("account_signed_in_email")
                    )
                }
                Surface(
                    color = lapakColors.omzetCardBg,
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, lapakColors.omzetCardBorder)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.VerifiedUser,
                            contentDescription = null,
                            tint = lapakColors.omzetCardAccent,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "Aktif",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = lapakColors.omzetCardAccent
                        )
                    }
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = lapakColors.cardBorder
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Peran Perangkat Ini",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = if (isOwner) "Owner (Akses Penuh)" else "Kasir / Karyawan",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isOwner) lapakColors.omzetCardAccent else lapakColors.payoutCardAccent,
                    modifier = Modifier.testTag("device_role_label")
                )
            }
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Status Lisensi",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Aktif Permanen",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = lapakColors.omzetCardAccent
                )
            }
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Kode Lisensi Terdaftar",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = registeredLicenseCode?.ifBlank { "-" } ?: "-",
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // Card 2: Status Perangkat Terhubung Real-Time (X / 2 HP) & Panduan
        SettingsSubPageCard {
            Surface(
                color = lapakColors.omzetCardBg,
                border = BorderStroke(1.dp, lapakColors.omzetCardBorder),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("connected_devices_status_badge")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Devices,
                            contentDescription = "Perangkat Terhubung",
                            tint = lapakColors.omzetCardAccent,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Perangkat Terhubung: $clampedDevices / 2 HP",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = lapakColors.omzetCardAccent
                            )
                            Text(
                                text = when {
                                    !isCloudLoaded -> "Mengunduh data cloud terlebih dahulu..."
                                    isSyncingCloud -> "Menyinkronkan data cloud..."
                                    lastCloudSyncAt != null -> "Data Cloud Siap • ${syncTimeFormatter.format(Date(lastCloudSyncAt))}"
                                    else -> "Data Cloud Siap • Sinkronisasi Real-Time Aktif"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Visual 2-slot status row (HP 1 = Owner & HP 2 = Kasir)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                DeviceSlotBadge(
                    label = "Perangkat 1",
                    roleHint = "HP Owner (Akses Penuh)",
                    isConnected = clampedDevices >= 1,
                    modifier = Modifier.weight(1f)
                )
                DeviceSlotBadge(
                    label = "Perangkat 2",
                    roleHint = "HP Kasir / Karyawan",
                    isConnected = clampedDevices >= 2,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(14.dp))

            // Panduan singkat menghubungkan 2 HP & Privasi Data Keuangan
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(top = 1.dp)
                            .size(18.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "Perangkat 1 berperan sebagai Owner (akses penuh). Perangkat 2 (login dengan akun Google yang sama) berperan sebagai Kasir/Karyawan — data omset, untung lapak, dan biaya operasional otomatis disembunyikan.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        // Card 3: Soft-Red Logout Button
        Button(
            onClick = onSignOutClick,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = lapakColors.dangerBg,
                contentColor = lapakColors.dangerText
            ),
            border = BorderStroke(1.dp, lapakColors.dangerText.copy(alpha = 0.28f)),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .testTag("sign_out_button")
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Logout,
                contentDescription = null,
                modifier = Modifier.size(19.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Keluar Akun (Logout)",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun DeviceSlotBadge(
    label: String,
    roleHint: String,
    isConnected: Boolean,
    modifier: Modifier = Modifier
) {
    val lapakColors = LocalLapakColors.current
    Surface(
        color = if (isConnected) lapakColors.stockSafeBg else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(
            width = 1.dp,
            color = if (isConnected) lapakColors.omzetCardBorder else lapakColors.cardBorder
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isConnected) Icons.Default.Smartphone else Icons.Default.AddToHomeScreen,
                contentDescription = null,
                tint = if (isConnected) lapakColors.stockSafeText else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isConnected) lapakColors.stockSafeText else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
                Text(
                    text = if (isConnected) "Terhubung" else roleHint,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
