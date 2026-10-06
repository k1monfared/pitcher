package pitcher.android.ui.modern

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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

@Composable
fun PitchPad(
    cents: Int,
    snap: Boolean,
    accent: Color,
    onCents: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentCents by rememberUpdatedState(cents)
    var dragging by remember { mutableStateOf(false) }
    var mode by remember { mutableStateOf(PitchGesture.Mode.NORMAL) }
    var lastSemitone by remember { mutableIntStateOf(0) }
    var lastTapMs by remember { mutableLongStateOf(0L) }
    var showExact by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current

    Box(
        modifier = modifier
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
                                snapCents = if (snap) 100 else 0,
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
                modifier = Modifier.clickable { showExact = true },
            )
            Text(
                "cents",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                when (mode) {
                    PitchGesture.Mode.FINE -> "fine"
                    PitchGesture.Mode.NORMAL -> "normal"
                    PitchGesture.Mode.COARSE -> "fast"
                },
                style = MaterialTheme.typography.labelLarge,
                color = accent,
            )
        }

        val hudAlpha by animateFloatAsState(
            targetValue = if (dragging) 1f else 0f,
            label = "hud",
        )
        PitchHud(
            cents = cents,
            accent = accent,
            alpha = hudAlpha,
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 8.dp),
        )
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
    Canvas(modifier = modifier.size(width = 56.dp, height = 280.dp)) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        drawRoundRect(
            color = Color.White.copy(alpha = 0.08f * alpha),
            topLeft = Offset(cx - 3f, 0f),
            size = Size(6f, h),
            cornerRadius = CornerRadius(3f, 3f),
        )
        for (semi in -12..12) {
            val frac = (range - semi * 100f) / (2f * range)
            val y = frac * h
            val major = semi % 12 == 0
            drawLine(
                color = Color.White.copy(alpha = (if (major) 0.4f else 0.18f) * alpha),
                start = Offset(cx - (if (major) 12f else 6f), y),
                end = Offset(cx + (if (major) 12f else 6f), y),
                strokeWidth = if (major) 2f else 1f,
            )
        }
        val frac = ((range - cents).toFloat() / (2f * range)).coerceIn(0f, 1f)
        val knobY = frac * h
        drawCircle(color = accent.copy(alpha = alpha), radius = w * 0.3f, center = Offset(cx, knobY))
    }
}
