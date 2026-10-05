package pitcher.android.media

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import java.io.File

/**
 * Publishes rendered files into a user-visible folder (Music/pitcher) via
 * MediaStore, so a render is kept even if it is never shared, and the folder
 * can be browsed from any file manager. Returns a content URI string, which is
 * directly shareable and does not need a FileProvider.
 */
object RenderedStore {

    const val FOLDER = "Music/pitcher"

    fun save(context: Context, displayName: String, mime: String, source: File): String {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Audio.Media.MIME_TYPE, mime)
            put(MediaStore.Audio.Media.RELATIVE_PATH, FOLDER)
            put(MediaStore.Audio.Media.IS_MUSIC, 1)
        }
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val uri = resolver.insert(collection, values)
            ?: error("could not create a file in $FOLDER")
        resolver.openOutputStream(uri)?.use { out ->
            source.inputStream().use { input -> input.copyTo(out) }
        } ?: error("could not write $displayName")
        return uri.toString()
    }

    fun delete(context: Context, uriString: String) {
        runCatching { context.contentResolver.delete(Uri.parse(uriString), null, null) }
    }

    fun exists(uriString: String): Boolean = uriString.startsWith("content://")
}
