package pitcher.android.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import pitcher.android.data.Track
import pitcher.android.data.Variant

private val PRESETS = listOf(-1200, -700, -500, -200, -100, 100, 200, 500, 700, 1200)

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun PitchLabScreen(
    track: Track?,
    variants: List<Variant>,
    cents: Int,
    selectedVariantId: Long?,
    onCents: (Int) -> Unit,
    onKeep: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (track == null) {
        Column(modifier = modifier.fillMaxSize().padding(24.dp)) {
            Text("No track open", style = MaterialTheme.typography.titleMedium)
            Text(
                "Open a track from the Library tab.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    var name by remember { mutableStateOf("") }
    val matching = variants.firstOrNull { it.cents == cents }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxSize().weight(1f, fill = false),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PitchFader(cents = cents, onCents = onCents, modifier = Modifier.size(56.dp, 320.dp))
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    (if (cents >= 0) "+$cents" else "$cents") + " cents",
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    "semitones: " + String.format("%.2f", cents / 100.0),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(-10, -1, 1, 10).forEach { d ->
                        TextButton(onClick = { onCents((cents + d).coerceIn(-1200, 1200)) }) {
                            Text(if (d > 0) "+$d" else "$d")
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            PRESETS.chunked(5).forEach { row ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { p ->
                        FilterChip(
                            selected = cents == p,
                            onClick = { onCents(p) },
                            label = { Text(if (p > 0) "+${p / 100}" else "${p / 100}") },
                        )
                    }
                }
            }
        }

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Name this pitch (optional)") },
            singleLine = true,
            modifier = Modifier.fillMaxSize(),
        )
        Button(
            onClick = {
                onKeep(name.trim().ifEmpty { null })
                name = ""
            },
            modifier = Modifier.fillMaxSize(),
        ) {
            Text(if (matching != null) "Update this pitch" else "Keep this pitch as variant")
        }
        Text(
            "shift ${if (cents >= 0) "+$cents" else "$cents"}c · live preview · " +
                if (matching != null) "already kept" else "not kept yet",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PitchFader(
    cents: Int,
    range: Int = 1200,
    onCents: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val trackColor = Color(0xFF1B1B1F)
    val accent = Color(0xFF6AA9FF)
    Canvas(
        modifier = modifier
            .pointerInput(range) {
                detectTapGestures { off -> onCents(centsFor(off.y, size.height.toFloat(), range)) }
            }
            .pointerInput(range) {
                detectDragGestures { change, _ ->
                    onCents(centsFor(change.position.y, size.height.toFloat(), range))
                }
            },
    ) {
        val w = size.width
        val h = size.height
        val corner = 12f
        drawRoundRect(
            color = trackColor,
            topLeft = Offset(0f, 0f),
            size = Size(w, h),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(corner, corner),
        )
        drawLine(
            color = Color(0xFF444444),
            start = Offset(0f, h / 2f),
            end = Offset(w, h / 2f),
            strokeWidth = 2f,
        )
        val frac = (range - cents.toDouble()) / (2.0 * range)
        val knobY = (frac * h).toFloat().coerceIn(0f, h)
        val cx = w / 2f
        drawLine(
            color = accent.copy(alpha = 0.4f),
            start = Offset(cx, h / 2f),
            end = Offset(cx, knobY),
            strokeWidth = 4f,
        )
        drawCircle(color = accent, radius = w * 0.28f, center = Offset(cx, knobY))
    }
}

private fun centsFor(y: Float, height: Float, range: Int): Int {
    if (height <= 0f) return 0
    val frac = (y / height).coerceIn(0f, 1f)
    return (range - frac * 2 * range).roundToInt()
}
