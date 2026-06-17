package app.cosmic.feature.common

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.unit.dp

/**
 * Cosmic's signature slider: small circular ball thumb that scales up while
 * dragged, and a slim 3 dp track. Replaces the default Material 3 thumb,
 * which is a chunky vertical pill that reads as "old Android".
 *
 * Drop-in for [Slider] — same value/onValueChange/range/steps API.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CosmicSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
    accent: Color = MaterialTheme.colorScheme.primary,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val dragged by interaction.collectIsDraggedAsState()
    val active = pressed || dragged

    // Ball grows from 14 dp → 18 dp while interacting. Subtle but enough
    // to feel responsive without bouncing into the artwork above.
    val ballSize by animateFloatAsState(
        targetValue = if (active) 18f else 14f,
        animationSpec = tween(durationMillis = 140),
        label = "thumbBall",
    )

    val inactiveTrack = accent.copy(alpha = 0.18f)
        .compositeOver(MaterialTheme.colorScheme.surface)

    Slider(
        value = value,
        onValueChange = onValueChange,
        onValueChangeFinished = onValueChangeFinished,
        valueRange = valueRange,
        steps = steps,
        enabled = enabled,
        interactionSource = interaction,
        modifier = modifier,
        colors = SliderDefaults.colors(
            thumbColor = accent,
            activeTrackColor = accent,
            inactiveTrackColor = inactiveTrack,
            disabledThumbColor = accent.copy(alpha = 0.5f),
            disabledActiveTrackColor = accent.copy(alpha = 0.3f),
            disabledInactiveTrackColor = accent.copy(alpha = 0.12f),
        ),
        thumb = {
            Box(
                modifier = Modifier
                    .padding(2.dp)
                    .size(ballSize.dp)
                    .shadow(elevation = if (active) 4.dp else 1.dp, shape = CircleShape)
                    .clip(CircleShape)
                    .background(accent),
            )
        },
        track = { state: SliderState ->
            val span = (state.valueRange.endInclusive - state.valueRange.start)
                .coerceAtLeast(0.0001f)
            val fraction = ((state.value - state.valueRange.start) / span)
                .coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(CircleShape)
                    .background(inactiveTrack),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction)
                        .height(3.dp)
                        .clip(CircleShape)
                        .background(accent),
                )
            }
        },
    )
}
