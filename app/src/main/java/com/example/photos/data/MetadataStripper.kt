package com.example.photos.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import java.io.File
import java.io.FileOutputStream

/** Makes a metadata-free copy (no EXIF / GPS) in the cache folder, ready to share. */
object MetadataStripper {
    fun strip(context: Context, m: Media): File? {
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        // Temporary copies are only needed while the share sheet is open.
        dir.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 3_600_000L }?.forEach { it.delete() }
        val base = m.name.substringBeforeLast('.', m.name).take(60)
        return try {
            when {
                m.isVideo -> {
                    val out = File(dir, "${base}_${m.id}.mp4")
                    if (VideoTrimmer.toFile(context, m.uri, 0L, -1L, out)) out else null.also { out.delete() }
                }
                m.isGif -> {
                    val out = File(dir, "${base}_${m.id}.gif")
                    context.contentResolver.openInputStream(m.uri)?.use { i -> FileOutputStream(out).use { i.copyTo(it) } }
                    out
                }
                else -> {
                    val png = m.mime.equals("image/png", ignoreCase = true)
                    val out = File(dir, "${base}_${m.id}.${if (png) "png" else "jpg"}")
                    val src = ImageDecoder.createSource(context.contentResolver, m.uri)
                    val bmp = ImageDecoder.decodeBitmap(src) { dec, info, _ ->
                        dec.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                        val longest = maxOf(info.size.width, info.size.height)
                        if (longest > 8000) dec.setTargetSampleSize((longest / 8000) + 1)
                    }
                    FileOutputStream(out).use {
                        bmp.compress(if (png) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, 95, it)
                    }
                    bmp.recycle()
                    out
                }
            }
        } catch (e: Exception) {
            null
        }
    }
}
