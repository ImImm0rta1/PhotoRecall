package com.example.photos.data

import android.content.ContentResolver
import android.content.ContentValues
import android.provider.MediaStore

/** Where to put files: an existing folder (relative path) or a new folder name. */
data class FolderTarget(val path: String?, val newName: String?)

/** Real file operations. Albums never use these: they are virtual. */
object FileOps {
    private fun sanitize(n: String): String =
        n.trim().replace(Regex("[\\\\/:*?\"<>|]"), "_").trim('.', ' ').ifEmpty { "Album" }

    private fun targetPath(m: Media, t: FolderTarget): String =
        t.path ?: ((if (m.isVideo) "Movies/" else "Pictures/") + sanitize(t.newName ?: "Album") + "/")

    /** Needs a prior MediaStore.createWriteRequest grant for [items]. Returns (done, failed). */
    fun moveAll(resolver: ContentResolver, items: List<Media>, t: FolderTarget): Pair<Int, Int> {
        var ok = 0
        var fail = 0
        for (m in items) {
            val v = ContentValues().apply { put(MediaStore.MediaColumns.RELATIVE_PATH, targetPath(m, t)) }
            val rows = runCatching { resolver.update(m.uri, v, null, null) }.getOrDefault(0)
            if (rows > 0) ok++ else fail++
        }
        return ok to fail
    }

    /** Copies bytes into a new MediaStore entry; the original stays untouched. */
    fun copyAll(resolver: ContentResolver, items: List<Media>, t: FolderTarget): Pair<Int, Int> {
        var ok = 0
        var fail = 0
        for (m in items) if (copyOne(resolver, m, targetPath(m, t))) ok++ else fail++
        return ok to fail
    }

    private fun copyOne(resolver: ContentResolver, m: Media, path: String): Boolean {
        val collection = if (m.isVideo) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, m.name)
            put(MediaStore.MediaColumns.MIME_TYPE, m.mime.ifBlank { if (m.isVideo) "video/mp4" else "image/jpeg" })
            put(MediaStore.MediaColumns.RELATIVE_PATH, path)
            if (m.dateTaken > 0) put(MediaStore.MediaColumns.DATE_TAKEN, m.dateTaken)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val dest = runCatching { resolver.insert(collection, values) }.getOrNull() ?: return false
        return try {
            val input = resolver.openInputStream(m.uri) ?: error("no input")
            input.use { inp -> resolver.openOutputStream(dest)?.use { out -> inp.copyTo(out) } ?: error("no output") }
            resolver.update(dest, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            true
        } catch (e: Exception) {
            runCatching { resolver.delete(dest, null, null) }
            false
        }
    }
}
