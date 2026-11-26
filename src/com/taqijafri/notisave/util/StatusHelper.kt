package com.taqijafri.notisave.util

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import com.taqijafri.notisave.data.Prefs

/** One WhatsApp status (image or video), addressed by its content Uri. */
data class StatusFile(
    val uri: Uri,
    val name: String,
    val isVideo: Boolean,
    val lastModified: Long,
    val size: Long,
    /** True when this is a copy already saved by this app. */
    val isSavedCopy: Boolean = false
)

/**
 * WhatsApp Status Saver helpers — Storage Access Framework edition.
 *
 * The user picks the WhatsApp folder once (system folder picker); the app
 * then reads the hidden ".Statuses" folder inside it and saves chosen
 * statuses to Pictures/Notisave through MediaStore. No all-files access,
 * no broad storage permission — which is also what Play Protect flags.
 * Nothing here touches WhatsApp's private data or its encryption.
 */
object StatusHelper {

    const val SAVED_RELATIVE_PATH = "Pictures/Notisave"
    const val PICK_FOLDER_REQUEST = 1001

    private const val MIME_DIR = DocumentsContract.Document.MIME_TYPE_DIR

    // ---------- folder access ----------

    /** True when a WhatsApp folder is granted and its .Statuses folder is reachable. */
    fun hasFolderAccess(context: Context): Boolean {
        val treeUri = savedTreeUri(context) ?: return false
        val persisted = context.contentResolver.persistedUriPermissions
            .any { it.uri == treeUri && it.isReadPermission }
        if (!persisted) return false
        return try {
            resolveStatusesFolder(context, treeUri) != null
        } catch (e: Exception) {
            false
        }
    }

    fun savedTreeUri(context: Context): Uri? {
        val raw = Prefs(context).statusFolderUri ?: return null
        return try {
            Uri.parse(raw)
        } catch (e: Exception) {
            null
        }
    }

    /** Opens the system folder picker, starting at WhatsApp's media folder. */
    fun openFolderPicker(fragment: android.app.Fragment) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        intent.addFlags(
            Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
        )
        val initial = DocumentsContract.buildDocumentUri(
            "com.android.externalstorage.documents",
            "primary:Android/media/com.whatsapp/WhatsApp/Media"
        )
        intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, initial)
        fragment.startActivityForResult(intent, PICK_FOLDER_REQUEST)
    }

    /** Persists the folder the user picked. Returns false when it can't be used. */
    fun handleFolderPicked(context: Context, treeUri: Uri): Boolean {
        return try {
            context.contentResolver.takePersistableUriPermission(
                treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            Prefs(context).statusFolderUri = treeUri.toString()
            resolveStatusesFolder(context, treeUri) != null
        } catch (e: Exception) {
            false
        }
    }

    // ---------- SAF document helpers ----------

    private data class Doc(
        val documentId: String,
        val name: String,
        val mime: String,
        val lastModified: Long,
        val size: Long
    )

    private fun listChildren(
        context: Context, treeUri: Uri, parentDocumentId: String
    ): List<Doc> {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            treeUri, parentDocumentId
        )
        val out = ArrayList<Doc>()
        val cursor = context.contentResolver.query(
            childrenUri,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED,
                DocumentsContract.Document.COLUMN_SIZE
            ),
            null, null, null
        ) ?: return out
        cursor.use {
            val idIdx = it.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameIdx = it.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeIdx = it.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
            val modIdx = it.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
            val sizeIdx = it.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
            while (it.moveToNext()) {
                val id = if (idIdx >= 0) it.getString(idIdx) else null ?: continue
                out.add(
                    Doc(
                        documentId = id,
                        name = if (nameIdx >= 0) it.getString(nameIdx) ?: "" else "",
                        mime = if (mimeIdx >= 0) it.getString(mimeIdx) ?: "" else "",
                        lastModified = if (modIdx >= 0) it.getLong(modIdx) else 0L,
                        size = if (sizeIdx >= 0) it.getLong(sizeIdx) else 0L
                    )
                )
            }
        }
        return out
    }

    private fun documentDisplayName(
        context: Context, treeUri: Uri, documentId: String
    ): String? {
        val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
        val cursor = context.contentResolver.query(
            docUri,
            arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null, null, null
        ) ?: return null
        cursor.use {
            if (it.moveToFirst()) {
                val idx = it.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                if (idx >= 0) return it.getString(idx)
            }
        }
        return null
    }

    /**
     * Finds the ".Statuses" folder under the granted folder.
     * Handles picking WhatsApp/Media itself, the WhatsApp folder, or
     * .Statuses directly.
     */
    fun resolveStatusesFolder(context: Context, treeUri: Uri): Uri? {
        val treeId = DocumentsContract.getTreeDocumentId(treeUri)
        if (documentDisplayName(context, treeUri, treeId) == ".Statuses") {
            return DocumentsContract.buildDocumentUriUsingTree(treeUri, treeId)
        }
        val roots = ArrayList<String>()
        roots.add(treeId)
        // If the user picked the WhatsApp folder, .Statuses lives under Media/.
        for (child in listChildren(context, treeUri, treeId)) {
            if (child.mime == MIME_DIR && child.name == "Media") roots.add(child.documentId)
        }
        for (rootId in roots) {
            val dot = listChildren(context, treeUri, rootId)
                .firstOrNull { it.name == ".Statuses" } ?: continue
            return DocumentsContract.buildDocumentUriUsingTree(treeUri, dot.documentId)
        }
        return null
    }

    /** Statuses you have viewed in WhatsApp, newest first. */
    fun listStatuses(context: Context): List<StatusFile> {
        val treeUri = savedTreeUri(context) ?: return emptyList()
        val statusesUri = resolveStatusesFolder(context, treeUri) ?: return emptyList()
        val statusesId = DocumentsContract.getDocumentId(statusesUri)
        val docs = listChildren(context, treeUri, statusesId)
        return docs
            .filter { it.mime.startsWith("image/") || it.mime.startsWith("video/") }
            .map {
                StatusFile(
                    uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, it.documentId),
                    name = it.name,
                    isVideo = it.mime.startsWith("video/"),
                    lastModified = it.lastModified,
                    size = it.size
                )
            }
            .sortedByDescending { it.lastModified }
    }

    // ---------- saving through MediaStore (Android 10+) ----------

    private fun mimeFor(status: StatusFile): String {
        val ext = status.name.substringAfterLast('.', "").lowercase()
        val mapped = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
        if (mapped != null) return mapped
        return if (status.isVideo) "video/mp4" else "image/jpeg"
    }

    /** Copies a status into Pictures/Notisave. Returns its MediaStore Uri, or null. */
    fun saveStatus(context: Context, status: StatusFile): Uri? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        findSavedUri(context, status.name)?.let { return it }
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, status.name)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeFor(status))
            put(MediaStore.MediaColumns.RELATIVE_PATH, "$SAVED_RELATIVE_PATH/")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val collection = if (status.isVideo) {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        val destUri = resolver.insert(collection, values) ?: return null
        try {
            resolver.openInputStream(status.uri)?.use { input ->
                resolver.openOutputStream(destUri)?.use { output ->
                    input.copyTo(output)
                } ?: run {
                    resolver.delete(destUri, null, null)
                    return null
                }
            } ?: run {
                resolver.delete(destUri, null, null)
                return null
            }
            val done = ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING, 0)
            }
            resolver.update(destUri, done, null, null)
            return destUri
        } catch (e: Exception) {
            try {
                resolver.delete(destUri, null, null)
            } catch (ignored: Exception) {
            }
            return null
        }
    }

    /** Saves every unsaved status; returns how many were newly saved. */
    fun saveAll(context: Context, statuses: List<StatusFile>): Int {
        var saved = 0
        for (s in statuses) {
            if (s.isSavedCopy || isSaved(context, s)) continue
            if (saveStatus(context, s) != null) saved++
        }
        return saved
    }

    fun isSaved(context: Context, status: StatusFile): Boolean {
        if (status.isSavedCopy) return true
        return findSavedUri(context, status.name) != null
    }

    /** MediaStore Uri of a previously saved copy with this name, or null. */
    fun findSavedUri(context: Context, name: String): Uri? {
        val resolver = context.contentResolver
        val collections = listOf(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        )
        for (collection in collections) {
            val cursor = resolver.query(
                collection,
                arrayOf(MediaStore.MediaColumns._ID),
                "${MediaStore.MediaColumns.DISPLAY_NAME}=? AND " +
                    "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?",
                arrayOf(name, "$SAVED_RELATIVE_PATH%"),
                null
            ) ?: continue
            cursor.use {
                if (it.moveToFirst()) {
                    val idIdx = it.getColumnIndex(MediaStore.MediaColumns._ID)
                    if (idIdx >= 0) {
                        return ContentUris.withAppendedId(collection, it.getLong(idIdx))
                    }
                }
            }
        }
        return null
    }

    /** Copies previously saved by this app, newest first. */
    fun listSaved(context: Context): List<StatusFile> {
        val out = ArrayList<StatusFile>()
        val resolver = context.contentResolver
        val collections = listOf(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI to false,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI to true
        )
        for ((collection, isVideo) in collections) {
            val cursor = resolver.query(
                collection,
                arrayOf(
                    MediaStore.MediaColumns._ID,
                    MediaStore.MediaColumns.DISPLAY_NAME,
                    MediaStore.MediaColumns.DATE_MODIFIED,
                    MediaStore.MediaColumns.SIZE
                ),
                "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?",
                arrayOf("$SAVED_RELATIVE_PATH%"),
                "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"
            ) ?: continue
            cursor.use {
                val idIdx = it.getColumnIndex(MediaStore.MediaColumns._ID)
                val nameIdx = it.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                val modIdx = it.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)
                val sizeIdx = it.getColumnIndex(MediaStore.MediaColumns.SIZE)
                while (it.moveToNext()) {
                    if (idIdx < 0) continue
                    val id = it.getLong(idIdx)
                    out.add(
                        StatusFile(
                            uri = ContentUris.withAppendedId(collection, id),
                            name = if (nameIdx >= 0) it.getString(nameIdx) ?: "" else "",
                            isVideo = isVideo,
                            lastModified = if (modIdx >= 0) it.getLong(modIdx) * 1000L else 0L,
                            size = if (sizeIdx >= 0) it.getLong(sizeIdx) else 0L,
                            isSavedCopy = true
                        )
                    )
                }
            }
        }
        return out.sortedByDescending { it.lastModified }
    }

    fun deleteSaved(context: Context, status: StatusFile): Boolean {
        return try {
            val uri = if (status.isSavedCopy) status.uri
            else findSavedUri(context, status.name) ?: return false
            context.contentResolver.delete(uri, null, null) > 0
        } catch (e: Exception) {
            false
        }
    }

    // ---------- thumbnails ----------

    /** Small preview bitmap for a grid cell (sampled image, or first video frame). */
    fun thumbnail(context: Context, status: StatusFile, targetPx: Int = 240): Bitmap? {
        return try {
            if (status.isVideo) {
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(context, status.uri)
                    retriever.getFrameAtTime(0)
                } finally {
                    try {
                        retriever.release()
                    } catch (e: Exception) {
                    }
                }
            } else {
                val resolver = context.contentResolver
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                resolver.openInputStream(status.uri)?.use {
                    BitmapFactory.decodeStream(it, null, bounds)
                }
                var sample = 1
                val maxSide = maxOf(bounds.outWidth, bounds.outHeight)
                while (maxSide / sample > targetPx) sample *= 2
                val opts = BitmapFactory.Options().apply { inSampleSize = sample }
                resolver.openInputStream(status.uri)?.use {
                    BitmapFactory.decodeStream(it, null, opts)
                }
            }
        } catch (e: Exception) {
            null
        }
    }
}
