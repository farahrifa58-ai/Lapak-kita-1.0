package com.example.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// =========================================================================
// "Soft UI / Neumorphism Emerald" Palette — Light (Warm Sage) & Dark (Slate)
// =========================================================================

// Light Mode Core — Soft Warm Sage Neumorphism
val BackgroundLight = Color(0xFFEEF2F0)
val SurfaceLight = Color(0xFFEEF2F0)
val SurfaceVariantLight = Color(0xFFE4EAE7)
val CardBorderLight = Color(0xFFD6DFDB)
val AccentLight = Color(0xFF1F6E50)
val AccentContainerLight = Color(0xFFDDF0E8)
val OnAccentContainerLight = Color(0xFF134834)
val TextPrimaryLight = Color(0xFF182420)
val TextSecondaryLight = Color(0xFF566660)
val NeuShadowDarkLight = Color(0xFFD1D9D5)
val NeuShadowLightLight = Color(0xFFFFFFFF)

// Dark Mode Core — Dark Slate Neumorphism
val BackgroundDark = Color(0xFF1A2220)
val SurfaceDark = Color(0xFF1A2220)
val SurfaceVariantDark = Color(0xFF212B28)
val CardBorderDark = Color(0xFF273430)
val AccentDark = Color(0xFF34D399)
val AccentContainerDark = Color(0xFF16352B)
val OnAccentContainerDark = Color(0xFFC5F5E3)
val TextPrimaryDark = Color(0xFFECF3F0)
val TextSecondaryDark = Color(0xFF93A59E)
val NeuShadowDarkDark = Color(0xFF111715)
val NeuShadowLightDark = Color(0xFF232D2B)

// Backward-compatible named tokens
val EmeraldDark = Color(0xFF164D39)
val EmeraldPrimary = AccentLight
val EmeraldLight = Color(0xFF34D399)
val EmeraldContainer = AccentContainerLight
val OnEmeraldContainer = OnAccentContainerLight

val TealSecondary = Color(0xFF256D78)
val TealContainer = Color(0xFFDEF0F2)
val OnTealContainer = Color(0xFF124249)

val AmberTertiary = Color(0xFFB45309)
val AmberContainer = Color(0xFFFEF0D8)
val OnAmberContainer = Color(0xFF753504)

// Status Colors
val StatusActive = Color(0xFF15803D)
val StatusLeave = Color(0xFFB45309)
val StatusInactive = Color(0xFF64748B)
val StatusActiveContainer = Color(0xFFDFF3E7)
val StatusLeaveContainer = Color(0xFFFEF1D9)
val StatusInactiveContainer = Color(0xFFE5EAE8)

@Immutable
data class LapakExtendedColors(
    val cardBorder: Color,
    // Neumorphism Surface & Dual Soft Shadow Tokens
    val neuSurface: Color,
    val neuShadowDark: Color,
    val neuShadowLight: Color,
    // 4 Dashboard Summary Cards (Monochromatic Surface + Vibrant Accents)
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
    neuSurface = BackgroundLight,
    neuShadowDark = NeuShadowDarkLight,
    neuShadowLight = NeuShadowLightLight,
    // 1. Total Omzet — Deep Emerald Accent (#15803D)
    omzetCardBg = Color(0xFFE4F2EA),
    omzetCardBorder = Color(0xFFC8E4D5),
    omzetCardAccent = Color(0xFF15803D),
    // 2. Untung Lapak — Deep Cobalt Blue Accent (#1D4ED8)
    profitCardBg = Color(0xFFE6EEF9),
    profitCardBorder = Color(0xFFC7DAFA),
    profitCardAccent = Color(0xFF1D4ED8),
    // 3. Setoran ke Penjual — Warm Amber Accent (#B45309)
    payoutCardBg = Color(0xFFFAF0DE),
    payoutCardBorder = Color(0xFFF0D7AC),
    payoutCardAccent = Color(0xFFB45309),
    // 4. Biaya Operasional — Cool Slate Accent (#475569)
    expenseCardBg = Color(0xFFE7ECEA),
    expenseCardBorder = Color(0xFFD2DBD7),
    expenseCardAccent = Color(0xFF475569),
    // Stock Badges
    stockSafeBg = Color(0xFFDCF2E5),
    stockSafeText = Color(0xFF15803D),
    stockLowBg = Color(0xFFFDEDE4),
    stockLowText = Color(0xFFC2410C),
    stockEmptyBg = Color(0xFFE4EAE7),
    stockEmptyText = Color(0xFF64748B),
    // Status Badges
    statusActiveBg = Color(0xFFDCF2E5),
    statusActiveText = Color(0xFF15803D),
    statusLeaveBg = Color(0xFFFEF1D9),
    statusLeaveText = Color(0xFFB45309),
    statusInactiveBg = Color(0xFFE4EAE7),
    statusInactiveText = Color(0xFF475569),
    // Alerts
    warningBg = Color(0xFFFAF2E3),
    warningBorder = Color(0xFFEED3A5),
    warningText = Color(0xFFB45309),
    dangerBg = Color(0xFFFBE9E9),
    dangerText = Color(0xFFB91C1C),
    // WhatsApp
    whatsappBg = Color(0xFFDEF4E7),
    whatsappText = Color(0xFF15803D)
)

val DarkLapakColors = LapakExtendedColors(
    cardBorder = CardBorderDark,
    neuSurface = BackgroundDark,
    neuShadowDark = NeuShadowDarkDark,
    neuShadowLight = NeuShadowLightDark,
    // 1. Total Omzet — Luminous Mint (#34D399)
    omzetCardBg = Color(0xFF182D26),
    omzetCardBorder = Color(0xFF234539),
    omzetCardAccent = Color(0xFF34D399),
    // 2. Untung Lapak — Luminous Sky Blue (#60A5FA)
    profitCardBg = Color(0xFF192634),
    profitCardBorder = Color(0xFF253B52),
    profitCardAccent = Color(0xFF60A5FA),
    // 3. Setoran ke Penjual — Luminous Amber (#FBBF24)
    payoutCardBg = Color(0xFF2A2418),
    payoutCardBorder = Color(0xFF433822),
    payoutCardAccent = Color(0xFFFBBF24),
    // 4. Biaya Operasional — Cool Mist Slate (#94A3B8)
    expenseCardBg = Color(0xFF202826),
    expenseCardBorder = Color(0xFF2E3A37),
    expenseCardAccent = Color(0xFF94A3B8),
    // Stock Badges
    stockSafeBg = Color(0xFF17352B),
    stockSafeText = Color(0xFF34D399),
    stockLowBg = Color(0xFF3A241A),
    stockLowText = Color(0xFFFB923C),
    stockEmptyBg = Color(0xFF242E2B),
    stockEmptyText = Color(0xFF94A3B8),
    // Status Badges
    statusActiveBg = Color(0xFF17352B),
    statusActiveText = Color(0xFF34D399),
    statusLeaveBg = Color(0xFF332918),
    statusLeaveText = Color(0xFFFBBF24),
    statusInactiveBg = Color(0xFF242E2B),
    statusInactiveText = Color(0xFF94A3B8),
    // Alerts
    warningBg = Color(0xFF2A2418),
    warningBorder = Color(0xFF453922),
    warningText = Color(0xFFFBBF24),
    dangerBg = Color(0xFF351D1D),
    dangerText = Color(0xFFF87171),
    // WhatsApp
    whatsappBg = Color(0xFF163428),
    whatsappText = Color(0xFF34D399)
)

val LocalLapakColors = staticCompositionLocalOf { LightLapakColors }
