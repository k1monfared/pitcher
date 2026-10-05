package pitcher.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
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

@Composable
fun PlayerScreen(
    track: Track?,
    peaks: FloatArray,
    positionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    bookmarks: List<Bookmark>,
    onTogglePlay: () -> Unit,
    onSeekMs: (Long) -> Unit,
    onAddBookmark: (String?) -> Unit,
    onDeleteBookmark: (Long) -> Unit,
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

    KeepScreenOn()

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
            onSeekFraction = { f -> onSeekMs((f * durationMs).toLong()) },
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
        Slider(
            value = if (durationMs > 0) positionMs.toFloat() / durationMs else 0f,
            onValueChange = { f -> onSeekMs((f * durationMs).toLong()) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )

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
