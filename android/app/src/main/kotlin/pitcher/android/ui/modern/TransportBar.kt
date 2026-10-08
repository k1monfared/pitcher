package pitcher.android.ui.modern

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt

/** Play-speed presets. Snapping to these makes returning to 1x trivial. */
val SPEED_PRESETS = listOf(0.5f, 0.6f, 0.7f, 0.8f, 0.9f, 1f, 1.1f, 1.2f, 1.3f, 1.4f, 1.5f, 1.75f, 2f)

fun formatSpeed(speed: Float): String =
    if (speed == speed.toInt().toFloat()) "${speed.toInt()}x" else "${speed}x"

@Composable
fun TransportBar(
    playing: Boolean,
    tempo: Float,
    loopStartMs: Long?,
    loopEndMs: Long?,
    loopEnabled: Boolean,
    accent: Color,
    onPlayPause: () -> Unit,
    onSkip: (Long) -> Unit,
    onTempo: (Float) -> Unit,
    onSpeedDragging: (Boolean) -> Unit,
    onLoopTap: () -> Unit,
    onLoopClear: () -> Unit,
    onboarding: OnboardingTargets? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        TextButton(onClick = { onSkip(-5000) }) { Text("-5s") }

        Surface(shape = CircleShape, color = accent) {
            Text(
                text = if (playing) "Pause" else "Play",
                modifier = Modifier
                    .clickable(onClick = onPlayPause)
                    .padding(horizontal = 24.dp, vertical = 14.dp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF06121F),
            )
        }

        TextButton(onClick = { onSkip(5000) }) { Text("+5s") }

        SpeedControl(
            tempo = tempo,
            accent = accent,
            onTempo = onTempo,
            onDragging = onSpeedDragging,
            onboarding = onboarding,
        )

        LoopChip(
            startMs = loopStartMs,
            endMs = loopEndMs,
            enabled = loopEnabled,
            accent = accent,
            onTap = onLoopTap,
            onClear = onLoopClear,
        )
    }
}

@Composable
private fun SpeedControl(
    tempo: Float,
    accent: Color,
    onTempo: (Float) -> Unit,
    onDragging: (Boolean) -> Unit,
    onboarding: OnboardingTargets? = null,
) {
    val tempoNow by rememberUpdatedState(tempo)
    Surface(
        shape = RoundedCornerShape(50),
        color = Color.White.copy(alpha = 0.06f),
        modifier = Modifier.onboardingTarget(onboarding, "speed"),
    ) {
        Column(
            modifier = Modifier
                .pointerInput(Unit) {
                    val stepPx = 44f * density
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val startY = down.position.y
                        val startIndex = SPEED_PRESETS.indices.minByOrNull {
                            abs(SPEED_PRESETS[it] - tempoNow)
                        } ?: SPEED_PRESETS.indexOf(1f)
                        var moved = false
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            val dy = startY - change.position.y
                            if (!moved && abs(dy) > 12f) {
                                moved = true
                                onDragging(true)
                            }
                            if (moved) {
                                val delta = (dy / stepPx).roundToInt()
                                val idx = (startIndex + delta).coerceIn(0, SPEED_PRESETS.size - 1)
                                onTempo(SPEED_PRESETS[idx])
                            }
                            change.consume()
                        }
                        if (moved) {
                            onDragging(false)
                        } else {
                            // A tap returns to normal speed.
                            onTempo(1f)
                        }
                    }
                }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "play speed",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                formatSpeed(tempo),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = accent,
            )
        }
    }
}

/** Vertical speed slider shown while the speed chip is being dragged. */
@Composable
fun SpeedHud(tempo: Float, accent: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.width(88.dp),
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFF14141A),
        shadowElevation = 8.dp,
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            SPEED_PRESETS.reversed().forEach { p ->
                val selected = p == tempo
                Text(
                    formatSpeed(p),
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (selected) accent.copy(alpha = 0.25f) else Color.Transparent)
                        .padding(vertical = 4.dp),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    color = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun LoopChip(
    startMs: Long?,
    endMs: Long?,
    enabled: Boolean,
    accent: Color,
    onTap: () -> Unit,
    onClear: () -> Unit,
) {
    val label = when {
        startMs == null -> "loop"
        endMs == null -> "set B"
        enabled -> "loop on"
        else -> "loop off"
    }
    val active = enabled || (startMs != null && endMs == null)
    Surface(
        shape = RoundedCornerShape(50),
        color = if (active) accent.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.06f),
    ) {
        Text(
            label,
            modifier = Modifier
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        var longPress = false
                        val released = withTimeoutOrNull(450L) {
                            while (true) {
                                val e = awaitPointerEvent()
                                val ch = e.changes.firstOrNull { it.id == down.id } ?: return@withTimeoutOrNull true
                                if (!ch.pressed) return@withTimeoutOrNull true
                                ch.consume()
                            }
                            @Suppress("UNREACHABLE_CODE") true
                        }
                        if (released == null) {
                            longPress = true
                            while (true) {
                                val e = awaitPointerEvent()
                                val ch = e.changes.firstOrNull { it.id == down.id } ?: break
                                if (!ch.pressed) break
                                ch.consume()
                            }
                        }
                        if (longPress) onClear() else onTap()
                    }
                }
                .padding(horizontal = 14.dp, vertical = 10.dp),
            style = MaterialTheme.typography.labelLarge,
            color = if (active) accent else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
