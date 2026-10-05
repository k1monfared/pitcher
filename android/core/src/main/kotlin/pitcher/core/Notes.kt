package pitcher.core

data class NoteReading(
    val name: String,
    val midi: Int,
    val centsOff: Double,
)

object Notes {
    private val NOTE_NAMES = listOf(
        "C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B",
    )

    private val LETTERS = mapOf(
        'C' to 0, 'D' to 2, 'E' to 4, 'F' to 5, 'G' to 7, 'A' to 9, 'B' to 11,
    )

    fun ratioFromCents(cents: Int): Double = Math.pow(2.0, cents / 1200.0)

    fun hzToMidi(hz: Double): Double = 69.0 + 12.0 * log2(hz / 440.0)

    fun midiToHz(midi: Double): Double = 440.0 * Math.pow(2.0, (midi - 69.0) / 12.0)

    fun midiToNote(midi: Int): String {
        val idx = midi.mod(12)
        val octave = midi.floorDiv(12) - 1
        return "${NOTE_NAMES[idx]}$octave"
    }

    fun hzToNote(hz: Double): NoteReading {
        val m = hzToMidi(hz)
        val rounded = Math.round(m).toDouble()
        val midi = rounded.toInt()
        return NoteReading(
            name = midiToNote(midi),
            midi = midi,
            centsOff = 100.0 * (m - rounded),
        )
    }

    fun noteToHz(note: String): Double? {
        val s = note.trim()
        if (s.isEmpty()) return null

        val splitAt = s.indexOfFirst { it == '+' || it == '-' }
        val pitchPart: String
        val offsetCents: Int
        if (splitAt >= 0) {
            pitchPart = s.substring(0, splitAt)
            offsetCents = s.substring(splitAt).toIntOrNull() ?: return null
        } else {
            pitchPart = s
            offsetCents = 0
        }

        if (pitchPart.isEmpty() || !pitchPart[0].isUpperCase()) return null
        val letter = pitchPart[0]
        val semitone = LETTERS[letter] ?: return null

        var rest = pitchPart.substring(1)
        val accidental = when {
            rest.startsWith("#") -> {
                rest = rest.substring(1)
                1
            }
            rest.startsWith("b") -> {
                rest = rest.substring(1)
                -1
            }
            else -> 0
        }

        if (rest.isEmpty()) return null
        val octave = rest.toIntOrNull() ?: return null

        val midi = 12 * (octave + 1) + semitone + accidental
        val base = midiToHz(midi.toDouble())
        return base * ratioFromCents(offsetCents)
    }

    fun centsBetweenHz(source: Double, target: Double): Double =
        1200.0 * log2(target / source)

    fun centsBetweenNotes(source: String, target: String): Double? {
        val s = noteToHz(source) ?: return null
        val t = noteToHz(target) ?: return null
        return centsBetweenHz(s, t)
    }

    fun semitonesToCents(semitones: Double): Double = semitones * 100.0

    fun downloadFilename(
        trackTitle: String,
        variantName: String?,
        cents: Int,
        ext: String,
    ): String {
        val name = variantName?.trim().orEmpty()
        val pitch = if (name.isEmpty()) {
            "pitch " + signedCents(cents)
        } else {
            sanitizeFilename(name)
        }
        return "${sanitizeFilename(trackTitle)} - $pitch.$ext"
    }

    private fun signedCents(cents: Int): String =
        if (cents >= 0) "+$cents" else "$cents"

    private fun sanitizeFilename(s: String): String {
        val cleaned = buildString {
            for (c in s) {
                append(if (c == '/' || c == '\\' || c == '\u0000') '_' else c)
            }
        }.trim()
        return cleaned.ifEmpty { "untitled" }
    }

    private fun log2(x: Double): Double = Math.log(x) / Math.log(2.0)
}
