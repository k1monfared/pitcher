package pitcher.android.ui.modern

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.roundToInt
import pitcher.android.data.Bookmark
import pitcher.android.data.LoopSection
import pitcher.core.ChartLabels
import pitcher.core.Timeline
import pitcher.core.WaveView

private val BOOKMARK_GOLD = Color(0xFFFFD166)

/** Touch radius for grabbing a loop edge or a bookmark pin. */
private val GRAB_RADIUS = 24.dp

/** Where the playhead sits across a zoomed view while following. */
private const val FOLLOW_ANCHOR = 0.75

/**
 * The song chart. A loop lane on top (drag empty space to make a loop, tap a
 * loop to select it, long-press an edge and drag to fine-tune, long-press a
 * loop to rename or delete it), a bookmark strip of pins (tap to seek,
 * long-press and drag to move, long-press to rename or delete), and the
 * waveform (tap to seek, drag to scrub or pan, long-press and drag to scrub,
 * pinch to zoom). Edge and pin drags preview live through [onMoveLoopEdge] and
 * [onMoveBookmark] and are saved once through the commit callbacks on release.
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
    onCommitLoopEdge: (Long) -> Unit = {},
    onCommitBookmark: (Long) -> Unit = {},
    playing: Boolean = false,
    follow: Boolean = false,
    onFollowChange: (Boolean) -> Unit = {},
    onboarding: OnboardingTargets? = null,
) {
    var view by remember { mutableStateOf<WaveView.Window?>(null) }
    // Where the finger is while scrubbing the wave. The playhead is drawn here
    // and playback only moves when the finger lifts.
    var scrubMs by remember { mutableStateOf<Long?>(null) }
    val textMeasurer = rememberTextMeasurer()
    val followNow by rememberUpdatedState(follow)
    val onFollowChangeNow by rememberUpdatedState(onFollowChange)
    var laneStart by remember { mutableLongStateOf(0L) }
    var laneEnd by remember { mutableLongStateOf(0L) }
    var laneActive by remember { mutableStateOf(false) }
    var bubbleMs by remember { mutableStateOf<Long?>(null) }
    var chartWidth by remember { mutableFloatStateOf(0f) }
    var loopMenu by remember { mutableStateOf<LoopSection?>(null) }
    var bookmarkMenu by remember { mutableStateOf<Bookmark?>(null) }

    // Gesture handlers are keyed on Unit so a drag that changes the loops or
    // bookmarks does not restart them mid-gesture; they read the latest values.
    val loopsNow by rememberUpdatedState(loops)
    val bookmarksNow by rememberUpdatedState(bookmarks)
    val durationNow by rememberUpdatedState(durationMs)
    val onSeekNow by rememberUpdatedState(onSeekMs)
    val onCreateLoopNow by rememberUpdatedState(onCreateLoop)
    val onMoveLoopEdgeNow by rememberUpdatedState(onMoveLoopEdge)
    val onCommitLoopEdgeNow by rememberUpdatedState(onCommitLoopEdge)
    val onMoveBookmarkNow by rememberUpdatedState(onMoveBookmark)
    val onCommitBookmarkNow by rememberUpdatedState(onCommitBookmark)
    val onSelectLoopNow by rememberUpdatedState(onSelectLoop)

    LaunchedEffect(durationMs) {
        view = null
        laneActive = false
        bubbleMs = null
        scrubMs = null
    }

    // Following keeps the playhead three quarters across a zoomed view, so a
    // quarter of the view shows what is coming.
    LaunchedEffect(positionMs, follow, playing) {
        val v = view ?: return@LaunchedEffect
        if (!follow || !playing || scrubMs != null || durationMs <= 0) return@LaunchedEffect
        WaveView.followAt(v, positionMs.toDouble() / durationMs, FOLLOW_ANCHOR)?.let { view = it }
    }

    val span = view ?: WaveView.Window(0.0, 1.0)
    val shownMs = scrubMs ?: positionMs
    val spanNow by rememberUpdatedState(span)

    fun xToMs(x: Float, width: Float): Long {
        val d = durationNow
        if (d <= 0 || width <= 0f) return 0L
        val s = spanNow
        val frac = s.start + (x / width).coerceIn(0f, 1f) * (s.end - s.start)
        return (frac * d).toLong().coerceIn(0, d)
    }

    fun msToX(ms: Long, width: Float): Float {
        val d = durationNow
        if (d <= 0) return 0f
        val s = spanNow
        val frac = ms.toDouble() / d
        return (((frac - s.start) / (s.end - s.start)) * width).toFloat()
    }

    fun msPerPx(width: Float): Float {
        val s = spanNow
        return if (width <= 0f) 0f else ((s.end - s.start) * durationNow / width).toFloat()
    }

    fun precise(base: Long, dxPx: Float, rampPx: Float, msPerPx: Float): Long {
        val mag = abs(dxPx)
        val scaled = if (rampPx > 0f && mag < rampPx) dxPx * (mag / rampPx) else dxPx
        return (base + scaled * msPerPx).roundToInt().toLong().coerceIn(0L, durationNow)
    }

    fun coreLoops() = loopsNow.map { Timeline.Loop(it.id, it.startMs, it.endMs, it.enabled) }

    Box(modifier = modifier) {
        Column {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .onboardingTarget(onboarding, "loop")
                    .onSizeChanged { chartWidth = it.width.toFloat() }
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val width = size.width.toFloat()
                            val rampPx = 10.dp.toPx()
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val startMs = xToMs(down.position.x, width)
                            val tolerance = (GRAB_RADIUS.toPx() * msPerPx(width)).toLong()
                            val edge = Timeline.edgeAt(coreLoops(), startMs, tolerance)
                            val pressed = edge?.let { e -> loopsNow.firstOrNull { it.id == e.loopId } }
                                ?: Timeline.loopAt(coreLoops(), startMs)
                                    ?.let { hit -> loopsNow.firstOrNull { it.id == hit.id } }
                            val longPress = awaitLongPressOrCancellation(down.id)

                            suspend fun AwaitPointerEventScope.dragEdge(e: Timeline.Edge, loop: LoopSection) {
                                val base = if (e.isStart) loop.startMs else loop.endMs
                                val msPer = msPerPx(width)
                                var accum = currentEvent.changes.firstOrNull { it.id == down.id }
                                    ?.let { it.position.x - down.position.x } ?: 0f
                                var movedPx = abs(accum)
                                bubbleMs = base
                                trackUntilUp(down.id) { change ->
                                    accum += change.position.x - change.previousPosition.x
                                    movedPx = maxOf(movedPx, abs(accum))
                                    val next = precise(base, accum, rampPx, msPer)
                                    bubbleMs = next
                                    onMoveLoopEdgeNow(e.loopId, e.isStart, next)
                                }
                                bubbleMs = null
                                if (movedPx < 2f) {
                                    loopMenu = loop
                                } else {
                                    onCommitLoopEdgeNow(e.loopId)
                                }
                            }

                            suspend fun AwaitPointerEventScope.dragCreate(from: Long) {
                                laneStart = from
                                laneEnd = from
                                laneActive = true
                                bubbleMs = from
                                var farthest = 0f
                                trackUntilUp(down.id) { change ->
                                    laneEnd = xToMs(change.position.x, width)
                                    farthest = maxOf(farthest, abs(change.position.x - down.position.x))
                                    bubbleMs = laneEnd
                                }
                                if (farthest >= 10.dp.toPx()) onCreateLoopNow(laneStart, laneEnd)
                                laneActive = false
                                bubbleMs = null
                            }

                            val released = currentEvent.changes
                                .firstOrNull { it.id == down.id }?.pressed != true
                            when {
                                // Tap: select the loop under the finger.
                                longPress == null && released ->
                                    pressed?.let { onSelectLoopNow(it.id) }
                                // An edge drags directly or after a hold; a hold
                                // without movement opens the loop's menu.
                                edge != null && pressed != null -> dragEdge(edge, pressed)
                                // Holding inside a loop opens its menu.
                                longPress != null && pressed != null -> {
                                    trackUntilUp(down.id) {}
                                    loopMenu = pressed
                                }
                                // Dragging inside a loop would overlap it.
                                pressed != null -> trackUntilUp(down.id) {}
                                else -> dragCreate(startMs)
                            }
                        }
                    },
            ) {
                val w = size.width
                val h = size.height
                val barTop = 6f
                val barH = h - 12f
                val corner = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                val edgeW = 3.dp.toPx()
                val pad = 6.dp.toPx()
                loops.forEach { loop ->
                    val x0 = msToX(loop.startMs, w)
                    val x1 = msToX(loop.endMs, w)
                    val selected = loop.id == selectedLoopId
                    val color = when {
                        !loop.enabled -> Color.White.copy(alpha = 0.18f)
                        selected -> accent
                        else -> accent.copy(alpha = 0.55f)
                    }
                    val barW = (x1 - x0).coerceAtLeast(4f)
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(x0, barTop),
                        size = Size(barW, barH),
                        cornerRadius = corner,
                    )
                    // Square edge posts mark exactly where the loop starts and ends.
                    val post = if (loop.enabled) Color.White.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.4f)
                    drawRect(post, topLeft = Offset(x0, barTop - 3f), size = Size(edgeW, barH + 6f))
                    drawRect(post, topLeft = Offset(x1 - edgeW, barTop - 3f), size = Size(edgeW, barH + 6f))

                    val name = loop.name?.takeIf { it.isNotBlank() }
                    if (name != null) {
                        val style = TextStyle(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (loop.enabled) Color(0xFF06121F) else Color.White.copy(alpha = 0.7f),
                        )
                        val available = barW - 2 * pad - 2 * edgeW
                        val full = textMeasurer.measure(name, style, maxLines = 1)
                        val minimal = textMeasurer.measure(name.take(1) + "…", style, maxLines = 1)
                        val fit = ChartLabels.fit(
                            available,
                            full.size.width.toFloat(),
                            minimal.size.width.toFloat(),
                        )
                        val layout = when (fit) {
                            ChartLabels.Fit.FULL -> full
                            ChartLabels.Fit.ELLIPSIZED -> textMeasurer.measure(
                                name,
                                style,
                                overflow = TextOverflow.Ellipsis,
                                maxLines = 1,
                                constraints = Constraints(maxWidth = available.toInt().coerceAtLeast(0)),
                            )
                            ChartLabels.Fit.NONE -> null
                        }
                        layout?.let {
                            drawText(
                                it,
                                topLeft = Offset(
                                    x0 + edgeW + pad,
                                    barTop + (barH - it.size.height) / 2f,
                                ),
                            )
                        }
                    }
                }
                if (laneActive) {
                    val x0 = msToX(minOf(laneStart, laneEnd), w)
                    val x1 = msToX(maxOf(laneStart, laneEnd), w)
                    drawRoundRect(
                        color = accent.copy(alpha = 0.4f),
                        topLeft = Offset(x0, barTop),
                        size = Size((x1 - x0).coerceAtLeast(4f), barH),
                        cornerRadius = corner,
                    )
                }
            }

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val width = size.width.toFloat()
                            val rampPx = 10.dp.toPx()
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val tolerance = (GRAB_RADIUS.toPx() * msPerPx(width)).toLong()
                            val marks = bookmarksNow
                            val idx = Timeline.nearestIndex(
                                marks.map { (it.t * 1000).toLong() },
                                xToMs(down.position.x, width),
                                tolerance,
                            )
                            val hit = idx?.let { marks[it] }
                            val longPress = awaitLongPressOrCancellation(down.id)
                            if (longPress == null) {
                                val now = currentEvent.changes.firstOrNull { it.id == down.id }
                                if ((now == null || !now.pressed) && hit != null) {
                                    onSeekNow((hit.t * 1000).toLong())
                                }
                                return@awaitEachGesture
                            }
                            if (hit == null) {
                                trackUntilUp(down.id) {}
                                return@awaitEachGesture
                            }
                            val base = (hit.t * 1000).toLong()
                            val msPer = msPerPx(width)
                            var accum = 0f
                            var movedPx = 0f
                            bubbleMs = base
                            trackUntilUp(down.id) { change ->
                                accum += change.position.x - change.previousPosition.x
                                movedPx = maxOf(movedPx, abs(accum))
                                val next = precise(base, accum, rampPx, msPer)
                                bubbleMs = next
                                onMoveBookmarkNow(hit.id, next)
                            }
                            bubbleMs = null
                            if (movedPx < 2f) {
                                bookmarkMenu = hit
                            } else {
                                onCommitBookmarkNow(hit.id)
                            }
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

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(104.dp)
                    .onboardingTarget(onboarding, "wave")
                    .pointerInput(durationMs) {
                        detectTransformGestures { centroid, pan, zoom, _ ->
                            val w = view ?: WaveView.Window(0.0, 1.0)
                            val s = w.end - w.start
                            // Panning by hand means the user wants to look
                            // elsewhere, so stop following the playhead.
                            if (zoom == 1f && pan.x != 0f && view != null && followNow) {
                                onFollowChangeNow(false)
                            }
                            val panFrac = -(pan.x / size.width.toFloat()) * s
                            var next = WaveView.panBy(w, panFrac)
                            if (zoom != 1f) {
                                val absCenter = w.start + (centroid.x / size.width.toFloat()) * s
                                next = WaveView.zoomAt(next ?: w, absCenter, 1.0 / zoom)
                            }
                            view = next
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures { off ->
                            val width = size.width.toFloat()
                            val snapDist = 18.dp.toPx()
                            val hit = bookmarksNow.minByOrNull { b ->
                                abs(msToX((b.t * 1000).toLong(), width) - off.x)
                            }?.takeIf { b -> abs(msToX((b.t * 1000).toLong(), width) - off.x) < snapDist }
                            onSeekNow(hit?.let { (it.t * 1000).toLong() } ?: xToMs(off.x, width))
                        }
                    }
                    .pointerInput(Unit) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { off ->
                                scrubMs = xToMs(off.x, size.width.toFloat())
                            },
                            onDragEnd = {
                                scrubMs?.let { onSeekNow(it) }
                                scrubMs = null
                            },
                            onDragCancel = { scrubMs = null },
                        ) { change, _ ->
                            scrubMs = xToMs(change.position.x, size.width.toFloat())
                            change.consume()
                        }
                    }
                    .pointerInput(Unit) {
                        // Zoomed out there is nothing to pan, so a plain drag scrubs.
                        // This runs before the pan and long-press handlers and
                        // steps aside for a pinch, a long-press, or a zoomed view.
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val s = spanNow
                            if (s.end - s.start < 0.999) return@awaitEachGesture
                            val slop = viewConfiguration.touchSlop
                            val holdMs = viewConfiguration.longPressTimeoutMillis
                            var scrubbing = false
                            while (true) {
                                val event = awaitPointerEvent()
                                if (event.changes.size > 1) {
                                    if (scrubbing) scrubMs = null
                                    return@awaitEachGesture
                                }
                                val change = event.changes.firstOrNull { it.id == down.id }
                                    ?: return@awaitEachGesture
                                if (!scrubbing) {
                                    if (!change.pressed) return@awaitEachGesture
                                    if (change.uptimeMillis - down.uptimeMillis >= holdMs) {
                                        return@awaitEachGesture
                                    }
                                    if (abs(change.position.x - down.position.x) > slop) scrubbing = true
                                }
                                if (scrubbing) {
                                    val ms = xToMs(change.position.x, size.width.toFloat())
                                    scrubMs = ms
                                    change.consume()
                                    if (!change.pressed) {
                                        onSeekNow(ms)
                                        scrubMs = null
                                        return@awaitEachGesture
                                    }
                                }
                            }
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
                        val played = idx.toFloat() / n <= shownMs.toFloat() / durationMs.coerceAtLeast(1)
                        drawRoundRect(
                            color = if (played) accent.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.28f),
                            topLeft = Offset(x, mid - barH),
                            size = Size((step - 1f).coerceAtLeast(1f), barH * 2f),
                            cornerRadius = CornerRadius(1f, 1f),
                        )
                    }
                }

                // Bookmark names read bottom to top beside their line, so they
                // label the wave without crowding the strips above it.
                val nameStyle = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Medium, color = BOOKMARK_GOLD)
                bookmarks.forEach { b ->
                    val x = msToX((b.t * 1000).toLong(), w)
                    if (x < 0f || x > w) return@forEach
                    drawLine(
                        color = BOOKMARK_GOLD.copy(alpha = 0.35f),
                        start = Offset(x, 0f),
                        end = Offset(x, h),
                        strokeWidth = 1.dp.toPx(),
                    )
                    val name = b.name?.takeIf { it.isNotBlank() } ?: return@forEach
                    val layout = textMeasurer.measure(
                        name,
                        nameStyle,
                        overflow = TextOverflow.Ellipsis,
                        maxLines = 1,
                        constraints = Constraints(maxWidth = (h - 8.dp.toPx()).toInt().coerceAtLeast(0)),
                    )
                    val pivot = Offset(x + 2.dp.toPx(), h - 4.dp.toPx())
                    rotate(-90f, pivot) { drawText(layout, topLeft = pivot) }
                }

                if (durationMs > 0) {
                    val pf = shownMs.toFloat() / durationMs
                    if (pf >= span.start && pf <= span.end) {
                        val px = msToX(shownMs, w)
                        drawLine(
                            color = Color.White.copy(alpha = 0.9f),
                            start = Offset(px, 2f),
                            end = Offset(px, h - 6f),
                            strokeWidth = 3f,
                        )
                        val label = textMeasurer.measure(
                            formatTime(shownMs),
                            TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color.White),
                        )
                        val padX = 4.dp.toPx()
                        val boxW = label.size.width + 2 * padX
                        val boxH = label.size.height.toFloat()
                        val boxX = ChartLabels.sideLabelX(px, boxW, w, gap = 3.dp.toPx())
                        val boxY = h - boxH - 4.dp.toPx()
                        drawRoundRect(
                            color = Color(0xCC08080B),
                            topLeft = Offset(boxX, boxY),
                            size = Size(boxW, boxH),
                            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                        )
                        drawText(label, topLeft = Offset(boxX + padX, boxY))
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
            initialName = loop.name ?: "",
            onRename = { onRenameLoop(loop.id, it); loopMenu = null },
            onDelete = { onDeleteLoop(loop.id); loopMenu = null },
            onDismiss = { loopMenu = null },
        )
    }
    bookmarkMenu?.let { bookmark ->
        ChartMenu(
            title = bookmark.name ?: "Bookmark ${formatTime((bookmark.t * 1000).toLong())}",
            initialName = bookmark.name ?: "",
            onRename = { onRenameBookmark(bookmark.id, it); bookmarkMenu = null },
            onDelete = { onDeleteBookmark(bookmark.id); bookmarkMenu = null },
            onDismiss = { bookmarkMenu = null },
        )
    }
}

/** Follows one pointer until it lifts, consuming its moves. */
private suspend fun AwaitPointerEventScope.trackUntilUp(
    id: PointerId,
    onMove: (PointerInputChange) -> Unit,
) {
    while (true) {
        val event = awaitPointerEvent()
        val change = event.changes.firstOrNull { it.id == id } ?: return
        if (change.pressed) onMove(change)
        change.consume()
        if (!change.pressed) return
    }
}

@Composable
private fun ChartMenu(
    title: String,
    initialName: String,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initialName) }
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
                TextButton(onClick = { renaming = true }) { Text("Rename") }
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
