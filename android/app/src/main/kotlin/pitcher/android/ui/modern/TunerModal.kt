package pitcher.android.ui.modern

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import pitcher.android.data.Bookmark
import pitcher.android.ui.PitcherViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TunerModal(vm: PitcherViewModel, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Tuner", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Pick a source and a target note. Detect at the playhead, choose a bookmark, " +
                    "or type a note like c#4 or a4+37.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            NoteField(
                label = "Source note",
                value = vm.tunerSource,
                onChange = { vm.updateTunerSource(it) },
                onDetect = { vm.detectIntoTunerSource() },
                bookmarks = vm.bookmarks,
                onBookmark = { ms ->
                    vm.seekTo(ms)
                    vm.detectIntoTunerSource()
                },
            )
            NoteField(
                label = "Target note",
                value = vm.tunerTarget,
                onChange = { vm.updateTunerTarget(it) },
                onDetect = { vm.detectIntoTunerTarget() },
                bookmarks = vm.bookmarks,
                onBookmark = { ms ->
                    vm.seekTo(ms)
                    vm.detectIntoTunerTarget()
                },
            )

            vm.detectMessage?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }

            val cents = vm.tunerCents()
            if (cents != null) {
                Text(
                    "Interval: %.1f cents (%.2f semitones)".format(cents, cents / 100.0),
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                Text(
                    "Set both notes to see the interval.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Button(
                onClick = {
                    vm.applyTunerToFader()
                    onDismiss()
                },
                enabled = cents != null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Apply to slider")
            }
        }
    }
}

@Composable
private fun NoteField(
    label: String,
    value: String?,
    onChange: (String) -> Unit,
    onDetect: () -> Unit,
    bookmarks: List<Bookmark>,
    onBookmark: (Long) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    // Typed text stays local so the trimmed saved value does not fight the
    // cursor; a detected note replaces it.
    var text by remember { mutableStateOf(value ?: "") }
    LaunchedEffect(value) {
        if (value?.trim() != text.trim()) text = value ?: ""
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(
            value = text,
            onValueChange = {
                text = it
                onChange(it)
            },
            label = { Text(label) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onDetect) { Text("detect at playhead") }
            Box {
                TextButton(onClick = { menu = true }, enabled = bookmarks.isNotEmpty()) {
                    Text("from bookmark")
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    bookmarks.forEach { b ->
                        DropdownMenuItem(
                            text = { Text(b.name ?: formatTime((b.t * 1000).toLong())) },
                            onClick = {
                                menu = false
                                onBookmark((b.t * 1000).toLong())
                            },
                        )
                    }
                }
            }
        }
    }
}
