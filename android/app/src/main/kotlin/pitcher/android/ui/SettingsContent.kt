package pitcher.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SettingsContent(vm: PitcherViewModel, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Interface", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = vm.uiStyle == UiStyle.MODERN,
                onClick = { vm.changeUiStyle(UiStyle.MODERN) },
                label = { Text("Modern") },
            )
            FilterChip(
                selected = vm.uiStyle == UiStyle.CLASSIC,
                onClick = { vm.changeUiStyle(UiStyle.CLASSIC) },
                label = { Text("Classic") },
            )
        }
        Text(
            "Both share the same engine, library, and playback. Classic is kept as a fallback.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        Text("Playback", style = MaterialTheme.typography.titleMedium)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Keep screen on", style = MaterialTheme.typography.bodyMedium)
            Switch(checked = vm.keepScreenOn, onCheckedChange = { vm.changeKeepScreenOn(it) })
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        Text("Storage", style = MaterialTheme.typography.titleMedium)
        Text(
            "Imported audio: ${fmtSize(vm.storageImportsBytes)}",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            "Exported renders (cache): ${fmtSize(vm.storageExportsBytes)}",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            "Rendered pitches live in ${pitcher.android.media.RenderedStore.FOLDER}.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = { vm.clearExports() }, enabled = vm.storageExportsBytes > 0) {
            Text("Clear exported renders")
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        Text("Engines", style = MaterialTheme.typography.titleMedium)
        Text(
            "Live preview: Media3 Sonic (pitch at fixed tempo)\n" +
                "Renders: in-app WSOLA (stereo, mono fallback)\n" +
                "Tuner: built-in YIN",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Text("pitcher for Android 2.0.0", style = MaterialTheme.typography.bodySmall)
        Text(
            "Offline only. This app requests no network permission.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

fun fmtSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = listOf("B", "KB", "MB", "GB")
    var value = bytes.toDouble()
    var i = 0
    while (value >= 1024 && i < units.lastIndex) {
        value /= 1024
        i++
    }
    return if (i == 0) "$bytes B" else String.format("%.1f %s", value, units[i])
}
