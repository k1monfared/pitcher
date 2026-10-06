package pitcher.android.ui.modern

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import kotlin.math.abs

private enum class DragTarget { NONE, SEEK, A, B }

@Composable
fun WaveformScrubber(
    peaks: FloatArray,
    positionMs: Long,
    durationMs: Long,
    loopStartMs: Long?,
    loopEndMs: Long?,
    accent: Color,
    onSeekMs: (Long) -> Unit,
    onSetLoop: (Long?, Long?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var width by remember { mutableStateOf(1f) }
    var target by remember { mutableStateOf(DragTarget.NONE) }
    var a by remember { mutableStateOf(loopStartMs) }
    var b by remember { mutableStateOf(loopEndMs) }

    fun xToMs(x: Float): Long =
        if (width <= 0f || durationMs <= 0) 0L
        else ((x / width).coerceIn(0f, 1f) * durationMs).toLong()

    fun msToX(ms: Long): Float =
        if (durationMs <= 0) 0f else (ms.toFloat() / durationMs) * width

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(96.dp)
            .onSizeChanged { width = it.width.toFloat() }
            .pointerInput(peaks, durationMs, loopStartMs, loopEndMs) {
                detectDragGestures(
                    onDragStart = { off ->
                        val handleZone = 32f * density
                        target = when {
                            loopStartMs != null && abs(off.x - msToX(loopStartMs)) < handleZone -> DragTarget.A
                            loopEndMs != null && abs(off.x - msToX(loopEndMs)) < handleZone -> DragTarget.B
                            else -> DragTarget.SEEK
                        }
                        if (target == DragTarget.SEEK) onSeekMs(xToMs(off.x))
                    },
                    onDragEnd = {
                        if (target == DragTarget.A || target == DragTarget.B) {
                            val s = a ?: 0L
                            val e = b ?: durationMs
                            onSetLoop(minOf(s, e), maxOf(s, e))
                        }
                        target = DragTarget.NONE
                    },
                    onDragCancel = { target = DragTarget.NONE },
                ) { change, _ ->
                    val x = change.position.x
                    when (target) {
                        DragTarget.SEEK -> onSeekMs(xToMs(x))
                        DragTarget.A -> a = xToMs(x)
                        DragTarget.B -> b = xToMs(x)
                        DragTarget.NONE -> {}
                    }
                    change.consume()
                }
            },
    ) {
        val h = size.height
        val mid = h / 2f
        drawRoundRect(
            color = Color.White.copy(alpha = 0.05f),
            size = Size(size.width, h),
            cornerRadius = CornerRadius(16f, 16f),
        )

        // loop region
        val ls = a ?: loopStartMs
        val le = b ?: loopEndMs
        if (ls != null && le != null && durationMs > 0) {
            val x0 = msToX(minOf(ls, le))
            val x1 = msToX(maxOf(ls, le))
            drawRoundRect(
                color = accent.copy(alpha = 0.18f),
                topLeft = Offset(x0, 0f),
                size = Size((x1 - x0).coerceAtLeast(2f), h),
                cornerRadius = CornerRadius(16f, 16f),
            )
        }

        val progress = if (durationMs > 0) positionMs.toFloat() / durationMs else 0f
        if (peaks.isNotEmpty()) {
            val step = size.width / peaks.size
            for (i in peaks.indices) {
                val x = i * step
                val v = peaks[i]
                val barH = (v * mid * 0.9f).coerceAtLeast(1f)
                val played = (i.toFloat() / peaks.size) <= progress
                drawRoundRect(
                    color = if (played) accent.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.28f),
                    topLeft = Offset(x, mid - barH),
                    size = Size((step - 1f).coerceAtLeast(1f), barH * 2f),
                    cornerRadius = CornerRadius(1f, 1f),
                )
            }
        } else {
            drawRoundRect(
                color = Color.White.copy(alpha = 0.2f),
                topLeft = Offset(0f, mid - 1f),
                size = Size(size.width, 2f),
                cornerRadius = CornerRadius(1f, 1f),
            )
        }

        val px = progress * size.width
        drawLine(
            color = Color.White.copy(alpha = 0.9f),
            start = Offset(px, 4f),
            end = Offset(px, h - 4f),
            strokeWidth = 3f,
        )

        // loop handles
        listOf(ls, le).forEach { m ->
            if (m != null) {
                val hx = msToX(m)
                drawCircle(color = accent, radius = 10f, center = Offset(hx, h - 10f))
            }
        }
    }
}
