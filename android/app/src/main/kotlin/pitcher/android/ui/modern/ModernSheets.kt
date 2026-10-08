package pitcher.android.ui.modern

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import pitcher.android.media.RenderedStore
import pitcher.android.ui.PitcherViewModel
import pitcher.core.ExportFormat
import pitcher.core.Notes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportSheet(vm: PitcherViewModel, onDismiss: () -> Unit) {
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
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Render", style = MaterialTheme.typography.headlineSmall)

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
                Text(
                    ".$ext",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 6.dp),
                )
                FormatStrip(
                    selected = vm.exportFormat,
                    onSelect = { vm.changeExportFormat(it) },
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clickable { vm.changeExportLoopOnly(!vm.exportLoopOnly) },
            ) {
                Checkbox(
                    checked = vm.exportLoopOnly,
                    onCheckedChange = { vm.changeExportLoopOnly(it) },
                    enabled = vm.loops.any { it.enabled },
                )
                Text("Render only the enabled loops", style = MaterialTheme.typography.bodyMedium)
            }

            Button(
                onClick = { vm.renderAs(baseName.trim().ifEmpty { null }) },
                enabled = !vm.exporting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (vm.exporting) "Rendering..." else "Save file")
            }
            OutlinedButton(
                onClick = { vm.shareAs(baseName.trim().ifEmpty { null }) },
                enabled = !vm.exporting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Share")
            }
            if (vm.exporting) {
                OutlinedButton(
                    onClick = { vm.cancelExport() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Cancel render")
                }
            }
            vm.exportMessage?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
            }
        }
    }
}

@Composable
private fun FormatStrip(selected: ExportFormat, onSelect: (ExportFormat) -> Unit) {
    Column(modifier = Modifier.width(78.dp).padding(start = 8.dp)) {
        ExportFormat.entries.forEach { fmt ->
            val active = fmt == selected
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (active) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                } else {
                    Color.White.copy(alpha = 0.05f)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp)
                    .clickable { onSelect(fmt) },
            ) {
                Text(
                    fmt.id,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (active) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    textAlign = TextAlign.Center,
                )
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
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            pitcher.android.ui.SettingsContent(vm = vm)
            Spacer(modifier = Modifier.padding(bottom = 24.dp))
        }
    }
}
