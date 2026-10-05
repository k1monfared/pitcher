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

enum class Destination(val label: String) {
    Library("Library"),
    Player("Player"),
    Pitch("Pitch"),
    Tuner("Tuner"),
    Pitches("Pitches"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PitcherApp() {
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
                Destination.Library -> PlaceholderScreen(
                    "Library",
                    "Import an audio or video file, then rename or delete tracks. " +
                        "Files you imported stay yours; only pitcher's own renders are removed.",
                    modifier,
                )
                Destination.Player -> PlaceholderScreen(
                    "Player",
                    "Zoomable waveform with precise gesture seeking, loop, speed, and bookmarks. " +
                        "Screen stays on while this is open.",
                    modifier,
                )
                Destination.Pitch -> PlaceholderScreen(
                    "Pitch Lab",
                    "Move the fader in cents or semitones and keep the pitches you like. " +
                        "The fader always reads total cents from the original.",
                    modifier,
                )
                Destination.Tuner -> PlaceholderScreen(
                    "Tuner",
                    "Detect the note at the playhead, or type a note or a frequency. " +
                        "Target note and target Hz fill each other in.",
                    modifier,
                )
                Destination.Pitches -> PlaceholderScreen(
                    "Pitches",
                    "The original plus every kept pitch, sorted by how far it shifted.",
                    modifier,
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
