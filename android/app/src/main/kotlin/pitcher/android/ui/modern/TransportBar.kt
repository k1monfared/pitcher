package pitcher.android.ui.modern

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt
import pitcher.core.LoopMode

/** Play-speed presets. Snapping to these makes returning to 1x trivial. */
val SPEED_PRESETS = listOf(0.5f, 0.6f, 0.7f, 0.8f, 0.9f, 1f, 1.1f, 1.2f, 1.3f, 1.4f, 1.5f, 1.75f, 2f)

fun formatSpeed(speed: Float): String =
    if (speed == speed.toInt().toFloat()) "${speed.toInt()}x" else "${speed}x"

@Composable
fun TransportBar(
    playing: Boolean,
    tempo: Float,
    loopMode: LoopMode,
    hasLoops: Boolean,
    accent: Color,
    onPlayPause: () -> Unit,
    onSkip: (Long) -> Unit,
    onTempo: (Float) -> Unit,
    onSpeedDragging: (Boolean) -> Unit,
    onCycleLoopMode: () -> Unit,
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

        LoopModeButton(
            mode = loopMode,
            hasLoops = hasLoops,
            accent = accent,
            onClick = onCycleLoopMode,
        )
    }
}

/** A Spotify-style repeat button: off, all loops, or one loop. */
@Composable
private fun LoopModeButton(
    mode: LoopMode,
    hasLoops: Boolean,
    accent: Color,
    onClick: () -> Unit,
) {
    val active = mode != LoopMode.NONE
    val tint = if (active) accent else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        shape = RoundedCornerShape(50),
        color = if (active) accent.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.06f),
    ) {
        Row(
            modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                RepeatGlyph(tint = tint)
                if (mode == LoopMode.ONE) {
                    Text(
                        "1",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = tint,
                    )
                }
            }
            Text(
                when (mode) {
                    LoopMode.NONE -> "repeat"
                    LoopMode.ALL -> "all loops"
                    LoopMode.ONE -> "this loop"
                },
                style = MaterialTheme.typography.labelLarge,
                color = tint,
            )
        }
    }
}

@Composable
private fun RepeatGlyph(tint: Color) {
    Canvas(modifier = Modifier.size(width = 20.dp, height = 14.dp)) {
        val w = size.width
        val h = size.height
        val stroke = 2f
        drawRoundRect(
            color = tint,
            topLeft = Offset(stroke / 2, stroke / 2),
            size = Size(w - stroke, h - stroke),
            cornerRadius = CornerRadius(4f, 4f),
            style = Stroke(width = stroke),
        )
        val path = Path().apply {
            moveTo(w * 0.55f, 0f)
            lineTo(w * 0.55f, 6f)
            lineTo(w * 0.80f, 3f)
            close()
        }
        drawPath(path, color = tint)
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
