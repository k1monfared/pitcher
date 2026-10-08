package pitcher.android.ui.modern

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pitcher.android.data.Bookmark
import pitcher.core.WaveView

/**
 * Waveform with zoom, pan, bookmarks, and a separate loop lane. Tap seeks
 * (snapping to a nearby bookmark), drag pans, pinch zooms, and the thin lane
 * above sets the A/B loop. Time stamps along the bottom keep the user oriented.
 */
@Composable
fun WaveformScrubber(
    peaks: FloatArray,
    positionMs: Long,
    durationMs: Long,
    loopStartMs: Long?,
    loopEndMs: Long?,
    bookmarks: List<Bookmark>,
    accent: Color,
    onSeekMs: (Long) -> Unit,
    onSetLoop: (Long?, Long?) -> Unit,
    modifier: Modifier = Modifier,
    onboarding: OnboardingTargets? = null,
) {
    var view by remember { mutableStateOf<WaveView.Window?>(null) }
    var laneStartMs by remember { mutableStateOf<Long?>(null) }
    var laneEndMs by remember { mutableStateOf<Long?>(null) }
    val textMeasurer = rememberTextMeasurer()

    LaunchedEffect(durationMs) {
        view = null
        laneStartMs = null
        laneEndMs = null
    }

    val span = view ?: WaveView.Window(0.0, 1.0)

    fun xToMs(x: Float, width: Float): Long {
        if (durationMs <= 0 || width <= 0f) return 0L
        val frac = span.start + (x / width).coerceIn(0f, 1f) * (span.end - span.start)
        return (frac * durationMs).toLong().coerceIn(0, durationMs)
    }

    fun msToX(ms: Long, width: Float): Float {
        if (durationMs <= 0) return 0f
        val frac = ms.toDouble() / durationMs
        return (((frac - span.start) / (span.end - span.start)) * width).toFloat()
    }

    Column(modifier = modifier) {
        // Loop lane: drag to set A and B.
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(26.dp)
                .onboardingTarget(onboarding, "loop")
                .pointerInput(span, durationMs) {
                    val width = size.width.toFloat()
                    detectDragGestures(
                        onDragStart = { off ->
                            val ms = xToMs(off.x, width)
                            laneStartMs = ms
                            laneEndMs = ms
                        },
                        onDragEnd = {
                            val a = laneStartMs
                            val b = laneEndMs
                            if (a != null && b != null && a != b) onSetLoop(a, b) else onSetLoop(null, null)
                        },
                        onDragCancel = {
                            laneStartMs = null
                            laneEndMs = null
                        },
                    ) { change, _ ->
                        laneEndMs = xToMs(change.position.x, width)
                        change.consume()
                    }
                },
        ) {
            val w = size.width
            val h = size.height
            val ls = laneStartMs ?: loopStartMs
            val le = laneEndMs ?: loopEndMs
            if (ls != null && le != null && durationMs > 0) {
                val x0 = msToX(minOf(ls, le), w)
                val x1 = msToX(maxOf(ls, le), w)
                drawRoundRect(
                    color = accent.copy(alpha = 0.35f),
                    topLeft = Offset(x0, h - 10f),
                    size = Size((x1 - x0).coerceAtLeast(3f), 10f),
                    cornerRadius = CornerRadius(5f, 5f),
                )
                listOf(x0, x1).forEach { x ->
                    drawCircle(color = accent, radius = 7f, center = Offset(x, h - 5f))
                }
            } else {
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.06f),
                    topLeft = Offset(0f, h - 10f),
                    size = Size(w, 10f),
                    cornerRadius = CornerRadius(5f, 5f),
                )
            }
        }

        // Waveform body: tap to seek, drag to pan, pinch to zoom.
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(104.dp)
                .onboardingTarget(onboarding, "wave")
                .pointerInput(durationMs) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        val w = view ?: WaveView.Window(0.0, 1.0)
                        val s = w.end - w.start
                        val panFrac = -(pan.x / size.width.toFloat()) * s
                        var next = WaveView.panBy(w, panFrac)
                        if (zoom != 1f) {
                            val absCenter = w.start + (centroid.x / size.width.toFloat()) * s
                            next = WaveView.zoomAt(next ?: w, absCenter, 1.0 / zoom)
                        }
                        view = next
                    }
                }
                .pointerInput(span, durationMs, bookmarks) {
                    val width = size.width.toFloat()
                    detectTapGestures { off ->
                        val snapDist = 18f * density
                        val hit = bookmarks.minByOrNull { b ->
                            kotlin.math.abs(msToX((b.t * 1000).toLong(), width) - off.x)
                        }?.takeIf { b ->
                            kotlin.math.abs(msToX((b.t * 1000).toLong(), width) - off.x) < snapDist
                        }
                        onSeekMs(hit?.let { (it.t * 1000).toLong() } ?: xToMs(off.x, width))
                    }
                },
        ) {
            val w = size.width
            val h = size.height
            val mid = (h - 16f) / 2f
            drawRoundRect(
                color = Color.White.copy(alpha = 0.05f),
                size = Size(w, h),
                cornerRadius = CornerRadius(16f, 16f),
            )

            // bookmarks
            bookmarks.forEach { b ->
                val x = msToX((b.t * 1000).toLong(), w)
                if (x in 0f..w) {
                    drawLine(
                        color = Color(0xFFFFD166),
                        start = Offset(x, 4f),
                        end = Offset(x, h - 18f),
                        strokeWidth = 2f,
                    )
                }
            }

            if (peaks.isNotEmpty()) {
                val n = peaks.size
                val firstVisible = ((span.start * n).toInt()).coerceIn(0, n)
                val lastVisible = ((span.end * n).toInt() + 1).coerceIn(0, n)
                val count = (lastVisible - firstVisible).coerceAtLeast(1)
                val step = w / count
                for (i in 0 until count) {
                    val idx = firstVisible + i
                    if (idx >= n) break
                    val x = i * step
                    val barH = (peaks[idx] * mid * 0.9f).coerceAtLeast(1f)
                    val played = idx.toFloat() / n <= positionMs.toFloat() / durationMs.coerceAtLeast(1)
                    drawRoundRect(
                        color = if (played) accent.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.28f),
                        topLeft = Offset(x, mid - barH),
                        size = Size((step - 1f).coerceAtLeast(1f), barH * 2f),
                        cornerRadius = CornerRadius(1f, 1f),
                    )
                }
            }

            if (durationMs > 0) {
                val pf = positionMs.toFloat() / durationMs
                if (pf >= span.start && pf <= span.end) {
                    val px = msToX(positionMs, w)
                    drawLine(
                        color = Color.White.copy(alpha = 0.9f),
                        start = Offset(px, 4f),
                        end = Offset(px, h - 18f),
                        strokeWidth = 3f,
                    )
                }
            }

            // time stamps along the bottom
            if (durationMs > 0) {
                val labelStyle = TextStyle(color = Color.White.copy(alpha = 0.5f), fontSize = 9.sp)
                val ticks = 4
                for (i in 0..ticks) {
                    val frac = i.toFloat() / ticks
                    val ms = ((span.start + frac * (span.end - span.start)) * durationMs).toLong()
                    val x = (frac * w).coerceIn(0f, w - 30f)
                    drawText(
                        textMeasurer = textMeasurer,
                        text = formatTime(ms),
                        topLeft = Offset(x + 3f, h - 14f),
                        style = labelStyle,
                    )
                }
            }
        }
    }
}

fun formatTime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val m = total / 60
    val s = total % 60
    return "%d:%02d".format(m, s)
}
