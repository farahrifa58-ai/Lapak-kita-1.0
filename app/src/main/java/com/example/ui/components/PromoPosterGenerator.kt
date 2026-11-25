package com.example.ui.components

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.example.data.model.PosterTemplate
import com.example.data.model.ShopIconKey
import com.example.data.model.ShopProfile
import com.example.data.repository.ProductDetailItem
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.ceil

data class PosterRenderConfig(
    val template: String,
    val introText: String,
    val outroText: String,
    val showLogo: Boolean,
    val showShopName: Boolean,
    val showContact: Boolean,
    val showStock: Boolean = false,
    val customBgPath: String?
)

data class PosterCategoryGroup(
    val categoryName: String,
    val items: List<ProductDetailItem>
)

data class PosterStyleSpec(
    val key: String,
    val label: String,
    val bgStart: Int,
    val bgEnd: Int,
    val titleColor: Int,
    val accentGoldColor: Int,
    val subtitleColor: Int,
    val listLabelBg: Int,
    val listLabelText: Int,
    val categoryBadgeStart: Int,
    val categoryBadgeEnd: Int,
    val categoryBadgeText: Int,
    val itemNameColor: Int,
    val priceColor: Int,
    val stockColor: Int,
    val dividerColor: Int,
    val patternColor: Int,
    val footerOutroColor: Int,
    val footerContactColor: Int,
    val isDark: Boolean
)

object PromoPosterStyles {
    // Default signature theme: Deep Emerald (#16382C -> #0F2920) with crisp white & golden yellow
    val softEmerald = PosterStyleSpec(
        key = PosterTemplate.SOFT_EMERALD,
        label = "Hijau Lapak",
        bgStart = 0xFF16382C.toInt(),
        bgEnd = 0xFF0F2920.toInt(),
        titleColor = 0xFFFFFFFF.toInt(),
        accentGoldColor = 0xFFFDE047.toInt(),
        subtitleColor = 0xFFFDE047.toInt(),
        listLabelBg = 0x26FDE047,
        listLabelText = 0xFFFFFFFF.toInt(),
        categoryBadgeStart = 0xFFFDE047.toInt(),
        categoryBadgeEnd = 0xFFFDBA74.toInt(),
        categoryBadgeText = 0xFF122B22.toInt(),
        itemNameColor = 0xFFFFFFFF.toInt(),
        priceColor = 0xFFFDE047.toInt(),
        stockColor = 0xFFA7F3D0.toInt(),
        dividerColor = 0x26FFFFFF,
        patternColor = 0xFFFFFFFF.toInt(),
        footerOutroColor = 0xFFFDE047.toInt(),
        footerContactColor = 0xFFE6F4EE.toInt(),
        isDark = true
    )

    val darkGold = PosterStyleSpec(
        key = PosterTemplate.DARK_GOLD,
        label = "Dark Luxury Gold",
        bgStart = 0xFF1A1F1D.toInt(),
        bgEnd = 0xFF0B0E0D.toInt(),
        titleColor = 0xFFFFFFFF.toInt(),
        accentGoldColor = 0xFFF5D061.toInt(),
        subtitleColor = 0xFFF5D061.toInt(),
        listLabelBg = 0x26F5D061,
        listLabelText = 0xFFFFF8DC.toInt(),
        categoryBadgeStart = 0xFFF5D061.toInt(),
        categoryBadgeEnd = 0xFFE5A93C.toInt(),
        categoryBadgeText = 0xFF17130A.toInt(),
        itemNameColor = 0xFFFFFFFF.toInt(),
        priceColor = 0xFFF5D061.toInt(),
        stockColor = 0xFFD6D3D1.toInt(),
        dividerColor = 0x2BF5D061,
        patternColor = 0xFFF5D061.toInt(),
        footerOutroColor = 0xFFF5D061.toInt(),
        footerContactColor = 0xFFE7E5E4.toInt(),
        isDark = true
    )

    val freshTerracotta = PosterStyleSpec(
        key = PosterTemplate.FRESH_TERRACOTTA,
        label = "Fresh Terracotta",
        bgStart = 0xFF9E3A1E.toInt(),
        bgEnd = 0xFF66200F.toInt(),
        titleColor = 0xFFFFFFFF.toInt(),
        accentGoldColor = 0xFFFDE047.toInt(),
        subtitleColor = 0xFFFDE68A.toInt(),
        listLabelBg = 0x26FDE68A,
        listLabelText = 0xFFFFFFFF.toInt(),
        categoryBadgeStart = 0xFFFDE047.toInt(),
        categoryBadgeEnd = 0xFFFB923C.toInt(),
        categoryBadgeText = 0xFF3B1206.toInt(),
        itemNameColor = 0xFFFFFFFF.toInt(),
        priceColor = 0xFFFDE047.toInt(),
        stockColor = 0xFFFFEDD5.toInt(),
        dividerColor = 0x29FFFFFF,
        patternColor = 0xFFFFFFFF.toInt(),
        footerOutroColor = 0xFFFDE047.toInt(),
        footerContactColor = 0xFFFFEFE6.toInt(),
        isDark = true
    )

    val warmCream = PosterStyleSpec(
        key = PosterTemplate.WARM_CREAM,
        label = "Warm Cream Bakery",
        bgStart = 0xFFFDF5E6.toInt(),
        bgEnd = 0xFFF1D9B5.toInt(),
        titleColor = 0xFF3E2410.toInt(),
        accentGoldColor = 0xFFD97706.toInt(),
        subtitleColor = 0xFFB45309.toInt(),
        listLabelBg = 0x26B45309,
        listLabelText = 0xFF4A2B11.toInt(),
        categoryBadgeStart = 0xFFF59E0B.toInt(),
        categoryBadgeEnd = 0xFFEA580C.toInt(),
        categoryBadgeText = 0xFFFFFFFF.toInt(),
        itemNameColor = 0xFF2B1A0D.toInt(),
        priceColor = 0xFFB45309.toInt(),
        stockColor = 0xFF1F6B4E.toInt(),
        dividerColor = 0x2E78350F,
        patternColor = 0xFF92400E.toInt(),
        footerOutroColor = 0xFFB45309.toInt(),
        footerContactColor = 0xFF4A2E14.toInt(),
        isDark = false
    )

    val allTemplates = listOf(softEmerald, darkGold, freshTerracotta, warmCream)

    fun resolve(templateKey: String): PosterStyleSpec {
        return when (templateKey) {
            PosterTemplate.WARM_CREAM -> warmCream
            PosterTemplate.DARK_GOLD -> darkGold
            PosterTemplate.FRESH_TERRACOTTA -> freshTerracotta
            else -> softEmerald
        }
    }
}

object PromoPosterGenerator {

    private val preferredCategoryOrder = listOf(
        "makan berat",
        "makanan",
        "cemilan",
        "kue",
        "minuman"
    )

    fun formatShortPrice(price: Double): String {
        val rounded = price.toLong().coerceAtLeast(0L)
        return when {
            rounded >= 1000L && rounded % 1000L == 0L -> "${rounded / 1000L}K"
            rounded >= 1000L && rounded % 100L == 0L -> {
                val k = rounded / 1000.0
                "${String.format(Locale.US, "%.1f", k)}K"
            }
            rounded >= 1000L -> {
                val k = rounded / 1000.0
                "${String.format(Locale.US, "%.1f", k)}K"
            }
            else -> rounded.toString()
        }
    }

    fun groupReadyProductsByCategory(readyProducts: List<ProductDetailItem>): List<PosterCategoryGroup> {
        val active = readyProducts.filter { it.currentStock > 0 && it.product.isActive }
        return active
            .groupBy { it.product.category.trim().ifEmpty { "Menu Lainnya" } }
            .entries
            .sortedWith(
                compareBy<Map.Entry<String, List<ProductDetailItem>>> { entry ->
                    val idx = preferredCategoryOrder.indexOf(entry.key.lowercase())
                    if (idx >= 0) idx else preferredCategoryOrder.size
                }.thenBy { it.key.lowercase() }
            )
            .map { (cat, list) ->
                PosterCategoryGroup(
                    categoryName = cat,
                    items = list.sortedBy { it.product.name.lowercase() }
                )
            }
    }

    /**
     * Generates a crisp 1080px-wide customer promo poster PNG bitmap.
     * - Borderless design seamlessly integrated onto the dark emerald (#16382C -> #0F2920)
     *   background with subtle geometric lattice + transparent culinary/stall motifs (6%–10% opacity).
     * - Grouped automatically by category with soft yellow/orange capsule badges.
     * - Auto 3 dense columns (> 15 products) or 2 columns (<= 15 products), fitting 70–120 items cleanly.
     * - STRICT PRIVACY: NEVER includes seller/kitchen names.
     */
    fun generatePosterBitmap(
        context: Context,
        shopProfile: ShopProfile,
        readyProducts: List<ProductDetailItem>,
        config: PosterRenderConfig
    ): Bitmap {
        val items = readyProducts.filter { it.currentStock > 0 && it.product.isActive }
        val groups = groupReadyProductsByCategory(items)
        val colCount = if (items.size <= 15) 2 else 3

        val customBgBitmap = if (config.template == PosterTemplate.CUSTOM_IMAGE && !config.customBgPath.isNullOrBlank()) {
            loadLocalBitmap(context, config.customBgPath, maxDim = 1280)
        } else null
        val style = if (customBgBitmap != null) PromoPosterStyles.softEmerald else PromoPosterStyles.resolve(config.template)

        val width = 1080
        val padX = if (colCount == 3) 34f else 48f
        val padTop = 44f
        val padBottom = 42f

        // Adaptive density so 15 items look spacious and 70-120 items fit cleanly in 1 vertical poster
        val densityTier = when {
            colCount == 2 -> 0
            items.size <= 45 -> 1
            items.size <= 80 -> 2
            else -> 3
        }
        val rowHeight = when (densityTier) {
            0 -> 50f
            1 -> 42f
            2 -> 36f
            else -> 32f
        }
        val itemTextSize = when (densityTier) {
            0 -> 24f
            1 -> 20f
            2 -> 18.5f
            else -> 17f
        }
        val priceTextSize = when (densityTier) {
            0 -> 23.5f
            1 -> 19.5f
            2 -> 18f
            else -> 16.5f
        }
        val stockTextSize = when (densityTier) {
            0 -> 18.5f
            1 -> 15.5f
            2 -> 14.5f
            else -> 13.5f
        }
        val catBadgeHeight = when (densityTier) {
            0 -> 44f
            1 -> 40f
            2 -> 36f
            else -> 33f
        }
        val catSpacingTop = when (densityTier) {
            0 -> 24f
            1 -> 20f
            2 -> 16f
            else -> 13f
        }
        val catSpacingBelowBadge = when (densityTier) {
            0 -> 14f
            1 -> 11f
            2 -> 9f
            else -> 7f
        }

        val headerHeight = computeHeaderHeight(config, compact = densityTier >= 2)
        val menuHeight = if (groups.isEmpty()) {
            220f
        } else {
            var h = 10f
            groups.forEachIndexed { idx, group ->
                if (idx > 0) h += catSpacingTop
                h += catBadgeHeight + catSpacingBelowBadge
                val rows = ceil(group.items.size.toDouble() / colCount).toInt()
                h += rows * rowHeight
            }
            h + 18f
        }
        val footerHeight = computeFooterHeight(shopProfile, config)

        val rawHeight = (padTop + headerHeight + menuHeight + footerHeight + padBottom).toInt()
        val height = rawHeight.coerceAtLeast(1350)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // 1. Background (Gradient + Subtle Geometric Pattern + Transparent Culinary Motifs 6%-10%)
        if (customBgBitmap != null) {
            drawCenterCropBitmap(canvas, customBgBitmap, width, height)
            val scrim = Paint().apply {
                shader = LinearGradient(
                    0f, 0f, 0f, height.toFloat(),
                    0xCC0D231B.toInt(), 0xE0091813.toInt(),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), scrim)
        } else {
            val bgPaint = Paint().apply {
                shader = LinearGradient(
                    0f, 0f, width.toFloat() * 0.25f, height.toFloat(),
                    style.bgStart, style.bgEnd,
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

            // Soft radial glow behind header for depth
            val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(
                    width / 2f, 220f, width * 0.62f,
                    if (style.isDark) 0x1AFDE047 else 0x24F59E0B,
                    0x00000000,
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), glowPaint)

            // Geometric lattice + culinary stall watermark motifs at 6%-10% opacity
            drawGeometricAndCulinaryMotifs(canvas, width.toFloat(), height.toFloat(), style)
        }

        // Subtle outer framing line
        val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = style.dividerColor
            this.style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        canvas.drawRoundRect(
            RectF(18f, 18f, width - 18f, height - 18f),
            24f, 24f, framePaint
        )

        val centerX = width / 2f
        val extraSpace = (height - rawHeight).coerceAtLeast(0).toFloat()
        var cursorY = padTop + (extraSpace * 0.28f)

        // 2. Poster Header (Golden Logo + Bold White Title + Golden Subtitle + "LIST MENU READY STOK")
        if (config.showLogo) {
            val badgeSize = if (densityTier >= 2) 78f else 94f
            val badgeRect = RectF(
                centerX - badgeSize / 2f,
                cursorY,
                centerX + badgeSize / 2f,
                cursorY + badgeSize
            )
            val customLogo = if (shopProfile.shopIconKey == ShopIconKey.CUSTOM_PHOTO && !shopProfile.logoUri.isNullOrBlank()) {
                loadLocalBitmap(context, shopProfile.logoUri, maxDim = 256)
            } else null

            val badgeBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (style.isDark) 0x24FDE047 else 0x22B45309
            }
            val badgeBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = style.accentGoldColor
                alpha = 180
                this.style = Paint.Style.STROKE
                strokeWidth = 3f
            }
            canvas.drawRoundRect(badgeRect, 24f, 24f, badgeBg)
            canvas.drawRoundRect(badgeRect, 24f, 24f, badgeBorder)

            if (customLogo != null) {
                val save = canvas.save()
                val clipPath = Path().apply { addRoundRect(badgeRect, 24f, 24f, Path.Direction.CW) }
                canvas.clipPath(clipPath)
                drawCenterCropBitmap(canvas, customLogo, badgeRect)
                canvas.restoreToCount(save)
            } else {
                drawGoldenShopIcon(
                    canvas = canvas,
                    cx = badgeRect.centerX(),
                    cy = badgeRect.centerY(),
                    iconSize = badgeSize * 0.56f,
                    color = style.accentGoldColor,
                    iconKey = shopProfile.shopIconKey
                )
            }
            cursorY += badgeSize + 18f
        }

        if (config.showShopName) {
            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = style.titleColor
                textSize = if (densityTier >= 2) 54f else 62f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
                letterSpacing = 0.08f
            }
            val shopTitle = shopProfile.shopName.trim().ifEmpty { "LAPAK KITA" }.uppercase()
            canvas.drawText(shopTitle.take(26), centerX, cursorY + 46f, titlePaint)
            cursorY += if (densityTier >= 2) 60f else 68f
        }

        // Golden Yellow Subtitle
        val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = style.subtitleColor
            textSize = if (densityTier >= 2) 27f else 30f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val subtitleText = config.introText.trim().ifEmpty { "Menu Fresh & Ready Stok Hari Ini!" }
        canvas.drawText(subtitleText.take(54), centerX, cursorY + 26f, subtitlePaint)
        cursorY += 44f

        // "LIST MENU READY STOK" Ribbon Label with Flanking Golden Lines
        val labelText = "LIST MENU READY STOK"
        val ribbonTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = style.listLabelText
            textSize = 22f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.14f
        }
        val ribbonTextW = ribbonTextPaint.measureText(labelText)
        val ribbonW = ribbonTextW + 52f
        val ribbonH = 42f
        val ribbonRect = RectF(
            centerX - ribbonW / 2f,
            cursorY,
            centerX + ribbonW / 2f,
            cursorY + ribbonH
        )
        val ribbonBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = style.listLabelBg
        }
        val ribbonStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = style.accentGoldColor
            alpha = 140
            this.style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        val flankLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = style.accentGoldColor
            alpha = 95
            strokeWidth = 2f
        }
        canvas.drawLine(padX + 12f, ribbonRect.centerY(), ribbonRect.left - 18f, ribbonRect.centerY(), flankLinePaint)
        canvas.drawLine(ribbonRect.right + 18f, ribbonRect.centerY(), width - padX - 12f, ribbonRect.centerY(), flankLinePaint)
        canvas.drawRoundRect(ribbonRect, ribbonH / 2f, ribbonH / 2f, ribbonBgPaint)
        canvas.drawRoundRect(ribbonRect, ribbonH / 2f, ribbonH / 2f, ribbonStrokePaint)
        canvas.drawText(labelText, centerX, ribbonRect.centerY() + 8f, ribbonTextPaint)
        cursorY += ribbonH + 26f

        // 3. Borderless Menu List Grouped by Category (Directly on Background, NO White Box!)
        val contentLeft = padX
        val contentRight = width - padX
        val contentWidth = contentRight - contentLeft
        val colGap = if (colCount == 3) 20f else 34f
        val colWidth = (contentWidth - colGap * (colCount - 1)) / colCount

        if (groups.isEmpty()) {
            val emptyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = style.itemNameColor
                alpha = 200
                textSize = 30f
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("Semua stok hari ini sudah habis terjual", centerX, cursorY + 90f, emptyPaint)
            cursorY += 180f
        } else {
            val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = style.itemNameColor
                textSize = itemTextSize
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.LEFT
            }
            val pricePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = style.priceColor
                textSize = priceTextSize
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.RIGHT
            }
            val stockPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = style.stockColor
                textSize = stockTextSize
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                textAlign = Paint.Align.RIGHT
            }
            val catTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = style.categoryBadgeText
                textSize = if (densityTier >= 2) 19f else 22f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.LEFT
                letterSpacing = 0.06f
            }
            val colGuidePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = style.dividerColor
                strokeWidth = 1.5f
            }
            val rowSubDividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = style.dividerColor
                alpha = 28
                strokeWidth = 1f
            }

            groups.forEachIndexed { groupIdx, group ->
                if (groupIdx > 0) cursorY += catSpacingTop

                // Category Capsule Badge (Soft Yellow / Orange)
                val catLabel = group.categoryName.uppercase()
                val badgeTextW = catTextPaint.measureText(catLabel)
                val badgeW = (badgeTextW + 44f).coerceAtLeast(155f)
                val badgeRect = RectF(contentLeft, cursorY, contentLeft + badgeW, cursorY + catBadgeHeight)

                val badgeFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = LinearGradient(
                        badgeRect.left, badgeRect.top, badgeRect.right, badgeRect.bottom,
                        style.categoryBadgeStart, style.categoryBadgeEnd,
                        Shader.TileMode.CLAMP
                    )
                }
                canvas.drawRoundRect(badgeRect, catBadgeHeight / 2f, catBadgeHeight / 2f, badgeFillPaint)
                canvas.drawText(
                    catLabel,
                    badgeRect.left + 22f,
                    badgeRect.centerY() + (catTextPaint.textSize * 0.34f),
                    catTextPaint
                )

                // Subtle horizontal accent line extending right from capsule badge
                val catLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = style.accentGoldColor
                    alpha = 65
                    strokeWidth = 2f
                }
                canvas.drawLine(
                    badgeRect.right + 16f,
                    badgeRect.centerY(),
                    contentRight,
                    badgeRect.centerY(),
                    catLinePaint
                )

                cursorY += catBadgeHeight + catSpacingBelowBadge

                val groupStartY = cursorY
                val rows = ceil(group.items.size.toDouble() / colCount).toInt()
                val groupBottomY = groupStartY + rows * rowHeight

                // Subtle vertical guides between columns within this category block
                if (rows > 0) {
                    for (c in 1 until colCount) {
                        val guideX = contentLeft + c * colWidth + (c - 0.5f) * colGap
                        canvas.drawLine(guideX, groupStartY + 4f, guideX, groupBottomY - 6f, colGuidePaint)
                    }
                }

                // Column-major or row-major fill? Row-major keeps numbering natural left-to-right (1, 2, 3 / 4, 5, 6)
                for (r in 0 until rows) {
                    val rowTop = groupStartY + r * rowHeight
                    val baselineY = rowTop + rowHeight * 0.68f

                    for (c in 0 until colCount) {
                        val itemIndex = r * colCount + c
                        if (itemIndex >= group.items.size) continue

                        val detail = group.items[itemIndex]
                        val cellLeft = contentLeft + c * (colWidth + colGap)
                        val cellRight = cellLeft + colWidth

                        // Right-aligned price (+ optional small stock count "(14)")
                        val priceStr = formatShortPrice(detail.product.sellingPrice)
                        val stockStr = if (config.showStock) " (${detail.currentStock})" else ""
                        val stockW = if (config.showStock) stockPaint.measureText(stockStr) else 0f
                        val priceW = pricePaint.measureText(priceStr)

                        if (config.showStock) {
                            canvas.drawText(stockStr, cellRight - 2f, baselineY, stockPaint)
                            canvas.drawText(priceStr, cellRight - 2f - stockW, baselineY, pricePaint)
                        } else {
                            canvas.drawText(priceStr, cellRight - 2f, baselineY, pricePaint)
                        }

                        // Left-aligned numbered product name ONLY (NO seller name!)
                        val reservedRight = priceW + stockW + 10f
                        val maxNameWidth = (colWidth - reservedRight).coerceAtLeast(80f)
                        val numberedName = "${itemIndex + 1}. ${detail.product.name}"
                        val cleanName = truncateText(numberedName, namePaint, maxNameWidth)
                        canvas.drawText(cleanName, cellLeft + 2f, baselineY, namePaint)
                    }

                    if (r < rows - 1) {
                        canvas.drawLine(
                            contentLeft,
                            rowTop + rowHeight,
                            contentRight,
                            rowTop + rowHeight,
                            rowSubDividerPaint
                        )
                    }
                }

                cursorY = groupBottomY
            }
        }

        // 4. Footer Integrated Directly on Background
        val footerTopY = (height - padBottom - footerHeight + 14f).coerceAtLeast(cursorY + 26f)
        val footerLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = style.accentGoldColor
            alpha = 90
            strokeWidth = 2f
        }
        canvas.drawLine(contentLeft + 24f, footerTopY, contentRight - 24f, footerTopY, footerLinePaint)

        var footerCursorY = footerTopY + 46f
        val outroPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = style.footerOutroColor
            textSize = 29f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val outroText = config.outroText.trim().ifEmpty { "Yuk pesan sekarang sebelum kehabisan!" }
        canvas.drawText(outroText.take(58), centerX, footerCursorY, outroPaint)

        if (config.showContact) {
            val contactPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = style.footerContactColor
                textSize = 24f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }
            val addrPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = style.footerContactColor
                alpha = 215
                textSize = 21f
                textAlign = Paint.Align.CENTER
            }
            if (shopProfile.whatsappNumber.isNotBlank()) {
                footerCursorY += 40f
                canvas.drawText("WhatsApp Order: ${shopProfile.whatsappNumber}", centerX, footerCursorY, contactPaint)
            }
            if (shopProfile.address.isNotBlank()) {
                footerCursorY += 34f
                val cleanAddr = truncateText(shopProfile.address, addrPaint, width - padX * 2 - 40f)
                canvas.drawText(cleanAddr, centerX, footerCursorY, addrPaint)
            }
        }

        return bitmap
    }

    private fun computeHeaderHeight(config: PosterRenderConfig, compact: Boolean): Float {
        var h = 44f + 42f + 26f // subtitle + ribbon + bottom gap
        if (config.showLogo) h += (if (compact) 78f else 94f) + 18f
        if (config.showShopName) h += if (compact) 60f else 68f
        return h
    }

    private fun computeFooterHeight(shopProfile: ShopProfile, config: PosterRenderConfig): Float {
        var h = 74f
        if (config.showContact) {
            if (shopProfile.whatsappNumber.isNotBlank()) h += 40f
            if (shopProfile.address.isNotBlank()) h += 36f
        }
        return h
    }

    /**
     * Draws subtle geometric diamond lattice and culinary/stall vector illustrations at 6%–10% opacity
     * directly on the poster background for handcrafted editorial texture.
     */
    private fun drawGeometricAndCulinaryMotifs(
        canvas: Canvas,
        width: Float,
        height: Float,
        style: PosterStyleSpec
    ) {
        // 1. Subtle Geometric Diamond Grid (~6% opacity -> alpha 15)
        val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = style.patternColor
            alpha = 15
            this.style = Paint.Style.STROKE
            strokeWidth = 1.6f
        }
        val step = 120f
        var x = -height
        while (x < width + height) {
            canvas.drawLine(x, 0f, x + height, height, gridPaint)
            canvas.drawLine(x + height, 0f, x, height, gridPaint)
            x += step
        }

        // 2. Subtle Concentric Corner Rings (~7% opacity -> alpha 18)
        val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = style.accentGoldColor
            alpha = 18
            this.style = Paint.Style.STROKE
            strokeWidth = 2.5f
        }
        canvas.drawCircle(width - 40f, 90f, 175f, ringPaint)
        canvas.drawCircle(width - 40f, 90f, 230f, ringPaint)
        canvas.drawCircle(50f, height - 110f, 165f, ringPaint)
        canvas.drawCircle(50f, height - 110f, 220f, ringPaint)

        // 3. Transparent Culinary & Stall Icon Watermarks (~8% opacity -> alpha 21)
        val motifPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = style.patternColor
            alpha = 21
            this.style = Paint.Style.STROKE
            strokeWidth = 3.5f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        val positions = listOf(
            Triple(120f, 165f, 0),
            Triple(width - 135f, 240f, 1),
            Triple(width * 0.16f, height * 0.44f, 2),
            Triple(width * 0.86f, height * 0.56f, 3),
            Triple(width * 0.18f, height * 0.78f, 1),
            Triple(width * 0.84f, height - 175f, 0)
        )
        positions.forEach { (cx, cy, kind) ->
            when (kind) {
                0 -> drawStallWatermark(canvas, cx, cy, 74f, motifPaint)
                1 -> drawClocheWatermark(canvas, cx, cy, 72f, motifPaint)
                2 -> drawBowlWatermark(canvas, cx, cy, 68f, motifPaint)
                else -> drawCupWatermark(canvas, cx, cy, 66f, motifPaint)
            }
        }
    }

    private fun drawStallWatermark(canvas: Canvas, cx: Float, cy: Float, size: Float, paint: Paint) {
        val half = size / 2f
        // Roof awning trapezoid
        val roof = Path().apply {
            moveTo(cx - half, cy - half * 0.15f)
            lineTo(cx - half * 0.78f, cy - half * 0.68f)
            lineTo(cx + half * 0.78f, cy - half * 0.68f)
            lineTo(cx + half, cy - half * 0.15f)
            close()
        }
        canvas.drawPath(roof, paint)
        // Scallops under awning
        val scallopW = size / 4f
        for (i in 0 until 4) {
            val sx = cx - half + i * scallopW
            canvas.drawArc(
                RectF(sx, cy - half * 0.28f, sx + scallopW, cy + half * 0.05f),
                0f, 180f, false, paint
            )
        }
        // Stall counter body
        canvas.drawRect(cx - half * 0.78f, cy + half * 0.05f, cx + half * 0.78f, cy + half * 0.72f, paint)
        canvas.drawLine(cx - half * 0.92f, cy + half * 0.72f, cx + half * 0.92f, cy + half * 0.72f, paint)
    }

    private fun drawClocheWatermark(canvas: Canvas, cx: Float, cy: Float, size: Float, paint: Paint) {
        val half = size / 2f
        canvas.drawArc(RectF(cx - half * 0.85f, cy - half * 0.48f, cx + half * 0.85f, cy + half * 0.85f), 180f, 180f, false, paint)
        canvas.drawCircle(cx, cy - half * 0.56f, half * 0.13f, paint)
        canvas.drawLine(cx - half, cy + half * 0.25f, cx + half, cy + half * 0.25f, paint)
        canvas.drawLine(cx - half * 0.82f, cy + half * 0.39f, cx + half * 0.82f, cy + half * 0.39f, paint)
    }

    private fun drawBowlWatermark(canvas: Canvas, cx: Float, cy: Float, size: Float, paint: Paint) {
        val half = size / 2f
        canvas.drawArc(RectF(cx - half * 0.85f, cy - half * 0.25f, cx + half * 0.85f, cy + half * 0.75f), 0f, 180f, true, paint)
        canvas.drawLine(cx - half * 0.36f, cy + half * 0.78f, cx + half * 0.36f, cy + half * 0.78f, paint)
        // Steam curls
        for (dx in listOf(-half * 0.28f, 0f, half * 0.28f)) {
            val steam = Path().apply {
                moveTo(cx + dx, cy - half * 0.35f)
                quadTo(cx + dx + 8f, cy - half * 0.55f, cx + dx - 4f, cy - half * 0.75f)
            }
            canvas.drawPath(steam, paint)
        }
    }

    private fun drawCupWatermark(canvas: Canvas, cx: Float, cy: Float, size: Float, paint: Paint) {
        val half = size / 2f
        canvas.drawRoundRect(RectF(cx - half * 0.62f, cy - half * 0.30f, cx + half * 0.38f, cy + half * 0.60f), 10f, 10f, paint)
        canvas.drawArc(RectF(cx + half * 0.24f, cy - half * 0.14f, cx + half * 0.76f, cy + half * 0.36f), -90f, 180f, false, paint)
        canvas.drawLine(cx - half * 0.72f, cy + half * 0.72f, cx + half * 0.54f, cy + half * 0.72f, paint)
    }

    /**
     * Draws a crisp golden vector stall/culinary icon in the poster header (avoids inconsistent emojis).
     */
    private fun drawGoldenShopIcon(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        iconSize: Float,
        color: Int,
        iconKey: String
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            this.style = Paint.Style.STROKE
            strokeWidth = 4.2f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        when (iconKey) {
            ShopIconKey.CAFE -> drawCupWatermark(canvas, cx, cy, iconSize, paint)
            ShopIconKey.RESTAURANT -> drawClocheWatermark(canvas, cx, cy, iconSize, paint)
            ShopIconKey.BAKERY -> drawBowlWatermark(canvas, cx, cy, iconSize, paint)
            else -> drawStallWatermark(canvas, cx, cy, iconSize, paint)
        }
    }

    private fun truncateText(text: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        var end = text.length
        while (end > 3 && paint.measureText(text.substring(0, end) + "..") > maxWidth) {
            end--
        }
        return text.substring(0, end.coerceAtLeast(1)) + ".."
    }

    private fun drawCenterCropBitmap(canvas: Canvas, src: Bitmap, targetW: Int, targetH: Int) {
        drawCenterCropBitmap(canvas, src, RectF(0f, 0f, targetW.toFloat(), targetH.toFloat()))
    }

    private fun drawCenterCropBitmap(canvas: Canvas, src: Bitmap, dest: RectF) {
        val srcRatio = src.width.toFloat() / src.height.toFloat().coerceAtLeast(1f)
        val destRatio = dest.width() / dest.height().coerceAtLeast(1f)
        val srcRect = if (srcRatio > destRatio) {
            val cropW = (src.height * destRatio).toInt()
            val left = (src.width - cropW) / 2
            Rect(left, 0, left + cropW, src.height)
        } else {
            val cropH = (src.width / destRatio).toInt()
            val top = (src.height - cropH) / 2
            Rect(0, top, src.width, top + cropH)
        }
        canvas.drawBitmap(src, srcRect, dest, Paint(Paint.FILTER_BITMAP_FLAG))
    }

    fun savePosterToGallery(context: Context, bitmap: Bitmap): Result<String> {
        return try {
            val fileName = "LapakKita_Promo_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.png"
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Lapak Kita")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: return Result.failure(IllegalStateException("Gagal membuat file galeri"))

            resolver.openOutputStream(uri)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            } ?: return Result.failure(IllegalStateException("Gagal menulis gambar ke galeri"))

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
            Result.success(fileName)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun sharePosterToWhatsApp(context: Context, bitmap: Bitmap, caption: String) {
        try {
            val cacheFolder = File(context.cacheDir, "posters").apply { mkdirs() }
            val imageFile = File(cacheFolder, "promo_ready_stok_${System.currentTimeMillis()}.png")
            FileOutputStream(imageFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }

            val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                imageFile
            )

            val waIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                putExtra(Intent.EXTRA_TEXT, caption)
                setPackage("com.whatsapp")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            try {
                context.startActivity(waIntent)
            } catch (_: Exception) {
                val chooserIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, contentUri)
                    putExtra(Intent.EXTRA_TEXT, caption)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                val chooser = Intent.createChooser(chooserIntent, "Bagikan Poster Promo").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(chooser)
            }
        } catch (_: Exception) {
            // Ignored safely; caller handles status
        }
    }
}
