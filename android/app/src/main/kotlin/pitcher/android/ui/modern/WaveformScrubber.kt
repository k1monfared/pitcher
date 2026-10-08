package pitcher.android.ui.modern

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.roundToInt
import pitcher.android.data.Bookmark
import pitcher.android.data.LoopSection
import pitcher.core.WaveView

private val BOOKMARK_GOLD = Color(0xFFFFD166)

private class DragState {
    var loopId: Long? = null
    var isStart: Boolean = false
    var bookmarkId: Long? = null
    var base: Long = 0L
    var accum: Float = 0f
}

/**
 * The song chart. A wider loop lane on top (drag empty space to make a loop,
 * long-press an edge to fine-tune), a bookmark strip of pins (long-press a pin
 * to move it), and the waveform (tap to seek, drag to pan when zoomed,
 * long-press and drag to scrub, pinch to zoom). Precise drags use a small
 * precision ramp and show a timestamp that follows the thumb.
 */
@Composable
fun WaveformScrubber(
    peaks: FloatArray,
    positionMs: Long,
    durationMs: Long,
    loops: List<LoopSection>,
    selectedLoopId: Long?,
    bookmarks: List<Bookmark>,
    accent: Color,
    onSeekMs: (Long) -> Unit,
    onCreateLoop: (Long, Long) -> Unit,
    onMoveLoopEdge: (Long, Boolean, Long) -> Unit,
    onMoveBookmark: (Long, Long) -> Unit,
    onSelectLoop: (Long) -> Unit,
    onRenameLoop: (Long, String) -> Unit,
    onDeleteLoop: (Long) -> Unit,
    onRenameBookmark: (Long, String) -> Unit,
    onDeleteBookmark: (Long) -> Unit,
    modifier: Modifier = Modifier,
    onboarding: OnboardingTargets? = null,
) {
    var view by remember { mutableStateOf<WaveView.Window?>(null) }
    var laneStart by remember { mutableLongStateOf(0L) }
    var laneEnd by remember { mutableLongStateOf(0L) }
    var laneActive by remember { mutableStateOf(false) }
    var bubbleMs by remember { mutableStateOf<Long?>(null) }
    var chartWidth by remember { mutableFloatStateOf(0f) }
    var loopMenu by remember { mutableStateOf<LoopSection?>(null) }
    var bookmarkMenu by remember { mutableStateOf<Bookmark?>(null) }
    val drag = remember { DragState() }
    val textMeasurer = rememberTextMeasurer()

    LaunchedEffect(durationMs) {
        view = null
        laneActive = false
        bubbleMs = null
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

    fun msPerPx(width: Float): Float =
        if (width <= 0f) 0f else ((span.end - span.start) * durationMs / width).toFloat()

    fun precise(base: Long, dxPx: Float, rampPx: Float, msPerPx: Float): Long {
        val mag = abs(dxPx)
        val scaled = if (rampPx > 0f && mag < rampPx) dxPx * (mag / rampPx) else dxPx
        return (base + scaled * msPerPx).roundToInt().toLong().coerceIn(0L, durationMs)
    }

    Box(modifier = modifier) {
        Column {
            // Loop lane: drag to create, hold then drag an edge to fine-tune.
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .onboardingTarget(onboarding, "loop")
                    .onSizeChanged { chartWidth = it.width.toFloat() }
                    .pointerInput(span, durationMs, loops) {
                        val width = size.width.toFloat()
                        val msPer = msPerPx(width)
                        val rampPx = 10f * density
                        val slopPx = 10f * density
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val startX = down.position.x
                            val startY = down.position.y
                            val startMs = xToMs(startX, width)
                            var mode = 0
                            var edgeLoopId: Long? = null
                            var edgeIsStart = false
                            var edgeBase = 0L
                            var accum = 0f
                            var lastX = startX
                            val startTime = down.uptimeMillis
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                val pos = change.position
                                if (mode == 0 &&
                                    (abs(pos.x - startX) > slopPx || abs(pos.y - startY) > slopPx)
                                ) {
                                    val held = change.uptimeMillis - startTime
                                    val hit = loops.minByOrNull {
                                        minOf(abs(it.startMs - startMs), abs(it.endMs - startMs))
                                    }
                                    val nearEdge = hit != null &&
                                        minOf(abs(hit.startMs - startMs), abs(hit.endMs - startMs)) < 32 * density
                                    if (held > 250L && nearEdge) {
                                        mode = 2
                                        edgeLoopId = hit!!.id
                                        edgeIsStart = abs(hit.startMs - startMs) <= abs(hit.endMs - startMs)
                                        edgeBase = if (edgeIsStart) hit.startMs else hit.endMs
                                        accum = 0f
                                        bubbleMs = edgeBase
                                    } else if (held > 250L) {
                                        mode = 3
                                        loopMenu = loops.firstOrNull { startMs in it.startMs..it.endMs } ?: hit
                                    } else {
                                        mode = 1
                                        laneStart = startMs
                                        laneEnd = startMs
                                        laneActive = true
                                        bubbleMs = startMs
                                    }
                                }
                                when (mode) {
                                    1 -> {
                                        laneEnd = xToMs(pos.x, width)
                                        bubbleMs = laneEnd
                                    }
                                    2 -> {
                                        accum += pos.x - lastX
                                        val next = precise(edgeBase, accum, rampPx, msPer)
                                        bubbleMs = next
                                        edgeLoopId?.let { onMoveLoopEdge(it, edgeIsStart, next) }
                                    }
                                }
                                lastX = pos.x
                                change.consume()
                                if (!change.pressed) break
                            }
                            if (mode == 1) {
                                if (abs(laneEnd - laneStart) >= 200L) onCreateLoop(laneStart, laneEnd)
                                laneActive = false
                                bubbleMs = null
                            } else if (mode == 2) {
                                bubbleMs = null
                            }
                        }
                    },
            ) {
                val w = size.width
                val h = size.height
                val barTop = 6f
                val barH = h - 12f
                loops.forEach { loop ->
                    val x0 = msToX(loop.startMs, w)
                    val x1 = msToX(loop.endMs, w)
                    val selected = loop.id == selectedLoopId
                    val color = when {
                        !loop.enabled -> Color.White.copy(alpha = 0.18f)
                        selected -> accent
                        else -> accent.copy(alpha = 0.55f)
                    }
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(x0, barTop),
                        size = Size((x1 - x0).coerceAtLeast(4f), barH),
                        cornerRadius = CornerRadius(barH / 2f, barH / 2f),
                    )
                    listOf(x0, x1).forEach { x ->
                        drawCircle(color = color, radius = 9f, center = Offset(x, barTop + barH / 2f))
                    }
                }
                if (laneActive) {
                    val x0 = msToX(minOf(laneStart, laneEnd), w)
                    val x1 = msToX(maxOf(laneStart, laneEnd), w)
                    drawRoundRect(
                        color = accent.copy(alpha = 0.4f),
                        topLeft = Offset(x0, barTop),
                        size = Size((x1 - x0).coerceAtLeast(4f), barH),
                        cornerRadius = CornerRadius(barH / 2f, barH / 2f),
                    )
                }
            }

            // Bookmark strip: pins that can be long-pressed and dragged.
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
                    .pointerInput(span, durationMs, bookmarks) {
                        val width = size.width.toFloat()
                        detectTapGestures(
                            onLongPress = { off ->
                                val ms = xToMs(off.x, width)
                                val hit = bookmarks.minByOrNull { abs((it.t * 1000).toLong() - ms) }
                                if (hit != null && abs((hit.t * 1000).toLong() - ms) < 32 * density) {
                                    bookmarkMenu = hit
                                }
                            },
                            onTap = { off ->
                                val ms = xToMs(off.x, width)
                                val hit = bookmarks.minByOrNull { abs((it.t * 1000).toLong() - ms) }
                                if (hit != null && abs((hit.t * 1000).toLong() - ms) < 32 * density) {
                                    onSeekMs((hit.t * 1000).toLong())
                                }
                            },
                        )
                    }
                    .pointerInput(span, durationMs, bookmarks) {
                        val width = size.width.toFloat()
                        val msPer = msPerPx(width)
                        val rampPx = 10f * density
                        detectDragGesturesAfterLongPress(
                            onDragStart = { off ->
                                val ms = xToMs(off.x, width)
                                val hit = bookmarks.minByOrNull { abs((it.t * 1000).toLong() - ms) }
                                if (hit != null && abs((hit.t * 1000).toLong() - ms) < 32 * density) {
                                    drag.bookmarkId = hit.id
                                    drag.base = (hit.t * 1000).toLong()
                                    drag.accum = 0f
                                    bubbleMs = drag.base
                                }
                            },
                            onDragEnd = { drag.bookmarkId = null; bubbleMs = null },
                            onDragCancel = { drag.bookmarkId = null; bubbleMs = null },
                        ) { change, amount ->
                            val id = drag.bookmarkId ?: return@detectDragGesturesAfterLongPress
                            drag.accum += amount.x
                            val next = precise(drag.base, drag.accum, rampPx, msPer)
                            bubbleMs = next
                            onMoveBookmark(id, next)
                            change.consume()
                        }
                    },
            ) {
                val w = size.width
                val h = size.height
                val tagH = 13f
                val tagW = 15f
                bookmarks.forEach { b ->
                    val x = msToX((b.t * 1000).toLong(), w)
                    if (x in 0f..w) {
                        drawLine(
                            color = BOOKMARK_GOLD.copy(alpha = 0.5f),
                            start = Offset(x, tagH + 4f),
                            end = Offset(x, h),
                            strokeWidth = 3f,
                        )
                        drawRoundRect(
                            color = BOOKMARK_GOLD,
                            topLeft = Offset(x - tagW / 2f, 0f),
                            size = Size(tagW, tagH),
                            cornerRadius = CornerRadius(4f, 4f),
                        )
                        val point = Path().apply {
                            moveTo(x - 5f, tagH)
                            lineTo(x + 5f, tagH)
                            lineTo(x, tagH + 6f)
                            close()
                        }
                        drawPath(point, color = BOOKMARK_GOLD)
                    }
                }
            }

            // Waveform body: tap seek, drag pan, long-press scrub, pinch zoom.
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
                                abs(msToX((b.t * 1000).toLong(), width) - off.x)
                            }?.takeIf { b -> abs(msToX((b.t * 1000).toLong(), width) - off.x) < snapDist }
                            onSeekMs(hit?.let { (it.t * 1000).toLong() } ?: xToMs(off.x, width))
                        }
                    }
                    .pointerInput(span, durationMs) {
                        val width = size.width.toFloat()
                        detectDragGesturesAfterLongPress(
                            onDragStart = { off ->
                                bubbleMs = xToMs(off.x, width)
                            },
                            onDragEnd = {
                                bubbleMs?.let { onSeekMs(it) }
                                bubbleMs = null
                            },
                            onDragCancel = { bubbleMs = null },
                        ) { change, _ ->
                            bubbleMs = xToMs(change.position.x, width)
                            change.consume()
                        }
                    },
            ) {
                val w = size.width
                val h = size.height
                val mid = (h - 18f) / 2f
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.05f),
                    size = Size(w, h),
                    cornerRadius = CornerRadius(16f, 16f),
                )

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
                            start = Offset(px, 2f),
                            end = Offset(px, h - 6f),
                            strokeWidth = 3f,
                        )
                    }
                }
            }

            if (durationMs > 0) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    for (i in 0..4) {
                        val frac = i / 4f
                        val ms = ((span.start + frac * (span.end - span.start)) * durationMs).toLong()
                        Text(
                            formatTime(ms),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        bubbleMs?.let { ms ->
            val x = msToX(ms, chartWidth)
            Surface(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset { IntOffset((x - 40f).roundToInt().coerceAtLeast(0), 0) },
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFF14141A),
                shadowElevation = 6.dp,
            ) {
                Text(
                    formatTime(ms),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = accent),
                )
            }
        }
    }

    loopMenu?.let { loop ->
        ChartMenu(
            title = loop.name ?: "Loop ${formatTime(loop.startMs)} - ${formatTime(loop.endMs)}",
            onRename = { onRenameLoop(loop.id, it); loopMenu = null },
            onDelete = { onDeleteLoop(loop.id); loopMenu = null },
            onDismiss = { loopMenu = null },
        )
    }
    bookmarkMenu?.let { bookmark ->
        ChartMenu(
            title = bookmark.name ?: "Bookmark ${formatTime((bookmark.t * 1000).toLong())}",
            onRename = { onRenameBookmark(bookmark.id, it); bookmarkMenu = null },
            onDelete = { onDeleteBookmark(bookmark.id); bookmarkMenu = null },
            onDismiss = { bookmarkMenu = null },
        )
    }
}

@Composable
private fun ChartMenu(
    title: String,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf("") }
    var renaming by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            if (renaming) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    label = { Text("Name") },
                )
            } else {
                Text("Rename or delete this.")
            }
        },
        confirmButton = {
            if (renaming) {
                TextButton(onClick = { onRename(text) }) { Text("Save") }
            } else {
                TextButton(onClick = { text = ""; renaming = true }) { Text("Rename") }
            }
        },
        dismissButton = {
            if (renaming) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
            } else {
                TextButton(onClick = onDelete) { Text("Delete") }
            }
        },
    )
}

fun formatTime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val m = total / 60
    val s = total % 60
    return "%d:%02d".format(m, s)
}
