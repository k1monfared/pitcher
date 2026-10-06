package pitcher.android.ui.modern

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import pitcher.android.data.Variant
import pitcher.android.ui.PitcherViewModel
import pitcher.android.ui.theme.PitchHues

@Composable
fun StudioScreen(
    vm: PitcherViewModel,
    onOpenLibrary: () -> Unit,
    onOpenPitches: () -> Unit,
    onOpenExport: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val track = vm.current
    if (track == null) {
        EmptyStudio(onOpenLibrary)
        return
    }
    var snap by remember { mutableStateOf(false) }
    StudioContent(
        title = track.title,
        artist = track.artist,
        cents = vm.faderCents,
        snap = snap,
        peaks = vm.peaks,
        positionMs = vm.positionMs,
        durationMs = vm.durationMs,
        loopStartMs = vm.loopStartMs,
        loopEndMs = vm.loopEndMs,
        playing = vm.isPlaying,
        tempo = vm.tempo,
        loopEnabled = vm.loopEnabled,
        variants = vm.variants,
        selectedVariantId = vm.selectedVariantId,
        onCents = { vm.setPitchCents(it) },
        onSnapToggle = { snap = !snap },
        onSeekMs = { vm.seekTo(it) },
        onSetLoop = { a, b -> if (a != null && b != null) vm.setLoop(a, b) else vm.clearLoop() },
        onPlayPause = { vm.togglePlay() },
        onSkip = { delta -> vm.seekTo(vm.positionMs + delta) },
        onTempo = { vm.changeTempo(it) },
        onLoopToggle = { vm.toggleLoop() },
        onSelectOriginal = { vm.selectOriginal() },
        onSelectVariant = { vm.selectVariant(it) },
        onSaveCurrent = { vm.renderAndKeep(null) },
        onOpenLibrary = onOpenLibrary,
        onOpenPitches = onOpenPitches,
        onOpenExport = onOpenExport,
        onOpenSettings = onOpenSettings,
    )
}

@Composable
fun StudioContent(
    title: String,
    artist: String?,
    cents: Int,
    snap: Boolean,
    peaks: FloatArray,
    positionMs: Long,
    durationMs: Long,
    loopStartMs: Long?,
    loopEndMs: Long?,
    playing: Boolean,
    tempo: Float,
    loopEnabled: Boolean,
    variants: List<Variant>,
    selectedVariantId: Long?,
    onCents: (Int) -> Unit,
    onSnapToggle: () -> Unit,
    onSeekMs: (Long) -> Unit,
    onSetLoop: (Long?, Long?) -> Unit,
    onPlayPause: () -> Unit,
    onSkip: (Long) -> Unit,
    onTempo: (Float) -> Unit,
    onLoopToggle: () -> Unit,
    onSelectOriginal: () -> Unit,
    onSelectVariant: (Variant) -> Unit,
    onSaveCurrent: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenPitches: () -> Unit,
    onOpenExport: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val accent by animateColorAsState(targetValue = PitchHues.forCents(cents), label = "accent")

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF08080B))) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .blur(120.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(accent.copy(alpha = 0.35f), Color.Transparent),
                    ),
                ),
        )

        Column(
            modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 16.dp),
        ) {
            StudioTopBar(
                title = title,
                artist = artist,
                onOpenLibrary = onOpenLibrary,
                onOpenPitches = onOpenPitches,
            )

            PitchPad(
                cents = cents,
                snap = snap,
                accent = accent,
                onCents = onCents,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    intervalWords(cents),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = onSnapToggle) {
                    Text(if (snap) "snap: semitone" else "snap: off")
                }
            }

            WaveformScrubber(
                peaks = peaks,
                positionMs = positionMs,
                durationMs = durationMs,
                loopStartMs = loopStartMs,
                loopEndMs = loopEndMs,
                accent = accent,
                onSeekMs = onSeekMs,
                onSetLoop = onSetLoop,
                modifier = Modifier.fillMaxWidth(),
            )

            TransportBar(
                playing = playing,
                tempo = tempo,
                loopEnabled = loopEnabled,
                accent = accent,
                onPlayPause = onPlayPause,
                onSkip = onSkip,
                onTempo = onTempo,
                onLoopToggle = onLoopToggle,
            )

            PitchShelf(
                variants = variants,
                selectedVariantId = selectedVariantId,
                faderCents = cents,
                accent = accent,
                onSelectOriginal = onSelectOriginal,
                onSelectVariant = onSelectVariant,
                onSaveCurrent = onSaveCurrent,
                onOpenPitches = onOpenPitches,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                TextButton(onClick = onOpenExport) { Text("Export this pitch") }
                TextButton(onClick = onOpenSettings) { Text("Settings") }
            }
        }
    }
}

@Composable
private fun StudioTopBar(
    title: String,
    artist: String?,
    onOpenLibrary: () -> Unit,
    onOpenPitches: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        TextButton(onClick = onOpenLibrary) { Text("Library") }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.weight(1f),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            artist?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        TextButton(onClick = onOpenPitches) { Text("Pitches") }
    }
}

@Composable
private fun EmptyStudio(onOpenLibrary: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xFF08080B)).safeDrawingPadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("No song open", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Import a song to start shifting its pitch.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onOpenLibrary) { Text("Open Library") }
        }
    }
}

private val INTERVALS = listOf(
    "unison", "minor second", "major second", "minor third", "major third", "perfect fourth",
    "tritone", "perfect fifth", "minor sixth", "major sixth", "minor seventh", "major seventh",
    "octave",
)

fun intervalWords(cents: Int): String {
    if (cents == 0) return "unison"
    val semis = kotlin.math.abs(cents) / 100.0
    val whole = kotlin.math.round(semis).toInt()
    val dir = if (cents < 0) "down" else "up"
    val name = INTERVALS[whole.coerceIn(0, INTERVALS.size - 1)]
    val exact = if (kotlin.math.abs(semis - whole) < 0.05) name else "%.2f semitones".format(semis)
    return "$dir ${if (whole == 0) "a fraction of a semitone" else "a $exact"}"
}
