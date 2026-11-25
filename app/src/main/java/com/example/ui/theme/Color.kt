package com.example.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// =========================================================================
// "Soft Emerald & Warm Neutral" Palette — Gentle on the eyes, Light & Dark
// =========================================================================

// Light Mode Core
val BackgroundLight = Color(0xFFF6F8F7)
val SurfaceLight = Color(0xFFFFFFFF)
val SurfaceVariantLight = Color(0xFFEDF2EF)
val CardBorderLight = Color(0xFFE5ECE8)
val AccentLight = Color(0xFF2D7A5E)
val AccentContainerLight = Color(0xFFE1F2EB)
val OnAccentContainerLight = Color(0xFF164A37)
val TextPrimaryLight = Color(0xFF1A2421)
val TextSecondaryLight = Color(0xFF5C6B66)

// Dark Mode Core
val BackgroundDark = Color(0xFF0F1412)
val SurfaceDark = Color(0xFF18201D)
val SurfaceVariantDark = Color(0xFF202B27)
val CardBorderDark = Color(0xFF26332E)
val AccentDark = Color(0xFF4DB68E)
val AccentContainerDark = Color(0xFF1B3B2F)
val OnAccentContainerDark = Color(0xFFC5F0DE)
val TextPrimaryDark = Color(0xFFEBF2EF)
val TextSecondaryDark = Color(0xFF94A6A0)

// Backward-compatible named tokens (pointing to Soft Emerald)
val EmeraldDark = Color(0xFF1C5641)
val EmeraldPrimary = AccentLight
val EmeraldLight = Color(0xFF4DB68E)
val EmeraldContainer = AccentContainerLight
val OnEmeraldContainer = OnAccentContainerLight

val TealSecondary = Color(0xFF2E7D87)
val TealContainer = Color(0xFFE1F3F5)
val OnTealContainer = Color(0xFF144B52)

val AmberTertiary = Color(0xFFB46918)
val AmberContainer = Color(0xFFFEF3DF)
val OnAmberContainer = Color(0xFF754008)

// Status Colors (Soft semantic tones)
val StatusActive = Color(0xFF2D7A5E)
val StatusLeave = Color(0xFFD97706)
val StatusInactive = Color(0xFF6B7873)
val StatusActiveContainer = Color(0xFFE1F2EB)
val StatusLeaveContainer = Color(0xFFFEF3DF)
val StatusInactiveContainer = Color(0xFFECEFED)

@Immutable
data class LapakExtendedColors(
    val cardBorder: Color,
    // 4 Dashboard Summary Pastel Cards
    val omzetCardBg: Color,
    val omzetCardBorder: Color,
    val omzetCardAccent: Color,
    val profitCardBg: Color,
    val profitCardBorder: Color,
    val profitCardAccent: Color,
    val payoutCardBg: Color,
    val payoutCardBorder: Color,
    val payoutCardAccent: Color,
    val expenseCardBg: Color,
    val expenseCardBorder: Color,
    val expenseCardAccent: Color,
    // Stock Badges (Safe > 3, Low 1..3, Empty 0)
    val stockSafeBg: Color,
    val stockSafeText: Color,
    val stockLowBg: Color,
    val stockLowText: Color,
    val stockEmptyBg: Color,
    val stockEmptyText: Color,
    // Status Badges
    val statusActiveBg: Color,
    val statusActiveText: Color,
    val statusLeaveBg: Color,
    val statusLeaveText: Color,
    val statusInactiveBg: Color,
    val statusInactiveText: Color,
    // Soft Warning / Danger Alerts
    val warningBg: Color,
    val warningBorder: Color,
    val warningText: Color,
    val dangerBg: Color,
    val dangerText: Color,
    // WhatsApp accent
    val whatsappBg: Color,
    val whatsappText: Color
)

val LightLapakColors = LapakExtendedColors(
    cardBorder = CardBorderLight,
    // 1. Total Omzet — Soft Mint Green
    omzetCardBg = Color(0xFFE9F6F0),
    omzetCardBorder = Color(0xFFCDE9DC),
    omzetCardAccent = Color(0xFF23684F),
    // 2. Untung Lapak — Soft Sky Blue
    profitCardBg = Color(0xFFEAF3FB),
    profitCardBorder = Color(0xFFCDE1F5),
    profitCardAccent = Color(0xFF246094),
    // 3. Setoran ke Penjual — Warm Cream/Amber
    payoutCardBg = Color(0xFFFDF5E6),
    payoutCardBorder = Color(0xFFF4DFB9),
    payoutCardAccent = Color(0xFF9C5E14),
    // 4. Biaya Operasional — Soft Neutral Gray
    expenseCardBg = Color(0xFFF1F3F2),
    expenseCardBorder = Color(0xFFDCE2DF),
    expenseCardAccent = Color(0xFF4D5A55),
    // Stock Badges
    stockSafeBg = Color(0xFFE3F4EC),
    stockSafeText = Color(0xFF1F6B4E),
    stockLowBg = Color(0xFFFFEFE6),
    stockLowText = Color(0xFFC24A16),
    stockEmptyBg = Color(0xFFECEFED),
    stockEmptyText = Color(0xFF66736E),
    // Status Badges
    statusActiveBg = Color(0xFFE1F2EB),
    statusActiveText = Color(0xFF1F6B4E),
    statusLeaveBg = Color(0xFFFEF3DF),
    statusLeaveText = Color(0xFF9C5E14),
    statusInactiveBg = Color(0xFFECEFED),
    statusInactiveText = Color(0xFF5C6B66),
    // Alerts
    warningBg = Color(0xFFFEF6E7),
    warningBorder = Color(0xFFF2DCB4),
    warningText = Color(0xFF8F540F),
    dangerBg = Color(0xFFFCECEC),
    dangerText = Color(0xFFB42D2D),
    // WhatsApp
    whatsappBg = Color(0xFFE3F5EB),
    whatsappText = Color(0xFF1A7A4C)
)

val DarkLapakColors = LapakExtendedColors(
    cardBorder = CardBorderDark,
    // 1. Total Omzet — Soft Dark Mint
    omzetCardBg = Color(0xFF162B23),
    omzetCardBorder = Color(0xFF234437),
    omzetCardAccent = Color(0xFF63CAA3),
    // 2. Untung Lapak — Soft Dark Sky Blue
    profitCardBg = Color(0xFF162533),
    profitCardBorder = Color(0xFF243B50),
    profitCardAccent = Color(0xFF72B5EE),
    // 3. Setoran ke Penjual — Soft Dark Warm Amber
    payoutCardBg = Color(0xFF2B2316),
    payoutCardBorder = Color(0xFF453721),
    payoutCardAccent = Color(0xFFE9B262),
    // 4. Biaya Operasional — Soft Dark Slate
    expenseCardBg = Color(0xFF1E2422),
    expenseCardBorder = Color(0xFF2E3834),
    expenseCardAccent = Color(0xFFAAB8B3),
    // Stock Badges
    stockSafeBg = Color(0xFF18362B),
    stockSafeText = Color(0xFF6AD0A8),
    stockLowBg = Color(0xFF3A2319),
    stockLowText = Color(0xFFFF9B73),
    stockEmptyBg = Color(0xFF252D2A),
    stockEmptyText = Color(0xFF94A6A0),
    // Status Badges
    statusActiveBg = Color(0xFF18362B),
    statusActiveText = Color(0xFF6AD0A8),
    statusLeaveBg = Color(0xFF362A18),
    statusLeaveText = Color(0xFFE9B262),
    statusInactiveBg = Color(0xFF252D2A),
    statusInactiveText = Color(0xFF94A6A0),
    // Alerts
    warningBg = Color(0xFF2C2316),
    warningBorder = Color(0xFF473923),
    warningText = Color(0xFFE9B262),
    dangerBg = Color(0xFF341C1C),
    dangerText = Color(0xFFF28B82),
    // WhatsApp
    whatsappBg = Color(0xFF163527),
    whatsappText = Color(0xFF63D09B)
)

val LocalLapakColors = staticCompositionLocalOf { LightLapakColors }
