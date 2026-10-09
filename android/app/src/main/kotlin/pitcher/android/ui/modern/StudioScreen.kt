package pitcher.android.ui.modern

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import pitcher.android.data.Bookmark
import pitcher.android.data.LoopSection
import pitcher.android.data.Variant
import pitcher.android.ui.PitcherViewModel
import pitcher.android.ui.theme.PitchHues
import pitcher.core.LoopMode

@Composable
fun StudioScreen(
    vm: PitcherViewModel,
    onOpenLibrary: () -> Unit,
    onOpenExport: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenTuner: () -> Unit = {},
    onOpenTimeline: () -> Unit = {},
    forceSpeedHud: Boolean = false,
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
        loops = vm.loops,
        selectedLoopId = vm.selectedLoopId,
        loopMode = vm.loopMode,
        bookmarks = vm.bookmarks,
        playing = vm.isPlaying,
        tempo = vm.tempo,
        variants = vm.variants,
        busyIds = vm.pitchWork.keys.toSet(),
        follow = vm.followPlayhead,
        onFollowChange = { vm.changeFollowPlayhead(it) },
        onCents = { vm.setPitchCents(it) },
        onSnapToggle = { snap = !snap },
        onSeekMs = { vm.seekTo(it) },
        onCreateLoop = { a, b -> vm.addLoop(a, b) },
        onMoveLoopEdge = { id, isStart, ms -> vm.previewLoopEdge(id, isStart, ms) },
        onCommitLoopEdge = { vm.commitLoopEdge(it) },
        onMoveBookmark = { id, ms -> vm.previewBookmark(id, ms / 1000.0) },
        onCommitBookmark = { vm.commitBookmark(it) },
        onSelectLoop = { vm.selectLoop(it) },
        onRenameLoop = { id, name -> vm.renameLoop(id, name) },
        onDeleteLoop = { vm.deleteLoop(it) },
        onRenameBookmark = { id, name -> vm.renameBookmark(id, name) },
        onDeleteBookmark = { vm.deleteBookmark(it) },
        onCycleLoopMode = { vm.cycleLoopMode() },
        onPlayPause = { vm.togglePlay() },
        onSkip = { delta -> vm.seekTo(vm.positionMs + delta) },
        onTempo = { vm.changeTempo(it) },
        onAddBookmark = { vm.addBookmark(null) },
        onSelectPitch = { variant ->
            if (variant == null) vm.selectOriginal() else vm.selectVariant(variant)
        },
        onKeepPitch = { vm.keepPitch() },
        onOpenFile = onOpenExport,
        onRenameVariant = { v, name -> vm.renameVariant(v, name) },
        onShareVariant = { vm.shareVariant(it) },
        onDeleteVariant = { vm.deleteVariant(it) },
        onCancelVariant = { vm.cancelPitch(it.id) },
        onOpenLibrary = onOpenLibrary,
        onOpenSettings = onOpenSettings,
        onOpenTuner = onOpenTuner,
        onOpenTimeline = onOpenTimeline,
        hapticsEnabled = vm.hapticsEnabled,
        forceSpeedHud = forceSpeedHud,
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
    loops: List<LoopSection>,
    selectedLoopId: Long?,
    loopMode: LoopMode,
    bookmarks: List<Bookmark>,
    playing: Boolean,
    tempo: Float,
    variants: List<Variant>,
    onCents: (Int) -> Unit,
    onSnapToggle: () -> Unit,
    onSeekMs: (Long) -> Unit,
    onCreateLoop: (Long, Long) -> Unit,
    onMoveLoopEdge: (Long, Boolean, Long) -> Unit,
    onMoveBookmark: (Long, Long) -> Unit,
    onSelectLoop: (Long) -> Unit,
    onRenameLoop: (Long, String) -> Unit,
    onDeleteLoop: (Long) -> Unit,
    onRenameBookmark: (Long, String) -> Unit,
    onDeleteBookmark: (Long) -> Unit,
    onCycleLoopMode: () -> Unit,
    onPlayPause: () -> Unit,
    onSkip: (Long) -> Unit,
    onTempo: (Float) -> Unit,
    onAddBookmark: () -> Unit,
    onSelectPitch: (Variant?) -> Unit,
    onKeepPitch: () -> Unit,
    onOpenFile: () -> Unit,
    onRenameVariant: (Variant, String) -> Unit,
    onShareVariant: (Variant) -> Unit,
    onDeleteVariant: (Variant) -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenSettings: () -> Unit,
    busyIds: Set<Long> = emptySet(),
    onCancelVariant: (Variant) -> Unit = {},
    follow: Boolean = false,
    onFollowChange: (Boolean) -> Unit = {},
    onCommitLoopEdge: (Long) -> Unit = {},
    onCommitBookmark: (Long) -> Unit = {},
    onOpenTuner: () -> Unit = {},
    onOpenTimeline: () -> Unit = {},
    hapticsEnabled: Boolean = true,
    forceSpeedHud: Boolean = false,
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
            StudioTopBar(
                title = title,
                artist = artist,
                onOpenLibrary = onOpenLibrary,
                onOpenSettings = onOpenSettings,
            )

            PitchPad(
                cents = cents,
                snap = snap,
                accent = accent,
                onCents = onCents,
                onOpenTuner = onOpenTuner,
                hapticsEnabled = hapticsEnabled,
                caption = intervalWords(cents),
                onboarding = onboarding,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row {
                    TextButton(onClick = onAddBookmark) { Text("bookmark") }
                    TextButton(
                        onClick = onOpenTuner,
                        modifier = Modifier.onboardingTarget(onboarding, "tuner"),
                    ) { Text("tuner") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ToggleChip(
                        label = "follow",
                        on = follow,
                        onClick = { onFollowChange(!follow) },
                    )
                    ToggleChip(
                        label = "snap",
                        on = snap,
                        onClick = onSnapToggle,
                        modifier = Modifier.onboardingTarget(onboarding, "snap"),
                    )
                }
            }

            WaveformScrubber(
                peaks = peaks,
                positionMs = positionMs,
                durationMs = durationMs,
                loops = loops,
                selectedLoopId = selectedLoopId,
                bookmarks = bookmarks,
                accent = accent,
                onSeekMs = onSeekMs,
                onCreateLoop = onCreateLoop,
                onMoveLoopEdge = onMoveLoopEdge,
                onCommitLoopEdge = onCommitLoopEdge,
                onMoveBookmark = onMoveBookmark,
                onCommitBookmark = onCommitBookmark,
                onSelectLoop = onSelectLoop,
                onRenameLoop = onRenameLoop,
                onDeleteLoop = onDeleteLoop,
                onRenameBookmark = onRenameBookmark,
                onDeleteBookmark = onDeleteBookmark,
                playing = playing,
                tempo = tempo,
                follow = follow,
                onFollowChange = onFollowChange,
                onboarding = onboarding,
                modifier = Modifier.fillMaxWidth(),
            )

            TransportBar(
                playing = playing,
                tempo = tempo,
                loopMode = loopMode,
                hasLoops = loops.any { it.enabled },
                accent = accent,
                onPlayPause = onPlayPause,
                onSkip = onSkip,
                onTempo = onTempo,
                onSpeedDragging = { speedDragging = it },
                onCycleLoopMode = onCycleLoopMode,
                onboarding = onboarding,
            )

            PitchShelf(
                variants = variants,
                faderCents = cents,
                accent = accent,
                busyIds = busyIds,
                onSelectPitch = onSelectPitch,
                onKeepPitch = onKeepPitch,
                onOpenFile = onOpenFile,
                onRenameVariant = onRenameVariant,
                onShareVariant = onShareVariant,
                onDeleteVariant = onDeleteVariant,
                onCancelVariant = onCancelVariant,
                onboarding = onboarding,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                TextButton(
                    onClick = onOpenTimeline,
                    modifier = Modifier.onboardingTarget(onboarding, "timeline"),
                ) { Text("loops & bookmarks") }
            }
        }

        if (speedDragging || forceSpeedHud) {
            SpeedHud(
                tempo = tempo,
                accent = accent,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}

@Composable
private fun ToggleChip(
    label: String,
    on: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // No background: on reads as the accent colour and bold, off as muted.
    val interaction = remember { MutableInteractionSource() }
    Text(
        label,
        modifier = modifier
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
        color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun StudioTopBar(
    title: String,
    artist: String?,
    onOpenLibrary: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        TextButton(
            onClick = onOpenLibrary,
            modifier = Modifier.align(Alignment.CenterStart),
        ) { Text("Library") }
        GearButton(
            onClick = onOpenSettings,
            modifier = Modifier.align(Alignment.CenterEnd),
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.align(Alignment.Center).padding(horizontal = 96.dp),
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
private fun GearButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    // No background, tinted like the Library link opposite it.
    val tint = MaterialTheme.colorScheme.primary
    androidx.compose.foundation.Canvas(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(10.dp)
            .size(22.dp),
    ) {
        val w = size.width
        val cx = w / 2f
        val cy = w / 2f
        val teeth = 8
        val outer = w * 0.46f
        val inner = w * 0.30f
        for (i in 0 until teeth) {
            val a = Math.toRadians(i * 360.0 / teeth)
            val x = cx + (outer * 0.62f * kotlin.math.cos(a)).toFloat()
            val y = cy + (outer * 0.62f * kotlin.math.sin(a)).toFloat()
            drawCircle(color = tint, radius = w * 0.11f, center = Offset(x, y))
        }
        drawCircle(color = tint, radius = inner, center = Offset(cx, cy))
        drawCircle(color = Color(0xFF08080B), radius = inner * 0.42f, center = Offset(cx, cy))
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
    return when {
        kotlin.math.abs(semis - whole) < 0.05 && whole in 1 until INTERVALS.size ->
            "$dir ${if (name.first() in "aeiou") "an" else "a"} $name"
        semis < 1.0 -> "$dir a fraction of a semitone"
        else -> "$dir %.2f semitones".format(semis)
    }
}
