package com.vigilante.app.ui.theme

import androidx.compose.ui.graphics.Color

/*
 * Institutional palette (user-selected direction "أ — مؤسسي رصين"):
 * deep navy authority + calm blue accent, sourced from the
 * Government/Public Service palette of the UI UX Pro Max design database.
 *
 * Contrast follows the "Inclusive Design" rules from the same source: body
 * text ≥ 7:1, secondary text ≥ 4.5:1, and no state is signalled by colour
 * alone — every status also carries a label or an icon.
 */

// ---- brand constants (used by the header in both light and dark) ----
val NavyDeep = Color(0xFF0F172A)
val NavyDeepAlt = Color(0xFF1B2A46)
val BlueAccent = Color(0xFF0369A1)
val BlueAccentSoft = Color(0xFF38BDF8)

// ---------------- light ----------------
val LightPrimary = Color(0xFF0F172A)
val LightOnPrimary = Color(0xFFFFFFFF)
val LightPrimaryContainer = Color(0xFFE2E8F0)
val LightOnPrimaryContainer = Color(0xFF0F172A)

val LightSecondary = Color(0xFF0369A1)
val LightOnSecondary = Color(0xFFFFFFFF)
val LightSecondaryContainer = Color(0xFFE0F2FE)
val LightOnSecondaryContainer = Color(0xFF075985)

val LightTertiary = Color(0xFF15803D)
val LightOnTertiary = Color(0xFFFFFFFF)
val LightTertiaryContainer = Color(0xFFDCFCE7)
val LightOnTertiaryContainer = Color(0xFF14532D)

val LightBackground = Color(0xFFF8FAFC)
val LightOnBackground = Color(0xFF0F172A)
val LightSurface = Color(0xFFFFFFFF)
val LightOnSurface = Color(0xFF0F172A)
val LightSurfaceVariant = Color(0xFFF1F5F9)
val LightOnSurfaceVariant = Color(0xFF475569)
val LightOutline = Color(0xFFCBD5E1)
val LightOutlineVariant = Color(0xFFE2E8F0)

val ErrorLight = Color(0xFFDC2626)
val OnErrorLight = Color(0xFFFFFFFF)
val ErrorContainerLight = Color(0xFFFEE2E2)
val OnErrorContainerLight = Color(0xFF7F1D1D)

// ---------------- dark ----------------
val DarkPrimary = Color(0xFF7DD3FC)
val DarkOnPrimary = Color(0xFF082F49)
val DarkPrimaryContainer = Color(0xFF0C4A6E)
val DarkOnPrimaryContainer = Color(0xFFE0F2FE)

val DarkSecondary = Color(0xFF38BDF8)
val DarkOnSecondary = Color(0xFF082F49)
val DarkSecondaryContainer = Color(0xFF075985)
val DarkOnSecondaryContainer = Color(0xFFE0F2FE)

val DarkTertiary = Color(0xFF4ADE80)
val DarkOnTertiary = Color(0xFF052E16)
val DarkTertiaryContainer = Color(0xFF166534)
val DarkOnTertiaryContainer = Color(0xFFDCFCE7)

val DarkBackground = Color(0xFF0B1220)
val DarkOnBackground = Color(0xFFE2E8F0)
val DarkSurface = Color(0xFF111A2B)
val DarkOnSurface = Color(0xFFE2E8F0)
val DarkSurfaceVariant = Color(0xFF1B2638)
val DarkOnSurfaceVariant = Color(0xFF94A3B8)
val DarkOutline = Color(0xFF334155)
val DarkOutlineVariant = Color(0xFF1E293B)

val ErrorDark = Color(0xFFF87171)
val OnErrorDark = Color(0xFF450A0A)
val ErrorContainerDark = Color(0xFF7F1D1D)
val OnErrorContainerDark = Color(0xFFFEE2E2)

/** Semantic colours Material's scheme has no slot for. */
data class VigilanteExtraColors(
    val headerStart: Color,
    val headerEnd: Color,
    val onHeader: Color,
    val onHeaderMuted: Color,
    val statValue: Color,
    val success: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    val cardBorder: Color,
    val tileIconBg: Color,
    val tileIcon: Color
)

val LightExtras = VigilanteExtraColors(
    headerStart = NavyDeep,
    headerEnd = NavyDeepAlt,
    onHeader = Color(0xFFFFFFFF),
    onHeaderMuted = Color(0xFFCBD5E1),
    statValue = BlueAccent,
    success = Color(0xFF15803D),
    successContainer = Color(0xFFDCFCE7),
    onSuccessContainer = Color(0xFF14532D),
    warning = Color(0xFFB45309),
    warningContainer = Color(0xFFFEF3C7),
    onWarningContainer = Color(0xFF78350F),
    cardBorder = Color(0xFFE2E8F0),
    tileIconBg = Color(0xFFE0F2FE),
    tileIcon = BlueAccent
)

val DarkExtras = VigilanteExtraColors(
    headerStart = Color(0xFF0C1626),
    headerEnd = Color(0xFF16233A),
    onHeader = Color(0xFFF1F5F9),
    onHeaderMuted = Color(0xFF94A3B8),
    statValue = BlueAccentSoft,
    success = Color(0xFF4ADE80),
    successContainer = Color(0xFF14532D),
    onSuccessContainer = Color(0xFFDCFCE7),
    warning = Color(0xFFFBBF24),
    warningContainer = Color(0xFF78350F),
    onWarningContainer = Color(0xFFFEF3C7),
    cardBorder = Color(0xFF1E293B),
    tileIconBg = Color(0xFF0C4A6E),
    tileIcon = Color(0xFF7DD3FC)
)
