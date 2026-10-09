package pitcher.core

/**
 * Decides when a saved render can be reused instead of rendering again. A
 * render matches when the file name, format, and loop set are the same; the
 * pitch is matched by the caller, since renders hang off a saved pitch.
 */
object RenderPlan {

    data class Record(
        val id: Long,
        val uri: String,
        val format: String,
        val fileName: String,
        val segments: String,
    )

    /** A stable key for the rendered ranges; empty means the whole track. */
    fun segmentsKey(segments: List<Pair<Long, Long>>?): String =
        segments.orEmpty().sortedBy { it.first }.joinToString(",") { "${it.first}-${it.second}" }

    fun reusable(records: List<Record>, format: String, fileName: String, segments: String): Record? =
        records
            .filter { it.format == format && it.fileName.trim() == fileName.trim() && it.segments == segments }
            .maxByOrNull { it.id }
}
