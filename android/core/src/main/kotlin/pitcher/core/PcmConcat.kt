package pitcher.core

/**
 * Concatenates decoded PCM segments channel by channel, with hard cuts. Used to
 * stitch multiple loop sections into one render before a single encode. A
 * segment with fewer channels contributes silence for the missing ones.
 */
object PcmConcat {

    fun concat(segments: List<Array<FloatArray>>): Array<FloatArray> {
        if (segments.isEmpty()) return emptyArray()
        val channels = segments.maxOf { it.size }
        val result = Array(channels) { FloatArray(0) }
        for (c in 0 until channels) {
            var total = 0
            for (seg in segments) {
                total += if (c < seg.size) seg[c].size else seg.firstOrNull()?.size ?: 0
            }
            val out = FloatArray(total)
            var pos = 0
            for (seg in segments) {
                val src = if (c < seg.size) seg[c] else FloatArray(seg.firstOrNull()?.size ?: 0)
                System.arraycopy(src, 0, out, pos, src.size)
                pos += src.size
            }
            result[c] = out
        }
        return result
    }
}
