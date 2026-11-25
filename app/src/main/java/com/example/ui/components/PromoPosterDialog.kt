package com.example.ui.components

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.AppSettings
import com.example.data.model.PosterTemplate
import com.example.data.model.ShopProfile
import com.example.data.repository.ProductDetailItem
import com.example.ui.theme.LocalLapakColors
import com.example.ui.viewmodel.LapakViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun PromoPosterDialog(
    shopProfile: ShopProfile,
    appSettings: AppSettings,
    productsWithDetails: List<ProductDetailItem>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lapakColors = LocalLapakColors.current

    val readyProducts = remember(productsWithDetails) {
        productsWithDetails
            .filter { it.currentStock > 0 && it.product.isActive }
            .sortedBy { it.product.name.lowercase() }
    }

    // Live customizable state initialized from Settings -> Kustomisasi Promosi defaults
    var selectedTemplate by remember(appSettings.posterTemplate) { mutableStateOf(appSettings.posterTemplate) }
    var showLogo by remember(appSettings.posterShowLogo) { mutableStateOf(appSettings.posterShowLogo) }
    var showShopName by remember(appSettings.posterShowShopName) { mutableStateOf(appSettings.posterShowShopName) }
    var showContact by remember(appSettings.posterShowContact) { mutableStateOf(appSettings.posterShowContact) }
    var customBgPath by remember(appSettings.posterCustomBgUri) { mutableStateOf(appSettings.posterCustomBgUri) }
    var isExporting by remember { mutableStateOf(false) }

    // Zero-permission Android Photo Picker for custom gallery background
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                val savedPath = copyPickedImageToInternal(context, uri, "poster_custom_bg.jpg")
                withContext(Dispatchers.Main) {
                    if (savedPath != null) {
                        customBgPath = savedPath
                        selectedTemplate = PosterTemplate.CUSTOM_IMAGE
                    } else {
                        Toast.makeText(context, "Gagal memuat gambar galeri", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    val renderConfig = remember(
        selectedTemplate, appSettings.posterIntroText, appSettings.posterOutroText,
        showLogo, showShopName, showContact, customBgPath
    ) {
        PosterRenderConfig(
            template = selectedTemplate,
            introText = appSettings.posterIntroText,
            outroText = appSettings.posterOutroText,
            showLogo = showLogo,
            showShopName = showShopName,
            showContact = showContact,
            customBgPath = customBgPath
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, lapakColors.cardBorder),
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.92f)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Dialog Top Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Poster Promo Pelanggan",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${readyProducts.size} menu ready stok • Tanpa nama penyetor",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Tutup")
                    }
                }

                HorizontalDivider(color = lapakColors.cardBorder)

                // Scrollable Body: Live Poster Preview + Customization Controls
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // 1. LIVE POSTER PREVIEW CARD
                    PosterLivePreviewCard(
                        shopProfile = shopProfile,
                        readyProducts = readyProducts,
                        config = renderConfig
                    )

                    // 2. TEMPLATE BACKGROUND PICKER
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Pilih Background Poster:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(PromoPosterStyles.allTemplates) { tpl ->
                                val isSelected = selectedTemplate == tpl.key
                                Surface(
                                    onClick = { selectedTemplate = tpl.key },
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    border = BorderStroke(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else lapakColors.cardBorder
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(18.dp)
                                                .clip(RoundedCornerShape(5.dp))
                                                .background(
                                                    Brush.linearGradient(
                                                        listOf(Color(tpl.bgStart), Color(tpl.bgEnd))
                                                    )
                                                )
                                                .border(1.dp, Color.Black.copy(alpha = 0.15f), RoundedCornerShape(5.dp))
                                        )
                                        Spacer(Modifier.width(7.dp))
                                        Text(
                                            text = tpl.label,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }

                            // Gallery Image option (Zero-permission Photo Picker)
                            item {
                                val isGallery = selectedTemplate == PosterTemplate.CUSTOM_IMAGE
                                Surface(
                                    onClick = {
                                        photoPickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isGallery) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    border = BorderStroke(
                                        width = if (isGallery) 2.dp else 1.dp,
                                        color = if (isGallery) MaterialTheme.colorScheme.primary else lapakColors.cardBorder
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.PhotoLibrary,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = "Gambar Galeri",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 3. QUICK ELEMENT TOGGLES
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                        border = BorderStroke(1.dp, lapakColors.cardBorder),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            PosterToggleRow("Tampilkan Icon / Logo Lapak", showLogo) { showLogo = it }
                            PosterToggleRow("Tampilkan Nama Lapak", showShopName) { showShopName = it }
                            PosterToggleRow("Tampilkan Nomor WA & Alamat", showContact) { showContact = it }
                        }
                    }
                }

                HorizontalDivider(color = lapakColors.cardBorder)

                // Sticky Bottom Action Buttons
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            if (isExporting) return@Button
                            isExporting = true
                            scope.launch {
                                val bmp = withContext(Dispatchers.Default) {
                                    PromoPosterGenerator.generatePosterBitmap(context, shopProfile, readyProducts, renderConfig)
                                }
                                isExporting = false
                                val caption = "${appSettings.posterIntroText} — ${shopProfile.shopName}. ${appSettings.posterOutroText}"
                                PromoPosterGenerator.sharePosterToWhatsApp(context, bmp, caption)
                            }
                        },
                        enabled = !isExporting && readyProducts.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("poster_share_wa_button")
                    ) {
                        if (isExporting) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(8.dp))
                        Text("Bagikan Gambar ke WhatsApp", fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = {
                            if (isExporting) return@OutlinedButton
                            isExporting = true
                            scope.launch {
                                val result = withContext(Dispatchers.IO) {
                                    val bmp = PromoPosterGenerator.generatePosterBitmap(context, shopProfile, readyProducts, renderConfig)
                                    PromoPosterGenerator.savePosterToGallery(context, bmp)
                                }
                                isExporting = false
                                if (result.isSuccess) {
                                    Toast.makeText(context, "Poster disimpan ke Galeri (Pictures/Lapak Kita)!", Toast.LENGTH_LONG).show()
                                } else {
                                    Toast.makeText(context, "Gagal menyimpan gambar: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        enabled = !isExporting && readyProducts.isNotEmpty(),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .testTag("poster_save_gallery_button")
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Simpan Gambar", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun PosterToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun PosterLivePreviewCard(
    shopProfile: ShopProfile,
    readyProducts: List<ProductDetailItem>,
    config: PosterRenderConfig
) {
    val style = remember(config.template) { PromoPosterStyles.resolve(config.template) }
    val customBgBitmap = if (config.template == PosterTemplate.CUSTOM_IMAGE && !config.customBgPath.isNullOrBlank()) {
        rememberLocalBitmap(config.customBgPath, maxDim = 720)
    } else null

    val titleColor = if (customBgBitmap != null) Color.White else Color(style.titleColor)
    val subColor = if (customBgBitmap != null) Color(0xFFE6F4EE) else Color(style.subtitleColor)
    val footerTextColor = if (customBgBitmap != null) Color.White else Color(style.footerText)
    val footerBgColor = if (customBgBitmap != null) Color.White.copy(alpha = 0.2f) else Color(style.footerBg)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.verticalGradient(
                    listOf(Color(style.bgStart), Color(style.bgEnd))
                )
            )
    ) {
        if (customBgBitmap != null) {
            Image(
                bitmap = customBgBitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize()
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xB810241D), Color(0xD40B1713))
                        )
                    )
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (config.showLogo) {
                ShopAvatarBadge(
                    iconKey = shopProfile.shopIconKey,
                    logoUri = shopProfile.logoUri,
                    size = 46.dp,
                    containerColor = if (config.template == PosterTemplate.WARM_CREAM && customBgBitmap == null) {
                        Color.White
                    } else {
                        Color.White.copy(alpha = 0.18f)
                    },
                    iconTint = if (config.template == PosterTemplate.WARM_CREAM && customBgBitmap == null) {
                        Color(0xFFB4690E)
                    } else {
                        Color.White
                    }
                )
                Spacer(Modifier.height(8.dp))
            }

            if (config.showShopName) {
                Text(
                    text = shopProfile.shopName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = titleColor,
                    textAlign = TextAlign.Center
                )
            }

            Text(
                text = config.introText,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = subColor,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(12.dp))

            // Menu Card (STRICTLY NO SELLER NAME; auto 2 columns when > 8 items)
            val useTwoColumns = readyProducts.size > 8
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(style.cardBg),
                border = BorderStroke(1.dp, Color(style.cardBorder)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(if (useTwoColumns) 10.dp else 12.dp)) {
                    if (readyProducts.isEmpty()) {
                        Text(
                            text = "Belum ada produk dengan stok tersedia (> 0).",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(style.itemNameColor).copy(alpha = 0.7f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp)
                        )
                    } else if (useTwoColumns) {
                        val rows = remember(readyProducts) { readyProducts.chunked(2) }
                        rows.forEachIndexed { rowIdx, pair ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(IntrinsicSize.Min)
                                    .padding(vertical = 5.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                PosterTwoColPreviewItem(
                                    detail = pair[0],
                                    style = style,
                                    modifier = Modifier.weight(1f)
                                )
                                Box(
                                    modifier = Modifier
                                        .width(1.dp)
                                        .fillMaxHeight()
                                        .background(Color(style.cardBorder))
                                )
                                if (pair.size > 1) {
                                    PosterTwoColPreviewItem(
                                        detail = pair[1],
                                        style = style,
                                        modifier = Modifier.weight(1f)
                                    )
                                } else {
                                    Spacer(Modifier.weight(1f))
                                }
                            }
                            if (rowIdx < rows.lastIndex) {
                                HorizontalDivider(color = Color(style.cardBorder))
                            }
                        }
                    } else {
                        readyProducts.forEachIndexed { idx, detail ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 7.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                    Text(
                                        text = detail.product.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(style.itemNameColor),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${LapakViewModel.formatRupiah(detail.product.sellingPrice)} / ${detail.product.unit}",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color(style.priceColor)
                                    )
                                }
                                val isLow = detail.currentStock <= 3
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = Color(if (isLow) style.stockLowBg else style.stockSafeBg)
                                ) {
                                    Text(
                                        text = "Sisa ${detail.currentStock} ${detail.product.unit}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(if (isLow) style.stockLowText else style.stockSafeText),
                                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                                    )
                                }
                            }
                            if (idx < readyProducts.lastIndex) {
                                HorizontalDivider(color = Color(style.cardBorder))
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // Footer Outro & Optional Contact
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = footerBgColor,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = config.outroText,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = footerTextColor,
                        textAlign = TextAlign.Center
                    )
                    if (config.showContact) {
                        if (shopProfile.whatsappNumber.isNotBlank()) {
                            Spacer(Modifier.height(3.dp))
                            Text(
                                text = "WA Order: ${shopProfile.whatsappNumber}",
                                style = MaterialTheme.typography.labelSmall,
                                color = footerTextColor.copy(alpha = 0.9f),
                                textAlign = TextAlign.Center
                            )
                        }
                        if (shopProfile.address.isNotBlank()) {
                            Text(
                                text = shopProfile.address,
                                style = MaterialTheme.typography.labelSmall,
                                color = footerTextColor.copy(alpha = 0.85f),
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PosterTwoColPreviewItem(
    detail: ProductDetailItem,
    style: PosterStyleSpec,
    modifier: Modifier = Modifier
) {
    val isLow = detail.currentStock <= 3
    Column(
        modifier = modifier.padding(vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = detail.product.name,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = Color(style.itemNameColor),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = LapakViewModel.formatRupiah(detail.product.sellingPrice),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.ExtraBold,
                color = Color(style.priceColor),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 4.dp)
            )
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = Color(if (isLow) style.stockLowBg else style.stockSafeBg)
            ) {
                Text(
                    text = "Sisa ${detail.currentStock}",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(if (isLow) style.stockLowText else style.stockSafeText),
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    maxLines = 1
                )
            }
        }
    }
}

