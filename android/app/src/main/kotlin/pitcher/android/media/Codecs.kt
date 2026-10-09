package pitcher.android.media

import android.media.MediaCodec
import android.media.MediaCodecList

/**
 * Creates audio codecs, preferring Android's own open-source software codecs
 * (the AOSP `c2.android` family) over a phone maker's closed-source ones. A
 * vendor codec is used only when the phone has nothing else for the format.
 */
object Codecs {

    data class Candidate(val name: String, val vendor: Boolean)

    /** The first platform codec in listed order, or else the first vendor codec. */
    fun choose(candidates: List<Candidate>): Candidate? =
        candidates.firstOrNull { !it.vendor } ?: candidates.firstOrNull()

    fun decoder(mime: String): MediaCodec = create(mime, encoder = false)

    fun encoder(mime: String): MediaCodec = create(mime, encoder = true)

    private fun create(mime: String, encoder: Boolean): MediaCodec {
        val candidates = runCatching {
            MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
                .filter { info ->
                    info.isEncoder == encoder && info.supportedTypes.any { it.equals(mime, ignoreCase = true) }
                }
                .map { Candidate(it.name, it.isVendor) }
        }.getOrDefault(emptyList())
        val chosen = choose(candidates)
        return when {
            chosen != null -> MediaCodec.createByCodecName(chosen.name)
            encoder -> MediaCodec.createEncoderByType(mime)
            else -> MediaCodec.createDecoderByType(mime)
        }
    }
}
