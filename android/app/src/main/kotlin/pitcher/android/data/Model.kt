package pitcher.android.data

data class Track(
    val id: Long,
    val sourcePath: String,
    val sourceKind: String,
    val sourceUrl: String?,
    val title: String,
    val artist: String?,
    val durationS: Double,
    val sampleRate: Int?,
    val createdAt: String,
    val variantCount: Int,
)

data class Variant(
    val id: Long,
    val trackId: Long,
    val name: String?,
    val cents: Int,
    val formant: Boolean,
    val engine: String,
    val pitchQuality: String,
    val sectionStart: Double?,
    val sectionEnd: Double?,
    val outputPath: String,
    val outputFormat: String?,
    val srcNote: String?,
    val srcHz: Double?,
    val targetNote: String?,
    val targetHz: Double?,
    val favorite: Boolean,
    val createdAt: String,
)

data class VariantSpec(
    val cents: Int,
    val formant: Boolean,
    val engine: String,
    val pitchQuality: String,
    val section: Pair<Double, Double>?,
    val outputPath: String,
    val outputFormat: String?,
    val srcNote: String? = null,
    val srcHz: Double? = null,
    val targetNote: String? = null,
    val targetHz: Double? = null,
)

data class Bookmark(
    val id: Long,
    val trackId: Long,
    val t: Double,
    val name: String?,
    val createdAt: String,
)
