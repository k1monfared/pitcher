package pitcher.android.ui.modern

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import pitcher.android.media.RenderedStore
import pitcher.android.ui.PitcherViewModel
import pitcher.core.ExportFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportSheet(vm: PitcherViewModel, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Export", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Rendered pitches are saved to ${RenderedStore.FOLDER} and kept in the library.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name this pitch (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Text("format", style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ExportFormat.entries.forEach { fmt ->
                    FilterChip(
                        selected = vm.exportFormat == fmt,
                        onClick = { vm.changeExportFormat(fmt) },
                        label = { Text(fmt.id) },
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = vm.exportLoopOnly,
                    onCheckedChange = { vm.changeExportLoopOnly(it) },
                    enabled = vm.loops.any { it.enabled },
                )
                Text("Render only the loops", style = MaterialTheme.typography.bodyMedium)
            }

            Button(
                onClick = { vm.renderAndKeep(name.trim().ifEmpty { null }) },
                enabled = !vm.exporting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (vm.exporting) "Rendering..." else "Save to ${RenderedStore.FOLDER}")
            }
            OutlinedButton(
                onClick = { vm.exportCurrent(name.trim().ifEmpty { null }) },
                enabled = !vm.exporting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Export / share")
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(vm: PitcherViewModel, onDismiss: () -> Unit) {
    LaunchedEffect(vm.onboardingActive) {
        if (vm.onboardingActive) onDismiss()
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            pitcher.android.ui.SettingsContent(vm = vm)
            androidx.compose.foundation.layout.Spacer(
                modifier = Modifier.padding(bottom = 24.dp),
            )
        }
    }
}
