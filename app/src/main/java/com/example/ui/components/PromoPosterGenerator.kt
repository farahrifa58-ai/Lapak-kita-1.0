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
import com.example.ui.viewmodel.LapakViewModel
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

data class PosterRenderConfig(
    val template: String,
    val introText: String,
    val outroText: String,
    val showLogo: Boolean,
    val showShopName: Boolean,
    val showContact: Boolean,
    val customBgPath: String?
)

data class PosterStyleSpec(
    val key: String,
    val label: String,
    val bgStart: Int,
    val bgEnd: Int,
    val cardBg: Int,
    val cardBorder: Int,
    val titleColor: Int,
    val subtitleColor: Int,
    val itemNameColor: Int,
    val priceColor: Int,
    val stockSafeBg: Int,
    val stockSafeText: Int,
    val stockLowBg: Int,
    val stockLowText: Int,
    val footerBg: Int,
    val footerText: Int,
    val isDark: Boolean
)

object PromoPosterStyles {
    val softEmerald = PosterStyleSpec(
        key = PosterTemplate.SOFT_EMERALD,
        label = "Soft Emerald",
        bgStart = 0xFF2D7A5E.toInt(),
        bgEnd = 0xFF194D3A.toInt(),
        cardBg = 0xFFFFFFFF.toInt(),
        cardBorder = 0xFFD9EAE2.toInt(),
        titleColor = 0xFFFFFFFF.toInt(),
        subtitleColor = 0xFFDFF4EA.toInt(),
        itemNameColor = 0xFF1A2421.toInt(),
        priceColor = 0xFF2D7A5E.toInt(),
        stockSafeBg = 0xFFE3F4EC.toInt(),
        stockSafeText = 0xFF1F6B4E.toInt(),
        stockLowBg = 0xFFFFEFE6.toInt(),
        stockLowText = 0xFFC24A16.toInt(),
        footerBg = 0x33FFFFFF,
        footerText = 0xFFFFFFFF.toInt(),
        isDark = false
    )

    val warmCream = PosterStyleSpec(
        key = PosterTemplate.WARM_CREAM,
        label = "Warm Cream Bakery",
        bgStart = 0xFFFDF5E6.toInt(),
        bgEnd = 0xFFF3DEC0.toInt(),
        cardBg = 0xFFFFFFFF.toInt(),
        cardBorder = 0xFFEAD3B1.toInt(),
        titleColor = 0xFF4A2E14.toInt(),
        subtitleColor = 0xFF7D532B.toInt(),
        itemNameColor = 0xFF2E1D0E.toInt(),
        priceColor = 0xFFB4690E.toInt(),
        stockSafeBg = 0xFFE8F5ED.toInt(),
        stockSafeText = 0xFF226B4E.toInt(),
        stockLowBg = 0xFFFFECE0.toInt(),
        stockLowText = 0xFFC54B1A.toInt(),
        footerBg = 0xFFFFFFFF.toInt(),
        footerText = 0xFF4A2E14.toInt(),
        isDark = false
    )

    val darkGold = PosterStyleSpec(
        key = PosterTemplate.DARK_GOLD,
        label = "Dark Luxury Gold",
        bgStart = 0xFF1B2420.toInt(),
        bgEnd = 0xFF0D1210.toInt(),
        cardBg = 0xFF19221F.toInt(),
        cardBorder = 0xFF3F3824.toInt(),
        titleColor = 0xFFE9C478.toInt(),
        subtitleColor = 0xFFD4DDD8.toInt(),
        itemNameColor = 0xFFF2F6F4.toInt(),
        priceColor = 0xFFE9C478.toInt(),
        stockSafeBg = 0xFF1B3A2E.toInt(),
        stockSafeText = 0xFF6CD6AD.toInt(),
        stockLowBg = 0xFF3F261A.toInt(),
        stockLowText = 0xFFFFA578.toInt(),
        footerBg = 0xFF212B27.toInt(),
        footerText = 0xFFE9C478.toInt(),
        isDark = true
    )

    val freshTerracotta = PosterStyleSpec(
        key = PosterTemplate.FRESH_TERRACOTTA,
        label = "Fresh Terracotta",
        bgStart = 0xFFC75D3B.toInt(),
        bgEnd = 0xFF8C3720.toInt(),
        cardBg = 0xFFFFFBF7.toInt(),
        cardBorder = 0xFFF1D4C6.toInt(),
        titleColor = 0xFFFFFFFF.toInt(),
        subtitleColor = 0xFFFFE7DC.toInt(),
        itemNameColor = 0xFF2D1A14.toInt(),
        priceColor = 0xFFB84523.toInt(),
        stockSafeBg = 0xFFE5F4EC.toInt(),
        stockSafeText = 0xFF1F6B4E.toInt(),
        stockLowBg = 0xFFFFEAE2.toInt(),
        stockLowText = 0xFFC43B1B.toInt(),
        footerBg = 0x33FFFFFF,
        footerText = 0xFFFFFFFF.toInt(),
        isDark = false
    )

    val allTemplates = listOf(softEmerald, warmCream, darkGold, freshTerracotta)

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

    /**
     * Generates a crisp 1080px-wide customer promo poster PNG bitmap.
     * STRICT PRIVACY: Only shows items with currentStock > 0 and NEVER includes seller/kitchen names.
     * AUTO LAYOUT:
     * - <= 8 items: spacious 1-column vertical poster (min 4:5 ratio, 1080x1350+)
     * - > 8 items: clean 2-column side-by-side layout inside a vertical poster (4:5 to 9:16)
     *   so 15-24+ items remain super crisp and legible on mobile screens without tiny text.
     */
    fun generatePosterBitmap(
        context: Context,
        shopProfile: ShopProfile,
        readyProducts: List<ProductDetailItem>,
        config: PosterRenderConfig
    ): Bitmap {
        val items = readyProducts.filter { it.currentStock > 0 && it.product.isActive }
        val useTwoColumns = items.size > 8
        val style = PromoPosterStyles.resolve(config.template)
        val width = 1080
        val pad = if (useTwoColumns) 44f else 56f

        val headerHeight = buildHeaderHeight(config, compact = useTwoColumns)
        val rowHeight = if (useTwoColumns) {
            when {
                items.size <= 16 -> 114f
                items.size <= 22 -> 104f
                else -> 96f
            }
        } else {
            112f
        }
        val numRows = if (items.isEmpty()) 0 else if (useTwoColumns) (items.size + 1) / 2 else items.size
        val listBodyHeight = if (items.isEmpty()) 220f else (numRows * rowHeight) + 40f
        val footerHeight = if (config.showContact) 208f else 120f
        val rawHeight = (pad + headerHeight + listBodyHeight + footerHeight + pad).toInt()
        // Enforce clean vertical 4:5 (1080x1350) minimum height; naturally expands toward 9:16 (1080x1920) for 15-24+ items
        val height = rawHeight.coerceAtLeast(1350)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // 1. Draw Background (Template Gradient or Custom Gallery Image)
        val customBgBitmap = if (config.template == PosterTemplate.CUSTOM_IMAGE && !config.customBgPath.isNullOrBlank()) {
            loadLocalBitmap(context, config.customBgPath, maxDim = 1280)
        } else null

        if (customBgBitmap != null) {
            drawCenterCropBitmap(canvas, customBgBitmap, width, height)
            // Dark scrim for guaranteed text legibility
            val scrim = Paint().apply {
                shader = LinearGradient(
                    0f, 0f, 0f, height.toFloat(),
                    0xB810241D.toInt(), 0xD40B1713.toInt(),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), scrim)
        } else {
            val bgPaint = Paint().apply {
                shader = LinearGradient(
                    0f, 0f, width.toFloat() * 0.4f, height.toFloat(),
                    style.bgStart, style.bgEnd,
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

            // Decorative subtle soft circles
            val decoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (config.template == PosterTemplate.WARM_CREAM) 0x18B4690E else 0x16FFFFFF
            }
            canvas.drawCircle(width - 90f, 110f, 210f, decoPaint)
            canvas.drawCircle(70f, height - 120f, 180f, decoPaint)
        }

        val effectiveTitleColor = if (customBgBitmap != null) Color.WHITE else style.titleColor
        val effectiveSubColor = if (customBgBitmap != null) 0xFFE6F4EE.toInt() else style.subtitleColor

        // Distribute any extra vertical space gracefully so 4:5 posters look balanced
        val extraSpace = (height - rawHeight).coerceAtLeast(0).toFloat()
        var cursorY = pad + 10f + (extraSpace * 0.35f)
        val centerX = width / 2f

        // 2. Draw Header Section
        if (config.showLogo) {
            val badgeSize = if (useTwoColumns) 96f else 116f
            val badgeRect = RectF(
                centerX - badgeSize / 2f,
                cursorY,
                centerX + badgeSize / 2f,
                cursorY + badgeSize
            )
            val badgeBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (config.template == PosterTemplate.WARM_CREAM && customBgBitmap == null) {
                    0xFFFFFFFF.toInt()
                } else {
                    0x2AFFFFFF
                }
            }
            canvas.drawRoundRect(badgeRect, 28f, 28f, badgeBgPaint)

            val customLogo = if (shopProfile.shopIconKey == ShopIconKey.CUSTOM_PHOTO && !shopProfile.logoUri.isNullOrBlank()) {
                loadLocalBitmap(context, shopProfile.logoUri, maxDim = 256)
            } else null

            if (customLogo != null) {
                val save = canvas.save()
                val clipPath = Path().apply { addRoundRect(badgeRect, 28f, 28f, Path.Direction.CW) }
                canvas.clipPath(clipPath)
                drawCenterCropBitmap(canvas, customLogo, badgeRect)
                canvas.restoreToCount(save)
            } else {
                val iconEmoji = when (shopProfile.shopIconKey) {
                    ShopIconKey.BAKERY -> "🥐"
                    ShopIconKey.CAFE -> "☕"
                    ShopIconKey.BASKET -> "🧺"
                    ShopIconKey.RESTAURANT -> "🍽️"
                    else -> "🏪"
                }
                val emojiPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    textSize = if (useTwoColumns) 48f else 56f
                    textAlign = Paint.Align.CENTER
                }
                canvas.drawText(iconEmoji, centerX, cursorY + badgeSize * 0.67f, emojiPaint)
            }
            cursorY += badgeSize + (if (useTwoColumns) 18f else 24f)
        }

        if (config.showShopName) {
            val shopPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = effectiveTitleColor
                textSize = if (useTwoColumns) 50f else 56f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText(shopProfile.shopName.take(28), centerX, cursorY + 44f, shopPaint)
            cursorY += if (useTwoColumns) 58f else 66f
        }

        // Intro Promo Tagline
        val introPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = effectiveSubColor
            textSize = if (useTwoColumns) 29f else 31f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(config.introText.take(54), centerX, cursorY + 32f, introPaint)
        cursorY += if (useTwoColumns) 44f else 52f

        val sdf = SimpleDateFormat("EEEE, dd MMM yyyy • HH:mm", Locale("id", "ID"))
        val datePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = effectiveSubColor
            alpha = 215
            textSize = 23f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("Update: ${sdf.format(Date())} WIB", centerX, cursorY + 22f, datePaint)
        cursorY += if (useTwoColumns) 42f else 54f

        // 3. Draw Menu Card Container (STRICTLY NO SELLER NAME!)
        val cardLeft = pad
        val cardRight = width - pad
        val cardTop = cursorY
        val cardBottom = cardTop + listBodyHeight
        val cardRect = RectF(cardLeft, cardTop, cardRight, cardBottom)

        val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = style.cardBg }
        val cardBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = style.cardBorder
            this.style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        canvas.drawRoundRect(cardRect, 34f, 34f, cardPaint)
        canvas.drawRoundRect(cardRect, 34f, 34f, cardBorderPaint)

        val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = style.cardBorder
            strokeWidth = 2f
        }

        if (items.isEmpty()) {
            val emptyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = style.itemNameColor
                textSize = 32f
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("Semua stok hari ini sudah habis terjual", centerX, cardTop + 120f, emptyPaint)
        } else if (useTwoColumns) {
            // === 2-COLUMN SIDE-BY-SIDE LAYOUT (> 8 items) ===
            val cardInnerPadX = 24f
            val colGap = 28f
            val usableW = (cardRight - cardLeft) - (cardInnerPadX * 2f)
            val colW = (usableW - colGap) / 2f
            val leftColStart = cardLeft + cardInnerPadX
            val rightColStart = leftColStart + colW + colGap

            // Subtle vertical center divider between column 1 and column 2
            canvas.drawLine(
                centerX,
                cardTop + 22f,
                centerX,
                cardBottom - 22f,
                dividerPaint
            )

            val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = style.itemNameColor
                textSize = if (items.size <= 18) 27f else 25f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.LEFT
            }
            val pricePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = style.priceColor
                textSize = if (items.size <= 18) 25f else 23f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.LEFT
            }
            val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 20f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }

            var rowY = cardTop + 20f
            for (r in 0 until numRows) {
                val leftIdx = r * 2
                val rightIdx = leftIdx + 1

                drawTwoColPosterCell(
                    canvas = canvas,
                    detail = items[leftIdx],
                    cellLeft = leftColStart,
                    cellTop = rowY,
                    cellWidth = colW,
                    cellHeight = rowHeight,
                    style = style,
                    namePaint = namePaint,
                    pricePaint = pricePaint,
                    badgeTextPaint = badgeTextPaint
                )

                if (rightIdx < items.size) {
                    drawTwoColPosterCell(
                        canvas = canvas,
                        detail = items[rightIdx],
                        cellLeft = rightColStart,
                        cellTop = rowY,
                        cellWidth = colW,
                        cellHeight = rowHeight,
                        style = style,
                        namePaint = namePaint,
                        pricePaint = pricePaint,
                        badgeTextPaint = badgeTextPaint
                    )
                }

                if (r < numRows - 1) {
                    canvas.drawLine(leftColStart, rowY + rowHeight, leftColStart + colW, rowY + rowHeight, dividerPaint)
                    if (rightIdx < items.size) {
                        canvas.drawLine(rightColStart, rowY + rowHeight, rightColStart + colW, rowY + rowHeight, dividerPaint)
                    }
                }
                rowY += rowHeight
            }
        } else {
            // === SPACIOUS 1-COLUMN LAYOUT (<= 8 items) ===
            val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = style.itemNameColor
                textSize = 33f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.LEFT
            }
            val pricePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = style.priceColor
                textSize = 31f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.LEFT
            }

            var itemY = cardTop + 22f
            items.forEachIndexed { idx, detail ->
                val rowCenterY = itemY + rowHeight / 2f
                val textLeft = cardLeft + 36f

                // Product Name ONLY (never seller name!)
                val cleanName = truncateText(detail.product.name, namePaint, 580f)
                canvas.drawText(cleanName, textLeft, itemY + 46f, namePaint)

                // Selling Price + Unit
                val priceLabel = "${LapakViewModel.formatRupiah(detail.product.sellingPrice)} / ${detail.product.unit}"
                canvas.drawText(priceLabel, textLeft, itemY + 86f, pricePaint)

                // Remaining Stock Pill Badge on Right
                val isLow = detail.currentStock <= 3
                val stockText = "Sisa ${detail.currentStock} ${detail.product.unit}"
                val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = if (isLow) style.stockLowText else style.stockSafeText
                    textSize = 25f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    textAlign = Paint.Align.CENTER
                }
                val textWidth = badgeTextPaint.measureText(stockText)
                val pillW = (textWidth + 46f).coerceAtLeast(170f)
                val pillH = 54f
                val pillRight = cardRight - 36f
                val pillLeft = pillRight - pillW
                val pillTop = rowCenterY - pillH / 2f
                val pillRect = RectF(pillLeft, pillTop, pillRight, pillTop + pillH)

                val pillBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = if (isLow) style.stockLowBg else style.stockSafeBg
                }
                canvas.drawRoundRect(pillRect, 27f, 27f, pillBgPaint)
                canvas.drawText(stockText, pillRect.centerX(), pillRect.centerY() + 9f, badgeTextPaint)

                if (idx < items.lastIndex) {
                    canvas.drawLine(cardLeft + 36f, itemY + rowHeight, cardRight - 36f, itemY + rowHeight, dividerPaint)
                }
                itemY += rowHeight
            }
        }

        // 4. Footer Outro & Optional Contact Info
        val footerTop = cardBottom + 28f
        val footerRect = RectF(cardLeft, footerTop, cardRight, footerTop + footerHeight - 16f)
        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (customBgBitmap != null) 0x33FFFFFF else style.footerBg
        }
        canvas.drawRoundRect(footerRect, 26f, 26f, footerPaint)

        val footerMainTextColor = if (customBgBitmap != null) Color.WHITE else style.footerText
        val outroPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = footerMainTextColor
            textSize = 29f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        var footerCursorY = footerTop + 54f
        canvas.drawText(config.outroText.take(56), centerX, footerCursorY, outroPaint)

        if (config.showContact) {
            val contactPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = footerMainTextColor
                alpha = 230
                textSize = 25f
                textAlign = Paint.Align.CENTER
            }
            if (shopProfile.whatsappNumber.isNotBlank()) {
                footerCursorY += 46f
                canvas.drawText("Order / WhatsApp: ${shopProfile.whatsappNumber}", centerX, footerCursorY, contactPaint)
            }
            if (shopProfile.address.isNotBlank()) {
                footerCursorY += 38f
                val addrLine = truncateText(shopProfile.address, contactPaint, width - pad * 2 - 64f)
                canvas.drawText(addrLine, centerX, footerCursorY, contactPaint)
            }
        }

        return bitmap
    }

    private fun drawTwoColPosterCell(
        canvas: Canvas,
        detail: ProductDetailItem,
        cellLeft: Float,
        cellTop: Float,
        cellWidth: Float,
        cellHeight: Float,
        style: PosterStyleSpec,
        namePaint: Paint,
        pricePaint: Paint,
        badgeTextPaint: Paint
    ) {
        // Line 1: Product Name ONLY (never seller name!)
        val cleanName = truncateText(detail.product.name, namePaint, cellWidth - 6f)
        canvas.drawText(cleanName, cellLeft + 4f, cellTop + cellHeight * 0.40f, namePaint)

        // Line 2: Selling Price (left) + Compact Stock Badge (right)
        val isLow = detail.currentStock <= 3
        val stockText = "Sisa ${detail.currentStock}"
        badgeTextPaint.color = if (isLow) style.stockLowText else style.stockSafeText
        val pillW = (badgeTextPaint.measureText(stockText) + 28f).coerceAtLeast(94f)
        val pillH = 38f
        val pillRight = cellLeft + cellWidth - 2f
        val pillLeft = pillRight - pillW
        val pillTop = cellTop + cellHeight * 0.54f
        val pillRect = RectF(pillLeft, pillTop, pillRight, pillTop + pillH)

        val pillBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isLow) style.stockLowBg else style.stockSafeBg
        }
        canvas.drawRoundRect(pillRect, 19f, 19f, pillBgPaint)
        canvas.drawText(stockText, pillRect.centerX(), pillRect.centerY() + 7f, badgeTextPaint)

        val priceLabel = LapakViewModel.formatRupiah(detail.product.sellingPrice)
        val maxPriceW = (pillLeft - cellLeft - 14f).coerceAtLeast(100f)
        val cleanPrice = truncateText(priceLabel, pricePaint, maxPriceW)
        canvas.drawText(cleanPrice, cellLeft + 4f, cellTop + cellHeight * 0.80f, pricePaint)
    }

    private fun buildHeaderHeight(config: PosterRenderConfig, compact: Boolean = false): Float {
        var h = if (compact) 88f else 110f
        if (config.showLogo) h += if (compact) 114f else 140f
        if (config.showShopName) h += if (compact) 58f else 68f
        return h
    }

    private fun truncateText(text: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        var end = text.length
        while (end > 3 && paint.measureText(text.substring(0, end) + "...") > maxWidth) {
            end--
        }
        return text.substring(0, end.coerceAtLeast(1)) + "..."
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
                // Fallback to general share chooser if WhatsApp is not installed
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
            // Ignored safely; caller shows toast on failure if needed
        }
    }
}
