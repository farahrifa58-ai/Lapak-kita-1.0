package com.example.ui.components

import android.graphics.BlurMaskFilter
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.LocalLapakColors

/**
 * Tactile Soft UI / Neumorphism extruded (raised) modifier using dual directional shadows:
 * - Top-left soft highlight (`neuShadowLight`)
 * - Bottom-right soft ambient shadow (`neuShadowDark`)
 * Eliminates harsh 1dp borders for a calm, ergonomic, physical feel.
 */
fun Modifier.neuExtruded(
    cornerRadius: Dp = 16.dp,
    shadowOffset: Dp = 5.dp,
    blurRadius: Dp = 10.dp,
    surfaceColor: Color? = null,
    lightShadowColor: Color? = null,
    darkShadowColor: Color? = null
): Modifier = composed {
    val lapakColors = LocalLapakColors.current
    val fill = surfaceColor ?: lapakColors.neuSurface
    val lightShadow = lightShadowColor ?: lapakColors.neuShadowLight
    val darkShadow = darkShadowColor ?: lapakColors.neuShadowDark

    this.drawBehind {
        val radiusPx = cornerRadius.toPx()
        val offsetPx = shadowOffset.toPx()
        val blurPx = blurRadius.toPx().coerceAtLeast(1f)

        drawIntoCanvas { canvas ->
            val darkPaint = Paint().apply {
                isAntiAlias = true
            }
            val darkFrameworkPaint = darkPaint.asFrameworkPaint().apply {
                color = darkShadow.toArgb()
                maskFilter = BlurMaskFilter(blurPx, BlurMaskFilter.Blur.NORMAL)
            }
            canvas.nativeCanvas.drawRoundRect(
                offsetPx,
                offsetPx,
                size.width + offsetPx,
                size.height + offsetPx,
                radiusPx,
                radiusPx,
                darkFrameworkPaint
            )

            val lightPaint = Paint().apply {
                isAntiAlias = true
            }
            val lightFrameworkPaint = lightPaint.asFrameworkPaint().apply {
                color = lightShadow.toArgb()
                maskFilter = BlurMaskFilter(blurPx, BlurMaskFilter.Blur.NORMAL)
            }
            canvas.nativeCanvas.drawRoundRect(
                -offsetPx,
                -offsetPx,
                size.width - offsetPx,
                size.height - offsetPx,
                radiusPx,
                radiusPx,
                lightFrameworkPaint
            )

            val surfacePaint = Paint().apply {
                isAntiAlias = true
                color = fill
            }
            canvas.nativeCanvas.drawRoundRect(
                0f,
                0f,
                size.width,
                size.height,
                radiusPx,
                radiusPx,
                surfacePaint.asFrameworkPaint()
            )
        }
    }
}

/**
 * Tactile Soft UI / Neumorphism inset (carved/recessed) modifier for input fields,
 * inactive filter tabs, search bars, and pressed surfaces.
 */
fun Modifier.neuInset(
    cornerRadius: Dp = 14.dp,
    shadowOffset: Dp = 3.dp,
    blurRadius: Dp = 7.dp,
    surfaceColor: Color? = null,
    lightShadowColor: Color? = null,
    darkShadowColor: Color? = null
): Modifier = composed {
    val lapakColors = LocalLapakColors.current
    val fill = surfaceColor ?: lapakColors.neuSurface
    val lightShadow = lightShadowColor ?: lapakColors.neuShadowLight
    val darkShadow = darkShadowColor ?: lapakColors.neuShadowDark

    this.drawWithContent {
        val radiusPx = cornerRadius.toPx()
        val offsetPx = shadowOffset.toPx()
        val blurPx = blurRadius.toPx().coerceAtLeast(1f)
        val strokePx = (offsetPx * 1.6f).coerceAtLeast(2f)

        val clipShape = Path().apply {
            addRoundRect(
                RoundRect(
                    left = 0f,
                    top = 0f,
                    right = size.width,
                    bottom = size.height,
                    cornerRadius = CornerRadius(radiusPx, radiusPx)
                )
            )
        }

        drawRoundRect(
            color = fill,
            cornerRadius = CornerRadius(radiusPx, radiusPx)
        )

        clipPath(clipShape) {
            drawIntoCanvas { canvas ->
                val darkPaint = android.graphics.Paint().apply {
                    isAntiAlias = true
                    style = android.graphics.Paint.Style.STROKE
                    strokeWidth = strokePx
                    color = darkShadow.toArgb()
                    maskFilter = BlurMaskFilter(blurPx, BlurMaskFilter.Blur.NORMAL)
                }
                canvas.nativeCanvas.drawRoundRect(
                    offsetPx * 0.4f,
                    offsetPx * 0.4f,
                    size.width + offsetPx * 1.4f,
                    size.height + offsetPx * 1.4f,
                    radiusPx,
                    radiusPx,
                    darkPaint
                )

                val lightPaint = android.graphics.Paint().apply {
                    isAntiAlias = true
                    style = android.graphics.Paint.Style.STROKE
                    strokeWidth = strokePx
                    color = lightShadow.toArgb()
                    maskFilter = BlurMaskFilter(blurPx, BlurMaskFilter.Blur.NORMAL)
                }
                canvas.nativeCanvas.drawRoundRect(
                    -offsetPx * 1.4f,
                    -offsetPx * 1.4f,
                    size.width - offsetPx * 0.4f,
                    size.height - offsetPx * 0.4f,
                    radiusPx,
                    radiusPx,
                    lightPaint
                )
            }
        }

        drawContent()
    }
}

/**
 * Standard Neumorphic raised card container (rounded-2xl / 16.dp, monochromatic base surface, dual soft shadows).
 */
@Composable
fun NeuCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 16.dp,
    shadowOffset: Dp = 5.dp,
    blurRadius: Dp = 10.dp,
    surfaceColor: Color? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)
    val baseModifier = modifier
        .neuExtruded(
            cornerRadius = cornerRadius,
            shadowOffset = shadowOffset,
            blurRadius = blurRadius,
            surfaceColor = surfaceColor
        )
        .clip(shape)
        .let { m ->
            if (onClick != null) m.clickable(onClick = onClick) else m
        }

    Column(
        modifier = baseModifier,
        content = content
    )
}

/**
 * Circular raised Neumorphic icon badge with subtle colored tint halo.
 */
@Composable
fun NeuIconBadge(
    accentColor: Color,
    size: Dp = 36.dp,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val lapakColors = LocalLapakColors.current
    Box(
        modifier = modifier
            .size(size)
            .neuExtruded(
                cornerRadius = size / 2,
                shadowOffset = 3.dp,
                blurRadius = 6.dp,
                surfaceColor = lapakColors.neuSurface
            )
            .clip(CircleShape)
            .background(accentColor.copy(alpha = 0.11f)),
        contentAlignment = Alignment.Center,
        content = content
    )
}

/**
 * Tactile Neumorphic filter/timeframe chip (extruded primary when selected, soft inset when inactive).
 */
@Composable
fun NeuFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val lapakColors = LocalLapakColors.current
    val shape = RoundedCornerShape(14.dp)
    val chipModifier = if (selected) {
        modifier
            .neuExtruded(
                cornerRadius = 14.dp,
                shadowOffset = 3.dp,
                blurRadius = 7.dp,
                surfaceColor = MaterialTheme.colorScheme.primary
            )
            .clip(shape)
    } else {
        modifier
            .neuInset(
                cornerRadius = 14.dp,
                shadowOffset = 2.dp,
                blurRadius = 5.dp,
                surfaceColor = lapakColors.neuSurface
            )
            .clip(shape)
    }

    Box(
        modifier = chipModifier
            .defaultMinSize(minHeight = 38.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}
