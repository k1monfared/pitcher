package pitcher.core

import de.sciss.jump3r.mp3.BRHist
import de.sciss.jump3r.mp3.BitStream
import de.sciss.jump3r.mp3.GainAnalysis
import de.sciss.jump3r.mp3.GetAudio
import de.sciss.jump3r.mp3.ID3Tag
import de.sciss.jump3r.mp3.Lame
import de.sciss.jump3r.mp3.Parse
import de.sciss.jump3r.mp3.Presets
import de.sciss.jump3r.mp3.Quantize
import de.sciss.jump3r.mp3.QuantizePVT
import de.sciss.jump3r.mp3.Reservoir
import de.sciss.jump3r.mp3.Takehiro
import de.sciss.jump3r.mp3.VBRTag
import de.sciss.jump3r.mp3.VbrMode
import de.sciss.jump3r.mp3.Version
import de.sciss.jump3r.mpg.Common
import de.sciss.jump3r.mpg.Interface
import de.sciss.jump3r.mpg.MPGLib
import java.io.File

/**
 * MP3 encoding via the raw LAME core from jump3r. The library's high-level
 * LameEncoder depends on javax.sound, which does not exist on Android, so this
 * wires the modules the same way that class does and drives `mp3.Lame`
 * directly. VBR quality follows LAME's 0..9 scale (lower is better).
 */
object Mp3Writer {

    fun write(
        file: File,
        channels: Array<FloatArray>,
        sampleRate: Int,
        vbrQuality: Int = 2,
    ) {
        require(channels.isNotEmpty()) { "no channels" }
        val numChannels = channels.size.coerceAtMost(2)
        val frames = channels[0].size

        val lame = Lame()
        val gaud = GetAudio()
        val ga = GainAnalysis()
        val bs = BitStream()
        val p = Presets()
        val qupvt = QuantizePVT()
        val qu = Quantize()
        val vbr = VBRTag()
        val ver = Version()
        val id3 = ID3Tag()
        val rv = Reservoir()
        val tak = Takehiro()
        val parse = Parse()
        val hist = BRHist()
        val mpg = MPGLib()
        val intf = Interface()
        val common = Common()

        lame.setModules(ga, bs, p, qupvt, qu, vbr, ver, id3, mpg)
        bs.setModules(ga, mpg, ver, vbr)
        id3.setModules(bs, ver)
        p.setModules(lame)
        qu.setModules(bs, rv, qupvt, tak)
        qupvt.setModules(tak, rv, lame.enc.psy)
        rv.setModules(bs)
        tak.setModules(qupvt)
        vbr.setModules(lame, bs, ver)
        gaud.setModules(parse, mpg)
        parse.setModules(ver, id3, p)
        mpg.setModules(intf, common)
        intf.setModules(vbr, common)

        val gfp = lame.lame_init()
        gfp.num_channels = numChannels
        gfp.in_samplerate = sampleRate
        gfp.VBR = VbrMode.vbr_default
        gfp.VBR_q = vbrQuality.coerceIn(0, 9)
        gfp.quality = 2
        id3.id3tag_init(gfp)
        gfp.write_id3tag_automatic = false
        gfp.findReplayGain = false

        val rc = lame.lame_init_params(gfp)
        check(rc >= 0) { "lame_init_params failed: $rc" }

        val src0 = channels[0]
        val src1 = if (numChannels > 1) channels[1] else channels[0]
        val mp3buf = ByteArray((frames * 1.25 + 7200).toInt().coerceAtLeast(7200))

        file.parentFile?.mkdirs()
        try {
            file.outputStream().buffered().use { out ->
                val chunk = 1152
                var pos = 0
                while (pos < frames) {
                    val n = minOf(chunk, frames - pos)
                    val left = IntArray(n)
                    val right = IntArray(n)
                    for (i in 0 until n) {
                        left[i] = (src0[pos + i].coerceIn(-1f, 1f) * 32767f).toInt()
                        right[i] = (src1[pos + i].coerceIn(-1f, 1f) * 32767f).toInt()
                    }
                    val written =
                        lame.lame_encode_buffer_int(gfp, left, right, n, mp3buf, 0, mp3buf.size)
                    if (written > 0) out.write(mp3buf, 0, written)
                    pos += n
                }
                val flushed = lame.lame_encode_flush(gfp, mp3buf, 0, mp3buf.size)
                if (flushed > 0) out.write(mp3buf, 0, flushed)
            }
        } finally {
            lame.lame_close(gfp)
        }
    }
}
