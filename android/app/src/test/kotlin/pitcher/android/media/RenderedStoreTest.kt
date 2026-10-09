package pitcher.android.media

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class RenderedStoreTest {

    @Test
    fun noFolderReadsAsTheDefault() {
        assertEquals(RenderedStore.FOLDER, RenderedStore.folderLabel(null))
    }

    @Test
    fun treeUriReadsAsItsPath() {
        val uri = "content://com.android.externalstorage.documents/tree/primary%3AMusic%2FCovers"
        assertEquals("Music/Covers", RenderedStore.folderLabel(uri))
    }

    @Test
    fun volumeRootReadsAsTheVolume() {
        val uri = "content://com.android.externalstorage.documents/tree/primary%3A"
        assertEquals("primary", RenderedStore.folderLabel(uri))
    }
}
