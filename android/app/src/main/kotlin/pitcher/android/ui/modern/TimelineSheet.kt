package pitcher.android.ui.modern

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import pitcher.android.ui.PitcherViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineSheet(vm: PitcherViewModel, onDismiss: () -> Unit) {
    var renamingBookmark by remember { mutableStateOf<Bookmark?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("Loops", style = MaterialTheme.typography.titleLarge)
            Text(
                "Drag the lane above the wave to add one. Off loops are skipped on export.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (vm.loops.isEmpty()) {
                Text("No loops yet.", style = MaterialTheme.typography.bodyMedium)
            }
            vm.loops.forEachIndexed { index, loop ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = loop.name ?: "",
                        onValueChange = { vm.renameLoop(loop.id, it) },
                        placeholder = { Text("loop ${index + 1}") },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "${formatTime(loop.startMs)} - ${formatTime(loop.endMs)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                    Switch(
                        checked = loop.enabled,
                        onCheckedChange = { vm.setLoopEnabled(loop.id, it) },
                    )
                    TextButton(onClick = { vm.deleteLoop(loop.id) }) { Text("delete") }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            Text("Bookmarks", style = MaterialTheme.typography.titleLarge)
            Text(
                "Tap the bookmark button above the wave to add one at the playhead.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (vm.bookmarks.isEmpty()) {
                Text("No bookmarks yet.", style = MaterialTheme.typography.bodyMedium)
            }
            vm.bookmarks.forEach { bookmark ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        bookmark.name ?: formatTime((bookmark.t * 1000).toLong()),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        formatTime((bookmark.t * 1000).toLong()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = {
                        vm.seekTo((bookmark.t * 1000).toLong())
                        onDismiss()
                    }) { Text("go") }
                    TextButton(onClick = { renamingBookmark = bookmark }) { Text("rename") }
                    TextButton(onClick = { vm.deleteBookmark(bookmark.id) }) { Text("delete") }
                }
            }
        }
    }

    renamingBookmark?.let { bookmark ->
        RenameDialog(
            title = "Rename bookmark",
            initial = bookmark.name ?: "",
            onConfirm = {
                vm.renameBookmark(bookmark.id, it)
                renamingBookmark = null
            },
            onDismiss = { renamingBookmark = null },
        )
    }
}

@Composable
private fun RenameDialog(
    title: String,
    initial: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                label = { Text("Name") },
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(text) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
