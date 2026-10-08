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
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
import pitcher.android.data.VariantSpec
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
    var selectedVariantId by mutableStateOf<Long?>(null)
        private set
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
    var exportMessage by mutableStateOf<String?>(null)
        private set
    var exportFormat by mutableStateOf(ExportFormat.M4a)
        private set
    var exportLoopOnly by mutableStateOf(false)
        private set
    var exportMime by mutableStateOf(ExportFormat.M4a.mime)
        private set
    var exporting by mutableStateOf(false)
        private set
    var keepScreenOn by mutableStateOf(true)
        private set
    var hapticsEnabled by mutableStateOf(true)
        private set
    var onboardingActive by mutableStateOf(false)
        private set
    var storageImportsBytes by mutableStateOf(0L)
        private set
    var storageExportsBytes by mutableStateOf(0L)
        private set

    private var controller: MediaController? = null
    private var renderJob: Job? = null

    @Volatile
    private var renderCancelled = false

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(playing: Boolean) {
            isPlaying = playing
        }

        override fun onPlaybackStateChanged(state: Int) {
            if (state == Player.STATE_READY) {
                durationMs = (controller?.duration ?: 0L).coerceAtLeast(0)
            }
            if (state == Player.STATE_ENDED) {
                isPlaying = false
            }
        }
    }

    private val prefs by lazy {
        getApplication<Application>().getSharedPreferences("pitcher", Application.MODE_PRIVATE)
    }

    init {
        keepScreenOn = prefs.getBoolean("keep_screen_on", true)
        hapticsEnabled = prefs.getBoolean("haptics", true)
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
                delay(50)
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
        exportLoopOnly = loops.any { it.enabled }
        tunerSource = track.tunerSource
        tunerTarget = track.tunerTarget
        peaks = FloatArray(0)
        detectedHz = null
        detectMessage = null
        faderCents = 0
        selectedVariantId = null
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
        controller?.stop()
        repo.deleteTrackWithFiles(track.id, dataDir)
        if (current?.id == track.id) {
            current = null
            variants = emptyList()
            bookmarks = emptyList()
            peaks = FloatArray(0)
            positionMs = 0
            durationMs = 0
        }
        refreshTracks()
    }

    fun addBookmark(name: String?) {
        val track = current ?: return
        val atMs = controller?.currentPosition ?: positionMs
        val existing = bookmarks.firstOrNull { kotlin.math.abs((it.t * 1000).toLong() - atMs) < 750L }
        if (existing != null) {
            repo.deleteBookmark(existing.id)
        } else {
            repo.addBookmark(track.id, atMs / 1000.0, name)
        }
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

    fun moveBookmark(id: Long, t: Double) {
        repo.updateBookmarkTime(id, t)
        current?.let { bookmarks = repo.listBookmarks(it.id) }
    }

    fun selectOriginal() {
        selectedVariantId = null
        setPitchCents(0)
    }

    private fun coreLoops(): List<Timeline.Loop> =
        loops.map { Timeline.Loop(it.id, it.startMs, it.endMs, it.enabled) }

    fun addLoop(startMs: Long, endMs: Long, name: String? = null) {
        val track = current ?: return
        val s = minOf(startMs, endMs)
        val e = maxOf(startMs, endMs)
        if (!Timeline.canAdd(coreLoops(), s, e)) return
        val id = repo.addLoop(track.id, s, e, name)
        loops = repo.listLoops(track.id)
        selectedLoopId = id
        exportLoopOnly = true
    }

    fun deleteLoop(id: Long) {
        repo.deleteLoop(id)
        current?.let { loops = repo.listLoops(it.id) }
        if (selectedLoopId == id) selectedLoopId = loops.firstOrNull()?.id
        if (loops.isEmpty()) {
            loopMode = LoopMode.NONE
            exportLoopOnly = false
        }
    }

    fun renameLoop(id: Long, name: String) {
        repo.renameLoop(id, name)
        current?.let { loops = repo.listLoops(it.id) }
    }

    fun setLoopEnabled(id: Long, enabled: Boolean) {
        repo.setLoopEnabled(id, enabled)
        current?.let { loops = repo.listLoops(it.id) }
    }

    fun moveLoopEdge(id: Long, isStart: Boolean, valueMs: Long) {
        val track = current ?: return
        val moved = Timeline.moveEdge(coreLoops(), id, isStart, valueMs, durationMs)
        val target = moved.firstOrNull { it.id == id } ?: return
        repo.updateLoopEdges(id, target.startMs, target.endMs)
        loops = repo.listLoops(track.id)
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
        val track = current ?: return
        loops.forEach { repo.deleteLoop(it.id) }
        loops = emptyList()
        selectedLoopId = null
        loopMode = LoopMode.NONE
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

    /**
     * Renders the current pitch into the library folder (Music/pitcher) and
     * keeps it. Renders are slow, so the file is saved right away and is not
     * lost if it is never shared.
     */
    fun renderAndKeep(name: String?) {
        startRender {
            renderToLibrary(faderCents, name)
            current?.let { variants = repo.listVariants(it.id) }
            exportMessage = "saved to ${RenderedStore.FOLDER}"
        }
    }

    /** Renders a specific saved pitch into the library. */
    fun renderAndKeepVariant(variant: Variant) {
        startRender {
            renderToLibrary(variant.cents, variant.name)
            current?.let { variants = repo.listVariants(it.id) }
            exportMessage = "saved to ${RenderedStore.FOLDER}"
        }
    }

    /** Renders the current pitch using an explicit full file base name. */
    fun renderAs(baseName: String?) {
        startRender {
            renderToLibrary(faderCents, baseName, fullName = true)
            current?.let { variants = repo.listVariants(it.id) }
            exportMessage = "saved"
        }
    }

    /** Renders (if needed) with an explicit full base name, then shares it. */
    fun shareAs(baseName: String?) {
        startRender {
            val variant = renderToLibrary(faderCents, baseName, fullName = true)
            current?.let { variants = repo.listVariants(it.id) }
            if (variant != null) {
                shareUri = Uri.parse(variant.outputPath)
                exportMime = exportFormat.mime
                exportMessage = "ready to share"
            } else {
                exportMessage = "render failed"
            }
        }
    }

    /** Ensures the current pitch is rendered, then opens the share sheet. */
    fun exportCurrent(name: String?) {
        startRender {
            val track = current!!
            val format = exportFormat
            val hasLoops = renderSegments() != null
            val existing = if (hasLoops) {
                null
            } else {
                repo.findVariant(track.id, faderCents, true, null, format.id)
            }
            val variant = if (existing != null && RenderedStore.exists(existing.outputPath)) {
                existing
            } else {
                renderToLibrary(faderCents, name)
            }
            current?.let { variants = repo.listVariants(it.id) }
            if (variant != null) {
                shareUri = Uri.parse(variant.outputPath)
                exportMime = format.mime
                exportMessage = "ready to share"
            } else {
                exportMessage = "render failed"
            }
        }
    }

    /**
     * Runs a render on the view-model scope, tracking the job so [cancelExport]
     * can abort it. Renders are CPU-bound and do not suspend, so the job is
     * cancelled together with the [renderCancelled] flag the renderer polls.
     */
    private fun startRender(block: suspend () -> Unit) {
        if (exporting || current == null) return
        exporting = true
        renderCancelled = false
        renderJob = viewModelScope.launch {
            exportMessage = "rendering ${exportFormat.id}..."
            try {
                block()
            } catch (_: CancellationException) {
                exportMessage = "cancelled"
            } catch (e: Throwable) {
                exportMessage = "render failed: ${reasonOf(e)}"
            } finally {
                exporting = false
                renderJob = null
            }
        }
    }

    /** Aborts an in-flight render or export, if any. */
    fun cancelExport() {
        if (!exporting) return
        renderCancelled = true
        renderJob?.cancel()
        exportMessage = "cancelling..."
    }

    fun shareVariant(variant: Variant) {
        if (RenderedStore.exists(variant.outputPath)) {
            shareUri = Uri.parse(variant.outputPath)
            exportMime = ExportFormat.parse(variant.outputFormat ?: "")?.mime ?: "audio/*"
            exportMessage = "ready to share"
        } else {
            setPitchCents(variant.cents)
            exportCurrent(variant.name)
        }
    }

    private suspend fun renderToLibrary(cents: Int, name: String?, fullName: Boolean = false): Variant? {
        val track = current ?: return null
        val format = exportFormat
        val segments = renderSegments()
        val section = segments?.takeIf { it.size == 1 }?.first()
            ?.let { it.first / 1000.0 to it.second / 1000.0 }
        val app = getApplication<Application>()
        val folder = effectiveFolder()
        return withContext(Dispatchers.IO) {
            val tmpDir = File(app.cacheDir, "renders").apply { mkdirs() }
            val ext = format.extension
            val tmp = File(tmpDir, "render-${System.nanoTime()}.$ext")
            try {
                AudioRenderer.render(
                    sourcePath = track.sourcePath,
                    target = tmp,
                    format = format,
                    cents = cents,
                    segments = segments,
                    cancelled = { renderCancelled },
                )
                val displayName = if (fullName && !name.isNullOrBlank()) {
                    "${name.trim()}.$ext"
                } else {
                    Notes.downloadFilename(
                        track.title,
                        track.artist,
                        track.sourcePath,
                        name,
                        cents,
                        ext,
                    )
                }
                val uri = RenderedStore.save(app, folder, displayName, format.mime, tmp)
                val existing = if (segments == null) {
                    repo.findVariant(track.id, cents, true, null, format.id)
                } else {
                    null
                }
                if (existing != null) {
                    repo.updateVariantFile(existing.id, uri, format.id, name)
                    repo.getVariant(existing.id)
                } else {
                    val id = repo.addVariantFull(
                        track.id,
                        VariantSpec(
                            cents = cents,
                            formant = true,
                            engine = "sonic",
                            pitchQuality = "quality",
                            section = section,
                            outputPath = uri,
                            outputFormat = format.id,
                            targetNote = name?.takeIf { it.isNotBlank() },
                        ),
                    )
                    repo.getVariant(id)
                }
            } finally {
                tmp.delete()
            }
        }
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
            val note = Notes.hzToNote(reading.hz).name
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

    fun selectVariant(variant: Variant) {
        selectedVariantId = variant.id
        setPitchCents(variant.cents)
    }

    fun renameVariant(variant: Variant, name: String) {
        repo.renameVariant(variant.id, name)
        current?.let { variants = repo.listVariants(it.id) }
    }

    fun deleteVariant(variant: Variant) {
        if (RenderedStore.exists(variant.outputPath)) {
            RenderedStore.delete(getApplication(), variant.outputPath)
        }
        repo.deleteVariant(variant.id)
        variants = variants.filterNot { it.id == variant.id }
        if (selectedVariantId == variant.id) selectOriginal()
    }

    override fun onCleared() {
        controller?.release()
        repo.close()
        super.onCleared()
    }
}
