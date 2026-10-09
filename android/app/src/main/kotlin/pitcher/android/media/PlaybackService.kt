package pitcher.android.media

import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/**
 * Keeps playback alive when the app is backgrounded or the screen is off,
 * and surfaces lock-screen / notification controls. Media3 requires the
 * player and session to live in the service; the UI talks to it through a
 * MediaController.
 */
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        val attributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
        // Decode with Android's own open-source codecs ahead of vendor ones,
        // the same preference the renderer and decoders use (see Codecs).
        val selector = MediaCodecSelector { mime, secure, tunneling ->
            MediaCodecUtil.getDecoderInfos(mime, secure, tunneling).sortedBy { it.vendor }
        }
        val renderers = DefaultRenderersFactory(this).setMediaCodecSelector(selector)
        val player = ExoPlayer.Builder(this, renderers)
            .setAudioAttributes(attributes, true)
            .setHandleAudioBecomingNoisy(true)
            .build()
        mediaSession = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }
}
