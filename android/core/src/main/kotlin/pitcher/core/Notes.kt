package pitcher.core

data class NoteReading(
    val name: String,
    val midi: Int,
    val centsOff: Double,
)

object Notes {
    fun ratioFromCents(cents: Int): Double = TODO()

    fun hzToMidi(hz: Double): Double = TODO()

    fun midiToHz(midi: Double): Double = TODO()

    fun midiToNote(midi: Int): String = TODO()

    fun hzToNote(hz: Double): NoteReading = TODO()

    fun noteToHz(note: String): Double? = TODO()

    fun centsBetweenHz(source: Double, target: Double): Double = TODO()

    fun centsBetweenNotes(source: String, target: String): Double? = TODO()

    fun semitonesToCents(semitones: Double): Double = TODO()

    fun downloadFilename(
        trackTitle: String,
        variantName: String?,
        cents: Int,
        ext: String,
    ): String = TODO()
}
