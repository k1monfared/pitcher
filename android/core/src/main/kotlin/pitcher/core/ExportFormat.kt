package pitcher.core

enum class ExportFormat(
    val id: String,
    val extension: String,
    val mime: String,
    val lossless: Boolean,
) {
    Wav("wav", "wav", "audio/wav", true),
    Mp3("mp3", "mp3", "audio/mpeg", false),
    M4a("m4a", "m4a", "audio/mp4", false),
    Opus("opus", "opus", "audio/ogg", false),
    ;

    companion object {
        fun parse(value: String): ExportFormat? =
            entries.firstOrNull { it.id == value.lowercase() }
    }
}
