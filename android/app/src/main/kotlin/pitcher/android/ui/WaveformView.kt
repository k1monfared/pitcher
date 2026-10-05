package pitcher.android.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

@Composable
fun WaveformView(
    peaks: FloatArray,
    positionMs: Long,
    durationMs: Long,
    onSeekFraction: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val barColor = Color(0xFF7A7A85)
    val playedColor = Color(0xFF6AA9FF)
    val headColor = Color(0xFFFF6B6B)
    val bgColor = Color(0xFF121216)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(140.dp)
            .pointerInput(durationMs) {
                detectTapGestures { offset: Offset ->
                    val fraction = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                    onSeekFraction(fraction)
                }
            },
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().height(140.dp)) {
            drawRect(color = bgColor)
            val mid = size.height / 2f
            if (peaks.isEmpty()) {
                drawLine(
                    color = barColor,
                    start = Offset(0f, mid),
                    end = Offset(size.width, mid),
                    strokeWidth = 2f,
                )
                return@Canvas
            }
            val step = size.width / peaks.size
            val progress = if (durationMs > 0) {
                (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
            } else {
                0f
            }
            for (i in peaks.indices) {
                val x = i * step
                val h = (peaks[i] * mid).coerceAtLeast(1f)
                val played = (x / size.width) <= progress
                drawRect(
                    color = if (played) playedColor else barColor,
                    topLeft = Offset(x, mid - h),
                    size = Size((step - 1f).coerceAtLeast(1f), h * 2f),
                )
            }
            val px = progress * size.width
            drawLine(
                color = headColor,
                start = Offset(px, 0f),
                end = Offset(px, size.height),
                strokeWidth = 2f,
            )
        }
    }
}
