package pitcher.android.media

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import java.io.File

/**
 * Publishes rendered files. When a folder tree URI is set (SAF), the file is
 * written there through DocumentsContract. Otherwise it falls back to the
 * shared Music/pitcher collection via MediaStore. Returns a URI string, which is
 * directly shareable.
 */
object RenderedStore {

    const val FOLDER = "Music/pitcher"

    fun save(
        context: Context,
        folderTreeUri: String?,
        displayName: String,
        mime: String,
        source: File,
    ): String {
        if (!folderTreeUri.isNullOrBlank()) {
            runCatching { return saveToTree(context, folderTreeUri, displayName, mime, source) }
        }
        return saveToMediaStore(context, displayName, mime, source)
    }

    private fun saveToTree(
        context: Context,
        folderTreeUri: String,
        displayName: String,
        mime: String,
        source: File,
    ): String {
        val resolver = context.contentResolver
        val treeUri = Uri.parse(folderTreeUri)
        val parent = DocumentsContract.buildDocumentUriUsingTree(
            treeUri,
            DocumentsContract.getTreeDocumentId(treeUri),
        )
        val doc = DocumentsContract.createDocument(resolver, parent, mime, displayName)
            ?: error("could not create $displayName in the chosen folder")
        resolver.openOutputStream(doc)?.use { out ->
            source.inputStream().use { input -> input.copyTo(out) }
        } ?: error("could not write $displayName")
        return doc.toString()
    }

    private fun saveToMediaStore(
        context: Context,
        displayName: String,
        mime: String,
        source: File,
    ): String {
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

    /**
     * An intent to show the saved file: the chosen SAF folder when set, otherwise
     * the file itself (the default Music/pitcher collection has no folder URI).
     */
    fun openIntent(context: Context, fileUri: String, folderTreeUri: String?): Intent? {
        if (!folderTreeUri.isNullOrBlank()) {
            val treeUri = Uri.parse(folderTreeUri)
            return Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(treeUri, DocumentsContract.Document.MIME_TYPE_DIR)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        val file = Uri.parse(fileUri)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(file, "audio/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /**
     * A readable name for a save folder: the path inside the storage volume for
     * a SAF tree (`primary:Music/Covers` reads as `Music/Covers`), or the default.
     */
    fun folderLabel(folderTreeUri: String?): String {
        if (folderTreeUri.isNullOrBlank()) return FOLDER
        val docId = runCatching { DocumentsContract.getTreeDocumentId(Uri.parse(folderTreeUri)) }
            .getOrNull() ?: return folderTreeUri
        val path = docId.substringAfter(':', docId)
        return path.ifBlank { docId.substringBefore(':') }
    }

    fun exists(uriString: String): Boolean =
        uriString.startsWith("content://") || uriString.startsWith("file://")
}
