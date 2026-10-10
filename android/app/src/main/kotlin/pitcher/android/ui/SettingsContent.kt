package pitcher.android.ui

import android.content.Intent
import android.net.Uri
import pitcher.android.BuildConfig
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun SettingsContent(vm: PitcherViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
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
            vm.updateDefaultFolder(uri.toString())
        }
    }

    Column(
        modifier = modifier.fillMaxWidth().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (BuildConfig.SUPPORT_LINKS) {
            SupportLinksSection(onOpen = { url ->
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                }
            })
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
        }

        Text("Playback", style = MaterialTheme.typography.titleMedium)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Keep screen on", style = MaterialTheme.typography.bodyMedium)
            Switch(checked = vm.keepScreenOn, onCheckedChange = { vm.changeKeepScreenOn(it) })
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Haptics", style = MaterialTheme.typography.bodyMedium)
            Switch(checked = vm.hapticsEnabled, onCheckedChange = { vm.changeHaptics(it) })
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        Text("Storage", style = MaterialTheme.typography.titleMedium)
        Text(
            "Imported audio: ${fmtSize(vm.storageImportsBytes)}",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            "Kept pitches, rendered in the background: ${fmtSize(vm.storageExportsBytes)}",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            "Clearing these is safe. They are rendered again when needed. Files you saved " +
                "stay in their folder.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = { vm.clearExports() }, enabled = vm.storageExportsBytes > 0) {
            Text("Clear background renders")
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        Text("Saves", style = MaterialTheme.typography.titleMedium)
        Text(
            "Default folder: ${vm.defaultFolder ?: "Music/pitcher"}",
            style = MaterialTheme.typography.bodyMedium,
        )
        Button(onClick = { folderPicker.launch(null) }) { Text("Choose default folder") }
        if (vm.defaultFolder != null) {
            Text(
                "A song can override this from the render sheet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        Text("Engines", style = MaterialTheme.typography.titleMedium)
        Text(
            "Playback and saved files: Media3 Sonic, so files match the preview\n" +
                "Codecs: Android's own open-source codecs first\n" +
                "Tuner: built-in YIN",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        Text("Guided tour", style = MaterialTheme.typography.titleMedium)
        Text(
            "A short tour points out the pitch gesture, the loops, the play speed, and render.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = { vm.startOnboarding() }) { Text("Replay the tour") }

        Text("pitcher ${BuildConfig.VERSION_NAME} for Android", style = MaterialTheme.typography.bodySmall)
        Text(
            "Offline only. This app requests no network permission. Free software under " +
                "the GPL-3.0.",
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
