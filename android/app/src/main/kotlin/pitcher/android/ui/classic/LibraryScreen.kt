package pitcher.android.ui.classic

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import pitcher.android.data.Track

@Composable
fun LibraryScreen(
    tracks: List<Track>,
    message: String?,
    onImport: (android.net.Uri) -> Unit,
    onOpen: (Track) -> Unit,
    onDelete: (Track) -> Unit,
    onRename: (Track, String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(onImport) }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Tracks", style = MaterialTheme.typography.titleMedium)
            Button(onClick = { launcher.launch(arrayOf("audio/*", "video/*")) }) {
                Text("Import")
            }
        }
        if (message != null) {
            Text(
                message,
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.secondary,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        HorizontalDivider()
        if (tracks.isEmpty()) {
            Text(
                "Import an audio or video file to begin.",
                modifier = Modifier.padding(16.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(tracks, key = { it.id }) { track ->
                    TrackRow(
                        track = track,
                        onOpen = { onOpen(track) },
                        onDelete = { onDelete(track) },
                        onRename = { title, artist -> onRename(track, title, artist) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun TrackRow(
    track: Track,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    onRename: (String, String) -> Unit,
) {
    var editing by remember { mutableStateOf(false) }
    var title by remember(track.title) { mutableStateOf(track.title) }
    var artist by remember(track.artist) { mutableStateOf(track.artist ?: "") }

    if (editing) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Title") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = artist,
                onValueChange = { artist = it },
                label = { Text("Artist") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    onRename(title, artist)
                    editing = false
                }) { Text("Save") }
                TextButton(onClick = {
                    title = track.title
                    artist = track.artist ?: ""
                    editing = false
                }) { Text("Cancel") }
            }
        }
        return
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                track.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val subtitle = buildString {
                track.artist?.let { append(it) }
                if (track.variantCount > 0) {
                    if (isNotEmpty()) append(" · ")
                    append("${track.variantCount} pitches")
                }
            }
            if (subtitle.isNotEmpty()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        TextButton(onClick = { editing = true }) { Text("Rename") }
        TextButton(onClick = onDelete) { Text("Delete") }
    }
}
