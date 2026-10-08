package pitcher.android.ui.modern

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** One stop on the guided tour. [targetKey] points at a registered view. */
data class OnboardingStep(
    val targetKey: String,
    val title: String,
    val body: String,
)

/**
 * Shown before any song is open. Its single step points at Import, and its hole
 * stays tappable so the user can start right away.
 */
val LIBRARY_TOUR: List<OnboardingStep> = listOf(
    OnboardingStep(
        "library.import",
        "Import a song",
        "Tap Import to add an audio or video file, or share one into pitcher. " +
            "Open it to reach the studio, where the rest of the tour picks up.",
    ),
)

/**
 * The main-tour script. It covers the gesture surface and the handful of
 * controls that are not self-explanatory; plain buttons are left out.
 */
val MAIN_TOUR: List<OnboardingStep> = listOf(
    OnboardingStep(
        "pad",
        "Shift the pitch",
        "Slide up or down to move the pitch.",
    ),
    OnboardingStep(
        "cents",
        "Reset or type a value",
        "Double-tap to reset. Tap the number to type an exact value.",
    ),
    OnboardingStep(
        "snap",
        "Snap",
        "Turn snap on to land on a grid.",
    ),
    OnboardingStep(
        "wave",
        "Move around the song",
        "Tap to jump, drag to pan, and pinch to zoom.",
    ),
    OnboardingStep(
        "loop",
        "Make a loop",
        "Drag the lane above the wave.",
    ),
    OnboardingStep(
        "speed",
        "Change the play speed",
        "Hold the speed control and slide to pick a speed.",
    ),
    OnboardingStep(
        "save",
        "Keep a pitch",
        "Tap + render to keep the current pitch. Long-press a pitch to rename or share it.",
    ),
    OnboardingStep(
        "timeline",
        "Loops and bookmarks",
        "Manage your loops and bookmarks here.",
    ),
)

/** Collects the on-screen bounds of every tour target, keyed by [OnboardingStep.targetKey]. */
class OnboardingTargets {
    val bounds = mutableStateMapOf<String, Rect>()
}

@Composable
fun rememberOnboardingTargets(): OnboardingTargets = remember { OnboardingTargets() }

/** Registers this element's bounds as the highlight for [key]. No-op when [targets] is null. */
fun Modifier.onboardingTarget(targets: OnboardingTargets?, key: String): Modifier =
    if (targets == null) {
        this
    } else {
        this.onGloballyPositioned { targets.bounds[key] = it.boundsInRoot() }
    }

/**
 * A dimming coach-mark layer. It darkens everything except the current step's
 * target, rings that target, and floats a card with the explanation. Touches
 * outside the card are swallowed so the tour stays in control.
 */
@Composable
fun OnboardingOverlay(
    targets: OnboardingTargets,
    onFinish: () -> Unit,
    steps: List<OnboardingStep> = MAIN_TOUR,
    skippable: Boolean = true,
    onSkip: () -> Unit = onFinish,
    finalLabel: String = "Done",
    onStep: (OnboardingStep) -> Unit = {},
) {
    var index by remember { mutableStateOf(0) }
    val step = steps.getOrNull(index)
    if (step == null) {
        LaunchedEffect(Unit) { onFinish() }
        return
    }
    LaunchedEffect(index) { onStep(step) }

    val density = LocalDensity.current
    val accent = MaterialTheme.colorScheme.primary
    val scrim = Color(0xE608080B)

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val padPx = with(density) { 8.dp.toPx() }
        val raw = targets.bounds[step.targetKey]
        val hole = raw?.let {
            Rect(
                left = (it.left - padPx).coerceIn(0f, w),
                top = (it.top - padPx).coerceIn(0f, h),
                right = (it.right + padPx).coerceIn(0f, w),
                bottom = (it.bottom + padPx).coerceIn(0f, h),
            )
        }

        if (hole == null) {
            Box(Modifier.fillMaxSize().background(scrim).swallowTaps())
        } else {
            ScrimRect(0f, 0f, w, hole.top, scrim, density)
            ScrimRect(0f, hole.bottom, w, h - hole.bottom, scrim, density)
            ScrimRect(0f, hole.top, hole.left, hole.height, scrim, density)
            ScrimRect(hole.right, hole.top, w - hole.right, hole.height, scrim, density)
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawRoundRect(
                    color = accent,
                    topLeft = Offset(hole.left, hole.top),
                    size = Size(hole.width, hole.height),
                    cornerRadius = CornerRadius(14.dp.toPx(), 14.dp.toPx()),
                    style = Stroke(width = 2.dp.toPx()),
                )
            }
        }

        Box(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
            val align = when {
                hole == null -> Alignment.Center
                hole.center.y < h / 2f -> Alignment.BottomCenter
                else -> Alignment.TopCenter
            }
            Surface(
                modifier = Modifier.align(align).fillMaxWidth().padding(16.dp),
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFF14141A),
                shadowElevation = 12.dp,
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        "STEP ${index + 1} OF ${steps.size}",
                        style = MaterialTheme.typography.labelSmall,
                        color = accent,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        step.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        step.body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (skippable) {
                            TextButton(onClick = onSkip) { Text("Skip") }
                        } else {
                            Box(Modifier.size(1.dp))
                        }
                        Row {
                            if (index > 0) {
                                TextButton(onClick = { index-- }) { Text("Back") }
                            }
                            Button(onClick = {
                                if (index == steps.lastIndex) onFinish() else index++
                            }) {
                                Text(if (index == steps.lastIndex) finalLabel else "Next")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScrimRect(left: Float, top: Float, width: Float, height: Float, color: Color, density: androidx.compose.ui.unit.Density) {
    if (width <= 0f || height <= 0f) return
    Box(
        modifier = Modifier
            .offset { IntOffset(left.roundToInt(), top.roundToInt()) }
            .size(with(density) { width.toDp() }, with(density) { height.toDp() })
            .background(color)
            .swallowTaps(),
    )
}

/** Consumes every touch so the content underneath cannot react during the tour. */
private fun Modifier.swallowTaps(): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        down.consume()
        var pressed = true
        while (pressed) {
            val event = awaitPointerEvent()
            event.changes.forEach { it.consume() }
            pressed = event.changes.any { it.pressed }
        }
    }
}
