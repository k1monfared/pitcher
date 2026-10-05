package pitcher.android.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ShelfRepositoryTest {

    private lateinit var context: Context
    private lateinit var repo: ShelfRepository
    private lateinit var dbName: String

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        dbName = "test-${System.nanoTime()}.db"
        repo = ShelfRepository(context, dbName)
    }

    @After
    fun tearDown() {
        repo.close()
        context.deleteDatabase(dbName)
    }

    @Test
    fun addAndListTrack() {
        val id = repo.addTrack("/music/a.wav", "file", null, "A", "Artist", 12.0, 44100)
        assertTrue(id > 0)
        val tracks = repo.listTracks()
        assertEquals(1, tracks.size)
        assertEquals("A", tracks[0].title)
        assertEquals("Artist", tracks[0].artist)
        assertEquals(0, tracks[0].variantCount)
    }

    @Test
    fun blankArtistStoredAsNull() {
        val id = repo.addTrack("/music/a.wav", "file", null, "A", "  ", 12.0, 44100)
        assertNull(repo.getTrack(id)!!.artist)
    }

    @Test
    fun addVariantAndCount() {
        val tid = repo.addTrack("/music/a.wav", "file", null, "A", "", 12.0, 44100)
        repo.addVariantFull(tid, spec(-100, "/out/a.opus"))
        repo.addVariantFull(tid, spec(700, "/out/b.opus"))
        assertEquals(2, repo.listTracks()[0].variantCount)
        assertEquals(2, repo.listVariants(tid).size)
    }

    @Test
    fun variantsSortedByShiftAmount() {
        val tid = repo.addTrack("/music/a.wav", "file", null, "A", "", 12.0, 44100)
        listOf(200, -100, 0, -300, 100).forEach { repo.addVariantFull(tid, spec(it, "/out/$it.opus")) }
        assertEquals(listOf(-300, -100, 0, 100, 200), repo.listVariants(tid).map { it.cents })
    }

    @Test
    fun findVariantMatchesRenderSettings() {
        val tid = repo.addTrack("/music/a.wav", "file", null, "A", "", 12.0, 44100)
        val v = repo.addVariantFull(tid, spec(-600, "/out/a.opus"))
        assertEquals(v, repo.findVariant(tid, -600, true, null, "opus")!!.id)
        assertNull(repo.findVariant(tid, -500, true, null, "opus"))
        assertNull(repo.findVariant(tid, -600, false, null, "opus"))
        assertNull(repo.findVariant(tid, -600, true, 1.0 to 2.0, "opus"))
        assertNull(repo.findVariant(tid, -600, true, null, "mp3"))
    }

    @Test
    fun renameVariantSetsName() {
        val tid = repo.addTrack("/music/a.wav", "file", null, "A", "", 12.0, 44100)
        val v = repo.addVariantFull(tid, spec(-100, "/out/a.opus"))
        repo.renameVariant(v, "low end")
        assertEquals("low end", repo.getVariant(v)!!.name)
        repo.renameVariant(v, "   ")
        assertNull(repo.getVariant(v)!!.name)
    }

    @Test
    fun renameTrackUpdatesTitleAndArtist() {
        val tid = repo.addTrack("/music/a.wav", "file", null, "Old", "Nobody", 12.0, 44100)
        repo.renameTrack(tid, "New", "Somebody")
        val t = repo.getTrack(tid)!!
        assertEquals("New", t.title)
        assertEquals("Somebody", t.artist)
        repo.renameTrack(tid, "Only", null)
        assertEquals("Only", repo.getTrack(tid)!!.title)
        assertEquals("Somebody", repo.getTrack(tid)!!.artist)
    }

    @Test
    fun deleteTrackCascadesVariantsAndBookmarks() {
        val tid = repo.addTrack("/music/a.wav", "file", null, "A", "", 12.0, 44100)
        repo.addVariantFull(tid, spec(-100, "/out/a.opus"))
        repo.addBookmark(tid, 5.0, null)
        repo.deleteTrack(tid)
        assertTrue(repo.listTracks().isEmpty())
        assertTrue(repo.listVariants(tid).isEmpty())
        assertTrue(repo.listBookmarks(tid).isEmpty())
    }

    @Test
    fun deleteTrackWithFilesRemovesManagedAndKeepsExternal() {
        val base = File(context.cacheDir, "pitcher-${System.nanoTime()}").apply { mkdirs() }
        val data = File(base, "data").apply { mkdirs() }
        val external = File(base, "music").apply { mkdirs() }

        val managed = File(data, "imports/a.opus").apply {
            parentFile!!.mkdirs()
            writeText("audio")
        }
        val variant = File(data, "out/a_-100.opus").apply {
            parentFile!!.mkdirs()
            writeText("variant")
        }
        val tid = repo.addTrack(managed.absolutePath, "url", null, "A", "", 12.0, 48000)
        repo.addVariantFull(tid, spec(-100, variant.absolutePath))

        assertTrue(repo.deleteTrackWithFiles(tid, data))
        assertFalse(managed.exists())
        assertFalse(variant.exists())

        val ext = File(external, "b.wav").apply { writeText("audio") }
        val tid2 = repo.addTrack(ext.absolutePath, "file", null, "B", "", 12.0, 44100)
        assertTrue(repo.deleteTrackWithFiles(tid2, data))
        assertTrue(ext.exists())
    }

    @Test
    fun deleteTrackWithFilesMissingReturnsFalse() {
        assertFalse(repo.deleteTrackWithFiles(999, context.cacheDir))
    }

    @Test
    fun bookmarksCrudSortedByTime() {
        val tid = repo.addTrack("/music/a.wav", "file", null, "A", "", 60.0, 44100)
        val b2 = repo.addBookmark(tid, 30.0, "chorus")
        val b1 = repo.addBookmark(tid, 10.0, null)
        val marks = repo.listBookmarks(tid)
        assertEquals(2, marks.size)
        assertEquals(b1, marks[0].id)
        assertEquals(10.0, marks[0].t, 0.0)
        assertNull(marks[0].name)
        assertEquals(b2, marks[1].id)
        assertEquals("chorus", marks[1].name)

        repo.renameBookmark(b1, "intro")
        assertEquals("intro", repo.listBookmarks(tid)[0].name)
        repo.deleteBookmark(b2)
        assertEquals(1, repo.listBookmarks(tid).size)
    }

    @Test
    fun findTrackBySource() {
        val tid = repo.addTrack("/music/a.wav", "file", null, "A", "", 12.0, 44100)
        assertEquals(tid, repo.findTrackBySource("/music/a.wav"))
        assertNull(repo.findTrackBySource("/music/none.wav"))
    }

    private fun spec(cents: Int, path: String) = VariantSpec(
        cents = cents,
        formant = true,
        engine = "tarsos",
        pitchQuality = "quality",
        section = null,
        outputPath = path,
        outputFormat = "opus",
    )
}
