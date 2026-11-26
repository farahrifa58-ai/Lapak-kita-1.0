package com.example.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Minimalist Lucide-style modern vector icons with crisp 1.85dp rounded strokes.
 * Replaces chunky legacy icons across Bottom Navigation, Dashboard cards, and quick actions.
 */
object LucideIcons {

    private val strokeColor = SolidColor(Color.Black)
    private const val STROKE = 1.85f

    /** Dashboard: LayoutGrid */
    val LayoutGrid: ImageVector by lazy {
        ImageVector.Builder(
            name = "LucideLayoutGrid",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            // Top-left box (3,3,7,7) rx=1.5
            path(
                stroke = strokeColor,
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(4.5f, 3f); lineTo(8.5f, 3f); quadTo(10f, 3f, 10f, 4.5f)
                lineTo(10f, 8.5f); quadTo(10f, 10f, 8.5f, 10f)
                lineTo(4.5f, 10f); quadTo(3f, 10f, 3f, 8.5f)
                lineTo(3f, 4.5f); quadTo(3f, 3f, 4.5f, 3f); close()
            }
            // Top-right box (14,3,7,7)
            path(
                stroke = strokeColor,
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(15.5f, 3f); lineTo(19.5f, 3f); quadTo(21f, 3f, 21f, 4.5f)
                lineTo(21f, 8.5f); quadTo(21f, 10f, 19.5f, 10f)
                lineTo(15.5f, 10f); quadTo(14f, 10f, 14f, 8.5f)
                lineTo(14f, 4.5f); quadTo(14f, 3f, 15.5f, 3f); close()
            }
            // Bottom-right box (14,14,7,7)
            path(
                stroke = strokeColor,
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(15.5f, 14f); lineTo(19.5f, 14f); quadTo(21f, 14f, 21f, 15.5f)
                lineTo(21f, 19.5f); quadTo(21f, 21f, 19.5f, 21f)
                lineTo(15.5f, 21f); quadTo(14f, 21f, 14f, 19.5f)
                lineTo(14f, 15.5f); quadTo(14f, 14f, 15.5f, 14f); close()
            }
            // Bottom-left box (3,14,7,7)
            path(
                stroke = strokeColor,
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(4.5f, 14f); lineTo(8.5f, 14f); quadTo(10f, 14f, 10f, 15.5f)
                lineTo(10f, 19.5f); quadTo(10f, 21f, 8.5f, 21f)
                lineTo(4.5f, 21f); quadTo(3f, 21f, 3f, 19.5f)
                lineTo(3f, 15.5f); quadTo(3f, 14f, 4.5f, 14f); close()
            }
        }.build()
    }

    /** Jualan: Store */
    val Store: ImageVector by lazy {
        ImageVector.Builder(
            name = "LucideStore",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = strokeColor,
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                // Shop awning roof
                moveTo(3f, 9f)
                lineTo(5f, 4f)
                lineTo(19f, 4f)
                lineTo(21f, 9f)
                // Scallop bottom edge
                moveTo(3f, 9f)
                curveTo(3f, 10.65f, 4.35f, 12f, 6f, 12f)
                curveTo(7.65f, 12f, 9f, 10.65f, 9f, 9f)
                curveTo(9f, 10.65f, 10.35f, 12f, 12f, 12f)
                curveTo(13.65f, 12f, 15f, 10.65f, 15f, 9f)
                curveTo(15f, 10.65f, 16.35f, 12f, 18f, 12f)
                curveTo(19.65f, 12f, 21f, 10.65f, 21f, 9f)
                // Walls & floor
                moveTo(4f, 11.5f)
                lineTo(4f, 19f)
                quadTo(4f, 20.5f, 5.5f, 20.5f)
                lineTo(18.5f, 20.5f)
                quadTo(20f, 20.5f, 20f, 19f)
                lineTo(20f, 11.5f)
                // Door
                moveTo(9.5f, 20.5f)
                lineTo(9.5f, 15.5f)
                quadTo(9.5f, 14.5f, 10.5f, 14.5f)
                lineTo(13.5f, 14.5f)
                quadTo(14.5f, 14.5f, 14.5f, 15.5f)
                lineTo(14.5f, 20.5f)
            }
        }.build()
    }

    /** Kasir: Calculator */
    val Calculator: ImageVector by lazy {
        ImageVector.Builder(
            name = "LucideCalculator",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = strokeColor,
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                // Outer body
                moveTo(6f, 2.5f); lineTo(18f, 2.5f); quadTo(20f, 2.5f, 20f, 4.5f)
                lineTo(20f, 19.5f); quadTo(20f, 21.5f, 18f, 21.5f)
                lineTo(6f, 21.5f); quadTo(4f, 21.5f, 4f, 19.5f)
                lineTo(4f, 4.5f); quadTo(4f, 2.5f, 6f, 2.5f); close()
                // Display screen
                moveTo(8f, 6.5f); lineTo(16f, 6.5f)
                lineTo(16f, 9.5f); lineTo(8f, 9.5f); close()
                // Keypad dots/lines
                moveTo(8f, 13.5f); lineTo(8.1f, 13.5f)
                moveTo(12f, 13.5f); lineTo(12.1f, 13.5f)
                moveTo(16f, 13.5f); lineTo(16f, 17.5f)
                moveTo(8f, 17.5f); lineTo(8.1f, 17.5f)
                moveTo(12f, 17.5f); lineTo(12.1f, 17.5f)
            }
        }.build()
    }

    /** Riwayat: ReceiptText */
    val ReceiptText: ImageVector by lazy {
        ImageVector.Builder(
            name = "LucideReceiptText",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = strokeColor,
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(4f, 2.5f)
                lineTo(4f, 21.5f)
                lineTo(6.5f, 19.5f)
                lineTo(9.2f, 21.5f)
                lineTo(12f, 19.5f)
                lineTo(14.8f, 21.5f)
                lineTo(17.5f, 19.5f)
                lineTo(20f, 21.5f)
                lineTo(20f, 2.5f)
                lineTo(17.5f, 4.3f)
                lineTo(14.8f, 2.5f)
                lineTo(12f, 4.3f)
                lineTo(9.2f, 2.5f)
                lineTo(6.5f, 4.3f)
                close()
                // Text lines
                moveTo(8f, 9f); lineTo(16f, 9f)
                moveTo(8f, 13f); lineTo(16f, 13f)
                moveTo(8f, 16.5f); lineTo(12.5f, 16.5f)
            }
        }.build()
    }

    /** Penyetor: UsersRound */
    val UsersRound: ImageVector by lazy {
        ImageVector.Builder(
            name = "LucideUsersRound",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = strokeColor,
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                // Primary user head
                moveTo(10f, 4f)
                arcTo(3.5f, 3.5f, 0f, true, true, 9.99f, 4f)
                // Primary user shoulders
                moveTo(3f, 20f)
                curveTo(3f, 16.2f, 6.1f, 14f, 10f, 14f)
                curveTo(13.9f, 14f, 17f, 16.2f, 17f, 20f)
                // Secondary user head arc
                moveTo(16.2f, 5.2f)
                curveTo(17.9f, 5.6f, 19f, 7f, 19f, 8.8f)
                curveTo(19f, 10.6f, 17.9f, 12f, 16.2f, 12.4f)
                // Secondary user shoulder
                moveTo(18f, 14.5f)
                curveTo(20f, 15.2f, 21.5f, 17f, 21.5f, 20f)
            }
        }.build()
    }

    /** Total Omzet: Wallet */
    val Wallet: ImageVector by lazy {
        ImageVector.Builder(
            name = "LucideWallet",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = strokeColor,
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                // Top flap
                moveTo(19f, 7f); lineTo(19f, 5f); quadTo(19f, 3.5f, 17.5f, 3.5f)
                lineTo(5.5f, 3.5f); quadTo(3.5f, 3.5f, 3.5f, 5.5f)
                lineTo(3.5f, 18.5f); quadTo(3.5f, 20.5f, 5.5f, 20.5f)
                lineTo(18.5f, 20.5f); quadTo(20.5f, 20.5f, 20.5f, 18.5f)
                lineTo(20.5f, 9f); quadTo(20.5f, 7f, 18.5f, 7f)
                lineTo(5.5f, 7f)
                // Clasp pocket
                moveTo(20.5f, 12f); lineTo(16.5f, 12f); quadTo(15f, 12f, 15f, 13.5f)
                quadTo(15f, 15f, 16.5f, 15f); lineTo(20.5f, 15f)
            }
        }.build()
    }

    /** Untung Lapak: TrendingUp */
    val TrendingUp: ImageVector by lazy {
        ImageVector.Builder(
            name = "LucideTrendingUp",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = strokeColor,
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(22f, 7f); lineTo(13.5f, 15.5f); lineTo(8.5f, 10.5f); lineTo(2f, 17f)
                moveTo(16f, 7f); lineTo(22f, 7f); lineTo(22f, 13f)
            }
        }.build()
    }

    /** Setoran ke Penjual: HandCoins */
    val HandCoins: ImageVector by lazy {
        ImageVector.Builder(
            name = "LucideHandCoins",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = strokeColor,
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                // Coin circle
                moveTo(15.5f, 4f)
                arcTo(3f, 3f, 0f, true, true, 15.49f, 4f)
                // Second smaller coin arc
                moveTo(9.5f, 7.5f)
                arcTo(2.2f, 2.2f, 0f, true, true, 9.49f, 7.5f)
                // Hand holding palm
                moveTo(3f, 16f)
                lineTo(7f, 14f)
                lineTo(13f, 14f)
                quadTo(14.5f, 14f, 14.5f, 15.5f)
                quadTo(14.5f, 17f, 13f, 17f)
                lineTo(10f, 17f)
                moveTo(14.5f, 15.5f)
                lineTo(18.5f, 14f)
                quadTo(20.5f, 13.5f, 21f, 15f)
                quadTo(21.3f, 16.2f, 19.5f, 17.2f)
                lineTo(13.5f, 20f)
                quadTo(11.5f, 20.8f, 9.2f, 20.2f)
                lineTo(3f, 18.5f)
            }
        }.build()
    }

    /** Biaya Operasional: Receipt */
    val Receipt: ImageVector by lazy {
        ImageVector.Builder(
            name = "LucideReceipt",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = strokeColor,
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(5f, 3f); lineTo(5f, 21f); lineTo(7.5f, 19.3f); lineTo(10f, 21f)
                lineTo(12f, 19.3f); lineTo(14f, 21f); lineTo(16.5f, 19.3f); lineTo(19f, 21f)
                lineTo(19f, 3f); lineTo(16.5f, 4.7f); lineTo(14f, 3f); lineTo(12f, 4.7f)
                lineTo(10f, 3f); lineTo(7.5f, 4.7f); close()
                // Currency / line symbol
                moveTo(9f, 10f); lineTo(15f, 10f)
                moveTo(9f, 14f); lineTo(13.5f, 14f)
            }
        }.build()
    }

    /** Terima Titipan: PlusCircle (single crisp plus inside circle — eliminates "+ +") */
    val PlusCircle: ImageVector by lazy {
        ImageVector.Builder(
            name = "LucidePlusCircle",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = strokeColor,
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                pathFillType = PathFillType.NonZero
            ) {
                moveTo(12f, 2.5f)
                arcTo(9.5f, 9.5f, 0f, true, true, 11.99f, 2.5f)
                moveTo(12f, 8f); lineTo(12f, 16f)
                moveTo(8f, 12f); lineTo(16f, 12f)
            }
        }.build()
    }
}
