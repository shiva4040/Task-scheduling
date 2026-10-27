package com.taskscheduling.app.ui.theme

import androidx.compose.ui.graphics.Color

// =========================================================================
// CONCEPT BOARD DESIGN PALETTE (Light & Dark)
// =========================================================================

// Primary & Accent Brand Colors
val BrandPrimary = Color(0xFF6366F1) // Modern Indigo (#6366F1 from reference board)
val BrandPrimaryHover = Color(0xFF4F46E5)
val BrandSecondary = Color(0xFF06B6D4) // Teal / Cyan (#06B6D4 from reference board)
val BrandAccent = Color(0xFF8B5CF6) // Purple / Violet (#8B5CF6 from reference board)
val BrandCyan = Color(0xFF38BDF8) // Sky / Neon Cyan
val BrandDeepCyan = Color(0xFF0284C7)

// Status Colors
val StatusSuccess = Color(0xFF10B981) // Emerald
val StatusSuccessBgLight = Color(0xFFECFDF5)
val StatusSuccessBgDark = Color(0x2610B981)

val StatusWarning = Color(0xFFF59E0B) // Amber
val StatusWarningBgLight = Color(0xFFFFFBEB)
val StatusWarningBgDark = Color(0x26F59E0B)

val StatusError = Color(0xFFF43F5E) // Rose Red
val StatusErrorBgLight = Color(0xFFFFF1F2)
val StatusErrorBgDark = Color(0x26F43F5E)

// Dark Theme Surfaces & Backgrounds
val BgDeepDark = Color(0xFF090D16)
val BgSurfaceDark = Color(0xFF0F172A)
val BgCardDark = Color(0xFF1E293B)
val BgCardElevatedDark = Color(0xFF263348)
val BorderSubtleDark = Color(0xFF334155)
val BorderHighlightDark = Color(0xFF38BDF8)

// Dark Theme Typography
val TextPrimaryDark = Color(0xFFF8FAFC)
val TextSecondaryDark = Color(0xFF94A3B8)
val TextTertiaryDark = Color(0xFF64748B)

// Light Theme Surfaces & Backgrounds
val BgDeepLight = Color(0xFFF8FAFC) // Crisp modern background
val BgSurfaceLight = Color(0xFFFFFFFF) // Pure white container
val BgCardLight = Color(0xFFFFFFFF) // Clean elevated cards
val BgCardElevatedLight = Color(0xFFF1F5F9) // Slate 100 secondary surfaces
val BorderSubtleLight = Color(0xFFE2E8F0) // Refined low-contrast border
val BorderHighlightLight = Color(0xFF6366F1) // Indigo active highlight

// Light Theme Typography
val TextPrimaryLight = Color(0xFF0F172A) // Near-black for maximum contrast & readability
val TextSecondaryLight = Color(0xFF475569) // Neutral slate gray
val TextTertiaryLight = Color(0xFF94A3B8) // Muted captions

// Backward-compatibility aliases
val NeonCyan = BrandCyan
val DeepCyan = BrandDeepCyan
val AccentIndigo = BrandPrimary
val AccentViolet = BrandAccent
val AccentEmerald = StatusSuccess
val AccentAmber = StatusWarning
val AccentRose = StatusError

