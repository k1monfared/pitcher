package pitcher.android.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import pitcher.android.data.Track
import pitcher.android.data.Variant
import pitcher.android.media.RenderedStore
import pitcher.core.ExportFormat
import pitcher.core.FaderMath

private val PRESETS = listOf(-1200, -700, -500, -200, -100, 100, 200, 500, 700, 1200)

@OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    ExperimentalLayoutApi::class,
)
@Composable
fun PitchLabScreen(
    track: Track?,
    variants: List<Variant>,
    cents: Int,
    selectedVariantId: Long?,
    exportMessage: String?,
    exporting: Boolean,
    exportFormat: ExportFormat,
    exportLoopOnly: Boolean,
    loopAvailable: Boolean,
    onCents: (Int) -> Unit,
    onRender: (String?) -> Unit,
    onExport: (String?) -> Unit,
    onFormat: (ExportFormat) -> Unit,
    onLoopOnly: (Boolean) -> Unit,
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
        // The fader sits centered so there is screen space on both sides to
        // slide: left tunes finely, right moves fast.
        PitchFader(cents = cents, onCents = onCents, modifier = Modifier.size(64.dp, 320.dp))
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
        Text(
            "drag to shift · slide left = fine · slide right = fast · double-tap resets",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            maxItemsInEachRow = 5,
        ) {
            PRESETS.forEach { p ->
                FilterChip(
                    selected = cents == p,
                    onClick = { onCents(p) },
                    label = { Text(if (p > 0) "+${p / 100}" else "${p / 100}") },
                )
            }
        }

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Name this pitch (optional)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "shift ${if (cents >= 0) "+$cents" else "$cents"}c · live preview · " +
                if (matching != null) "rendered" else "not rendered yet",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "export format",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ExportFormat.entries.forEach { fmt ->
                FilterChip(
                    selected = exportFormat == fmt,
                    onClick = { onFormat(fmt) },
                    label = { Text(fmt.id) },
                )
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Checkbox(
                checked = exportLoopOnly,
                onCheckedChange = onLoopOnly,
                enabled = loopAvailable,
            )
            Text(
                if (loopAvailable) "Export only the A/B loop" else "Export only the A/B loop (set A/B first)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Button(
            onClick = { onRender(name.trim().ifEmpty { null }) },
            enabled = !exporting,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                if (exporting) "Rendering..." else "Render & keep (${exportFormat.id})",
            )
        }
        OutlinedButton(
            onClick = { onExport(name.trim().ifEmpty { null }) },
            enabled = !exporting,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Export / share (${exportFormat.id})")
        }
        Text(
            "Rendered pitches are saved to ${RenderedStore.FOLDER} and stay in the " +
                "library even if you do not share them.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (exportMessage != null) {
            Text(
                exportMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
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
    val fineColor = Color(0xFF7DDF9A)
    val coarseColor = Color(0xFFFFD166)

    val currentCents by rememberUpdatedState(cents)
    var dragging by remember { mutableStateOf(false) }
    var mode by remember { mutableStateOf(FaderMath.Mode.NORMAL) }
    val anchor = remember { mutableStateOf(Offset.Zero) }
    val startCents = remember { mutableIntStateOf(0) }
    val haptics = LocalHapticFeedback.current

    val accentNow = when (mode) {
        FaderMath.Mode.FINE -> fineColor
        FaderMath.Mode.COARSE -> coarseColor
        FaderMath.Mode.NORMAL -> accent
    }

    Canvas(
        modifier = modifier
            .pointerInput(range) {
                detectTapGestures(
                    onDoubleTap = { onCents(0) },
                )
            }
            .pointerInput(range) {
                val zonePx = (FaderMath.ZONE_FRAC * size.height).toFloat()
                detectDragGestures(
                    onDragStart = { off ->
                        dragging = true
                        anchor.value = off
                        startCents.intValue = currentCents
                        mode = FaderMath.Mode.NORMAL
                    },
                    onDragEnd = {
                        dragging = false
                        mode = FaderMath.Mode.NORMAL
                    },
                    onDragCancel = {
                        dragging = false
                        mode = FaderMath.Mode.NORMAL
                    },
                ) { change, _ ->
                    val dx = change.position.x - anchor.value.x
                    val dy = anchor.value.y - change.position.y
                    val nextMode = FaderMath.modeFor(dx, zonePx)
                    if (nextMode != mode) {
                        mode = nextMode
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                    onCents(
                        FaderMath.centsFromDrag(
                            startCents.intValue,
                            dy,
                            dx,
                            size.height.toFloat(),
                            range,
                        ),
                    )
                }
            },
    ) {
        val w = size.width
        val h = size.height
        drawRoundRect(
            color = trackColor,
            topLeft = Offset(0f, 0f),
            size = Size(w, h),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(12f, 12f),
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
            color = accentNow.copy(alpha = 0.4f),
            start = Offset(cx, h / 2f),
            end = Offset(cx, knobY),
            strokeWidth = 4f,
        )
        drawCircle(
            color = accentNow,
            radius = w * if (dragging) 0.34f else 0.28f,
            center = Offset(cx, knobY),
        )
    }
}
