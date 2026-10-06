package pitcher.android.ui.classic

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import pitcher.android.data.Bookmark
import pitcher.android.data.Track
import pitcher.core.Waveform

private val SPEEDS = listOf(
    0.5f, 0.6f, 0.7f, 0.8f, 0.9f, 1f, 1.1f, 1.2f, 1.3f, 1.4f, 1.5f, 1.75f, 2f,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    track: Track?,
    peaks: FloatArray,
    positionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    bookmarks: List<Bookmark>,
    tempo: Float,
    loopStartMs: Long?,
    loopEndMs: Long?,
    loopEnabled: Boolean,
    onTogglePlay: () -> Unit,
    onSeekMs: (Long) -> Unit,
    onAddBookmark: (String?) -> Unit,
    onDeleteBookmark: (Long) -> Unit,
    onTempo: (Float) -> Unit,
    onSetLoopStart: () -> Unit,
    onSetLoopEnd: () -> Unit,
    onToggleLoop: () -> Unit,
    onClearLoop: () -> Unit,
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

    var bookmarkName by remember { mutableStateOf("") }

    Column(modifier = modifier.fillMaxSize()) {
        Text(
            track.title,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            style = MaterialTheme.typography.titleMedium,
        )
        WaveformView(
            peaks = peaks,
            positionMs = positionMs,
            durationMs = durationMs,
            bookmarks = bookmarks,
            loopStartMs = loopStartMs,
            loopEndMs = loopEndMs,
            onSeekMs = onSeekMs,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                Waveform.formatClock(positionMs / 1000.0),
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(onClick = onTogglePlay) { Text(if (isPlaying) "Pause" else "Play") }
            Text(
                Waveform.formatClock(durationMs / 1000.0),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            listOf(-5000L, -1000L, 1000L, 5000L).forEach { delta ->
                TextButton(onClick = { onSeekMs(positionMs + delta) }) {
                    Text(if (delta > 0) "+${delta / 1000}s" else "${delta / 1000}s")
                }
            }
        }
        Slider(
            value = if (durationMs > 0) positionMs.toFloat() / durationMs else 0f,
            onValueChange = { f -> onSeekMs((f * durationMs).toLong()) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )

        Text(
            "speed",
            modifier = Modifier.padding(horizontal = 16.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            SPEEDS.forEach { s ->
                FilterChip(
                    selected = tempo == s,
                    onClick = { onTempo(s) },
                    label = { Text(if (s == s.toInt().toFloat()) "${s.toInt()}x" else "${s}x") },
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onSetLoopStart) { Text("Set A") }
            TextButton(onClick = onSetLoopEnd) { Text("Set B") }
            TextButton(onClick = onToggleLoop, enabled = loopStartMs != null && loopEndMs != null) {
                Text(if (loopEnabled) "Loop on" else "Loop off")
            }
            if (loopStartMs != null && loopEndMs != null) {
                Text(
                    "${Waveform.formatClock(loopStartMs / 1000.0)}-" +
                        Waveform.formatClock(loopEndMs / 1000.0),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onClearLoop) { Text("Clear") }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        Text(
            "Bookmarks",
            modifier = Modifier.padding(horizontal = 16.dp),
            style = MaterialTheme.typography.titleSmall,
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = bookmarkName,
                onValueChange = { bookmarkName = it },
                label = { Text("Name (optional)") },
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
            Button(onClick = {
                onAddBookmark(bookmarkName.trim().ifEmpty { null })
                bookmarkName = ""
            }) {
                Text("Mark")
            }
        }
        if (bookmarks.isEmpty()) {
            Text(
                "No bookmarks yet.",
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(bookmarks, key = { it.id }) { b ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        TextButton(onClick = { onSeekMs((b.t * 1000).toLong()) }) {
                            Text(Waveform.formatClock(b.t))
                        }
                        Text(
                            b.name ?: "(no name)",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        TextButton(onClick = { onDeleteBookmark(b.id) }) { Text("×") }
                    }
                }
            }
        }
    }
}
