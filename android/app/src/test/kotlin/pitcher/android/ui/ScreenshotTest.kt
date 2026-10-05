package pitcher.android.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import pitcher.android.data.Bookmark
import pitcher.android.data.Track
import pitcher.android.data.Variant
import pitcher.android.ui.theme.PitcherTheme
import pitcher.core.ExportFormat
import kotlin.math.abs
import kotlin.math.sin

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class ScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private val track = Track(
        id = 1,
        sourcePath = "/data/imports/nava.m4a",
        sourceKind = "file",
        sourceUrl = null,
        title = "Nava Sol Darya",
        artist = "Tasnife Yad Bad",
        durationS = 210.0,
        sampleRate = 44100,
        createdAt = "2026-01-01",
        variantCount = 2,
    )

    private val variants = listOf(
        variant(1, "nava sol darya", -600),
        variant(2, "nava sol up", 700),
    )

    private val bookmarks = listOf(
        Bookmark(1, 1, 12.0, "intro", "now"),
        Bookmark(2, 1, 48.0, null, "now"),
        Bookmark(3, 1, 96.0, "chorus", "now"),
    )

    private val peaks = FloatArray(600) { i ->
        val envelope = 0.4 + 0.6 * abs(sin(i / 37.0))
        (envelope * (0.5 + 0.5 * abs(sin(i / 5.0)))).toFloat()
    }

    @Test
    fun library() {
        compose.setContent {
            PitcherTheme {
                LibraryScreen(
                    tracks = listOf(track),
                    message = null,
                    onImport = {},
                    onOpen = {},
                    onDelete = {},
                    onRename = { _, _, _ -> },
                )
            }
        }
        compose.onRoot().captureRoboImage("build/screenshots/01-library.png")
    }

    @Test
    fun player() {
        compose.setContent {
            PitcherTheme {
                PlayerScreen(
                    track = track,
                    peaks = peaks,
                    positionMs = 48_000,
                    durationMs = 210_000,
                    isPlaying = true,
                    bookmarks = bookmarks,
                    tempo = 1f,
                    loopStartMs = 30_000,
                    loopEndMs = 60_000,
                    loopEnabled = true,
                    keepScreenOn = true,
                    onTogglePlay = {},
                    onSeekMs = {},
                    onAddBookmark = {},
                    onDeleteBookmark = {},
                    onTempo = {},
                    onSetLoopStart = {},
                    onSetLoopEnd = {},
                    onToggleLoop = {},
                    onClearLoop = {},
                )
            }
        }
        compose.onRoot().captureRoboImage("build/screenshots/02-player.png")
    }

    @Test
    fun pitchLab() {
        compose.setContent {
            PitcherTheme {
                PitchLabScreen(
                    track = track,
                    variants = variants,
                    cents = -600,
                    selectedVariantId = 1,
                    exportMessage = null,
                    exportFormat = ExportFormat.M4a,
                    exportLoopOnly = false,
                    loopAvailable = true,
                    onCents = {},
                    onKeep = {},
                    onExport = { _, _ -> },
                    onFormat = {},
                    onLoopOnly = {},
                )
            }
        }
        compose.onRoot().captureRoboImage("build/screenshots/03-pitch-lab.png")
    }

    @Test
    fun tuner() {
        compose.setContent {
            PitcherTheme {
                TunerScreen(
                    track = track,
                    detectedHz = 277.18,
                    detectMessage = null,
                    pendingShiftCents = -100,
                    onDetect = {},
                    onApply = {},
                )
            }
        }
        compose.onRoot().captureRoboImage("build/screenshots/04-tuner.png")
    }

    @Test
    fun pitches() {
        compose.setContent {
            PitcherTheme {
                PitchesScreen(
                    track = track,
                    variants = variants,
                    selectedVariantId = 1,
                    exportMessage = null,
                    onSelectOriginal = {},
                    onSelectVariant = {},
                    onRenameVariant = { _, _ -> },
                    onDeleteVariant = {},
                    onExportVariant = {},
                )
            }
        }
        compose.onRoot().captureRoboImage("build/screenshots/05-pitches.png")
    }

    private fun variant(id: Long, name: String, cents: Int) = Variant(
        id = id,
        trackId = 1,
        name = name,
        cents = cents,
        formant = true,
        engine = "sonic",
        pitchQuality = "quality",
        sectionStart = null,
        sectionEnd = null,
        outputPath = "",
        outputFormat = "live",
        srcNote = null,
        srcHz = null,
        targetNote = null,
        targetHz = null,
        favorite = false,
        createdAt = "2026-01-01",
    )
}
