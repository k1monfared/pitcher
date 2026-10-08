package pitcher.android.ui.modern

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.roundToInt
import pitcher.core.PitchGesture

private val HUD_GRADES = listOf(1200, 1000, 800, 600, 400, 200, 0, -200, -400, -600, -800, -1000, -1200)

@Composable
fun PitchPad(
    cents: Int,
    snap: Boolean,
    accent: Color,
    onCents: (Int) -> Unit,
    modifier: Modifier = Modifier,
    onboarding: OnboardingTargets? = null,
) {
    val currentCents by rememberUpdatedState(cents)
    val snapNow by rememberUpdatedState(snap)
    var dragging by remember { mutableStateOf(false) }
    var mode by remember { mutableStateOf(PitchGesture.Mode.NORMAL) }
    var lastSemitone by remember { mutableIntStateOf(0) }
    var lastTapMs by remember { mutableLongStateOf(0L) }
    var showExact by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current

    fun bump(delta: Int) {
        onCents((currentCents + delta).coerceIn(-1200, 1200))
    }

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        PillRow(deltas = listOf(1, 10, 100), onTap = { bump(it) })

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .onboardingTarget(onboarding, "pad")
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val startX = down.position.x
                        var lastY = down.position.y
                        var c = currentCents
                        var moved = false
                        dragging = true
                        lastSemitone = (c / 100.0).roundToInt()
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            val pos = change.position
                            val dy = lastY - pos.y
                            val offsetX = pos.x - startX
                            if (abs(offsetX) > 8f || abs(dy) > 8f) moved = true
                            if (moved) {
                                c = PitchGesture.step(
                                    currentCents = c,
                                    dyPx = dy,
                                    offsetPx = offsetX,
                                    widthPx = size.width.toFloat(),
                                    snapCents = if (snapNow) 100 else 0,
                                )
                                onCents(c)
                                val m = PitchGesture.mode(offsetX, size.width.toFloat())
                                if (m != mode) {
                                    mode = m
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                                val semi = (c / 100.0).roundToInt()
                                if (semi != lastSemitone) {
                                    lastSemitone = semi
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
                            }
                            lastY = pos.y
                            change.consume()
                        }
                        dragging = false
                        if (!moved) {
                            val now = System.currentTimeMillis()
                            if (now - lastTapMs < 350L) {
                                onCents(0)
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                lastTapMs = 0L
                            } else {
                                lastTapMs = now
                            }
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = (if (cents >= 0) "+$cents" else "$cents"),
                    fontSize = 84.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .onboardingTarget(onboarding, "cents")
                        .clickable { showExact = true },
                )
                Text(
                    "cents",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (dragging) {
                    Text(
                        when (mode) {
                            PitchGesture.Mode.FINE -> "fine · 1 cent"
                            PitchGesture.Mode.NORMAL -> "normal"
                            PitchGesture.Mode.COARSE -> "fast"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = accent,
                    )
                }
            }

            val hudAlpha by animateFloatAsState(
                targetValue = if (dragging) 1f else 0f,
                label = "hud",
            )
            PitchHud(
                cents = cents,
                accent = accent,
                alpha = hudAlpha,
                modifier = Modifier.align(Alignment.CenterStart),
            )
        }

        PillRow(deltas = listOf(-1, -10, -100), onTap = { bump(it) })
    }

    if (showExact) {
        ExactEntryDialog(
            initial = cents,
            onConfirm = {
                onCents(it)
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
