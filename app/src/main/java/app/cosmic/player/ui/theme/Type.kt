package app.cosmic.player.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp
import app.cosmic.player.R

/**
 * Cosmic uses Outfit (Google Fonts) — a modern geometric sans-serif with a
 * slightly compressed feel, more character than Roboto, and great for
 * music-app vibes (titles look bold + technical, body stays readable).
 *
 * Falls back to system sans-serif on first launch before the downloadable
 * font lands; subsequent launches read from the on-device cache.
 */
private val fontProvider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs,
)

private val outfit = GoogleFont("Outfit")

internal val OutfitFamily = FontFamily(
    Font(googleFont = outfit, fontProvider = fontProvider, weight = FontWeight.Normal),
    Font(googleFont = outfit, fontProvider = fontProvider, weight = FontWeight.Medium),
    Font(googleFont = outfit, fontProvider = fontProvider, weight = FontWeight.SemiBold),
    Font(googleFont = outfit, fontProvider = fontProvider, weight = FontWeight.Bold),
)

private val PressStart = GoogleFont("Space Grotesk")

internal val DisplayFamily = FontFamily(
    Font(googleFont = PressStart, fontProvider = fontProvider, weight = FontWeight.SemiBold),
    Font(googleFont = PressStart, fontProvider = fontProvider, weight = FontWeight.Bold),
)

private val tightLineHeight = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

internal val CosmicTypography = Typography(
    // Display + Headline use Space Grotesk for that distinctive feel on
    // big surfaces (Now Playing track title, Library header).
    displayLarge = TextStyle(
        fontFamily = DisplayFamily, fontWeight = FontWeight.SemiBold,
        fontSize = 48.sp, lineHeight = 56.sp, letterSpacing = (-0.5).sp,
        lineHeightStyle = tightLineHeight,
    ),
    displayMedium = TextStyle(
        fontFamily = DisplayFamily, fontWeight = FontWeight.SemiBold,
        fontSize = 36.sp, lineHeight = 44.sp, letterSpacing = (-0.4).sp,
        lineHeightStyle = tightLineHeight,
    ),
    displaySmall = TextStyle(
        fontFamily = DisplayFamily, fontWeight = FontWeight.SemiBold,
        fontSize = 30.sp, lineHeight = 36.sp, letterSpacing = (-0.3).sp,
        lineHeightStyle = tightLineHeight,
    ),
    headlineLarge = TextStyle(
        fontFamily = DisplayFamily, fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-0.3).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = DisplayFamily, fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp, lineHeight = 30.sp, letterSpacing = (-0.2).sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = DisplayFamily, fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp, lineHeight = 26.sp, letterSpacing = (-0.1).sp,
    ),

    titleLarge = TextStyle(
        fontFamily = OutfitFamily, fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp, lineHeight = 26.sp, letterSpacing = 0.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = OutfitFamily, fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp, lineHeight = 22.sp, letterSpacing = 0.1.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = OutfitFamily, fontWeight = FontWeight.Medium,
        fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp,
    ),

    bodyLarge = TextStyle(
        fontFamily = OutfitFamily, fontWeight = FontWeight.Normal,
        fontSize = 16.sp, lineHeight = 22.sp, letterSpacing = 0.15.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = OutfitFamily, fontWeight = FontWeight.Normal,
        fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.2.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = OutfitFamily, fontWeight = FontWeight.Normal,
        fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.25.sp,
    ),

    labelLarge = TextStyle(
        fontFamily = OutfitFamily, fontWeight = FontWeight.Medium,
        fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = OutfitFamily, fontWeight = FontWeight.Medium,
        fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = OutfitFamily, fontWeight = FontWeight.Medium,
        fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.45.sp,
    ),
)
