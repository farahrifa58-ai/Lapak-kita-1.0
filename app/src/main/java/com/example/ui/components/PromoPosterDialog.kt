package com.example.ui.components

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.AppSettings
import com.example.data.model.PosterTemplate
import com.example.data.model.ShopProfile
import com.example.data.repository.ProductDetailItem
import com.example.ui.theme.LocalLapakColors
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
    val groupedProducts = remember(readyProducts) {
        PromoPosterGenerator.groupReadyProductsByCategory(readyProducts)
    }
    val colCount = if (readyProducts.size <= 15) 2 else 3

    // Live customizable state initialized from Settings -> Kustomisasi Promosi defaults
    var selectedTemplate by remember(appSettings.posterTemplate) { mutableStateOf(appSettings.posterTemplate) }
    var showLogo by remember(appSettings.posterShowLogo) { mutableStateOf(appSettings.posterShowLogo) }
    var showShopName by remember(appSettings.posterShowShopName) { mutableStateOf(appSettings.posterShowShopName) }
    var showContact by remember(appSettings.posterShowContact) { mutableStateOf(appSettings.posterShowContact) }
    var showStock by remember { mutableStateOf(false) }
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
        showLogo, showShopName, showContact, showStock, customBgPath
    ) {
        PosterRenderConfig(
            template = selectedTemplate,
            introText = appSettings.posterIntroText,
            outroText = appSettings.posterOutroText,
            showLogo = showLogo,
            showShopName = showShopName,
            showContact = showContact,
            showStock = showStock,
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
                .fillMaxHeight(0.93f)
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
                            text = "${readyProducts.size} menu ready • ${groupedProducts.size} kategori • Layout $colCount kolom",
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
                    // 1. LIVE BORDERLESS POSTER PREVIEW
                    PosterLivePreviewCard(
                        shopProfile = shopProfile,
                        groupedProducts = groupedProducts,
                        totalItems = readyProducts.size,
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

                    // 3. QUICK ELEMENT TOGGLES (including Tampilkan Sisa Stok)
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                        border = BorderStroke(1.dp, lapakColors.cardBorder),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            PosterToggleRow(
                                label = "Tampilkan Sisa Stok",
                                subtitle = if (showStock) "Menampilkan angka stok kecil (14) di sebelah harga" else "Super ringkas tanpa stok (muat hingga 100+ produk)",
                                checked = showStock,
                                testTag = "poster_toggle_show_stock",
                                onCheckedChange = { showStock = it }
                            )
                            HorizontalDivider(color = lapakColors.cardBorder.copy(alpha = 0.6f))
                            PosterToggleRow(
                                label = "Tampilkan Icon / Logo Lapak",
                                checked = showLogo,
                                onCheckedChange = { showLogo = it }
                            )
                            PosterToggleRow(
                                label = "Tampilkan Nama Lapak",
                                checked = showShopName,
                                onCheckedChange = { showShopName = it }
                            )
                            PosterToggleRow(
                                label = "Tampilkan Nomor WA & Alamat",
                                checked = showContact,
                                onCheckedChange = { showContact = it }
                            )
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
    subtitle: String? = null,
    testTag: String? = null,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun PosterLivePreviewCard(
    shopProfile: ShopProfile,
    groupedProducts: List<PosterCategoryGroup>,
    totalItems: Int,
    config: PosterRenderConfig
) {
    val customBgBitmap = if (config.template == PosterTemplate.CUSTOM_IMAGE && !config.customBgPath.isNullOrBlank()) {
        rememberLocalBitmap(config.customBgPath, maxDim = 720)
    } else null
    val style = remember(config.template, customBgBitmap) {
        if (customBgBitmap != null) PromoPosterStyles.softEmerald else PromoPosterStyles.resolve(config.template)
    }
    val colCount = if (totalItems <= 15) 2 else 3

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.verticalGradient(
                    listOf(Color(style.bgStart), Color(style.bgEnd))
                )
            )
    ) {
        // Custom Gallery Photo + Dark Scrim OR Subtle Geometric & Culinary Watermark Texture (6%-10% opacity)
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
                            listOf(Color(0xCC0D231B), Color(0xE0091813))
                        )
                    )
            )
        } else {
            val patternColor = Color(style.patternColor)
            val goldAccent = Color(style.accentGoldColor)
            Canvas(modifier = Modifier.matchParentSize()) {
                // Subtle diagonal diamond lattice (~6% opacity)
                val step = 42.dp.toPx()
                var x = -size.height
                while (x < size.width + size.height) {
                    drawLine(
                        color = patternColor.copy(alpha = 0.06f),
                        start = Offset(x, 0f),
                        end = Offset(x + size.height, size.height),
                        strokeWidth = 1f
                    )
                    drawLine(
                        color = patternColor.copy(alpha = 0.06f),
                        start = Offset(x + size.height, 0f),
                        end = Offset(x, size.height),
                        strokeWidth = 1f
                    )
                    x += step
                }
                // Subtle golden accent corner rings (~8% opacity)
                drawCircle(
                    color = goldAccent.copy(alpha = 0.08f),
                    radius = 68.dp.toPx(),
                    center = Offset(size.width - 14.dp.toPx(), 26.dp.toPx()),
                    style = Stroke(width = 1.5f)
                )
                drawCircle(
                    color = goldAccent.copy(alpha = 0.08f),
                    radius = 58.dp.toPx(),
                    center = Offset(18.dp.toPx(), size.height - 32.dp.toPx()),
                    style = Stroke(width = 1.5f)
                )
                // Subtle translucent culinary dome/stall shapes (~8% opacity)
                val domeSize = 34.dp.toPx()
                drawArc(
                    color = patternColor.copy(alpha = 0.08f),
                    startAngle = 180f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = Offset(size.width - 54.dp.toPx(), size.height * 0.36f),
                    size = Size(domeSize, domeSize * 0.72f),
                    style = Stroke(width = 2f)
                )
                drawArc(
                    color = patternColor.copy(alpha = 0.08f),
                    startAngle = 0f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = Offset(16.dp.toPx(), size.height * 0.52f),
                    size = Size(domeSize, domeSize * 0.72f),
                    style = Stroke(width = 2f)
                )
            }
        }

        // Subtle inner poster border
        Box(
            modifier = Modifier
                .matchParentSize()
                .padding(7.dp)
                .border(1.dp, Color(style.dividerColor), RoundedCornerShape(12.dp))
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 13.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // === HEADER ===
            if (config.showLogo) {
                ShopAvatarBadge(
                    iconKey = shopProfile.shopIconKey,
                    logoUri = shopProfile.logoUri,
                    size = 42.dp,
                    containerColor = Color(style.accentGoldColor).copy(alpha = 0.16f),
                    iconTint = Color(style.accentGoldColor),
                    borderColor = Color(style.accentGoldColor).copy(alpha = 0.7f)
                )
                Spacer(Modifier.height(7.dp))
            }

            if (config.showShopName) {
                Text(
                    text = shopProfile.shopName.trim().ifEmpty { "LAPAK KITA" }.uppercase(),
                    fontSize = 20.sp,
                    letterSpacing = 1.4.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(style.titleColor),
                    textAlign = TextAlign.Center
                )
            }

            Text(
                text = config.introText.trim().ifEmpty { "Menu Fresh & Ready Stok Hari Ini!" },
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                color = Color(style.subtitleColor),
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(8.dp))

            // "LIST MENU READY STOK" Ribbon Label with Flanking Lines
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(1.dp)
                        .background(Color(style.accentGoldColor).copy(alpha = 0.42f))
                )
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color(style.listLabelBg))
                        .border(1.dp, Color(style.accentGoldColor).copy(alpha = 0.55f), RoundedCornerShape(999.dp))
                        .padding(horizontal = 10.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "LIST MENU READY STOK",
                        fontSize = 9.sp,
                        letterSpacing = 1.1.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(style.listLabelText)
                    )
                }
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(1.dp)
                        .background(Color(style.accentGoldColor).copy(alpha = 0.42f))
                )
            }

            Spacer(Modifier.height(12.dp))

            // === BORDERLESS MENU LIST BY CATEGORY (NO WHITE CARD, NO SELLER NAME) ===
            if (groupedProducts.isEmpty()) {
                Text(
                    text = "Belum ada produk dengan stok tersedia (> 0).",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(style.itemNameColor).copy(alpha = 0.75f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 18.dp)
                )
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    groupedProducts.forEach { group ->
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // Soft Yellow/Orange Category Capsule Badge
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(999.dp))
                                        .background(
                                            Brush.horizontalGradient(
                                                listOf(
                                                    Color(style.categoryBadgeStart),
                                                    Color(style.categoryBadgeEnd)
                                                )
                                            )
                                        )
                                        .padding(horizontal = 9.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = group.categoryName.uppercase(),
                                        fontSize = 8.5.sp,
                                        letterSpacing = 0.6.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color(style.categoryBadgeText)
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(1.dp)
                                        .background(Color(style.accentGoldColor).copy(alpha = 0.28f))
                                )
                            }

                            // 3-Column Dense Grid (> 15 products) or 2-Column Grid (<= 15 products)
                            val chunkedRows = remember(group.items, colCount) {
                                group.items.chunked(colCount)
                            }
                            chunkedRows.forEachIndexed { rowIdx, rowItems ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 1.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    for (c in 0 until colCount) {
                                        val item = rowItems.getOrNull(c)
                                        if (item != null) {
                                            val number = rowIdx * colCount + c + 1
                                            PosterCompactMenuCell(
                                                number = number,
                                                detail = item,
                                                style = style,
                                                showStock = config.showStock,
                                                compact = colCount == 3,
                                                modifier = Modifier.weight(1f)
                                            )
                                        } else {
                                            Spacer(Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // === INTEGRATED FOOTER (NO WHITE BOX) ===
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .height(1.dp)
                    .background(Color(style.accentGoldColor).copy(alpha = 0.4f))
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = config.outroText.trim().ifEmpty { "Yuk pesan sekarang sebelum kehabisan!" },
                fontSize = 11.5.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color(style.footerOutroColor),
                textAlign = TextAlign.Center
            )
            if (config.showContact) {
                if (shopProfile.whatsappNumber.isNotBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = "WA Order: ${shopProfile.whatsappNumber}",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(style.footerContactColor),
                        textAlign = TextAlign.Center
                    )
                }
                if (shopProfile.address.isNotBlank()) {
                    Text(
                        text = shopProfile.address,
                        fontSize = 9.sp,
                        color = Color(style.footerContactColor).copy(alpha = 0.85f),
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun PosterCompactMenuCell(
    number: Int,
    detail: ProductDetailItem,
    style: PosterStyleSpec,
    showStock: Boolean,
    compact: Boolean,
    modifier: Modifier = Modifier
) {
    val fontSize = if (compact) 8.5.sp else 10.sp
    val priceStr = PromoPosterGenerator.formatShortPrice(detail.product.sellingPrice)

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$number. ${detail.product.name}",
            fontSize = fontSize,
            lineHeight = if (compact) 11.sp else 13.sp,
            fontWeight = FontWeight.Bold,
            color = Color(style.itemNameColor),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(end = 3.dp)
        )
        Text(
            text = buildAnnotatedString {
                withStyle(
                    SpanStyle(
                        color = Color(style.priceColor),
                        fontWeight = FontWeight.ExtraBold
                    )
                ) {
                    append(priceStr)
                }
                if (showStock) {
                    withStyle(
                        SpanStyle(
                            color = Color(style.stockColor),
                            fontWeight = FontWeight.Medium,
                            fontSize = if (compact) 7.5.sp else 8.5.sp
                        )
                    ) {
                        append("(${detail.currentStock})")
                    }
                }
            },
            fontSize = fontSize,
            lineHeight = if (compact) 11.sp else 13.sp,
            maxLines = 1
        )
    }
}
