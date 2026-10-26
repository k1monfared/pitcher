package pitcher.android.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class SongPackageTest {
    private lateinit var context: Context
    private lateinit var repo: ShelfRepository
    private lateinit var dbName: String
    private lateinit var importsDir: File
    private lateinit var audio: File
    private val audioBytes = ByteArray(4096) { (it * 7).toByte() }
    private var trackId = 0L

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        dbName = "test-package-${System.nanoTime()}.db"
        repo = ShelfRepository(context, dbName)
        importsDir = File(context.filesDir, "imports-${System.nanoTime()}").apply { mkdirs() }
        audio = File(importsDir, "Nava Sol.m4a").apply { writeBytes(audioBytes) }
        trackId = repo.addTrack(
            sourcePath = audio.absolutePath, sourceKind = "file", sourceUrl = null,
            title = "Nava Sol", artist = "Tasnif", durationS = 210.5, sampleRate = 44100,
        )
        repo.setTunerNotes(trackId, "G4", "E4")
        repo.setSaveFolder(trackId, "content://somewhere/only/on/this/phone")
        val down = repo.addPitch(trackId, -300, "for Sara")
        repo.setFavorite(down, true)
        repo.addPitch(trackId, 200)
        repo.addVariantFull(
            trackId,
            VariantSpec(
                cents = 700, formant = true, engine = "sonic", pitchQuality = "quality",
                section = 10.0 to 20.0, outputPath = "/data/old/render.wav", outputFormat = "wav",
                srcNote = "C4", srcHz = 261.63, targetNote = "G4", targetHz = 392.0,
            ),
        )
        val bridge = repo.addLoop(trackId, startMs = 30_000, endMs = 45_000, name = "bridge")
        repo.setLoopEnabled(bridge, false)
        repo.addLoop(trackId, startMs = 1_000, endMs = 9_000)
        repo.addBookmark(trackId, 12.25, "verse 2")
        repo.addBookmark(trackId, 3.0, null)
    }

    @After
    fun tearDown() {
        repo.close()
        context.deleteDatabase(dbName)
        importsDir.deleteRecursively()
    }

    private fun exported(): ByteArray =
        ByteArrayOutputStream().also { SongPackage.export(repo, trackId, it) }.toByteArray()

    private fun import(bytes: ByteArray): Long =
        SongPackage.import(repo, ByteArrayInputStream(bytes), importsDir)

    @Test
    fun importAddsANewSongWithTheSameTitleAndAudio() {
        val newId = import(exported())

        assertNotEquals(trackId, newId)
        val track = repo.getTrack(newId)!!
        assertEquals("Nava Sol", track.title)
        assertEquals("Tasnif", track.artist)
        assertEquals(210.5, track.durationS, 1e-9)
        assertEquals(44100, track.sampleRate)
        assertEquals("G4", track.tunerSource)
        assertEquals("E4", track.tunerTarget)
        val copy = File(track.sourcePath)
        assertNotEquals(audio.absolutePath, copy.absolutePath)
        assertEquals(importsDir.canonicalPath, copy.parentFile!!.canonicalPath)
        assertTrue(copy.name.endsWith(".m4a"))
        assertArrayEquals(audioBytes, copy.readBytes())
    }

    @Test
    fun theSaveFolderStaysOnThisPhone() {
        val track = repo.getTrack(import(exported()))!!
        assertEquals(null, track.saveFolder)
    }

    @Test
    fun pitchesKeepTheirNamesAndSettings() {
        val pitches = repo.listVariants(import(exported()))
        assertEquals(listOf(-300, 200, 700), pitches.map { it.cents })
        assertEquals(listOf("for Sara", null, null), pitches.map { it.name })
        assertEquals(listOf(true, false, false), pitches.map { it.favorite })
        val fifth = pitches.last()
        assertTrue(fifth.formant)
        assertEquals(10.0, fifth.sectionStart!!, 1e-9)
        assertEquals(20.0, fifth.sectionEnd!!, 1e-9)
        assertEquals("C4", fifth.srcNote)
        assertEquals(392.0, fifth.targetHz!!, 1e-9)
        assertTrue(pitches.all { it.outputPath.isEmpty() && it.renderCount == 0 })
    }

    @Test
    fun loopsKeepTheirNamesEdgesAndState() {
        val loops = repo.listLoops(import(exported()))
        assertEquals(listOf(1_000L to 9_000L, 30_000L to 45_000L), loops.map { it.startMs to it.endMs })
        assertEquals(listOf(null, "bridge"), loops.map { it.name })
        assertEquals(listOf(true, false), loops.map { it.enabled })
    }

    @Test
    fun bookmarksKeepTheirNamesAndTimes() {
        val marks = repo.listBookmarks(import(exported()))
        assertEquals(listOf(3.0, 12.25), marks.map { it.t })
        assertEquals(listOf(null, "verse 2"), marks.map { it.name })
    }

    @Test
    fun importingTwiceLeavesEarlierSongsAlone() {
        val bytes = exported()
        val first = import(bytes)
        repo.renameLoop(repo.listLoops(first).first().id, "mine")
        val second = import(bytes)

        assertEquals(3, repo.listTracks().size)
        assertNotEquals(first, second)
        assertEquals("mine", repo.listLoops(first).first().name)
        assertEquals(null, repo.listLoops(second).first().name)
        assertNotEquals(repo.getTrack(first)!!.sourcePath, repo.getTrack(second)!!.sourcePath)
    }

    @Test
    fun recognisesItsOwnFilesByTheirFirstBytes() {
        assertTrue(SongPackage.looksLikePackage(exported().copyOf(8)))
        assertFalse(SongPackage.looksLikePackage(audioBytes.copyOf(8)))
        assertFalse(SongPackage.looksLikePackage(ByteArray(0)))
    }

    @Test
    fun fileNameUsesTheSongTitle() {
        assertEquals("Nava Sol.pitcher", SongPackage.fileName(repo.getTrack(trackId)!!))
    }

    @Test
    fun aZipThatIsNotAPackageIsRefusedAndAddsNothing() {
        val bytes = ByteArrayOutputStream().also { buf ->
            ZipOutputStream(buf).use { zip ->
                zip.putNextEntry(ZipEntry("readme.txt"))
                zip.write("hello".toByteArray())
                zip.closeEntry()
            }
        }.toByteArray()

        assertThrows(IllegalArgumentException::class.java) { import(bytes) }
        assertEquals(1, repo.listTracks().size)
    }

    @Test
    fun anAudioPathThatClimbsOutStaysInTheImportsFolder() {
        val json = """
            {"format":"pitcher-song","version":1,"title":"Evil","durationS":1.0,
             "audio":"../../../evil.m4a","pitches":[],"loops":[],"bookmarks":[]}
        """.trimIndent()
        val bytes = ByteArrayOutputStream().also { buf ->
            ZipOutputStream(buf).use { zip ->
                zip.putNextEntry(ZipEntry("song.json"))
                zip.write(json.toByteArray())
                zip.closeEntry()
                zip.putNextEntry(ZipEntry("../../../evil.m4a"))
                zip.write(byteArrayOf(1, 2, 3))
                zip.closeEntry()
            }
        }.toByteArray()

        val track = repo.getTrack(import(bytes))!!
        assertEquals(importsDir.canonicalPath, File(track.sourcePath).canonicalFile.parentFile!!.path)
    }
}
