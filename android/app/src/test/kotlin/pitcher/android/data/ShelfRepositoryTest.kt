package pitcher.android.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class ShelfRepositoryTest {

    private lateinit var context: Context
    private lateinit var repo: ShelfRepository
    private lateinit var dbName: String
    private var trackId = 0L

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        dbName = "test-shelf-${System.nanoTime()}.db"
        repo = ShelfRepository(context, dbName)
        trackId = repo.addTrack(
            sourcePath = "/tmp/x.m4a",
            sourceKind = "file",
            sourceUrl = null,
            title = "T",
            artist = null,
            durationS = 12.0,
            sampleRate = 44100,
        )
    }

    @After
    fun tearDown() {
        repo.close()
        context.deleteDatabase(dbName)
    }

    @Test
    fun loopsRoundTripInOrder() {
        repo.addLoop(trackId, startMs = 3000, endMs = 4000, name = "chorus")
        repo.addLoop(trackId, startMs = 1000, endMs = 2000)
        val loops = repo.listLoops(trackId)
        assertEquals(listOf(1000L, 3000L), loops.map { it.startMs })
        assertNull(loops.first().name)
        assertEquals("chorus", loops.last().name)
        assertTrue(loops.all { it.enabled })
    }

    @Test
    fun loopTogglesEdgesAndDelete() {
        val id = repo.addLoop(trackId, 1000, 2000)
        repo.setLoopEnabled(id, false)
        assertFalse(repo.getLoop(id)!!.enabled)
        repo.updateLoopEdges(id, 1500, 2500)
        assertEquals(1500L, repo.getLoop(id)!!.startMs)
        assertEquals(2500L, repo.getLoop(id)!!.endMs)
        repo.renameLoop(id, "verse")
        assertEquals("verse", repo.getLoop(id)!!.name)
        repo.deleteLoop(id)
        assertNull(repo.getLoop(id))
        assertTrue(repo.listLoops(trackId).isEmpty())
    }

    @Test
    fun tunerNotesAndSaveFolderPersistOnTheTrack() {
        repo.setTunerNotes(trackId, "C#4", "G5")
        repo.setSaveFolder(trackId, "content://tree/primary%3AMusic")
        val track = repo.getTrack(trackId)!!
        assertEquals("C#4", track.tunerSource)
        assertEquals("G5", track.tunerTarget)
        assertEquals("content://tree/primary%3AMusic", track.saveFolder)
    }

    @Test
    fun updatingAVariantFileKeepsItsName() {
        val id = repo.addVariantFull(
            trackId,
            VariantSpec(
                cents = 300,
                formant = false,
                engine = "wsola",
                pitchQuality = "quality",
                section = null,
                outputPath = "content://old",
                outputFormat = "m4a",
                targetNote = null,
            ),
        )
        repo.renameVariant(id, "key of E")
        repo.updateVariantFile(id, "content://new", "mp3")
        val v = repo.getVariant(id)!!
        assertEquals("key of E", v.name)
        assertEquals("content://new", v.outputPath)
        assertEquals("mp3", v.outputFormat)
    }

    @Test
    fun deletingTrackCascadesLoopsAndBookmarks() {
        repo.addLoop(trackId, 0, 1000)
        repo.addBookmark(trackId, 1.5, null)
        repo.deleteTrack(trackId)
        assertTrue(repo.listLoops(trackId).isEmpty())
        assertTrue(repo.listBookmarks(trackId).isEmpty())
    }
}
