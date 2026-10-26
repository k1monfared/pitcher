package pitcher.android.ui.modern

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import pitcher.android.data.SongPackage
import pitcher.android.data.Track
import pitcher.android.ui.PitcherViewModel

@Composable
fun ModernLibraryScreen(
    vm: PitcherViewModel,
    onOpen: () -> Unit,
    onClose: () -> Unit,
    onboarding: OnboardingTargets? = null,
) {
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let { vm.import(it) { onOpen() } } }
    var saving by remember { mutableStateOf<Track?>(null) }
    val saver = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(SongPackage.MIME),
    ) { uri ->
        val track = saving
        saving = null
        if (uri != null && track != null) vm.savePackage(track, uri)
    }

    LibraryContent(
        tracks = vm.tracks,
        message = vm.message,
        canClose = vm.current != null,
        onImport = { launcher.launch(IMPORT_TYPES) },
        onClose = onClose,
        onOpen = { track ->
            vm.openTrack(track)
            onOpen()
        },
        onDelete = { vm.deleteTrack(it) },
        onSharePackage = { vm.sharePackage(it) },
        onSavePackage = { track ->
            saving = track
            saver.launch(SongPackage.fileName(track))
        },
        onboarding = onboarding,
    )
}

private val IMPORT_TYPES = arrayOf("audio/*", "video/*", SongPackage.MIME, "application/octet-stream")

@Composable
fun LibraryContent(
    tracks: List<Track>,
    message: String?,
    canClose: Boolean,
    onImport: () -> Unit,
    onClose: () -> Unit,
    onOpen: (Track) -> Unit,
    onDelete: (Track) -> Unit,
    onSharePackage: (Track) -> Unit = {},
    onSavePackage: (Track) -> Unit = {},
    onboarding: OnboardingTargets? = null,
) {
    Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    "Library",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    songCountLabel(tracks.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Button(
                    onClick = onImport,
                    modifier = Modifier.onboardingTarget(onboarding, "library.import"),
                ) { Text("Import") }
                if (canClose) {
                    TextButton(onClick = onClose) { Text("Close") }
                }
            }
        }

        message?.let {
            Text(
                it,
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.secondary,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        HorizontalDivider()

        if (tracks.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("No songs yet", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Import an audio or video file, or a song package someone sent you.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(tracks, key = { it.id }) { track ->
                    SongRow(
                        track = track,
                        onOpen = { onOpen(track) },
                        onDelete = { onDelete(track) },
                        onSharePackage = { onSharePackage(track) },
                        onSavePackage = { onSavePackage(track) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun SongRow(
    track: Track,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    onSharePackage: () -> Unit,
    onSavePackage: () -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this song?") },
            text = {
                Text(
                    "\"${track.title.ifBlank { "(untitled)" }}\" and its saved pitches, loops, " +
                        "and bookmarks will be removed from pitcher.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Keep") }
            },
        )
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                track.title.ifBlank { "(untitled)" },
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val sub = buildString {
                track.artist?.let { append(it) }
                if (track.variantCount > 0) {
                    if (isNotEmpty()) append(" · ")
                    append("${track.variantCount} pitches")
                }
            }
            if (sub.isNotEmpty()) {
                Text(
                    sub,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Box {
            TextButton(onClick = { menuOpen = true }) { Text("More") }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Share song package") },
                    onClick = {
                        menuOpen = false
                        onSharePackage()
                    },
                )
                DropdownMenuItem(
                    text = { Text("Save song package") },
                    onClick = {
                        menuOpen = false
                        onSavePackage()
                    },
                )
                DropdownMenuItem(
                    text = { Text("Delete") },
                    onClick = {
                        menuOpen = false
                        confirmDelete = true
                    },
                )
            }
        }
    }
}
