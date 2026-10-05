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
import androidx.core.content.FileProvider
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pitcher.android.data.Bookmark
import pitcher.android.data.ShelfRepository
import pitcher.android.data.Track
import pitcher.android.data.Variant
import pitcher.android.data.VariantSpec
import pitcher.android.media.AudioRenderer
import pitcher.android.media.MediaImporter
import pitcher.android.media.PlaybackService
import pitcher.android.media.TunerDecoder
import pitcher.android.media.WaveformDecoder
import pitcher.core.ExportFormat
import pitcher.core.Notes
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
    var loopStartMs by mutableStateOf<Long?>(null)
        private set
    var loopEndMs by mutableStateOf<Long?>(null)
        private set
    var loopEnabled by mutableStateOf(false)
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
    var keepScreenOn by mutableStateOf(true)
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
                isPlaying = false
            }
        }
    }

    init {
        refreshTracks()
        connectController()
        viewModelScope.launch {
            while (isActive) {
                if (isPlaying) {
                    val pos = controller?.currentPosition ?: 0L
                    positionMs = pos
                    val end = loopEndMs
                    val start = loopStartMs
                    if (loopEnabled && end != null && start != null && pos >= end) {
                        controller?.seekTo(start)
                        positionMs = start
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
        val t = (controller?.currentPosition ?: positionMs) / 1000.0
        repo.addBookmark(track.id, t, name)
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

    fun selectOriginal() {
        selectedVariantId = null
        setPitchCents(0)
    }

    fun setLoopStart() {
        loopStartMs = controller?.currentPosition ?: positionMs
        normalizeLoop()
    }

    fun setLoopEnd() {
        loopEndMs = controller?.currentPosition ?: positionMs
        normalizeLoop()
    }

    private fun normalizeLoop() {
        val a = loopStartMs
        val b = loopEndMs
        if (a != null && b != null && b < a) {
            loopStartMs = b
            loopEndMs = a
        }
    }

    fun toggleLoop() {
        loopEnabled = !loopEnabled && loopStartMs != null && loopEndMs != null
        if (loopEnabled) {
            val start = loopStartMs
            if (start != null) {
                controller?.seekTo(start)
                positionMs = start
            }
        }
    }

    fun clearLoop() {
        loopEnabled = false
        loopStartMs = null
        loopEndMs = null
    }

    fun changeExportFormat(format: ExportFormat) {
        exportFormat = format
    }

    fun changeExportLoopOnly(value: Boolean) {
        exportLoopOnly = value
    }

    fun exportAndShare(cents: Int, name: String?) {
        val track = current ?: return
        val format = exportFormat
        val section = if (exportLoopOnly && loopStartMs != null && loopEndMs != null) {
            loopStartMs!! to loopEndMs!!
        } else {
            null
        }
        viewModelScope.launch {
            exportMessage = "rendering ${format.id}..."
            try {
                val outFile = withContext(Dispatchers.IO) {
                    val dir = File(getApplication<Application>().cacheDir, "exports").apply { mkdirs() }
                    val fileName = Notes.downloadFilename(track.title, name, cents, format.extension)
                    val target = File(dir, fileName)
                    AudioRenderer.render(
                        sourcePath = track.sourcePath,
                        target = target,
                        format = format,
                        cents = cents,
                        startMs = section?.first ?: 0L,
                        endMs = section?.second ?: -1L,
                    )
                    target
                }
                val app = getApplication<Application>()
                shareUri = FileProvider.getUriForFile(
                    app,
                    "${app.packageName}.fileprovider",
                    outFile,
                )
                exportMime = format.mime
                exportMessage = "ready to share"
            } catch (e: Exception) {
                exportMessage = "export failed: ${e.message}"
            }
        }
    }

    fun consumeShareUri() {
        shareUri = null
    }

    fun changeKeepScreenOn(value: Boolean) {
        keepScreenOn = value
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

    fun keepCurrent(name: String?) {
        val track = current ?: return
        val existing = repo.findVariant(track.id, faderCents, true, null, "live")
        if (existing != null) {
            if (!name.isNullOrBlank()) repo.renameVariant(existing.id, name)
            variants = repo.listVariants(track.id)
            selectedVariantId = existing.id
            return
        }
        val id = repo.addVariantFull(
            track.id,
            VariantSpec(
                cents = faderCents,
                formant = true,
                engine = "sonic",
                pitchQuality = "quality",
                section = null,
                outputPath = "",
                outputFormat = "live",
                targetNote = name?.takeIf { it.isNotBlank() },
            ),
        )
        variants = repo.listVariants(track.id)
        selectedVariantId = id
    }

    fun renameVariant(variant: Variant, name: String) {
        repo.renameVariant(variant.id, name)
        current?.let { variants = repo.listVariants(it.id) }
    }

    fun deleteVariant(variant: Variant) {
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
