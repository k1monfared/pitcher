package pitcher.android.ui

import android.app.Application
import android.content.ComponentName
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import androidx.compose.runtime.mutableStateMapOf
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.roundToInt
import pitcher.android.data.Bookmark
import pitcher.android.data.LoopSection
import pitcher.android.data.ShelfRepository
import pitcher.android.data.Track
import pitcher.android.data.Variant
import pitcher.android.media.AudioRenderer
import pitcher.android.media.MediaImporter
import pitcher.android.media.PlaybackService
import pitcher.android.media.RenderedStore
import pitcher.android.media.TunerDecoder
import pitcher.android.media.WaveformDecoder
import pitcher.core.ExportFormat
import pitcher.core.LoopMode
import pitcher.core.LoopPlayback
import pitcher.core.Notes
import pitcher.core.PcmConcat
import pitcher.core.RenderPlan
import pitcher.core.Timeline
import pitcher.core.Tuner
import pitcher.core.Waveform

class PitcherViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = ShelfRepository(app)

    var tracks by mutableStateOf<List<Track>>(emptyList())
        private set
    var current by mutableStateOf<Track?>(null)
        private set
    var variants by mutableStateOf<List<Variant>>(emptyList())
        private set
    var bookmarks by mutableStateOf<List<Bookmark>>(emptyList())
        private set
    var peaks by mutableStateOf(FloatArray(0))
        private set
    var positionMs by mutableStateOf(0L)
        private set
    var durationMs by mutableStateOf(0L)
        private set
    var isPlaying by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
        private set
    var detectedHz by mutableStateOf<Double?>(null)
        private set
    var detectMessage by mutableStateOf<String?>(null)
        private set
    var faderCents by mutableStateOf(0)
        private set
    var tempo by mutableStateOf(1f)
        private set
    /** The saved pitch matching the current pitch, so the shelf always shows what plays. */
    val selectedVariant: Variant?
        get() = variants.firstOrNull { it.cents == faderCents }
    val selectedVariantId: Long?
        get() = selectedVariant?.id
    var loops by mutableStateOf<List<LoopSection>>(emptyList())
        private set
    var selectedLoopId by mutableStateOf<Long?>(null)
        private set
    var loopMode by mutableStateOf(LoopMode.NONE)
        private set
    var tunerSource by mutableStateOf<String?>(null)
        private set
    var tunerTarget by mutableStateOf<String?>(null)
        private set
    var defaultFolder by mutableStateOf<String?>(null)
        private set
    var shareUri by mutableStateOf<Uri?>(null)
        private set
    var exportFormat by mutableStateOf(ExportFormat.M4a)
        private set
    var exportLoopOnly by mutableStateOf(false)
        private set
    var exportMime by mutableStateOf(ExportFormat.M4a.mime)
        private set
    var keepScreenOn by mutableStateOf(true)
        private set
    var hapticsEnabled by mutableStateOf(true)
        private set
    var followPlayhead by mutableStateOf(true)
        private set
    var onboardingActive by mutableStateOf(false)
        private set
    var storageImportsBytes by mutableStateOf(0L)
        private set
    var storageExportsBytes by mutableStateOf(0L)
        private set

    private var controller: MediaController? = null

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(playing: Boolean) {
            isPlaying = playing
        }

        override fun onPlaybackStateChanged(state: Int) {
            if (state == Player.STATE_READY) {
                durationMs = (controller?.duration ?: 0L).coerceAtLeast(0)
            }
            if (state == Player.STATE_ENDED) {
                // A loop that reaches the end of the track ends playback before
                // the tick sees it pass the loop end, so restart it here.
                val target = LoopPlayback.seekTarget(coreLoops(), loopMode, Long.MAX_VALUE, selectedLoopId)
                if (target != null) {
                    controller?.seekTo(target)
                    controller?.play()
                    positionMs = target
                } else {
                    isPlaying = false
                }
            }
        }
    }

    private val prefs by lazy {
        getApplication<Application>().getSharedPreferences("pitcher", Application.MODE_PRIVATE)
    }

    init {
        keepScreenOn = prefs.getBoolean("keep_screen_on", true)
        hapticsEnabled = prefs.getBoolean("haptics", true)
        followPlayhead = prefs.getBoolean("follow_playhead", true)
        onboardingActive = !prefs.getBoolean("onboarding_done", false)
        defaultFolder = prefs.getString("default_folder", null)
        refreshTracks()
        connectController()
        viewModelScope.launch {
            while (isActive) {
                if (isPlaying) {
                    val pos = controller?.currentPosition ?: 0L
                    positionMs = pos
                    val target = LoopPlayback.seekTarget(coreLoops(), loopMode, pos, selectedLoopId)
                    if (target != null) {
                        controller?.seekTo(target)
                        positionMs = target
                    }
                }
                delay(if (loopMode == LoopMode.NONE) 50 else 15)
            }
        }
    }

    private fun connectController() {
        val app = getApplication<Application>()
        val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
        val future = MediaController.Builder(app, token).buildAsync()
        future.addListener(
            {
                runCatching { future.get() }.onSuccess { c ->
                    controller = c
                    c.addListener(listener)
                }
            },
            MoreExecutors.directExecutor(),
        )
    }

    fun refreshTracks() {
        tracks = repo.listTracks()
    }

    fun import(uri: Uri, onDone: () -> Unit) {
        viewModelScope.launch {
            message = "importing..."
            try {
                val imported = withContext(Dispatchers.IO) { MediaImporter.copyIn(getApplication(), uri) }
                val id = repo.addTrack(
                    sourcePath = imported.file.absolutePath,
                    sourceKind = "file",
                    sourceUrl = null,
                    title = imported.displayName,
                    artist = null,
                    durationS = imported.durationS,
                    sampleRate = imported.sampleRate,
                )
                refreshTracks()
                repo.getTrack(id)?.let { openTrack(it) }
                message = null
            } catch (e: Exception) {
                message = "import failed: ${e.message}"
            } finally {
                onDone()
            }
        }
    }

    fun openTrack(track: Track) {
        current = track
        variants = repo.listVariants(track.id)
        bookmarks = repo.listBookmarks(track.id)
        loops = repo.listLoops(track.id)
        selectedLoopId = loops.firstOrNull()?.id
        loopMode = LoopMode.NONE
        exportLoopOnly = false
        tunerSource = track.tunerSource
        tunerTarget = track.tunerTarget
        peaks = FloatArray(0)
        detectedHz = null
        detectMessage = null
        faderCents = 0
        positionMs = 0
        durationMs = (track.durationS * 1000).toLong()
        controller?.apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(File(track.sourcePath))))
            prepare()
            seekTo(0)
            setPlaybackParameters(PlaybackParameters(tempo, 1f))
        }
        viewModelScope.launch {
            val p = withContext(Dispatchers.IO) { WaveformDecoder.decodePeaks(track.sourcePath) }
            if (current?.id == track.id) peaks = p
        }
    }

    fun togglePlay() {
        if (current == null) return
        val c = controller ?: return
        if (isPlaying) c.pause() else c.play()
    }

    fun seekTo(ms: Long) {
        val clamped = ms.coerceIn(0, maxOf(durationMs, 0))
        controller?.seekTo(clamped)
        positionMs = clamped
    }

    fun renameTrack(track: Track, title: String, artist: String) {
        repo.renameTrack(track.id, title, artist.ifBlank { null })
        refreshTracks()
        if (current?.id == track.id) current = repo.getTrack(track.id)
    }

    fun deleteTrack(track: Track) {
        val dataDir = File(getApplication<Application>().filesDir, "imports").parentFile!!
        val isCurrent = current?.id == track.id
        if (isCurrent) controller?.stop()
        repo.listVariants(track.id).forEach { forgetPitch(it.id) }
        repo.deleteTrackWithFiles(track.id, dataDir)
        if (isCurrent) {
            current = null
            variants = emptyList()
            bookmarks = emptyList()
            loops = emptyList()
            selectedLoopId = null
            loopMode = LoopMode.NONE
            peaks = FloatArray(0)
            positionMs = 0
            durationMs = 0
        }
        refreshTracks()
    }

    /** Adds a bookmark at the playhead. One already within 750 ms is kept, not toggled off. */
    fun addBookmark(name: String?) {
        val track = current ?: return
        val atMs = controller?.currentPosition ?: positionMs
        val times = bookmarks.map { (it.t * 1000).toLong() }
        if (Timeline.nearestIndex(times, atMs, toleranceMs = 750L) != null) return
        repo.addBookmark(track.id, atMs / 1000.0, name)
        bookmarks = repo.listBookmarks(track.id)
    }

    fun renameBookmark(id: Long, name: String) {
        repo.renameBookmark(id, name)
        current?.let { bookmarks = repo.listBookmarks(it.id) }
    }

    fun deleteBookmark(id: Long) {
        repo.deleteBookmark(id)
        bookmarks = bookmarks.filterNot { it.id == id }
    }

    /** Moves a bookmark in memory while it is dragged; [commitBookmark] saves it. */
    fun previewBookmark(id: Long, t: Double) {
        bookmarks = bookmarks.map { if (it.id == id) it.copy(t = t) else it }
    }

    fun commitBookmark(id: Long) {
        val b = bookmarks.firstOrNull { it.id == id } ?: return
        repo.updateBookmarkTime(id, b.t)
        current?.let { bookmarks = repo.listBookmarks(it.id) }
    }

    fun selectOriginal() {
        setPitchCents(0)
    }

    private fun coreLoops(): List<Timeline.Loop> =
        loops.map { Timeline.Loop(it.id, it.startMs, it.endMs, it.enabled) }

    fun addLoop(startMs: Long, endMs: Long, name: String? = null) {
        val track = current ?: return
        var s = minOf(startMs, endMs)
        var e = maxOf(startMs, endMs)
        if (e - s < Timeline.MIN_LOOP_MS) {
            val limit = if (durationMs > 0) durationMs else e
            e = (s + Timeline.MIN_LOOP_MS).coerceAtMost(maxOf(limit, s + Timeline.MIN_LOOP_MS))
            s = (e - Timeline.MIN_LOOP_MS).coerceAtLeast(0)
        }
        if (!Timeline.canAdd(coreLoops(), s, e)) return
        val id = repo.addLoop(track.id, s, e, name)
        loops = repo.listLoops(track.id)
        selectedLoopId = id
    }

    fun deleteLoop(id: Long) {
        repo.deleteLoop(id)
        current?.let { loops = repo.listLoops(it.id) }
        if (selectedLoopId == id) selectedLoopId = loops.firstOrNull()?.id
        if (loops.isEmpty()) loopMode = LoopMode.NONE
        if (loops.none { it.enabled }) exportLoopOnly = false
    }

    fun renameLoop(id: Long, name: String) {
        repo.renameLoop(id, name)
        current?.let { loops = repo.listLoops(it.id) }
    }

    fun setLoopEnabled(id: Long, enabled: Boolean) {
        repo.setLoopEnabled(id, enabled)
        current?.let { loops = repo.listLoops(it.id) }
    }

    /**
     * Moves a loop edge in memory while it is dragged, clamped by the timeline
     * rules. [commitLoopEdge] saves it when the finger lifts.
     */
    fun previewLoopEdge(id: Long, isStart: Boolean, valueMs: Long) {
        val moved = Timeline.moveEdge(coreLoops(), id, isStart, valueMs, durationMs)
        val target = moved.firstOrNull { it.id == id } ?: return
        loops = loops.map { if (it.id == id) it.copy(startMs = target.startMs, endMs = target.endMs) else it }
    }

    fun commitLoopEdge(id: Long) {
        val loop = loops.firstOrNull { it.id == id } ?: return
        repo.updateLoopEdges(id, loop.startMs, loop.endMs)
    }

    fun selectLoop(id: Long?) {
        selectedLoopId = id
    }

    /** Cycles the master loop mode: whole song, all loops, one loop. */
    fun cycleLoopMode() {
        loopMode = when (loopMode) {
            LoopMode.NONE -> LoopMode.ALL
            LoopMode.ALL -> LoopMode.ONE
            LoopMode.ONE -> LoopMode.NONE
        }
        if (loopMode == LoopMode.NONE) return
        val pos = controller?.currentPosition ?: positionMs
        val target = when (loopMode) {
            LoopMode.ONE -> {
                val containing = loops.firstOrNull { it.enabled && pos >= it.startMs && pos < it.endMs }
                val chosen = containing ?: loops.firstOrNull { it.enabled }
                selectedLoopId = chosen?.id
                chosen
            }
            LoopMode.ALL -> loops.firstOrNull { it.enabled }
            else -> null
        }
        target?.let {
            controller?.seekTo(it.startMs)
            positionMs = it.startMs
        }
    }

    fun clearLoops() {
        if (current == null) return
        loops.forEach { repo.deleteLoop(it.id) }
        loops = emptyList()
        selectedLoopId = null
        loopMode = LoopMode.NONE
        exportLoopOnly = false
    }

    fun changeExportFormat(format: ExportFormat) {
        exportFormat = format
    }

    fun changeExportLoopOnly(value: Boolean) {
        exportLoopOnly = value
    }

    /**
     * The ranges to render: the enabled loops when loop-only is on, or null for
     * the whole track.
     */
    private fun renderSegments(): List<Pair<Long, Long>>? {
        if (!exportLoopOnly) return null
        val enabled = Timeline.active(coreLoops())
        return enabled.takeIf { it.isNotEmpty() }?.map { it.startMs to it.endMs }
    }

    /** What a kept pitch is busy with. Each pitch works on its own, independently. */
    enum class PitchWork { PREPARING, SAVING, SHARING }

    /** Busy pitches by id. Several can be busy at once. */
    val pitchWork = mutableStateMapOf<Long, PitchWork>()

    /** The last failure or cancellation per pitch, shown in its sheet. */
    val pitchStatus = mutableStateMapOf<Long, String>()

    /** Bumps when a file is recorded, so the sheet re-checks what is saved. */
    var rendersVersion by mutableStateOf(0)
        private set

    /** A short message for a popup, consumed once shown. */
    var popup by mutableStateOf<String?>(null)
        private set

    fun consumePopup() {
        popup = null
    }

    // Two renders at a time keeps the phone responsive and memory in check.
    @OptIn(ExperimentalCoroutinesApi::class)
    private val renderDispatcher = Dispatchers.Default.limitedParallelism(2)
    private val prepares = HashMap<Long, Deferred<File>>()
    private val exportJobs = HashMap<Long, Job>()
    private val cancelFlags = HashMap<Long, AtomicBoolean>()

    private fun refreshVariants() {
        current?.let { variants = repo.listVariants(it.id) }
    }

    /**
     * Keeps the current pitch on the shelf and starts rendering it in the
     * background, so saving or sharing a file later only has to encode.
     */
    fun keepPitch() {
        val track = current ?: return
        if (faderCents == 0 || repo.findVariantByCents(track.id, faderCents) != null) return
        val id = repo.addPitch(track.id, faderCents)
        refreshVariants()
        prepare(id, faderCents)
    }

    private fun preparedFile(variantId: Long): File = File(exportsDir(), "pitch-$variantId.wav")

    /** A cancel flag shared by a pitch's prepare and export, fresh when it was idle. */
    private fun flagFor(variantId: Long): AtomicBoolean {
        val busy = prepares[variantId]?.isActive == true || exportJobs[variantId]?.isActive == true
        val existing = cancelFlags[variantId]
        if (busy && existing != null) return existing
        return AtomicBoolean(false).also { cancelFlags[variantId] = it }
    }

    /**
     * Renders a kept pitch of the whole song to a cached WAV, once. Concurrent
     * callers share the same render.
     */
    private fun prepare(variantId: Long, cents: Int): Deferred<File> {
        prepares[variantId]?.takeIf { it.isActive }?.let { return it }
        val track = current ?: return CompletableDeferred<File>().apply {
            completeExceptionally(IllegalStateException("no song open"))
        }
        val file = preparedFile(variantId)
        val flag = flagFor(variantId)
        val deferred = viewModelScope.async {
            if (file.exists()) return@async file
            if (pitchWork[variantId] == null) pitchWork[variantId] = PitchWork.PREPARING
            try {
                withContext(renderDispatcher) {
                    val part = File(file.path + ".part")
                    try {
                        AudioRenderer.render(
                            sourcePath = track.sourcePath,
                            target = part,
                            format = ExportFormat.Wav,
                            cents = cents,
                            cancelled = { flag.get() },
                        )
                        if (!part.renameTo(file)) error("could not keep the rendered pitch")
                    } finally {
                        part.delete()
                    }
                }
                file
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                pitchStatus[variantId] = "could not render: ${reasonOf(e)}"
                throw e
            } finally {
                if (pitchWork[variantId] == PitchWork.PREPARING) pitchWork.remove(variantId)
            }
        }
        prepares[variantId] = deferred
        return deferred
    }

    /** The key of the ranges the next render covers: empty for the whole track. */
    fun currentSegmentsKey(): String = RenderPlan.segmentsKey(renderSegments())

    /** The default file name, without extension, for [cents] and an optional pitch name. */
    fun defaultFileBase(cents: Int = faderCents, name: String? = selectedVariant?.name): String {
        val track = current ?: return ""
        val ext = exportFormat.extension
        val base = Notes.downloadFilename(track.title, track.artist, track.sourcePath, name, cents, ext)
            .removeSuffix(".$ext")
        return if (renderSegments() != null) "$base - loops" else base
    }

    /**
     * The saved file of [variantId] that already matches [fileBase], [format],
     * and [segments], if it still exists. A file deleted outside the app is
     * forgotten here so it can be saved again.
     */
    suspend fun matchingRender(
        variantId: Long,
        fileBase: String,
        format: ExportFormat = exportFormat,
        segments: String = currentSegmentsKey(),
    ): RenderPlan.Record? {
        val match = RenderPlan.reusable(repo.listRenders(variantId), format.id, fileBase, segments)
            ?: return null
        val present = withContext(Dispatchers.IO) { RenderedStore.isPresent(getApplication(), match.uri) }
        if (!present) {
            repo.deleteRender(match.id)
            refreshVariants()
            return null
        }
        return match
    }

    /** Saves [variant] as a file named [fileBase], unless that exact file exists. */
    fun saveFile(variant: Variant, fileBase: String) = exportPitch(variant, fileBase, share = false)

    /** Shares [variant] as [fileBase], rendering the file only if it does not exist. */
    fun shareFile(variant: Variant, fileBase: String) = exportPitch(variant, fileBase, share = true)

    /** Shares a kept pitch from the shelf's menu with its default file name. */
    fun shareVariant(variant: Variant) =
        exportPitch(variant, defaultFileBase(variant.cents, variant.name), share = true)

    private fun exportPitch(variant: Variant, fileBase: String, share: Boolean) {
        val track = current ?: return
        val id = variant.id
        if (exportJobs[id]?.isActive == true) return
        val format = exportFormat
        val segments = renderSegments()
        val segmentsKey = RenderPlan.segmentsKey(segments)
        val folder = effectiveFolder()
        val name = fileBase.trim().ifEmpty { defaultFileBase(variant.cents, variant.name) }
        val flag = flagFor(id)
        val app = getApplication<Application>()
        exportJobs[id] = viewModelScope.launch {
            pitchWork[id] = if (share) PitchWork.SHARING else PitchWork.SAVING
            pitchStatus.remove(id)
            try {
                val existing = matchingRender(id, name, format, segmentsKey)
                val uri = existing?.uri ?: run {
                    val prepared = prepare(id, variant.cents).await()
                    val saved = withContext(renderDispatcher) {
                        val tmpDir = File(app.cacheDir, "renders").apply { mkdirs() }
                        val tmp = File(tmpDir, "render-${System.nanoTime()}.${format.extension}")
                        try {
                            // The prepared file is already shifted, so this only
                            // cuts the loops and encodes.
                            AudioRenderer.render(
                                sourcePath = prepared.path,
                                target = tmp,
                                format = format,
                                cents = 0,
                                segments = segments,
                                cancelled = { flag.get() },
                            )
                            RenderedStore.save(app, folder, "$name.${format.extension}", format.mime, tmp)
                        } finally {
                            tmp.delete()
                        }
                    }
                    repo.addRender(id, saved, format.id, name, segmentsKey)
                    if (current?.id == track.id) refreshVariants()
                    rendersVersion++
                    saved
                }
                if (share) {
                    shareUri = Uri.parse(uri)
                    exportMime = format.mime
                } else if (existing == null) {
                    popup = "Saved $name.${format.extension} to ${RenderedStore.folderLabel(folder)}"
                } else {
                    popup = "Already saved"
                }
            } catch (_: CancellationException) {
                pitchStatus[id] = "cancelled"
            } catch (e: Throwable) {
                pitchStatus[id] = "failed: ${reasonOf(e)}"
                popup = "Could not save: ${reasonOf(e)}"
            } finally {
                pitchWork.remove(id)
                exportJobs.remove(id)
            }
        }
    }

    /** Stops whatever [variantId] is rendering, leaving other pitches alone. */
    fun cancelPitch(variantId: Long) {
        cancelFlags[variantId]?.set(true)
        exportJobs[variantId]?.cancel()
        prepares[variantId]?.cancel()
        prepares.remove(variantId)
        pitchWork.remove(variantId)
    }

    private fun reasonOf(e: Throwable): String = when (e) {
        is OutOfMemoryError -> "not enough memory; try a shorter track or a smaller section"
        else -> e.message ?: e.toString()
    }

    fun consumeShareUri() {
        shareUri = null
    }

    fun changeKeepScreenOn(value: Boolean) {
        keepScreenOn = value
        prefs.edit().putBoolean("keep_screen_on", value).apply()
    }

    fun changeHaptics(value: Boolean) {
        hapticsEnabled = value
        prefs.edit().putBoolean("haptics", value).apply()
    }

    fun changeFollowPlayhead(value: Boolean) {
        followPlayhead = value
        prefs.edit().putBoolean("follow_playhead", value).apply()
    }

    /** Reopens the guided tour, e.g. from the settings sheet. */
    fun startOnboarding() {
        onboardingActive = true
    }

    /** The user finished or skipped the tour. It will not auto-open again. */
    fun finishOnboarding() {
        onboardingActive = false
        prefs.edit().putBoolean("onboarding_done", true).apply()
    }

    fun refreshStorage() {
        viewModelScope.launch {
            storageImportsBytes = withContext(Dispatchers.IO) {
                dirSize(MediaImporter.importsDir(getApplication()))
            }
            storageExportsBytes = withContext(Dispatchers.IO) { dirSize(exportsDir()) }
        }
    }

    fun clearExports() {
        viewModelScope.launch {
            storageExportsBytes = withContext(Dispatchers.IO) {
                exportsDir().listFiles()?.forEach { runCatching { it.delete() } }
                dirSize(exportsDir())
            }
        }
    }

    private fun exportsDir(): File =
        File(getApplication<Application>().cacheDir, "exports").apply { mkdirs() }

    private fun dirSize(dir: File): Long =
        dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    fun detectAtPlayhead() {
        val track = current ?: return
        val atMs = controller?.currentPosition ?: positionMs
        viewModelScope.launch {
            detectMessage = "detecting..."
            detectedHz = null
            val window = withContext(Dispatchers.IO) {
                TunerDecoder.decodeWindow(track.sourcePath, atMs)
            }
            if (window == null) {
                detectMessage = "cannot read audio here"
                return@launch
            }
            val reading = Tuner.detect(window.samples, window.sampleRate)
            if (reading == null) {
                detectMessage = "no clear pitch at ${Waveform.formatClock(atMs / 1000.0)}"
            } else {
                detectedHz = reading.hz
                detectMessage = null
            }
        }
    }

    fun clearDetection() {
        detectedHz = null
        detectMessage = null
    }

    fun updateTunerSource(note: String?) {
        tunerSource = note?.trim()?.takeIf { it.isNotEmpty() }
        current?.let { repo.setTunerNotes(it.id, tunerSource, tunerTarget) }
    }

    fun updateTunerTarget(note: String?) {
        tunerTarget = note?.trim()?.takeIf { it.isNotEmpty() }
        current?.let { repo.setTunerNotes(it.id, tunerSource, tunerTarget) }
    }

    /** The interval between the source and target notes, in cents. */
    fun tunerCents(): Double? {
        val source = tunerSource ?: return null
        val target = tunerTarget ?: return null
        return Notes.centsBetweenNotes(source, target)
    }

    fun detectIntoTunerSource() = detectInto(isSource = true)

    fun detectIntoTunerTarget() = detectInto(isSource = false)

    private fun detectInto(isSource: Boolean) {
        val track = current ?: return
        val atMs = controller?.currentPosition ?: positionMs
        viewModelScope.launch {
            detectMessage = "detecting..."
            val reading = withContext(Dispatchers.IO) {
                TunerDecoder.decodeWindow(track.sourcePath, atMs)
                    ?.let { Tuner.detect(it.samples, it.sampleRate) }
            }
            if (reading == null) {
                detectMessage = "no clear pitch here"
                return@launch
            }
            detectMessage = null
            detectedHz = reading.hz
            val note = Notes.hzToNoteText(reading.hz)
            if (isSource) updateTunerSource(note) else updateTunerTarget(note)
        }
    }

    /** Sets the pitch slider to the interval between the two tuner notes. */
    fun applyTunerToFader() {
        val cents = tunerCents() ?: return
        setPitchCents(cents.roundToInt().coerceIn(-1200, 1200))
    }

    fun updateDefaultFolder(uri: String?) {
        defaultFolder = uri
        prefs.edit().putString("default_folder", uri).apply()
    }

    fun setSongFolder(uri: String?) {
        val track = current ?: return
        repo.setSaveFolder(track.id, uri)
        current = repo.getTrack(track.id)
    }

    fun effectiveFolder(): String? = current?.saveFolder ?: defaultFolder

    fun setPitchCents(cents: Int) {
        faderCents = cents
        applyPlaybackParams()
    }

    fun changeTempo(value: Float) {
        tempo = value
        applyPlaybackParams()
    }

    private fun applyPlaybackParams() {
        val pitch = Math.pow(2.0, faderCents / 1200.0).toFloat()
        controller?.setPlaybackParameters(PlaybackParameters(tempo, pitch))
    }

    /** Plays a kept pitch, and renders it in the background if it is not ready yet. */
    fun selectVariant(variant: Variant) {
        setPitchCents(variant.cents)
        if (!preparedFile(variant.id).exists()) prepare(variant.id, variant.cents)
    }

    fun renameVariant(variant: Variant, name: String) {
        repo.renameVariant(variant.id, name)
        current?.let { variants = repo.listVariants(it.id) }
    }

    /** Removes a pitch from the shelf. Files it saved stay in their folder. */
    fun deleteVariant(variant: Variant) {
        forgetPitch(variant.id)
        repo.deleteVariant(variant.id)
        variants = variants.filterNot { it.id == variant.id }
    }

    /** Stops a pitch's work and drops its background render. */
    private fun forgetPitch(variantId: Long) {
        cancelPitch(variantId)
        pitchStatus.remove(variantId)
        cancelFlags.remove(variantId)
        runCatching { preparedFile(variantId).delete() }
    }

    override fun onCleared() {
        controller?.release()
        repo.close()
        super.onCleared()
    }
}
