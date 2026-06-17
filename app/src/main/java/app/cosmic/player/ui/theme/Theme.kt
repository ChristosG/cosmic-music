package app.cosmic.player.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import app.cosmic.core.prefs.ThemeMode

// ────────────────────────────────────────────────────────────────────────────
// Cosmic palette — deep violet → electric indigo → soft cyan accent.
// Designed to feel "after dark in a planetarium": low-luminance backgrounds,
// saturated primaries, very dark surface containers so album art and gradient
// backdrops carry the visual weight.
// ────────────────────────────────────────────────────────────────────────────

private val CosmicViolet = Color(0xFFB8A4FF)
private val CosmicViolet80 = Color(0xFFD7C6FF)
private val CosmicViolet40 = Color(0xFF6B4FE0)
private val CosmicAccent = Color(0xFF8FE4FF)
private val CosmicMagenta = Color(0xFFFF96D9)

private val DarkBackground = Color(0xFF0B0814)   // near-black with a violet tint
private val DarkSurface = Color(0xFF15101F)
private val DarkSurfaceContainer = Color(0xFF1B1428)
private val DarkSurfaceContainerHigh = Color(0xFF221833)
private val DarkSurfaceContainerHighest = Color(0xFF2A1F40)
private val DarkSurfaceVariant = Color(0xFF2A2138)
private val DarkOutline = Color(0xFF463A5C)
private val DarkOutlineVariant = Color(0xFF2C2440)
private val DarkOnBackground = Color(0xFFEDE6FF)
private val DarkOnSurface = Color(0xFFEDE6FF)
private val DarkOnSurfaceVariant = Color(0xFFC7BCD9)

private val DarkColors = darkColorScheme(
    primary = CosmicViolet,
    onPrimary = Color(0xFF1A0F40),
    primaryContainer = Color(0xFF3F2E70),
    onPrimaryContainer = Color(0xFFE5DAFF),

    secondary = CosmicAccent,
    onSecondary = Color(0xFF002A38),
    secondaryContainer = Color(0xFF1F4252),
    onSecondaryContainer = Color(0xFFC4ECFF),

    tertiary = CosmicMagenta,
    onTertiary = Color(0xFF400028),
    tertiaryContainer = Color(0xFF5F1A45),
    onTertiaryContainer = Color(0xFFFFD8EE),

    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    surfaceContainer = DarkSurfaceContainer,
    surfaceContainerHigh = DarkSurfaceContainerHigh,
    surfaceContainerHighest = DarkSurfaceContainerHighest,
    surfaceContainerLow = Color(0xFF120D1C),
    surfaceContainerLowest = Color(0xFF0A0612),
    inverseSurface = Color(0xFFEDE6FF),
    inverseOnSurface = Color(0xFF1B1428),
    inversePrimary = CosmicViolet40,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
    error = Color(0xFFFFB4A8),
    onError = Color(0xFF690000),
    errorContainer = Color(0xFF7C0011),
    onErrorContainer = Color(0xFFFFDAD3),
    scrim = Color(0xFF000000),
)

private val AmoledColors = DarkColors.copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF080510),
    surfaceContainer = Color(0xFF0F0A1A),
    surfaceContainerHigh = Color(0xFF150E22),
    surfaceContainerHighest = Color(0xFF1C1530),
    surfaceVariant = Color(0xFF1C1530),
)

// ────────────────────────────────────────────────────────────────────────────
// SOLAR — light theme. Pale dark-orange primary on warm off-white. The
// warmth comes from a paper-like background (#FAF4ED) and stone-grey
// surfaces; primary is a burnt sienna that stays legible against both.
// ────────────────────────────────────────────────────────────────────────────

private val SolarOrange = Color(0xFFB45A2A)        // burnt sienna
private val SolarOrangeDeep = Color(0xFF7A3812)
private val SolarOrangeContainer = Color(0xFFFFD9C2)
private val SolarPaper = Color(0xFFFAF4ED)         // warm off-white background
private val SolarStone = Color(0xFFEFE7DD)         // stone-grey surface variant
private val SolarInk = Color(0xFF231C16)           // espresso-black on-surface

private val SolarColors = lightColorScheme(
    primary = SolarOrange,
    onPrimary = Color.White,
    primaryContainer = SolarOrangeContainer,
    onPrimaryContainer = SolarOrangeDeep,
    secondary = Color(0xFF6F5A47),                 // muted bronze
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEFDFCD),
    onSecondaryContainer = Color(0xFF2A1F12),
    tertiary = Color(0xFF8E5A2A),                  // amber accent
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDDB8),
    onTertiaryContainer = Color(0xFF311900),
    background = SolarPaper,
    onBackground = SolarInk,
    surface = SolarPaper,
    onSurface = SolarInk,
    surfaceVariant = SolarStone,
    onSurfaceVariant = Color(0xFF5A5147),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF6EEE2),
    surfaceContainer = Color(0xFFF1E8DC),
    surfaceContainerHigh = Color(0xFFEBE1D3),
    surfaceContainerHighest = Color(0xFFE5DAC9),
    outline = Color(0xFF8C8273),
    outlineVariant = Color(0xFFD3C9BB),
    inverseSurface = Color(0xFF36302A),
    inverseOnSurface = SolarPaper,
    inversePrimary = Color(0xFFFFB48A),
)

// ────────────────────────────────────────────────────────────────────────────
// ELECTRIC — true-black OLED dark theme with an electric-yellow accent.
// Background is pure 0x000000 (zero-power on AMOLED); primary is a hot,
// slightly-green chartreuse that sears against the void. Secondary stays
// cyan to ground the yellow and avoid traffic-light vibes.
// ────────────────────────────────────────────────────────────────────────────

private val ElectricYellow = Color(0xFFE5FF00)       // chartreuse / electric
private val ElectricYellowDim = Color(0xFFB8CC00)
private val ElectricCyan = Color(0xFF63E6FF)
private val ElectricMagenta = Color(0xFFFF4FC1)

private val ElectricColors = darkColorScheme(
    primary = ElectricYellow,
    onPrimary = Color(0xFF1A1F00),
    primaryContainer = Color(0xFF3A4A00),
    onPrimaryContainer = Color(0xFFE5FF00),
    secondary = ElectricCyan,
    onSecondary = Color(0xFF002831),
    secondaryContainer = Color(0xFF073B47),
    onSecondaryContainer = Color(0xFFB6ECFF),
    tertiary = ElectricMagenta,
    onTertiary = Color(0xFF3F0028),
    tertiaryContainer = Color(0xFF601A47),
    onTertiaryContainer = Color(0xFFFFD3EC),
    background = Color.Black,
    onBackground = Color(0xFFEAFFB0),
    surface = Color.Black,
    onSurface = Color(0xFFEAFFB0),
    surfaceVariant = Color(0xFF1B1F0A),
    onSurfaceVariant = Color(0xFFC9D199),
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF080A02),
    surfaceContainer = Color(0xFF0E1106),
    surfaceContainerHigh = Color(0xFF14180A),
    surfaceContainerHighest = Color(0xFF1B1F0E),
    outline = Color(0xFF6E7547),
    outlineVariant = Color(0xFF2C3119),
    inverseSurface = Color(0xFFEAFFB0),
    inverseOnSurface = Color(0xFF0E1106),
    inversePrimary = ElectricYellowDim,
    error = Color(0xFFFFB4A8),
    onError = Color(0xFF690000),
    errorContainer = Color(0xFF7C0011),
    onErrorContainer = Color(0xFFFFDAD3),
    scrim = Color.Black,
)

private val LightColors = lightColorScheme(
    primary = CosmicViolet40,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE5DAFF),
    onPrimaryContainer = Color(0xFF230E5A),
    secondary = Color(0xFF1F6478),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFC4ECFF),
    onSecondaryContainer = Color(0xFF001F2A),
    tertiary = Color(0xFFA12970),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFD8EE),
    onTertiaryContainer = Color(0xFF370030),
    background = Color(0xFFFAF7FF),
    onBackground = Color(0xFF1A1726),
    surface = Color(0xFFFAF7FF),
    onSurface = Color(0xFF1A1726),
    surfaceVariant = Color(0xFFE7E0F0),
    onSurfaceVariant = Color(0xFF49445A),
    outline = Color(0xFF7A748A),
    outlineVariant = Color(0xFFCBC4D9),
)

@Composable
fun CosmicTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val effectiveDark = when (themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT, ThemeMode.SOLAR -> false
        ThemeMode.DARK, ThemeMode.AMOLED, ThemeMode.ELECTRIC -> true
    }

    // Dynamic color preserves the Material You user-personalisation feel
    // EXCEPT for the curated Cosmic palettes (AMOLED / SOLAR / ELECTRIC),
    // which have an opinionated identity we don't want the system tint to
    // overwrite.
    val colors = when (themeMode) {
        ThemeMode.AMOLED -> AmoledColors
        ThemeMode.SOLAR -> SolarColors
        ThemeMode.ELECTRIC -> ElectricColors
        else -> when {
            dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                val ctx = LocalContext.current
                if (effectiveDark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
            }
            effectiveDark -> DarkColors
            else -> LightColors
        }
    }

    MaterialTheme(
        colorScheme = colors,
        typography = CosmicTypography,
        content = content,
    )
}
