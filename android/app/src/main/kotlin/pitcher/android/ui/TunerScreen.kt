package pitcher.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import pitcher.android.data.Track
import pitcher.core.Notes

private fun round1(hz: Double): Double = Math.round(hz * 10.0) / 10.0

@Composable
fun TunerScreen(
    track: Track?,
    detectedHz: Double?,
    detectMessage: String?,
    pendingShiftCents: Int?,
    onDetect: () -> Unit,
    onApply: (Int) -> Unit,
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

    var manualNote by remember { mutableStateOf("") }
    var manualHz by remember { mutableStateOf<Double?>(null) }
    var targetNote by remember { mutableStateOf("") }
    var targetHz by remember { mutableStateOf<Double?>(null) }

    val sourceHz = manualHz ?: detectedHz
    val dst = targetHz
    val interval = if (sourceHz != null && dst != null && sourceHz > 0) {
        Notes.centsBetweenHz(sourceHz, dst)
    } else {
        null
    }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(onClick = onDetect) { Text("Detect at playhead") }
        }

        if (detectedHz != null) {
            val reading = Notes.hzToNote(detectedHz)
            Text(
                "${reading.name}  ${fmtCents(reading.centsOff)}c  ${fmtHz(detectedHz)}",
                style = MaterialTheme.typography.titleMedium,
            )
        } else if (detectMessage != null) {
            Text(detectMessage, color = MaterialTheme.colorScheme.secondary)
        }

        Text("Source", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = manualNote,
                onValueChange = { v ->
                    manualNote = v
                    Notes.noteToHz(v)?.let { manualHz = round1(it) }
                },
                label = { Text("Note") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = manualHz?.let { fmtHz(it) } ?: "",
                onValueChange = { v -> manualHz = v.toDoubleOrNull() },
                label = { Text("Hz") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            "source: ${sourceHz?.let { fmtHz(it) } ?: "none"}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Text("Target", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = targetNote,
                onValueChange = { v ->
                    targetNote = v
                    targetHz = Notes.noteToHz(v)?.let { round1(it) }
                },
                label = { Text("Note") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = targetHz?.let { fmtHz(it) } ?: "",
                onValueChange = { v ->
                    val hz = v.toDoubleOrNull()
                    targetHz = hz
                    targetNote = if (hz != null && hz > 0) Notes.hzToNote(hz).name else ""
                },
                label = { Text("Hz") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
        }

        if (interval != null) {
            val cents = interval.roundToInt()
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Text(
                    "${fmtCents(interval)} cents",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                val already = pendingShiftCents == cents
                Button(onClick = { onApply(cents) }, enabled = !already) {
                    Text(if (already) "fader is here" else "Move fader here")
                }
            }
        }
    }
}

private fun fmtHz(hz: Double): String = String.format("%.1f", hz)

private fun fmtCents(cents: Double): String {
    val r = cents.roundToInt()
    return if (r >= 0) "+$r" else "$r"
}
