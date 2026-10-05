package pitcher.android.ui

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
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
import pitcher.android.media.MediaImporter
import pitcher.android.media.WaveformDecoder

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

    private val player: ExoPlayer = ExoPlayer.Builder(app).build().apply {
        addListener(object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                this@PitcherViewModel.isPlaying = playing
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    this@PitcherViewModel.durationMs = duration.coerceAtLeast(0)
                }
                if (state == Player.STATE_ENDED) {
                    this@PitcherViewModel.isPlaying = false
                }
            }
        })
    }

    init {
        refreshTracks()
        viewModelScope.launch {
            while (isActive) {
                if (isPlaying) positionMs = player.currentPosition
                delay(50)
            }
        }
    }

    fun refreshTracks() {
        tracks = repo.listTracks()
    }

    fun clearMessage() {
        message = null
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
        positionMs = 0
        player.setMediaItem(MediaItem.fromUri(Uri.fromFile(File(track.sourcePath))))
        player.prepare()
        player.seekTo(0)
        viewModelScope.launch {
            val p = withContext(Dispatchers.IO) { WaveformDecoder.decodePeaks(track.sourcePath) }
            if (current?.id == track.id) peaks = p
        }
    }

    fun togglePlay() {
        if (current == null) return
        if (isPlaying) player.pause() else player.play()
    }

    fun seekTo(ms: Long) {
        val clamped = ms.coerceIn(0, maxOf(durationMs, 0))
        player.seekTo(clamped)
        positionMs = clamped
    }

    fun renameTrack(track: Track, title: String, artist: String) {
        repo.renameTrack(track.id, title, artist.ifBlank { null })
        refreshTracks()
        if (current?.id == track.id) current = repo.getTrack(track.id)
    }

    fun deleteTrack(track: Track) {
        val dataDir = File(getApplication<Application>().filesDir, "imports").parentFile!!
        player.stop()
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
        val t = player.currentPosition / 1000.0
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
        current?.let { variants = repo.listVariants(it.id) }
    }

    override fun onCleared() {
        player.release()
        repo.close()
        super.onCleared()
    }
}
