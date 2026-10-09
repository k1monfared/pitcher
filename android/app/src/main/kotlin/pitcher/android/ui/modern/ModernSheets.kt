package pitcher.android.ui.modern

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import pitcher.android.media.RenderedStore
import pitcher.android.ui.PitcherViewModel
import pitcher.core.ExportFormat
import pitcher.core.RenderPlan
import pitcher.core.ShelfModel

private val DRUM_ITEM_HEIGHT = 26.dp
private val DRUM_RADIUS = 58.dp
private const val DRUM_STEP_DEGREES = 25f

/**
 * Saves or shares the current pitch as a file. The pitch is kept (and
 * rendering in the background) by the time this opens, so a save only has to
 * encode. The file name follows the pitch until it is edited. `Save file`
 * reads `Saved` once this name, format, and loop set exists, and comes back
 * when one of them changes. The buttons keep their place: while this pitch is
 * saving they read `Saving...` and `Cancel`. Other pitches are not affected.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportSheet(vm: PitcherViewModel, onDismiss: () -> Unit, onOpenTimeline: () -> Unit = {}) {
    val context = LocalContext.current
    val track = vm.current
    val variant = vm.selectedVariant
    val format = vm.exportFormat
    val defaultBase = vm.defaultFileBase()
    var typed by remember(track?.id, variant?.id) { mutableStateOf<String?>(null) }
    val fileBase = typed ?: defaultBase
    var match by remember { mutableStateOf<RenderPlan.Record?>(null) }
    var confirmCancel by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (variant == null) vm.keepPitch()
    }
    LaunchedEffect(
        variant?.id, fileBase, format, vm.exportLoopOnly, vm.loops, vm.exportAtSpeed, vm.tempo, vm.rendersVersion,
    ) {
        match = variant?.let { vm.matchingRender(it.id, fileBase) }
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
            val cents = vm.faderCents
            val centsText = if (cents >= 0) "+${cents}c" else "${cents}c"
            Column {
                Text("Save as a file", style = MaterialTheme.typography.headlineSmall)
                Text(
                    listOfNotNull(variant?.name?.takeIf { it.isNotBlank() }, centsText).joinToString(" · "),
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

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = fileBase,
                    onValueChange = { typed = it },
                    label = { Text("File name") },
                    modifier = Modifier.weight(1f),
                )
                Text(
                    ".",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp),
                )
                FormatDrum(selected = format, onSelect = { vm.changeExportFormat(it) })
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

            val speedChanged = vm.tempo != 1f
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = speedChanged) { vm.changeExportAtSpeed(!vm.exportAtSpeed) },
            ) {
                Checkbox(
                    checked = vm.exportAtSpeed && speedChanged,
                    onCheckedChange = null,
                    enabled = speedChanged,
                )
                Text(
                    if (speedChanged) {
                        "At the current speed (${formatSpeed(vm.tempo)})"
                    } else {
                        "At the current speed (playing at 1x)"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            val work = variant?.let { vm.pitchWork[it.id] }
            val exporting = work == PitcherViewModel.PitchWork.SAVING || work == PitcherViewModel.PitchWork.SHARING
            // One fixed line, so the buttons below never move.
            val status = when {
                work == PitcherViewModel.PitchWork.SAVING -> "saving..."
                work == PitcherViewModel.PitchWork.SHARING -> "getting the file ready to share..."
                work == PitcherViewModel.PitchWork.PREPARING -> "rendering this pitch in the background..."
                match != null -> "Saved in ${RenderedStore.folderLabel(folder)}"
                else -> variant?.let { vm.pitchStatus[it.id] } ?: ""
            }
            Text(
                status,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().height(18.dp),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = { variant?.let { vm.saveFile(it, fileBase) } },
                    enabled = variant != null && !exporting && match == null && fileBase.isNotBlank(),
                    modifier = Modifier.weight(1f).height(48.dp),
                ) {
                    if (work == PitcherViewModel.PitchWork.SAVING) {
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
                OutlinedButton(
                    onClick = {
                        if (exporting) {
                            confirmCancel = true
                        } else {
                            variant?.let { vm.shareFile(it, fileBase) }
                        }
                    },
                    enabled = variant != null && (exporting || fileBase.isNotBlank()),
                    modifier = Modifier.weight(1f).height(48.dp),
                ) {
                    Text(if (exporting) "Cancel" else "Share")
                }
            }

            if (confirmCancel) {
                AlertDialog(
                    onDismissRequest = { confirmCancel = false },
                    title = { Text("Cancel?") },
                    text = { Text("This file will not be saved. Other pitches keep going.") },
                    confirmButton = {
                        TextButton(onClick = {
                            variant?.let { vm.cancelPitch(it.id) }
                            confirmCancel = false
                        }) { Text("Cancel it") }
                    },
                    dismissButton = {
                        TextButton(onClick = { confirmCancel = false }) { Text("Keep going") }
                    },
                )
            }
        }
    }
}

/**
 * The format as a knob: the extensions sit on a drum that rolls with the
 * finger, the one in the middle is chosen, and letting go settles on the
 * nearest. Tapping an extension rolls to it.
 */
@Composable
internal fun FormatDrum(
    selected: ExportFormat,
    onSelect: (ExportFormat) -> Unit,
    modifier: Modifier = Modifier,
) {
    val entries = ExportFormat.entries
    val index = entries.indexOf(selected).coerceAtLeast(0)
    val scroll = remember { Animatable(index.toFloat()) }
    val scope = rememberCoroutineScope()
    val onSelectNow by rememberUpdatedState(onSelect)
    var dragging by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val radiusPx = with(density) { DRUM_RADIUS.toPx() }
    val stepPx = radiusPx * Math.toRadians(DRUM_STEP_DEGREES.toDouble()).toFloat()
    val accent = MaterialTheme.colorScheme.primary

    LaunchedEffect(index) {
        if (!dragging && ShelfModel.drumNearest(scroll.value, entries.size) != index) {
            scroll.animateTo(index.toFloat(), spring(dampingRatio = 0.8f, stiffness = 400f))
        }
    }

    Box(
        modifier = modifier
            .width(76.dp)
            .height(DRUM_RADIUS * 2 + DRUM_ITEM_HEIGHT)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val start = scroll.value
                    var moved = false
                    var picked = ShelfModel.drumNearest(start, entries.size)
                    dragging = true
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        val dy = change.position.y - down.position.y
                        if (!moved && abs(dy) > viewConfiguration.touchSlop) moved = true
                        if (moved) {
                            val next = ShelfModel.drumScroll(start, dy, stepPx)
                                .coerceIn(-0.4f, entries.lastIndex + 0.4f)
                            scope.launch { scroll.snapTo(next) }
                            val nearest = ShelfModel.drumNearest(next, entries.size)
                            if (nearest != picked) {
                                picked = nearest
                                onSelectNow(entries[nearest])
                            }
                        }
                        change.consume()
                        if (!change.pressed) break
                    }
                    if (!moved) {
                        // A tap rolls to the extension under the finger.
                        val centerY = size.height / 2f
                        val tapped = entries.indices.minByOrNull { i ->
                            val slot = ShelfModel.drumSlot(i, scroll.value, DRUM_STEP_DEGREES, radiusPx)
                            if (slot.visible) abs(centerY + slot.y - down.position.y) else Float.MAX_VALUE
                        } ?: picked
                        picked = tapped
                        onSelectNow(entries[tapped])
                    }
                    dragging = false
                    scope.launch {
                        scroll.animateTo(picked.toFloat(), spring(dampingRatio = 0.8f, stiffness = 400f))
                    }
                }
            },
    ) {
        val center = ShelfModel.drumNearest(scroll.value, entries.size)
        entries.forEachIndexed { i, f ->
            val slot = ShelfModel.drumSlot(i, scroll.value, DRUM_STEP_DEGREES, radiusPx)
            if (!slot.visible) return@forEachIndexed
            val on = i == center
            Surface(
                shape = RoundedCornerShape(50),
                color = if (on) accent else Color.White.copy(alpha = 0.08f),
                modifier = Modifier
                    .width(68.dp)
                    .height(DRUM_ITEM_HEIGHT)
                    .align(Alignment.TopCenter)
                    .offset {
                        IntOffset(0, (radiusPx + slot.y).roundToInt())
                    }
                    .graphicsLayer {
                        scaleY = slot.scale
                        alpha = 0.25f + 0.75f * slot.scale
                    },
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
