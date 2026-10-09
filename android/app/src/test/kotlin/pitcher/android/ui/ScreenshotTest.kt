package pitcher.android.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import pitcher.android.data.Bookmark
import pitcher.android.data.LoopSection
import pitcher.android.data.Track
import pitcher.android.data.Variant
import android.widget.ImageView
import androidx.compose.foundation.layout.size
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import pitcher.android.ui.modern.FormatDrum
import pitcher.android.ui.modern.LIBRARY_TOUR
import pitcher.core.ExportFormat
import pitcher.android.ui.modern.LibraryContent
import pitcher.android.ui.modern.OnboardingOverlay
import pitcher.android.ui.modern.OnboardingTargets
import pitcher.android.ui.modern.StudioContent
import pitcher.android.ui.theme.ModernTheme
import pitcher.core.LoopMode
import kotlin.math.abs
import kotlin.math.sin

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class ScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    // The studio animates every frame while playing, so the test clock is
    // driven by hand; otherwise Compose never goes idle.
    @Before
    fun pauseClock() {
        compose.mainClock.autoAdvance = false
    }

    private fun settle() {
        compose.mainClock.advanceTimeBy(1_000)
    }

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

    private val loops = listOf(
        LoopSection(1, 1, 30_000, 60_000, "chorus", true, "now"),
        LoopSection(2, 1, 90_000, 120_000, null, false, "now"),
    )

    private val peaks = FloatArray(600) { i ->
        val envelope = 0.4 + 0.6 * abs(sin(i / 37.0))
        (envelope * (0.5 + 0.5 * abs(sin(i / 5.0)))).toFloat()
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
        settle()
        compose.onRoot().captureRoboImage("build/screenshots/11-modern-library.png")
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    fun modernStudio() {
        compose.setContent {
            ModernTheme { StudioSample() }
        }
        settle()
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
        settle()
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
        settle()
        compose.onRoot().captureRoboImage("build/screenshots/13-modern-library-tour.png")
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    fun modernStudioUnkeptPitch() {
        compose.setContent {
            ModernTheme { StudioSample(cents = 250) }
        }
        settle()
        compose.onRoot().captureRoboImage("build/screenshots/14-modern-studio-new-pitch.png")
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    fun launcherIcon() {
        compose.setContent {
            AndroidView(
                factory = { context ->
                    ImageView(context).apply {
                        setImageResource(pitcher.android.R.mipmap.ic_launcher)
                        setBackgroundColor(android.graphics.Color.rgb(40, 40, 48))
                    }
                },
                modifier = Modifier.size(192.dp),
            )
        }
        settle()
        compose.onRoot().captureRoboImage("build/screenshots/16-launcher-icon.png")
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    fun formatKnob() {
        compose.setContent {
            ModernTheme {
                Box(modifier = Modifier.background(Color(0xFF08080B)).padding(24.dp)) {
                    FormatDrum(selected = ExportFormat.Mp3, onSelect = {})
                }
            }
        }
        settle()
        compose.onRoot().captureRoboImage("build/screenshots/15-format-knob.png")
    }

    @Composable
    private fun StudioSample(onboarding: OnboardingTargets? = null, cents: Int = -600) {
        StudioContent(
            title = "Nava Sol Darya",
            artist = "Tasnife Yad Bad",
            cents = cents,
            snap = false,
            peaks = peaks,
            positionMs = 48_000,
            durationMs = 210_000,
            loops = loops,
            selectedLoopId = 1,
            loopMode = LoopMode.ALL,
            bookmarks = bookmarks,
            playing = true,
            tempo = 1f,
            variants = variants,
            onCents = {},
            onSnapToggle = {},
            onSeekMs = {},
            onCreateLoop = { _, _ -> },
            onMoveLoopEdge = { _, _, _ -> },
            onMoveBookmark = { _, _ -> },
            onSelectLoop = {},
            onRenameLoop = { _, _ -> },
            onDeleteLoop = {},
            onRenameBookmark = { _, _ -> },
            onDeleteBookmark = {},
            onCycleLoopMode = {},
            onPlayPause = {},
            onSkip = {},
            onTempo = {},
            onAddBookmark = {},
            onSelectPitch = {},
            onKeepPitch = {},
            onOpenFile = {},
            onRenameVariant = { _, _ -> },
            onShareVariant = {},
            onDeleteVariant = {},
            onOpenLibrary = {},
            onOpenSettings = {},
            follow = true,
            busyIds = setOf(2L),
            onboarding = onboarding,
        )
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
