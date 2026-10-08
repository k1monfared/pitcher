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
import pitcher.android.data.Bookmark
import pitcher.android.data.Variant
import pitcher.android.ui.PitcherViewModel
import pitcher.android.ui.theme.PitchHues

@Composable
fun StudioScreen(
    vm: PitcherViewModel,
    onOpenLibrary: () -> Unit,
    onOpenExport: () -> Unit,
    onOpenSettings: () -> Unit,
    onboarding: OnboardingTargets? = null,
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
        loopEnabled = vm.loopEnabled,
        bookmarks = vm.bookmarks,
        playing = vm.isPlaying,
        tempo = vm.tempo,
        variants = vm.variants,
        selectedVariantId = vm.selectedVariantId,
        saving = vm.exporting,
        statusMessage = vm.exportMessage,
        onCents = { vm.setPitchCents(it) },
        onSnapToggle = { snap = !snap },
        onSeekMs = { vm.seekTo(it) },
        onSetLoop = { a, b -> if (a != null && b != null) vm.setLoop(a, b) else vm.clearLoop() },
        onPlayPause = { vm.togglePlay() },
        onSkip = { delta -> vm.seekTo(vm.positionMs + delta) },
        onTempo = { vm.changeTempo(it) },
        onLoopTap = { vm.loopChipTap() },
        onLoopClear = { vm.clearLoop() },
        onAddBookmark = { vm.addBookmark(null) },
        onSelectOriginal = { vm.selectOriginal() },
        onSelectVariant = { vm.selectVariant(it) },
        onSaveCurrent = { vm.renderAndKeep(null) },
        onRenameVariant = { v, name -> vm.renameVariant(v, name) },
        onExportVariant = { vm.renderAndKeepVariant(it) },
        onShareVariant = { vm.shareVariant(it) },
        onDeleteVariant = { vm.deleteVariant(it) },
        onOpenLibrary = onOpenLibrary,
        onOpenExport = onOpenExport,
        onOpenSettings = onOpenSettings,
        onCancelRender = { vm.cancelExport() },
        onboarding = onboarding,
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
    loopEnabled: Boolean,
    bookmarks: List<Bookmark>,
    playing: Boolean,
    tempo: Float,
    variants: List<Variant>,
    selectedVariantId: Long?,
    saving: Boolean,
    statusMessage: String?,
    onCents: (Int) -> Unit,
    onSnapToggle: () -> Unit,
    onSeekMs: (Long) -> Unit,
    onSetLoop: (Long?, Long?) -> Unit,
    onPlayPause: () -> Unit,
    onSkip: (Long) -> Unit,
    onTempo: (Float) -> Unit,
    onLoopTap: () -> Unit,
    onLoopClear: () -> Unit,
    onAddBookmark: () -> Unit,
    onSelectOriginal: () -> Unit,
    onSelectVariant: (Variant) -> Unit,
    onSaveCurrent: () -> Unit,
    onRenameVariant: (Variant, String) -> Unit,
    onExportVariant: (Variant) -> Unit,
    onShareVariant: (Variant) -> Unit,
    onDeleteVariant: (Variant) -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenExport: () -> Unit,
    onOpenSettings: () -> Unit,
    onCancelRender: () -> Unit = {},
    onboarding: OnboardingTargets? = null,
) {
    val accent by animateColorAsState(targetValue = PitchHues.forCents(cents), label = "accent")
    var speedDragging by remember { mutableStateOf(false) }

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
            StudioTopBar(title = title, artist = artist, onOpenLibrary = onOpenLibrary)

            PitchPad(
                cents = cents,
                snap = snap,
                accent = accent,
                onCents = onCents,
                onboarding = onboarding,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    intervalWords(cents),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row {
                    TextButton(onClick = onAddBookmark) { Text("bookmark") }
                    TextButton(
                        onClick = onSnapToggle,
                        modifier = Modifier.onboardingTarget(onboarding, "snap"),
                    ) {
                        Text(if (snap) "snap: on" else "snap: off")
                    }
                }
            }

            WaveformScrubber(
                peaks = peaks,
                positionMs = positionMs,
                durationMs = durationMs,
                loopStartMs = loopStartMs,
                loopEndMs = loopEndMs,
                bookmarks = bookmarks,
                accent = accent,
                onSeekMs = onSeekMs,
                onSetLoop = onSetLoop,
                onboarding = onboarding,
                modifier = Modifier.fillMaxWidth(),
            )

            TransportBar(
                playing = playing,
                tempo = tempo,
                loopStartMs = loopStartMs,
                loopEndMs = loopEndMs,
                loopEnabled = loopEnabled,
                accent = accent,
                onPlayPause = onPlayPause,
                onSkip = onSkip,
                onTempo = onTempo,
                onSpeedDragging = { speedDragging = it },
                onLoopTap = onLoopTap,
                onLoopClear = onLoopClear,
                onboarding = onboarding,
            )

            PitchShelf(
                variants = variants,
                selectedVariantId = selectedVariantId,
                faderCents = cents,
                accent = accent,
                onSelectOriginal = onSelectOriginal,
                onSelectVariant = onSelectVariant,
                onSaveCurrent = onSaveCurrent,
                onRenameVariant = onRenameVariant,
                onExportVariant = onExportVariant,
                onShareVariant = onShareVariant,
                onDeleteVariant = onDeleteVariant,
                saving = saving,
                onboarding = onboarding,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            )

            if (statusMessage != null || saving) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        statusMessage ?: "rendering...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    if (saving) {
                        TextButton(onClick = onCancelRender) { Text("cancel") }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                TextButton(
                    onClick = onOpenExport,
                    modifier = Modifier.onboardingTarget(onboarding, "export"),
                ) { Text("Export this pitch") }
                TextButton(onClick = onOpenSettings) { Text("Settings") }
            }
        }

        if (speedDragging) {
            SpeedHud(
                tempo = tempo,
                accent = accent,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}

@Composable
private fun StudioTopBar(
    title: String,
    artist: String?,
    onOpenLibrary: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        TextButton(
            onClick = onOpenLibrary,
            modifier = Modifier.align(Alignment.CenterStart),
        ) { Text("Library") }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.align(Alignment.Center),
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
