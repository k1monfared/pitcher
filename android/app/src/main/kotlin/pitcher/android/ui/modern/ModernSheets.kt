package pitcher.android.ui.modern

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import pitcher.android.media.RenderedStore
import pitcher.android.ui.PitcherViewModel
import pitcher.core.ChartLabels
import pitcher.core.ExportFormat
import pitcher.core.RenderPlan

private val FORMAT_PILL_HEIGHT = 34.dp
private val FORMAT_PILL_GAP = 4.dp

/**
 * Saves the current pitch as a file. The file name follows the pitch until it
 * is edited. `Save file` turns into `Saved` once a file with this name, format,
 * and loop set exists, and only comes back when one of those changes. `Share`
 * shares that file, rendering only if there is none. The two buttons keep
 * their place while rendering: they read `Saving...` and `Cancel`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportSheet(vm: PitcherViewModel, onDismiss: () -> Unit, onOpenTimeline: () -> Unit = {}) {
    val context = LocalContext.current
    val track = vm.current
    val format = vm.exportFormat
    val defaultBase = vm.defaultFileBase()
    var typed by remember(track?.id) { mutableStateOf<String?>(null) }
    val fileBase = typed ?: defaultBase
    var match by remember { mutableStateOf<RenderPlan.Record?>(null) }
    var confirmCancel by remember { mutableStateOf(false) }

    LaunchedEffect(fileBase, format, vm.exportLoopOnly, vm.loops, vm.faderCents, vm.rendersVersion) {
        match = vm.matchingRender(fileBase)
    }

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
            val pitch = vm.selectedVariant
            val cents = vm.faderCents
            val centsText = if (cents >= 0) "+${cents}c" else "${cents}c"
            Column {
                Text("Save as a file", style = MaterialTheme.typography.headlineSmall)
                Text(
                    listOfNotNull(pitch?.name?.takeIf { it.isNotBlank() }, centsText).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            val folder = vm.effectiveFolder()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Folder: ${RenderedStore.folderLabel(folder)}" + if (folder == null) " (default)" else "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).clickable { folderPicker.launch(null) },
                )
                if (track?.saveFolder != null) {
                    TextButton(onClick = { vm.setSongFolder(null) }) { Text("use default") }
                } else {
                    TextButton(onClick = { folderPicker.launch(null) }) { Text("change") }
                }
            }

            Row(verticalAlignment = Alignment.Top) {
                OutlinedTextField(
                    value = fileBase,
                    onValueChange = { typed = it },
                    label = { Text("File name") },
                    suffix = { Text(".${format.extension}") },
                    modifier = Modifier.weight(1f),
                )
                FormatPills(
                    selected = format,
                    onSelect = { vm.changeExportFormat(it) },
                    modifier = Modifier.padding(start = 12.dp, top = 6.dp),
                )
            }
            if (typed != null && typed != defaultBase) {
                TextButton(onClick = { typed = null }) { Text("use the default name") }
            }

            val hasEnabledLoops = vm.loops.any { it.enabled }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f)
                        .clickable(enabled = hasEnabledLoops) {
                            vm.changeExportLoopOnly(!vm.exportLoopOnly)
                        },
                ) {
                    Checkbox(
                        checked = vm.exportLoopOnly && hasEnabledLoops,
                        onCheckedChange = null,
                        enabled = hasEnabledLoops,
                    )
                    Text(
                        if (hasEnabledLoops) "Only the enabled loops" else "No loops to render",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                TextButton(onClick = onOpenTimeline) { Text("edit loops") }
            }

            // One fixed line, so the buttons below never move.
            val status = when {
                vm.exporting -> vm.exportMessage ?: "rendering..."
                match != null -> "Saved in ${RenderedStore.folderLabel(folder)}"
                else -> vm.exportMessage?.takeIf { it.startsWith("render failed") || it == "cancelled" } ?: ""
            }
            Text(
                status,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().height(18.dp),
            )

            val saving = vm.exporting && vm.renderAction == PitcherViewModel.RenderAction.SAVE
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = { vm.saveFile(fileBase) },
                    enabled = !vm.exporting && match == null && fileBase.isNotBlank(),
                    modifier = Modifier.weight(1f).height(48.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (saving) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Text("Saving...")
                            }
                        } else {
                            Text(if (match != null) "Saved" else "Save file")
                        }
                    }
                }
                OutlinedButton(
                    onClick = {
                        if (vm.exporting) confirmCancel = true else vm.shareFile(fileBase)
                    },
                    enabled = vm.exporting || fileBase.isNotBlank(),
                    modifier = Modifier.weight(1f).height(48.dp),
                ) {
                    Text(if (vm.exporting) "Cancel" else "Share")
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
        }
    }
}

/**
 * Every format as its own pill in a column. Tap a pill, or press and slide:
 * the choice follows the finger one pill at a time, like the speed control.
 */
@Composable
private fun FormatPills(
    selected: ExportFormat,
    onSelect: (ExportFormat) -> Unit,
    modifier: Modifier = Modifier,
) {
    val entries = ExportFormat.entries
    val current by rememberUpdatedState(selected)
    val onSelectNow by rememberUpdatedState(onSelect)
    Column(
        verticalArrangement = Arrangement.spacedBy(FORMAT_PILL_GAP),
        modifier = modifier.pointerInput(Unit) {
            val stepPx = (FORMAT_PILL_HEIGHT + FORMAT_PILL_GAP).toPx()
            awaitEachGesture {
                val down = awaitFirstDown()
                val startIndex = entries.indexOf(current)
                var moved = false
                var picked = startIndex
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    val dy = change.position.y - down.position.y
                    if (!moved && kotlin.math.abs(dy) > viewConfiguration.touchSlop) moved = true
                    if (moved) {
                        val idx = ChartLabels.slideIndex(startIndex, dy, stepPx, entries.size)
                        if (idx != picked) {
                            picked = idx
                            onSelectNow(entries[idx])
                        }
                    }
                    change.consume()
                    if (!change.pressed) break
                }
                if (!moved) {
                    val idx = (down.position.y / stepPx).toInt().coerceIn(0, entries.lastIndex)
                    onSelectNow(entries[idx])
                }
            }
        },
    ) {
        entries.forEach { f ->
            val on = f == selected
            Surface(
                shape = RoundedCornerShape(50),
                color = if (on) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.06f),
                modifier = Modifier.width(72.dp).height(FORMAT_PILL_HEIGHT),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        f.extension,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                        color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
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
