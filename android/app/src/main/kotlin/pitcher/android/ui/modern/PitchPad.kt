package pitcher.android.ui.modern

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import pitcher.core.PitchGesture

private val HUD_GRADES = listOf(1200, 1000, 800, 600, 400, 200, 0, -200, -400, -600, -800, -1000, -1200)

@Composable
fun PitchPad(
    cents: Int,
    snap: Boolean,
    accent: Color,
    onCents: (Int) -> Unit,
    onOpenTuner: () -> Unit,
    modifier: Modifier = Modifier,
    hapticsEnabled: Boolean = true,
    onboarding: OnboardingTargets? = null,
) {
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val snapNow by rememberUpdatedState(snap)
    val hapticsNow by rememberUpdatedState(hapticsEnabled)
    val onCentsNow by rememberUpdatedState(onCents)
    val onOpenTunerNow by rememberUpdatedState(onOpenTuner)

    val raw = remember { mutableStateOf(cents.toDouble()) }
    val lastApplied = remember { mutableIntStateOf(cents) }
    val dragging = remember { mutableStateOf(false) }
    val flinging = remember { mutableStateOf(false) }
    var showExact by remember { mutableStateOf(false) }

    fun apply(rawValue: Double) {
        val applied = if (snapNow) PitchGesture.snapCents(rawValue) else rawValue.roundToInt()
        val prev = lastApplied.intValue
        if (applied != prev) {
            lastApplied.intValue = applied
            onCentsNow(applied)
            if (hapticsNow && snapNow) {
                val strong = applied / 100 != prev / 100
                haptics.performHapticFeedback(
                    if (strong) HapticFeedbackType.LongPress else HapticFeedbackType.TextHandleMove,
                )
            }
        }
    }

    fun reset() {
        raw.value = 0.0
        apply(0.0)
    }

    fun adjust(delta: Int) {
        raw.value = (raw.value + delta)
            .coerceIn(PitchGesture.MIN_CENTS.toDouble(), PitchGesture.MAX_CENTS.toDouble())
        apply(raw.value)
    }

    fun fling(velocity: Double) {
        if (abs(velocity) < PitchGesture.STOP_CENTS_PER_SEC) return
        scope.launch {
            flinging.value = true
            var value = raw.value
            var v = velocity
            var last = withFrameNanos { it }
            while (true) {
                val now = withFrameNanos { it }
                val dt = ((now - last) / 1_000_000_000.0).coerceIn(0.001, 0.05)
                last = now
                val f = PitchGesture.fling(value, v, dt)
                value = f.cents
                v = f.velocityCentsPerSec
                raw.value = value
                apply(value)
                if (f.done) break
            }
            flinging.value = false
        }
    }

    LaunchedEffect(cents) {
        if (!dragging.value && !flinging.value && cents != lastApplied.intValue) {
            raw.value = cents.toDouble()
            lastApplied.intValue = cents
        }
    }
    LaunchedEffect(snap) {
        if (snap) apply(raw.value)
    }

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        PillRow(deltas = listOf(1, 10, 100), onTap = { delta -> adjust(delta) })

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .onboardingTarget(onboarding, "pad")
                .pointerInput(Unit) {
                    val slopPx = 10f * density.density
                    val rampPx = min(10f * density.density, 0.10f * size.height)
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val startX = down.position.x
                        val startY = down.position.y
                        var lastY = down.position.y
                        var lastTime = down.uptimeMillis
                        var rawLocal = raw.value
                        var travel = 0.0
                        var velocity = 0.0
                        var moved = false
                        dragging.value = true
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            val pos = change.position
                            val dt = (change.uptimeMillis - lastTime).coerceAtLeast(1) / 1000.0
                            val dy = lastY - pos.y
                            if (abs(pos.x - startX) > slopPx || abs(pos.y - startY) > slopPx) moved = true
                            if (moved) {
                                travel += dy
                                velocity = PitchGesture.smoothedSpeed(velocity, dy / dt, 0.25)
                                rawLocal = PitchGesture.advance(
                                    rawCents = rawLocal,
                                    dyPx = dy.toDouble(),
                                    totalTravelPx = travel,
                                    speedPxPerSec = abs(velocity),
                                    rampPx = rampPx.toDouble(),
                                )
                                raw.value = rawLocal
                                apply(rawLocal)
                                change.consume()
                            }
                            lastY = pos.y
                            lastTime = change.uptimeMillis
                            if (!change.pressed) break
                        }
                        dragging.value = false
                        if (moved) {
                            fling(velocity * PitchGesture.centsPerPx(abs(velocity)))
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .onboardingTarget(onboarding, "cents")
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = { showExact = true },
                                onDoubleTap = {
                                    reset()
                                    if (hapticsNow) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                },
                                onLongPress = { onOpenTunerNow() },
                            )
                        },
                ) {
                    Text(
                        text = (if (cents >= 0) "+$cents" else "$cents"),
                        fontSize = 84.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center,
                    )
                }
                Text(
                    "cents",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            val hudAlpha by animateFloatAsState(
                targetValue = if (dragging.value) 1f else 0f,
                label = "hud",
            )
            PitchHud(
                cents = cents,
                accent = accent,
                alpha = hudAlpha,
                modifier = Modifier.align(Alignment.CenterStart),
            )
        }

        PillRow(deltas = listOf(-1, -10, -100), onTap = { delta -> adjust(delta) })
    }

    if (showExact) {
        ExactEntryDialog(
            initial = cents,
            onConfirm = {
                raw.value = it.toDouble()
                apply(raw.value)
                showExact = false
            },
            onDismiss = { showExact = false },
        )
    }
}

@Composable
private fun PillRow(deltas: List<Int>, onTap: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        deltas.forEach { d ->
            Surface(
                shape = RoundedCornerShape(50),
                color = Color.White.copy(alpha = 0.06f),
                modifier = Modifier.padding(horizontal = 4.dp),
            ) {
                Text(
                    text = if (d > 0) "+$d" else "$d",
                    modifier = Modifier.clickable { onTap(d) }.padding(horizontal = 14.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ExactEntryDialog(
    initial: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initial.toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Exact pitch (cents)") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                label = { Text("-1200 to 1200") },
            )
        },
        confirmButton = {
            TextButton(onClick = {
                val v = text.toIntOrNull()?.coerceIn(-1200, 1200) ?: initial
                onConfirm(v)
            }) { Text("Set") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun PitchHud(
    cents: Int,
    accent: Color,
    alpha: Float,
    modifier: Modifier = Modifier,
) {
    if (alpha <= 0.01f) return
    val range = 1200
    val hudHeight = 300.dp
    Row(modifier = modifier.height(hudHeight)) {
        Canvas(modifier = Modifier.width(28.dp).fillMaxHeight()) {
            val w = size.width
            val h = size.height
            val cx = w / 2f
            drawRoundRect(
                color = Color.White.copy(alpha = 0.10f * alpha),
                topLeft = Offset(cx - 3f, 0f),
                size = Size(6f, h),
                cornerRadius = CornerRadius(3f, 3f),
            )
            var semi = -12
            while (semi <= 12) {
                val frac = (range - semi * 100f) / (2f * range)
                val y = frac * h
                val major = semi % 2 == 0
                drawLine(
                    color = Color.White.copy(alpha = (if (major) 0.45f else 0.2f) * alpha),
                    start = Offset(cx - (if (major) 12f else 6f), y),
                    end = Offset(cx + (if (major) 12f else 6f), y),
                    strokeWidth = if (major) 2f else 1f,
                )
                semi++
            }
            val frac = ((range - cents).toFloat() / (2f * range)).coerceIn(0f, 1f)
            drawCircle(
                color = accent.copy(alpha = alpha),
                radius = w * 0.34f,
                center = Offset(cx, frac * h),
            )
        }
        Column(
            modifier = Modifier.fillMaxHeight().padding(start = 4.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            HUD_GRADES.forEach { c ->
                Text(
                    text = if (c == 0) "0" else (if (c > 0) "+${c / 100}" else "${c / 100}"),
                    fontSize = 9.sp,
                    color = Color.White.copy(alpha = 0.55f * alpha),
                )
            }
        }
    }
}
