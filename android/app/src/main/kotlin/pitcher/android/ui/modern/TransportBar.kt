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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        TextButton(onClick = { onSkip(-5000) }, modifier = Modifier.width(SKIP_WIDTH)) { Text("-5s") }

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .width(PLAY_WIDTH)
                .clickable(onClick = onPlayPause)
                .padding(vertical = 6.dp)
                .semantics { contentDescription = if (playing) "Pause" else "Play" },
        ) {
            PlayGlyph(playing = playing, tint = accent)
        }

        TextButton(onClick = { onSkip(5000) }, modifier = Modifier.width(SKIP_WIDTH)) { Text("+5s") }

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

private val SKIP_WIDTH = 56.dp
private val PLAY_WIDTH = 96.dp
private val SPEED_WIDTH = 72.dp
private val REPEAT_WIDTH = 56.dp

/** A play triangle, or two pause bars while playing. */
@Composable
private fun PlayGlyph(playing: Boolean, tint: Color) {
    Canvas(modifier = Modifier.size(44.dp)) {
        val w = size.width
        val h = size.height
        if (playing) {
            val barW = w * 0.22f
            val barH = h * 0.70f
            val top = (h - barH) / 2f
            val corner = CornerRadius(barW * 0.25f, barW * 0.25f)
            drawRoundRect(tint, Offset(w * 0.22f, top), Size(barW, barH), corner)
            drawRoundRect(tint, Offset(w * 0.56f, top), Size(barW, barH), corner)
        } else {
            drawPath(
                Path().apply {
                    moveTo(w * 0.26f, h * 0.14f)
                    lineTo(w * 0.86f, h * 0.50f)
                    lineTo(w * 0.26f, h * 0.86f)
                    close()
                },
                tint,
            )
        }
    }
}

/** A Spotify-style repeat button, icon only: off, all loops, or one loop. */
@Composable
private fun LoopModeButton(
    mode: LoopMode,
    hasLoops: Boolean,
    accent: Color,
    onClick: () -> Unit,
) {
    val active = mode != LoopMode.NONE
    val tint = if (active) accent else MaterialTheme.colorScheme.onSurfaceVariant
    val description = when (mode) {
        LoopMode.NONE -> "Repeat off"
        LoopMode.ALL -> "Repeat all loops"
        LoopMode.ONE -> "Repeat this loop"
    }
    Box(
        modifier = Modifier
            .width(REPEAT_WIDTH)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        RepeatGlyph(tint = tint, one = mode == LoopMode.ONE)
    }
}

/**
 * Two arrows chasing each other round a loop: the top runs right, the bottom
 * runs left, each with a solid head. A small `1` sits in the middle for
 * "repeat this loop".
 */
@Composable
private fun RepeatGlyph(tint: Color, one: Boolean) {
    val measurer = rememberTextMeasurer()
    // Wide and tall enough that the "1" sits in a clear gap between the arms.
    Canvas(modifier = Modifier.size(width = 34.dp, height = 26.dp)) {
        val w = size.width
        val h = size.height
        val stroke = 2.2.dp.toPx()
        val head = 4.5.dp.toPx()
        val left = w * 0.10f
        val right = w * 0.90f
        val top = h * 0.16f
        val bottom = h * 0.84f
        val r = h * 0.18f

        val topArm = Path().apply {
            moveTo(left, h * 0.55f)
            lineTo(left, top + r)
            quadraticTo(left, top, left + r, top)
            lineTo(right - head * 1.2f, top)
        }
        drawPath(topArm, tint, style = Stroke(width = stroke, cap = StrokeCap.Round))
        drawPath(
            Path().apply {
                moveTo(right, top)
                lineTo(right - head * 1.6f, top - head)
                lineTo(right - head * 1.6f, top + head)
                close()
            },
            tint,
        )

        val bottomArm = Path().apply {
            moveTo(right, h * 0.45f)
            lineTo(right, bottom - r)
            quadraticTo(right, bottom, right - r, bottom)
            lineTo(left + head * 1.2f, bottom)
        }
        drawPath(bottomArm, tint, style = Stroke(width = stroke, cap = StrokeCap.Round))
        drawPath(
            Path().apply {
                moveTo(left, bottom)
                lineTo(left + head * 1.6f, bottom - head)
                lineTo(left + head * 1.6f, bottom + head)
                close()
            },
            tint,
        )

        if (one) {
            val text = measurer.measure(
                "1",
                TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = tint),
            )
            drawText(
                text,
                topLeft = Offset((w - text.size.width) / 2f, (h - text.size.height) / 2f),
            )
        }
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
        color = Color.Transparent,
        modifier = Modifier.width(SPEED_WIDTH).onboardingTarget(onboarding, "speed"),
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
                .padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "speed",
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
