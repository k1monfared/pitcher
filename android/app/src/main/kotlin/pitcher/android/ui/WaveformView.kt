package pitcher.android.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import pitcher.android.data.Bookmark
import pitcher.core.WaveView

@Composable
fun WaveformView(
    peaks: FloatArray,
    positionMs: Long,
    durationMs: Long,
    bookmarks: List<Bookmark>,
    loopStartMs: Long? = null,
    loopEndMs: Long? = null,
    onSeekMs: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var window by remember { mutableStateOf<WaveView.Window?>(null) }
    var follow by remember { mutableStateOf(true) }

    LaunchedEffect(durationMs) {
        window = null
        follow = true
    }

    val positionFrac = if (durationMs > 0) {
        (positionMs.toDouble() / durationMs).coerceIn(0.0, 1.0)
    } else {
        0.0
    }

    if (follow && window != null) {
        val next = WaveView.follow(window, positionFrac)
        if (next != window) window = next
    }

    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .pointerInput(durationMs) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        val w = window ?: WaveView.Window(0.0, 1.0)
                        val span = w.end - w.start
                        val panFrac = -(pan.x / size.width.toFloat()) * span
                        var next = WaveView.panBy(w, panFrac)
                        if (zoom != 1f) {
                            val absCenter = w.start + (centroid.x / size.width.toFloat()) * span
                            next = WaveView.zoomAt(next ?: w, absCenter, 1.0 / zoom)
                        }
                        window = next
                        follow = false
                    }
                }
                .pointerInput(durationMs, window) {
                    detectTapGestures(
                        onTap = { offset ->
                            val w = window ?: WaveView.Window(0.0, 1.0)
                            val frac = (offset.x / size.width.toFloat()).coerceIn(0f, 1f).toDouble()
                            onSeekMs(WaveView.fracToTime(w, frac, durationMs.toDouble()).toLong())
                        },
                        onDoubleTap = { offset ->
                            if (window == null) {
                                val frac =
                                    (offset.x / size.width.toFloat()).coerceIn(0f, 1f).toDouble()
                                window = WaveView.zoomAt(WaveView.Window(0.0, 1.0), frac, 0.25)
                                follow = false
                            } else {
                                window = null
                                follow = true
                            }
                        },
                    )
                },
        ) {
            Canvas(modifier = Modifier.fillMaxWidth().height(150.dp)) {
                val barColor = Color(0xFF7A7A85)
                val playedColor = Color(0xFF6AA9FF)
                val headColor = Color(0xFFFF6B6B)
                val markColor = Color(0xFFFFD166)
                drawRect(color = Color(0xFF121216))
                val mid = size.height / 2f
                val w = window ?: WaveView.Window(0.0, 1.0)
                val span = (w.end - w.start).coerceAtLeast(1e-6)

                if (durationMs > 0 && loopStartMs != null && loopEndMs != null) {
                    val f0 = (loopStartMs.toDouble() / durationMs).coerceIn(0.0, 1.0)
                    val f1 = (loopEndMs.toDouble() / durationMs).coerceIn(0.0, 1.0)
                    val x0 = (((f0 - w.start) / span).toFloat() * size.width).coerceIn(0f, size.width)
                    val x1 = (((f1 - w.start) / span).toFloat() * size.width).coerceIn(0f, size.width)
                    drawRect(
                        color = Color(0x336AA9FF),
                        topLeft = Offset(minOf(x0, x1), 0f),
                        size = Size(kotlin.math.abs(x1 - x0), size.height),
                    )
                }

                if (peaks.isEmpty()) {
                    drawLine(barColor, Offset(0f, mid), Offset(size.width, mid), strokeWidth = 2f)
                } else {
                    val n = peaks.size
                    for (i in 0 until n) {
                        val frac = i.toDouble() / n
                        if (frac < w.start || frac > w.end) continue
                        val x = ((frac - w.start) / span * size.width).toFloat()
                        val h = (peaks[i] * mid).coerceAtLeast(1f)
                        val played = frac <= positionFrac
                        drawRect(
                            color = if (played) playedColor else barColor,
                            topLeft = Offset(x, mid - h),
                            size = Size(1.5f, h * 2f),
                        )
                    }
                }

                for (b in bookmarks) {
                    val frac = if (durationMs > 0) (b.t * 1000.0 / durationMs) else 0.0
                    if (frac < w.start || frac > w.end) continue
                    val x = ((frac - w.start) / span * size.width).toFloat()
                    drawLine(markColor, Offset(x, 0f), Offset(x, 12f), strokeWidth = 2f)
                }

                if (positionFrac >= w.start && positionFrac <= w.end) {
                    val px = ((positionFrac - w.start) / span * size.width).toFloat()
                    drawLine(headColor, Offset(px, 0f), Offset(px, size.height), strokeWidth = 2f)
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (window == null) "fit" else "zoomed",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (window != null) {
                TextButton(onClick = {
                    follow = true
                    window = WaveView.follow(window, positionFrac)
                }) {
                    Text(if (follow) "following" else "recenter")
                }
                TextButton(onClick = {
                    window = null
                    follow = true
                }) {
                    Text("fit")
                }
            }
        }
    }
}
