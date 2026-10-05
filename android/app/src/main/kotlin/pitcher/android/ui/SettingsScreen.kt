package pitcher.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreenContent(
    importsBytes: Long,
    exportsBytes: Long,
    keepScreenOn: Boolean,
    onKeepScreenOn: (Boolean) -> Unit,
    onClearExports: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Storage", style = MaterialTheme.typography.titleMedium)
        Text(
            "Imported audio: ${fmtSize(importsBytes)}",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            "Exported renders (cache): ${fmtSize(exportsBytes)}",
            style = MaterialTheme.typography.bodyMedium,
        )
        Button(onClick = onClearExports, enabled = exportsBytes > 0) {
            Text("Clear exported renders")
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        Text("Playback", style = MaterialTheme.typography.titleMedium)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Keep screen on while playing", style = MaterialTheme.typography.bodyMedium)
            Switch(checked = keepScreenOn, onCheckedChange = onKeepScreenOn)
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        Text("Engines", style = MaterialTheme.typography.titleMedium)
        Text(
            "Live preview: Media3 Sonic (pitch at fixed tempo)\n" +
                "Kept/export renders: in-app WSOLA (mono WAV)\n" +
                "Tuner: built-in YIN",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
        Text("pitcher for Android 0.1.0", style = MaterialTheme.typography.bodySmall)
        Text(
            "Offline only. This app requests no network permission.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun fmtSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = listOf("B", "KB", "MB", "GB")
    var value = bytes.toDouble()
    var i = 0
    while (value >= 1024 && i < units.lastIndex) {
        value /= 1024
        i++
    }
    return if (i == 0) "${bytes} B" else String.format("%.1f %s", value, units[i])
}
