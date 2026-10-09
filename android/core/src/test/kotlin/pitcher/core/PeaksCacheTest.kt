package pitcher.core

import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class PeaksCacheTest {

    private lateinit var dir: File
    private lateinit var source: File

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("peaks").toFile()
        source = File(dir, "song.m4a").apply { writeBytes(ByteArray(1000) { it.toByte() }) }
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun savedPeaksReadBack() {
        val cache = File(dir, "song.peaks")
        val peaks = floatArrayOf(0f, 0.25f, 1f, 0.5f)
        PeaksCache.write(cache, source, peaks)
        assertArrayEquals(peaks, PeaksCache.read(cache, source), 0f)
    }

    @Test
    fun aChangedSourceInvalidatesTheCache() {
        val cache = File(dir, "song.peaks")
        PeaksCache.write(cache, source, floatArrayOf(0.1f, 0.2f))
        source.appendBytes(ByteArray(10))
        assertNull(PeaksCache.read(cache, source))
    }

    @Test
    fun aMissingOrDamagedCacheReadsAsNothing() {
        val cache = File(dir, "song.peaks")
        assertNull(PeaksCache.read(cache, source))
        cache.writeBytes(byteArrayOf(1, 2, 3))
        assertNull(PeaksCache.read(cache, source))
    }

    @Test
    fun emptyPeaksAreNotCached() {
        val cache = File(dir, "song.peaks")
        PeaksCache.write(cache, source, FloatArray(0))
        assertNull(PeaksCache.read(cache, source))
    }
}
