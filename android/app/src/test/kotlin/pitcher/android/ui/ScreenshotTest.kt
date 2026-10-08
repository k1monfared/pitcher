package pitcher.android.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
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
import pitcher.android.ui.classic.LibraryScreen
import pitcher.android.ui.classic.PitchesScreen
import pitcher.android.ui.classic.PitchLabScreen
import pitcher.android.ui.classic.PlayerScreen
import pitcher.android.ui.classic.TunerScreen
import pitcher.android.ui.modern.LIBRARY_TOUR
import pitcher.android.ui.modern.LibraryContent
import pitcher.android.ui.modern.OnboardingOverlay
import pitcher.android.ui.modern.OnboardingTargets
import pitcher.android.ui.modern.StudioContent
import pitcher.android.ui.theme.ModernTheme
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
    @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    fun modernLibrary() {
        val farsi = track.copy(
            id = 2,
            title = "سالار عقیلی و سیامک آقایی - کنسرت یاد باد",
            artist = "Salar Aghili & Siamak Aghaei",
        )
        compose.setContent {
            ModernTheme {
                LibraryContent(
                    tracks = listOf(track, farsi),
                    message = null,
                    canClose = true,
                    onImport = {},
                    onClose = {},
                    onOpen = {},
                    onDelete = {},
                )
            }
        }
        compose.onRoot().captureRoboImage("build/screenshots/11-modern-library.png")
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    fun modernStudio() {
        compose.setContent {
            ModernTheme { StudioSample() }
        }
        compose.onRoot().captureRoboImage("build/screenshots/10-modern-studio.png")
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    fun modernOnboarding() {
        val targets = OnboardingTargets()
        compose.setContent {
            ModernTheme {
                Box {
                    StudioSample(onboarding = targets)
                    OnboardingOverlay(targets = targets, onFinish = {})
                }
            }
        }
        compose.onRoot().captureRoboImage("build/screenshots/12-modern-onboarding.png")
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    fun modernLibraryOnboarding() {
        val targets = OnboardingTargets()
        compose.setContent {
            ModernTheme {
                Box {
                    LibraryContent(
                        tracks = listOf(track),
                        message = null,
                        canClose = false,
                        onImport = {},
                        onClose = {},
                        onOpen = {},
                        onDelete = {},
                        onboarding = targets,
                    )
                    OnboardingOverlay(
                        targets = targets,
                        steps = LIBRARY_TOUR,
                        finalLabel = "Next",
                        onFinish = {},
                    )
                }
            }
        }
        compose.onRoot().captureRoboImage("build/screenshots/13-modern-library-tour.png")
    }

    @Composable
    private fun StudioSample(onboarding: OnboardingTargets? = null) {
        StudioContent(
            title = "Nava Sol Darya",
            artist = "Tasnife Yad Bad",
            cents = -600,
            snap = false,
            peaks = peaks,
            positionMs = 48_000,
            durationMs = 210_000,
            loopStartMs = 30_000,
            loopEndMs = 60_000,
            loopEnabled = true,
            bookmarks = bookmarks,
            playing = true,
            tempo = 1f,
            variants = variants,
            selectedVariantId = 1,
            saving = false,
            statusMessage = null,
            onCents = {},
            onSnapToggle = {},
            onSeekMs = {},
            onSetLoop = { _, _ -> },
            onPlayPause = {},
            onSkip = {},
            onTempo = {},
            onLoopTap = {},
            onLoopClear = {},
            onAddBookmark = {},
            onSelectOriginal = {},
            onSelectVariant = {},
            onSaveCurrent = {},
            onRenameVariant = { _, _ -> },
            onExportVariant = {},
            onShareVariant = {},
            onDeleteVariant = {},
            onOpenLibrary = {},
            onOpenExport = {},
            onOpenSettings = {},
            onboarding = onboarding,
        )
    }

    @Test
    @Config(qualifiers = "w411dp-h1400dp-xxhdpi")
    fun pitchLab() {
        compose.setContent {
            PitcherTheme {
                PitchLabScreen(
                    track = track,
                    variants = variants,
                    cents = -600,
                    selectedVariantId = 1,
                    exportMessage = null,
                    exporting = false,
                    exportFormat = ExportFormat.M4a,
                    exportLoopOnly = false,
                    loopAvailable = true,
                    onCents = {},
                    onRender = {},
                    onExport = {},
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
