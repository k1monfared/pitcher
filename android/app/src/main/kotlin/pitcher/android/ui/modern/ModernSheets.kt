package pitcher.android.ui.modern

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import pitcher.android.media.RenderedStore
import pitcher.android.data.Variant
import pitcher.android.ui.PitcherViewModel
import pitcher.core.ExportFormat
import pitcher.core.Notes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportSheet(vm: PitcherViewModel, onDismiss: () -> Unit, onOpenTimeline: () -> Unit = {}) {
    val context = LocalContext.current
    val ext = vm.exportFormat.extension
    val track = vm.current
    val initialBase = remember(track?.id) {
        if (track == null) {
            ""
        } else {
            Notes.downloadFilename(
                track.title,
                track.artist,
                track.sourcePath,
                null,
                vm.faderCents,
                ext,
            ).removeSuffix(".$ext")
        }
    }
    var baseName by remember(track?.id) { mutableStateOf(initialBase) }
    var renamingPitch by remember { mutableStateOf<Variant?>(null) }

    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
            vm.setSongFolder(uri.toString())
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Render", style = MaterialTheme.typography.headlineSmall)

            val selectedVariant = vm.variants.firstOrNull { it.id == vm.selectedVariantId }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { vm.renderAndKeep(null) }, enabled = !vm.exporting) {
                    Text("+ render")
                }
                if (selectedVariant != null) {
                    TextButton(onClick = { renamingPitch = selectedVariant }) { Text("rename pitch") }
                    TextButton(onClick = { vm.deleteVariant(selectedVariant) }) { Text("delete") }
                }
            }

            val folder = vm.effectiveFolder()
            Text(
                "Save to ${folder ?: "${RenderedStore.FOLDER} (default)"}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { folderPicker.launch(null) },
            )
            Text(
                "Tap the address to change the folder for this song. Set a default in Settings.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = baseName,
                    onValueChange = { baseName = it },
                    label = { Text("File name") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                FormatToken(
                    selected = vm.exportFormat,
                    onSelect = { vm.changeExportFormat(it) },
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Checkbox(
                    checked = vm.exportLoopOnly,
                    onCheckedChange = { vm.changeExportLoopOnly(it) },
                    enabled = vm.loops.any { it.enabled },
                )
                Text(
                    "Render only the enabled loops",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.clickable { onOpenTimeline() },
                )
            }

            var confirmCancel by remember { mutableStateOf(false) }
            if (vm.exporting) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = {},
                        enabled = false,
                        modifier = Modifier.weight(2f),
                    ) {
                        Text("Rendering...")
                    }
                    OutlinedButton(
                        onClick = { confirmCancel = true },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Cancel")
                    }
                }
            } else {
                Button(
                    onClick = { vm.renderAs(baseName.trim().ifEmpty { null }) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Save file")
                }
                OutlinedButton(
                    onClick = { vm.shareAs(baseName.trim().ifEmpty { null }) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Share")
                }
            }
            if (confirmCancel) {
                AlertDialog(
                    onDismissRequest = { confirmCancel = false },
                    title = { Text("Cancel render?") },
                    text = { Text("The file will not be saved.") },
                    confirmButton = {
                        TextButton(onClick = {
                            vm.cancelExport()
                            confirmCancel = false
                        }) { Text("Cancel render") }
                    },
                    dismissButton = {
                        TextButton(onClick = { confirmCancel = false }) { Text("Keep rendering") }
                    },
                )
            }
            vm.exportMessage?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                    maxLines = 1,
                )
            }
        }
    }

    renamingPitch?.let { variant ->
        var text by remember(variant.id) { mutableStateOf(variant.name ?: "") }
        AlertDialog(
            onDismissRequest = { renamingPitch = null },
            title = { Text("Name this pitch") },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    label = { Text("Name") },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.renameVariant(variant, text)
                    renamingPitch = null
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { renamingPitch = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun FormatToken(selected: ExportFormat, onSelect: (ExportFormat) -> Unit) {
    val entries = ExportFormat.entries
    val index = entries.indexOf(selected).coerceAtLeast(0)
    val current by rememberUpdatedState(selected)
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color.White.copy(alpha = 0.06f),
        modifier = Modifier
            .padding(start = 8.dp)
            .pointerInput(entries) {
                val stepPx = 28f * density
                awaitEachGesture {
                    val down = awaitFirstDown()
                    var emitted = entries.indexOf(current)
                    var accum = 0f
                    var lastY = down.position.y
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        accum += change.position.y - lastY
                        lastY = change.position.y
                        while (accum >= stepPx) {
                            accum -= stepPx
                            emitted = (emitted + 1).coerceAtMost(entries.lastIndex)
                            onSelect(entries[emitted])
                        }
                        while (accum <= -stepPx) {
                            accum += stepPx
                            emitted = (emitted - 1).coerceAtLeast(0)
                            onSelect(entries[emitted])
                        }
                        change.consume()
                        if (!change.pressed) break
                    }
                }
            },
    ) {
        Column(
            modifier = Modifier.width(72.dp).padding(vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val previous = entries.getOrNull(index - 1)
            val next = entries.getOrNull(index + 1)
            Text(
                previous?.id ?: " ",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                maxLines = 1,
            )
            Text(
                selected.id,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
            )
            Text(
                next?.id ?: " ",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                maxLines = 1,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(vm: PitcherViewModel, onDismiss: () -> Unit) {
    LaunchedEffect(vm.onboardingActive) {
        if (vm.onboardingActive) onDismiss()
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            pitcher.android.ui.SettingsContent(vm = vm)
            Spacer(modifier = Modifier.padding(bottom = 24.dp))
        }
    }
}
