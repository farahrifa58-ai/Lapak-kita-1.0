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

    fun resolveColumnCount(totalItems: Int): Int = when {
        totalItems <= 8 -> 1
        totalItems <= 24 -> 2
        else -> 3
    }

    fun groupReadyProductsByCategory(readyProducts: List<ProductDetailItem>): List<PosterCategoryGroup> {
        val active = readyProducts.filter { it.currentStock > 0 && it.product.isActive }
        return active
            .groupBy { it.product.category.trim().ifEmpty { "Menu Lainnya" }.lowercase(Locale.getDefault()) }
            .entries
            .sortedWith(
                compareBy<Map.Entry<String, List<ProductDetailItem>>> { entry ->
                    val idx = preferredCategoryOrder.indexOf(entry.key)
                    if (idx >= 0) idx else preferredCategoryOrder.size
                }.thenBy { it.key }
            )
            .map { (_, list) ->
                val displayName = list.firstOrNull()?.product?.category?.trim()?.ifEmpty { "Menu Lainnya" } ?: "Menu Lainnya"
                PosterCategoryGroup(
                    categoryName = displayName,
                    items = list.sortedBy { it.product.name.lowercase(Locale.getDefault()) }
                )
            }
    }

    private data class PreparedPosterCell(
        val numberPrefix: String,
        val numberWidth: Float,
        val nameLines: List<String>,
        val priceStr: String,
        val stockStr: String,
        val priceWidth: Float,
        val stockWidth: Float,
        val colIndex: Int,
        val cellLeft: Float,
        val cellRight: Float
    )

    private data class PreparedPosterRow(
        val cells: List<PreparedPosterCell>,
        val rowHeight: Float
    )

    private data class PreparedPosterGroup(
        val categoryName: String,
        val effectiveColCount: Int,
        val colWidth: Float,
        val colGap: Float,
        val rows: List<PreparedPosterRow>,
        val totalRowsHeight: Float
    )

    /**
     * Generates a crisp 1080px-wide customer promo poster PNG bitmap.
     * - Borderless design seamlessly integrated onto the selected theme background with
     *   subtle geometric lattice + transparent culinary/stall motifs (6%–10% opacity).
     * - Grouped automatically by category (case-insensitive) with soft yellow/orange capsule badges.
     * - Adaptive 1-column (<= 8 items), 2-column (9–24 items), or 3-column (> 24 items) layout.
     * - FULL multi-line wrapping with hanging indent for long product names (ZERO "..." truncation).
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
        val colCount = resolveColumnCount(items.size)

        val customBgBitmap = if (config.template == PosterTemplate.CUSTOM_IMAGE && !config.customBgPath.isNullOrBlank()) {
            loadLocalBitmap(context, config.customBgPath, maxDim = 1280)
        } else null
        val style = if (customBgBitmap != null) PromoPosterStyles.softEmerald else PromoPosterStyles.resolve(config.template)

        val width = 1080
        val padX = when (colCount) {
            1 -> 58f
            2 -> 46f
            else -> 34f
        }
        val padTop = 44f
        val padBottom = 42f

        // Adaptive density tier so short menus look airy & editorial while 70-120 items still fit cleanly
        val densityTier = when {
            colCount == 1 -> 0
            colCount == 2 -> 1
            items.size <= 55 -> 2
            else -> 3
        }
        val itemTextSize = when (densityTier) {
            0 -> 28f
            1 -> 23.5f
            2 -> 19.5f
            else -> 17.5f
        }
        val priceTextSize = when (densityTier) {
            0 -> 28f
            1 -> 23f
            2 -> 19f
            else -> 17f
        }
        val stockTextSize = when (densityTier) {
            0 -> 21f
            1 -> 18f
            2 -> 15f
            else -> 13.5f
        }
        val lineHeight = when (densityTier) {
            0 -> 37f
            1 -> 31f
            2 -> 25.5f
            else -> 22.5f
        }
        val minRowHeight = when (densityTier) {
            0 -> 58f
            1 -> 48f
            2 -> 40f
            else -> 35f
        }
        val rowVerticalPadding = when (densityTier) {
            0 -> 20f
            1 -> 16f
            2 -> 13f
            else -> 11f
        }
        val catBadgeHeight = when (densityTier) {
            0 -> 46f
            1 -> 44f
            2 -> 38f
            else -> 34f
        }
        val catSpacingTop = when (densityTier) {
            0 -> 28f
            1 -> 24f
            2 -> 18f
            else -> 14f
        }
        val catSpacingBelowBadge = when (densityTier) {
            0 -> 16f
            1 -> 14f
            2 -> 10f
            else -> 8f
        }

        val contentLeft = padX
        val contentRight = width - padX
        val contentWidth = contentRight - contentLeft

        // Prepare paints up front so text measurement matches actual drawing 100%
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = style.titleColor
            textSize = if (densityTier >= 2) 54f else 62f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.08f
        }
        val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = style.subtitleColor
            textSize = if (densityTier >= 2) 27f else 30f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
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
        val outroPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = style.footerOutroColor
            textSize = 29f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
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

        // Pre-wrap header & footer text so nothing is ever clipped
        val shopTitleLines = if (config.showShopName) {
            wrapTextLines(
                text = shopProfile.shopName.trim().ifEmpty { "LAPAK KITA" }.uppercase(Locale.getDefault()),
                paint = titlePaint,
                maxWidth = contentWidth - 20f
            )
        } else emptyList()
        val subtitleLines = wrapTextLines(
            text = config.introText.trim().ifEmpty { "Menu Fresh & Ready Stok Hari Ini!" },
            paint = subtitlePaint,
            maxWidth = contentWidth - 24f
        )
        val outroLines = wrapTextLines(
            text = config.outroText.trim().ifEmpty { "Yuk pesan sekarang sebelum kehabisan!" },
            paint = outroPaint,
            maxWidth = contentWidth - 24f
        )
        val addressLines = if (config.showContact && shopProfile.address.isNotBlank()) {
            wrapTextLines(
                text = shopProfile.address.trim(),
                paint = addrPaint,
                maxWidth = contentWidth - 32f
            )
        } else emptyList()

        // Pre-layout every category & multi-line row with exact dynamic row heights
        val preparedGroups = groups.map { group ->
            val effectiveCols = if (group.items.size == 1) 1 else colCount
            val groupColGap = when (effectiveCols) {
                1 -> 0f
                2 -> 34f
                else -> 22f
            }
            val groupColWidth = (contentWidth - groupColGap * (effectiveCols - 1)) / effectiveCols
            val rowCount = ceil(group.items.size.toDouble() / effectiveCols).toInt()
            val preparedRows = mutableListOf<PreparedPosterRow>()
            var totalGroupRowsH = 0f

            for (r in 0 until rowCount) {
                val cells = mutableListOf<PreparedPosterCell>()
                var maxLinesInRow = 1
                for (c in 0 until effectiveCols) {
                    val itemIndex = r * effectiveCols + c
                    if (itemIndex >= group.items.size) continue
                    val detail = group.items[itemIndex]
                    val cellLeft = contentLeft + c * (groupColWidth + groupColGap)
                    val cellRight = cellLeft + groupColWidth

                    val priceStr = formatShortPrice(detail.product.sellingPrice)
                    val stockStr = if (config.showStock) " (${detail.currentStock})" else ""
                    val stockW = if (config.showStock) stockPaint.measureText(stockStr) else 0f
                    val priceW = pricePaint.measureText(priceStr)
                    val numberPrefix = "${itemIndex + 1}. "
                    val numberW = namePaint.measureText(numberPrefix)

                    val rightReserve = priceW + stockW + (if (effectiveCols == 1) 24f else 14f)
                    val maxNameWidth = (groupColWidth - numberW - rightReserve - 4f).coerceAtLeast(80f)
                    val wrappedLines = wrapTextLines(detail.product.name.trim(), namePaint, maxNameWidth)
                    if (wrappedLines.size > maxLinesInRow) {
                        maxLinesInRow = wrappedLines.size
                    }

                    cells.add(
                        PreparedPosterCell(
                            numberPrefix = numberPrefix,
                            numberWidth = numberW,
                            nameLines = wrappedLines,
                            priceStr = priceStr,
                            stockStr = stockStr,
                            priceWidth = priceW,
                            stockWidth = stockW,
                            colIndex = c,
                            cellLeft = cellLeft,
                            cellRight = cellRight
                        )
                    )
                }

                val computedRowH = maxOf(minRowHeight, maxLinesInRow * lineHeight + rowVerticalPadding)
                preparedRows.add(PreparedPosterRow(cells = cells, rowHeight = computedRowH))
                totalGroupRowsH += computedRowH
            }

            PreparedPosterGroup(
                categoryName = group.categoryName,
                effectiveColCount = effectiveCols,
                colWidth = groupColWidth,
                colGap = groupColGap,
                rows = preparedRows,
                totalRowsHeight = totalGroupRowsH
            )
        }

        val titleLineStep = if (densityTier >= 2) 58f else 66f
        val subtitleLineStep = if (densityTier >= 2) 34f else 38f
        val headerHeight = computeHeaderHeight(
            config = config,
            compact = densityTier >= 2,
            titleLineCount = shopTitleLines.size,
            titleLineStep = titleLineStep,
            subtitleLineCount = subtitleLines.size,
            subtitleLineStep = subtitleLineStep
        )
        val menuHeight = if (preparedGroups.isEmpty()) {
            220f
        } else {
            var h = 10f
            preparedGroups.forEachIndexed { idx, group ->
                if (idx > 0) h += catSpacingTop
                h += catBadgeHeight + catSpacingBelowBadge + group.totalRowsHeight
            }
            h + 22f
        }
        val footerHeight = computeFooterHeight(
            shopProfile = shopProfile,
            config = config,
            outroLineCount = outroLines.size,
            addressLineCount = addressLines.size
        )

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
            shopTitleLines.forEachIndexed { idx, line ->
                canvas.drawText(line, centerX, cursorY + 46f + idx * titleLineStep, titlePaint)
            }
            cursorY += shopTitleLines.size * titleLineStep + 4f
        }

        // Golden Yellow Subtitle (multi-line safe)
        subtitleLines.forEachIndexed { idx, line ->
            canvas.drawText(line, centerX, cursorY + 26f + idx * subtitleLineStep, subtitlePaint)
        }
        cursorY += subtitleLines.size * subtitleLineStep + 10f

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

        // 3. Borderless Menu List Grouped by Category (Full Wrapping, Hanging Indent, ZERO Truncation)
        if (preparedGroups.isEmpty()) {
            val emptyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = style.itemNameColor
                alpha = 200
                textSize = 30f
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("Semua stok hari ini sudah habis terjual", centerX, cursorY + 90f, emptyPaint)
            cursorY += 180f
        } else {
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
            val dotLeaderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = style.accentGoldColor
                alpha = 72
                strokeWidth = 2f
                pathEffect = DashPathEffect(floatArrayOf(3f, 9f), 0f)
            }
            val fontMetrics = namePaint.fontMetrics
            val ascentOffset = -fontMetrics.ascent

            preparedGroups.forEachIndexed { groupIdx, group ->
                if (groupIdx > 0) cursorY += catSpacingTop

                // Category Capsule Badge (Soft Yellow / Orange)
                val catLabel = group.categoryName.uppercase(Locale.getDefault())
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
                val groupBottomY = groupStartY + group.totalRowsHeight

                // Subtle vertical guides between columns within multi-column category blocks
                if (group.rows.isNotEmpty() && group.effectiveColCount > 1) {
                    for (c in 1 until group.effectiveColCount) {
                        val guideX = contentLeft + c * group.colWidth + (c - 0.5f) * group.colGap
                        canvas.drawLine(guideX, groupStartY + 4f, guideX, groupBottomY - 6f, colGuidePaint)
                    }
                }

                var rowTop = groupStartY
                group.rows.forEachIndexed { rIdx, row ->
                    val firstLineBaselineY = rowTop + (rowVerticalPadding * 0.45f) + ascentOffset

                    row.cells.forEach { cell ->
                        // Right-aligned price (+ optional small stock count "(14)") anchored to line 1
                        if (config.showStock) {
                            canvas.drawText(cell.stockStr, cell.cellRight - 2f, firstLineBaselineY, stockPaint)
                            canvas.drawText(
                                cell.priceStr,
                                cell.cellRight - 2f - cell.stockWidth,
                                firstLineBaselineY,
                                pricePaint
                            )
                        } else {
                            canvas.drawText(cell.priceStr, cell.cellRight - 2f, firstLineBaselineY, pricePaint)
                        }

                        // Number prefix on line 1, product name wrapped cleanly with hanging indent!
                        val textStartX = cell.cellLeft + 2f + cell.numberWidth
                        canvas.drawText(cell.numberPrefix, cell.cellLeft + 2f, firstLineBaselineY, namePaint)
                        cell.nameLines.forEachIndexed { lineIdx, lineText ->
                            canvas.drawText(
                                lineText,
                                textStartX,
                                firstLineBaselineY + lineIdx * lineHeight,
                                namePaint
                            )
                        }

                        // Elegant subtle dotted leader for spacious 1-column single-line rows
                        if (group.effectiveColCount == 1 && cell.nameLines.size == 1) {
                            val nameEndX = textStartX + namePaint.measureText(cell.nameLines.first()) + 14f
                            val priceStartX = cell.cellRight - 2f - cell.stockWidth - cell.priceWidth - 14f
                            if (priceStartX - nameEndX > 28f) {
                                val dotY = firstLineBaselineY - 4f
                                canvas.drawLine(nameEndX, dotY, priceStartX, dotY, dotLeaderPaint)
                            }
                        }
                    }

                    rowTop += row.rowHeight
                    if (rIdx < group.rows.lastIndex) {
                        canvas.drawLine(
                            contentLeft,
                            rowTop,
                            contentRight,
                            rowTop,
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
        outroLines.forEachIndexed { idx, line ->
            if (idx > 0) footerCursorY += 36f
            canvas.drawText(line, centerX, footerCursorY, outroPaint)
        }

        if (config.showContact) {
            if (shopProfile.whatsappNumber.isNotBlank()) {
                footerCursorY += 40f
                canvas.drawText("WhatsApp Order: ${shopProfile.whatsappNumber}", centerX, footerCursorY, contactPaint)
            }
            if (addressLines.isNotEmpty()) {
                addressLines.forEachIndexed { idx, line ->
                    footerCursorY += if (idx == 0) 34f else 28f
                    canvas.drawText(line, centerX, footerCursorY, addrPaint)
                }
            }
        }

        return bitmap
    }

    /**
     * Wraps [text] cleanly into full lines within [maxWidth] without ever truncating with "...".
     * Splits on word boundaries and breaks single over-long words safely if needed.
     */
    fun wrapTextLines(text: String, paint: Paint, maxWidth: Float): List<String> {
        val cleaned = text.trim().replace(Regex("\\s+"), " ")
        if (cleaned.isEmpty()) return listOf("")
        if (paint.measureText(cleaned) <= maxWidth) return listOf(cleaned)

        val result = mutableListOf<String>()
        val words = cleaned.split(" ")
        var currentLine = StringBuilder()

        fun flushCurrent() {
            if (currentLine.isNotEmpty()) {
                result.add(currentLine.toString())
                currentLine = StringBuilder()
            }
        }

        for (word in words) {
            val candidate = if (currentLine.isEmpty()) word else "$currentLine $word"
            if (paint.measureText(candidate) <= maxWidth) {
                currentLine = StringBuilder(candidate)
            } else {
                flushCurrent()
                if (paint.measureText(word) <= maxWidth) {
                    currentLine.append(word)
                } else {
                    // Break ultra-long unbroken token cleanly across lines without dropping characters
                    var start = 0
                    while (start < word.length) {
                        var end = start + 1
                        while (end <= word.length && paint.measureText(word.substring(start, end)) <= maxWidth) {
                            end++
                        }
                        val fitEnd = (end - 1).coerceAtLeast(start + 1)
                        val chunk = word.substring(start, fitEnd)
                        if (fitEnd < word.length) {
                            result.add(chunk)
                        } else {
                            currentLine.append(chunk)
                        }
                        start = fitEnd
                    }
                }
            }
        }
        flushCurrent()
        return result.ifEmpty { listOf(cleaned) }
    }

    private fun computeHeaderHeight(
        config: PosterRenderConfig,
        compact: Boolean,
        titleLineCount: Int,
        titleLineStep: Float,
        subtitleLineCount: Int,
        subtitleLineStep: Float
    ): Float {
        var h = (subtitleLineCount.coerceAtLeast(1) * subtitleLineStep) + 10f + 42f + 26f
        if (config.showLogo) h += (if (compact) 78f else 94f) + 18f
        if (config.showShopName) h += (titleLineCount.coerceAtLeast(1) * titleLineStep) + 4f
        return h
    }

    private fun computeFooterHeight(
        shopProfile: ShopProfile,
        config: PosterRenderConfig,
        outroLineCount: Int,
        addressLineCount: Int
    ): Float {
        var h = 74f + ((outroLineCount - 1).coerceAtLeast(0) * 36f)
        if (config.showContact) {
            if (shopProfile.whatsappNumber.isNotBlank()) h += 40f
            if (addressLineCount > 0) h += 34f + ((addressLineCount - 1) * 28f)
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
