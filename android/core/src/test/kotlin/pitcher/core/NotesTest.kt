package pitcher.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val EPS = 1e-6

class NotesTest {

    @Test
    fun ratioFromCentsZeroIsOne() {
        assertEquals(1.0, Notes.ratioFromCents(0), EPS)
    }

    @Test
    fun ratioFromCentsOctaveIsTwo() {
        assertEquals(2.0, Notes.ratioFromCents(1200), EPS)
        assertEquals(0.5, Notes.ratioFromCents(-1200), EPS)
    }

    @Test
    fun ratioFromCentsSemitone() {
        assertEquals(1.0594630943592953, Notes.ratioFromCents(100), 1e-12)
    }

    @Test
    fun hzMidiRoundTrip() {
        assertEquals(69.0, Notes.hzToMidi(440.0), EPS)
        assertEquals(440.0, Notes.midiToHz(69.0), EPS)
        for (m in 0..127) {
            val hz = Notes.midiToHz(m.toDouble())
            assertEquals(m.toDouble(), Notes.hzToMidi(hz), 1e-9)
        }
    }

    @Test
    fun a4Is440() {
        assertEquals(440.0, Notes.noteToHz("A4")!!, EPS)
    }

    @Test
    fun middleCIsC4() {
        assertEquals(261.6255653005986, Notes.midiToHz(60.0), 1e-9)
        assertEquals(Notes.midiToHz(60.0), Notes.noteToHz("C4")!!, EPS)
        val n = Notes.hzToNote(261.6255653005986)
        assertEquals("C4", n.name)
        assertTrue(kotlin.math.abs(n.centsOff) < 1e-6)
    }

    @Test
    fun noteNamesIncludeSharps() {
        assertEquals("C#4", Notes.midiToNote(61))
        assertEquals("A#4", Notes.midiToNote(70))
        assertEquals("C5", Notes.midiToNote(72))
    }

    @Test
    fun hzToNoteReportsCentsOff() {
        val n = Notes.hzToNote(277.18)
        assertEquals("C#4", n.name)
        assertTrue(kotlin.math.abs(n.centsOff) < 2.0)
    }

    @Test
    fun hzToNoteFlatSide() {
        val hz = 440.0 * Math.pow(2.0, -30.0 / 1200.0)
        val n = Notes.hzToNote(hz)
        assertEquals("A4", n.name)
        assertEquals(-30.0, n.centsOff, 1e-6)
    }

    @Test
    fun centsBetweenHzOctave() {
        assertEquals(1200.0, Notes.centsBetweenHz(220.0, 440.0), 1e-6)
        assertEquals(-1200.0, Notes.centsBetweenHz(440.0, 220.0), 1e-6)
    }

    @Test
    fun centsBetweenHzCSharpToC() {
        val c = Notes.centsBetweenHz(Notes.noteToHz("C#4")!!, Notes.noteToHz("C4")!!)
        assertEquals(-100.0, c, 1e-6)
    }

    @Test
    fun centsBetweenNotesCSharp4ToG5() {
        assertEquals(1800.0, Notes.centsBetweenNotes("C#4", "G5")!!, 1e-6)
    }

    @Test
    fun semitonesToCentsFractional() {
        assertEquals(12.5, Notes.semitonesToCents(1.0 / 8.0), EPS)
        assertEquals(6.25, Notes.semitonesToCents(1.0 / 16.0), EPS)
        assertEquals(506.25, Notes.semitonesToCents(5.0 + 1.0 / 16.0), EPS)
        assertEquals(-100.0, Notes.semitonesToCents(-1.0), EPS)
    }

    @Test
    fun noteToHzRejectsBadName() {
        assertNull(Notes.noteToHz("H4"))
        assertNull(Notes.noteToHz("C"))
        assertNull(Notes.noteToHz(""))
    }

    @Test
    fun noteToHzAcceptsMicrotonalSuffix() {
        val hz = Notes.noteToHz("C#4+37")!!
        val base = Notes.noteToHz("C#4")!!
        assertEquals(base * Math.pow(2.0, 37.0 / 1200.0), hz, 1e-6)
    }

    @Test
    fun noteToHzIsCaseInsensitive() {
        assertEquals(Notes.noteToHz("C#4")!!, Notes.noteToHz("c#4")!!, EPS)
        assertEquals(Notes.noteToHz("A4")!!, Notes.noteToHz("a4")!!, EPS)
        assertEquals(Notes.noteToHz("Bb3")!!, Notes.noteToHz("bb3")!!, EPS)
        assertEquals(Notes.noteToHz("Eb4")!!, Notes.noteToHz("eb4")!!, EPS)
        assertEquals(Notes.noteToHz("C#4+37")!!, Notes.noteToHz("c#4+37")!!, EPS)
    }

    @Test
    fun downloadFilenameMatchesServerRules() {
        assertEquals(
            "nava sol darya - Tasnife Yad Bad - low.opus",
            Notes.downloadFilename("nava sol darya", "Tasnife Yad Bad", "/x/y.opus", "low", -600, "opus"),
        )
        assertEquals(
            "nava sol darya - low.opus",
            Notes.downloadFilename("nava sol darya", null, "/x/y.opus", "low", -600, "opus"),
        )
        assertEquals(
            "nava sol darya - low.opus",
            Notes.downloadFilename("nava sol darya", "  ", "/x/y.opus", "low", -600, "opus"),
        )
        assertEquals(
            "nava sol darya - Artist - -600.opus",
            Notes.downloadFilename("nava sol darya", "Artist", "/x/y.opus", null, -600, "opus"),
        )
        assertEquals(
            "nava sol darya - +200.mp3",
            Notes.downloadFilename("nava sol darya", null, "/x/y.opus", "  ", 200, "mp3"),
        )
        assertEquals(
            "Original Song - Artist - low.opus",
            Notes.downloadFilename("", "Artist", "/music/Original Song.wav", "low", -600, "opus"),
        )
        assertEquals(
            "a_b_c - x_y - n_m.wav",
            Notes.downloadFilename("a/b\\c", "x/y", "/x/z", "n/m", 0, "wav"),
        )
    }
}
