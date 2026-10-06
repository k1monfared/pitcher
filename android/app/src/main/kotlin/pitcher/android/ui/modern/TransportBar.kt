package pitcher.android.ui.modern

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private val SPEED_PRESETS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)

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

        SpeedChip(tempo = tempo, onTempo = onTempo)

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
private fun SpeedChip(tempo: Float, onTempo: (Float) -> Unit) {
    Surface(shape = RoundedCornerShape(50), color = Color.White.copy(alpha = 0.06f)) {
        Text(
            text = "${"%.2f".format(tempo)}x",
            modifier = Modifier
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            val next = SPEED_PRESETS.firstOrNull { it > tempo + 0.01f }
                                ?: SPEED_PRESETS.first()
                            onTempo(next)
                        },
                    )
                }
                .pointerInput(Unit) {
                    var base = tempo
                    detectDragGestures(
                        onDragStart = { base = tempo },
                    ) { change, drag ->
                        base = (base + drag.x / 260f).coerceIn(0.5f, 2f)
                        onTempo(base)
                        change.consume()
                    }
                }
                .padding(horizontal = 14.dp, vertical = 10.dp),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
