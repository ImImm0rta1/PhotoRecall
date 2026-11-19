package com.example.photos.data

import android.content.ContentUris
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext

/** Reads photos/videos from MediaStore. Nothing is copied or uploaded anywhere. */
class MediaRepository(context: Context) {
    private val resolver = context.applicationContext.contentResolver
    private val imagesUri: Uri = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
    private val videosUri: Uri = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)

    /** Returns (live items, trashed items). */
    suspend fun load(): Pair<List<Media>, List<Media>> = withContext(Dispatchers.IO) {
        val live = query(imagesUri, isVideo = false, trashed = false) +
            query(videosUri, isVideo = true, trashed = false)
        val trash = query(imagesUri, isVideo = false, trashed = true) +
            query(videosUri, isVideo = true, trashed = true)
        live to trash
    }

    /** Emits once immediately, then whenever MediaStore changes. */
    fun changes(): Flow<Unit> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }
        resolver.registerContentObserver(imagesUri, true, observer)
        resolver.registerContentObserver(videosUri, true, observer)
        trySend(Unit)
        awaitClose { resolver.unregisterContentObserver(observer) }
    }

    private fun query(collection: Uri, isVideo: Boolean, trashed: Boolean): List<Media> {
        val projection = buildList {
            add(MediaStore.MediaColumns._ID)
            add(MediaStore.MediaColumns.DISPLAY_NAME)
            add(MediaStore.MediaColumns.MIME_TYPE)
            add(MediaStore.MediaColumns.SIZE)
            add(MediaStore.MediaColumns.DATE_TAKEN)
            add(MediaStore.MediaColumns.DATE_MODIFIED)
            add(MediaStore.MediaColumns.DATE_ADDED)
            add(MediaStore.MediaColumns.DATE_EXPIRES)
            add(MediaStore.MediaColumns.WIDTH)
            add(MediaStore.MediaColumns.HEIGHT)
            add(MediaStore.MediaColumns.RELATIVE_PATH)
            add(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
            if (isVideo) add(MediaStore.MediaColumns.DURATION)
        }.toTypedArray()

        val args = Bundle().apply {
            putInt(
                MediaStore.QUERY_ARG_MATCH_TRASHED,
                if (trashed) MediaStore.MATCH_ONLY else MediaStore.MATCH_EXCLUDE,
            )
        }

        val out = ArrayList<Media>()
        resolver.query(collection, projection, args, null)?.use { c ->
            val iId = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val iName = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val iMime = c.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
            val iSize = c.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            val iTaken = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_TAKEN)
            val iModified = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
            val iAdded = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
            val iExpires = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_EXPIRES)
            val iW = c.getColumnIndexOrThrow(MediaStore.MediaColumns.WIDTH)
            val iH = c.getColumnIndexOrThrow(MediaStore.MediaColumns.HEIGHT)
            val iPath = c.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH)
            val iBucket = c.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
            val iDuration = if (isVideo) c.getColumnIndexOrThrow(MediaStore.MediaColumns.DURATION) else -1

            while (c.moveToNext()) {
                val id = c.getLong(iId)
                val modified = c.getLong(iModified) * 1000L
                val taken = c.getLong(iTaken)
                out += Media(
                    id = id,
                    isVideo = isVideo,
                    uri = ContentUris.withAppendedId(collection, id),
                    name = c.getString(iName) ?: "",
                    mime = c.getString(iMime) ?: "",
                    size = c.getLong(iSize),
                    // DATE_TAKEN is missing for many files (downloads, screenshots): fall back.
                    dateTaken = if (taken > 0) taken else modified,
                    dateModified = modified,
                    dateAdded = c.getLong(iAdded) * 1000L,
                    dateExpires = c.getLong(iExpires) * 1000L,
                    width = c.getInt(iW),
                    height = c.getInt(iH),
                    durationMs = if (iDuration >= 0) c.getLong(iDuration) else 0L,
                    relativePath = c.getString(iPath) ?: "",
                    folderName = c.getString(iBucket) ?: "",
                )
            }
        }
        return out
    }
}
