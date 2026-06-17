package app.cosmic.player

import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.cosmic.core.prefs.ThemeMode
import app.cosmic.player.nav.CosmicNavHost
import app.cosmic.player.ui.theme.CosmicTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // SystemBarStyle.auto with two TRANSPARENT scrims tells the platform
        // not to draw the translucent grey/white scrim it normally adds in
        // edge-to-edge mode. Combined with the gradient backdrop the screens
        // already paint, this lets the app's art bleed under the status and
        // nav bars instead of showing the system bar background.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContent { Root() }
    }
}

@Composable
private fun Root() {
    val themeVm: ThemeViewModel = hiltViewModel()
    val prefs by themeVm.prefs.collectAsStateWithLifecycle()
    CosmicTheme(themeMode = prefs.theme, dynamicColor = prefs.dynamicColor) {
        Box(modifier = Modifier.fillMaxSize()) {
            CosmicNavHost()
            // Thin gradient behind the status icons. Without it the system
            // clock / battery / signal bleed into the gradient backdrop and
            // become unreadable on light themes (Solar) or against bright
            // album art. Tint follows the chosen theme — dark scrim on light
            // backgrounds (so dark icons stay legible), light scrim on dark
            // backgrounds (preserving the AMOLED look).
            val isLight = when (prefs.theme) {
                ThemeMode.LIGHT, ThemeMode.SOLAR -> true
                ThemeMode.DARK, ThemeMode.AMOLED, ThemeMode.ELECTRIC -> false
                ThemeMode.SYSTEM -> !isSystemInDarkTheme()
            }
            val scrim = if (isLight) Color.White else Color.Black
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsTopHeight(WindowInsets.statusBars)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                scrim.copy(alpha = 0.55f),
                                scrim.copy(alpha = 0f),
                            ),
                        ),
                    ),
            )
        }
    }
}
