package pitcher.core

import kotlin.math.abs

/**
 * Decides when a saved render can be reused instead of rendering again. A
 * render matches when the file name, format, loop set, and speed are the same; the
 * pitch is matched by the caller, since renders hang off a saved pitch.
 */
object RenderPlan {

    data class Record(
        val id: Long,
        val uri: String,
        val format: String,
        val fileName: String,
        val segments: String,
        val speed: Double = 1.0,
    )

    /** A stable key for the rendered ranges; empty means the whole track. */
    fun segmentsKey(segments: List<Pair<Long, Long>>?): String =
        segments.orEmpty().sortedBy { it.first }.joinToString(",") { "${it.first}-${it.second}" }

    fun reusable(
        records: List<Record>,
        format: String,
        fileName: String,
        segments: String,
        speed: Double = 1.0,
    ): Record? =
        records
            .filter {
                it.format == format && it.fileName.trim() == fileName.trim() &&
                    it.segments == segments && abs(it.speed - speed) < 1e-3
            }
            .maxByOrNull { it.id }
}
