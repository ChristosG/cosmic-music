package app.cosmic.feature.common

import androidx.compose.ui.graphics.Color
import kotlin.math.absoluteValue

/**
 * Generates a deterministic two-color gradient seeded by a stable id (track
 * id, playlist id, etc). Same id → same gradient across launches. Two
 * different ids on the same screen → visibly different gradients so users
 * can distinguish playlists/tracks at a glance.
 *
 * The math: hash → hue ∈ [0, 360). Two HSV colors at the same hue, different
 * saturation/value, give a subtle violet-to-cool-edge gradient that always
 * sits inside the Cosmic palette band (not garish, not dull).
 */
data class CosmicGradient(val top: Color, val bottom: Color) {
    companion object {
        fun fromSeed(seed: Long, dark: Boolean = true): CosmicGradient {
            val hue = ((seed * 2654435761L).toInt().absoluteValue % 360).toFloat()
            return if (dark) {
                CosmicGradient(
                    top = Color.hsv(hue, saturation = 0.55f, value = 0.32f),
                    bottom = Color.hsv((hue + 28f) % 360f, saturation = 0.40f, value = 0.18f),
                )
            } else {
                CosmicGradient(
                    top = Color.hsv(hue, saturation = 0.30f, value = 0.92f),
                    bottom = Color.hsv((hue + 28f) % 360f, saturation = 0.20f, value = 0.96f),
                )
            }
        }

        fun fromString(seed: String, dark: Boolean = true): CosmicGradient =
            fromSeed(seed.hashCode().toLong(), dark)
    }
}
