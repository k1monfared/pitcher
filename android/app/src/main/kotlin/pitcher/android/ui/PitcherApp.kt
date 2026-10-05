package pitcher.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

enum class Destination(val label: String) {
    Library("Library"),
    Player("Player"),
    Pitch("Pitch"),
    Tuner("Tuner"),
    Pitches("Pitches"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PitcherApp(vm: PitcherViewModel = viewModel()) {
    var current by remember { mutableStateOf(Destination.Library) }
    var showSettings by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (showSettings) "Settings" else current.label) },
                actions = {
                    TextButton(onClick = { showSettings = !showSettings }) {
                        Text(if (showSettings) "Done" else "Settings")
                    }
                },
            )
        },
        bottomBar = {
            if (!showSettings) {
                NavigationBar {
                    Destination.entries.forEach { dest ->
                        NavigationBarItem(
                            selected = current == dest,
                            onClick = { current = dest },
                            icon = { Text(dest.label.take(1)) },
                            label = { Text(dest.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        val modifier = Modifier.fillMaxSize().padding(padding)
        if (showSettings) {
            SettingsScreen(modifier)
        } else {
            when (current) {
                Destination.Library -> LibraryScreen(
                    tracks = vm.tracks,
                    message = vm.message,
                    onImport = { uri -> vm.import(uri) {} },
                    onOpen = { track ->
                        vm.openTrack(track)
                        current = Destination.Player
                    },
                    onDelete = { vm.deleteTrack(it) },
                    modifier = modifier,
                )
                Destination.Player -> PlayerScreen(
                    track = vm.current,
                    peaks = vm.peaks,
                    positionMs = vm.positionMs,
                    durationMs = vm.durationMs,
                    isPlaying = vm.isPlaying,
                    bookmarks = vm.bookmarks,
                    onTogglePlay = { vm.togglePlay() },
                    onSeekMs = { vm.seekTo(it) },
                    onAddBookmark = { vm.addBookmark(it) },
                    onDeleteBookmark = { vm.deleteBookmark(it) },
                    modifier = modifier,
                )
                Destination.Pitch -> PitchLabScreen(
                    track = vm.current,
                    variants = vm.variants,
                    cents = vm.faderCents,
                    selectedVariantId = vm.selectedVariantId,
                    onCents = { vm.setPitchCents(it) },
                    onKeep = { vm.keepCurrent(it) },
                    modifier = modifier,
                )
                Destination.Tuner -> TunerScreen(
                    track = vm.current,
                    detectedHz = vm.detectedHz,
                    detectMessage = vm.detectMessage,
                    pendingShiftCents = vm.faderCents,
                    onDetect = { vm.detectAtPlayhead() },
                    onApply = { vm.setPitchCents(it) },
                    modifier = modifier,
                )
                Destination.Pitches -> PitchesScreen(
                    track = vm.current,
                    variants = vm.variants,
                    selectedVariantId = vm.selectedVariantId,
                    onSelectOriginal = { vm.selectOriginal() },
                    onSelectVariant = { vm.selectVariant(it) },
                    onRenameVariant = { v, n -> vm.renameVariant(v, n) },
                    onDeleteVariant = { vm.deleteVariant(it) },
                    modifier = modifier,
                )
            }
        }
    }
}

@Composable
private fun PlaceholderScreen(title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Start,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SettingsScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Engine quality, default formats, keep-screen-on, background playback, storage.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
