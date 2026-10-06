package pitcher.android.ui.modern

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.abs

private val SPEED_PRESETS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
private const val AUDITION_SPEED = 0.7f

@Composable
fun TransportBar(
    playing: Boolean,
    tempo: Float,
    loopEnabled: Boolean,
    accent: Color,
    onPlayPause: () -> Unit,
    onSkip: (Long) -> Unit,
    onTempo: (Float) -> Unit,
    onLoopToggle: () -> Unit,
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
                    .padding(horizontal = 26.dp, vertical = 14.dp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF06121F),
            )
        }

        TextButton(onClick = { onSkip(5000) }) { Text("+5s") }

        SpeedChip(tempo = tempo, accent = accent, onTempo = onTempo)

        LoopChip(enabled = loopEnabled, accent = accent, onToggle = onLoopToggle)
    }
}

@Composable
private fun LoopChip(enabled: Boolean, accent: Color, onToggle: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(50),
        color = if (enabled) accent.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.06f),
    ) {
        Text(
            "loop",
            modifier = Modifier
                .clickable(onClick = onToggle)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            style = MaterialTheme.typography.labelLarge,
            color = if (enabled) accent else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SpeedChip(tempo: Float, accent: Color, onTempo: (Float) -> Unit) {
    val tempoNow by rememberUpdatedState(tempo)
    var audition by remember { mutableStateOf(false) }
    Surface(shape = RoundedCornerShape(50), color = Color.White.copy(alpha = 0.06f)) {
        Text(
            text = "${"%.2f".format(tempo)}x",
            modifier = Modifier
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val startX = down.position.x
                        var base = tempoNow
                        // Hold without moving for 400 ms: audition slower, then
                        // spring back on release. Move: adjust. Quick release: cycle.
                        val held = withTimeoutOrNull(400L) {
                            while (true) {
                                val e = awaitPointerEvent()
                                val ch = e.changes.firstOrNull { it.id == down.id }
                                    ?: return@withTimeoutOrNull false
                                if (!ch.pressed) return@withTimeoutOrNull false
                                if (abs(ch.position.x - startX) > 20f) return@withTimeoutOrNull false
                                ch.consume()
                            }
                            @Suppress("UNREACHABLE_CODE") false
                        }
                        if (held == null) {
                            audition = true
                            onTempo(AUDITION_SPEED)
                            while (true) {
                                val e = awaitPointerEvent()
                                val ch = e.changes.firstOrNull { it.id == down.id } ?: break
                                if (!ch.pressed) break
                                ch.consume()
                            }
                            audition = false
                            onTempo(base)
                        } else {
                            var lastX = startX
                            var moved = false
                            while (true) {
                                val e = awaitPointerEvent()
                                val ch = e.changes.firstOrNull { it.id == down.id } ?: break
                                if (!ch.pressed) break
                                if (abs(ch.position.x - startX) > 20f) moved = true
                                if (moved) {
                                    val dx = ch.position.x - lastX
                                    lastX = ch.position.x
                                    base = (base + dx / 260f).coerceIn(0.5f, 2f)
                                    onTempo(base)
                                }
                                ch.consume()
                            }
                            if (!moved) {
                                val next = SPEED_PRESETS.firstOrNull { it > tempoNow + 0.01f }
                                    ?: SPEED_PRESETS.first()
                                onTempo(next)
                            }
                        }
                    }
                }
                .padding(horizontal = 14.dp, vertical = 10.dp),
            style = MaterialTheme.typography.labelLarge,
            color = if (audition) accent else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
