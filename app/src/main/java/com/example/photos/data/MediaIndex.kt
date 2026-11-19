package com.example.photos.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.provider.MediaStore
import android.util.Size
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.util.Locale
import kotlin.math.abs

/** Per-photo derived data, computed once on-device: perceptual hash + GPS from EXIF. */
data class IndexEntry(
    val hash: Long?,
    val lat: Double?,
    val lon: Double?,
    /** True once GPS has been looked for (needs ACCESS_MEDIA_LOCATION to be meaningful). */
    val locDone: Boolean,
)

fun cellOf(ll: LatLon): String = String.format(Locale.US, "%.1f,%.1f", ll.lat, ll.lon)

fun cellLabel(cell: String): String {
    val parts = cell.split(',')
    val lat = parts[0].toDouble()
    val lon = parts[1].toDouble()
    return String.format(
        Locale.US, "%.1f°%s %.1f°%s",
        abs(lat), if (lat >= 0) "N" else "S",
        abs(lon), if (lon >= 0) "E" else "W",
    )
}

/** Append-only text index in app-private storage: key \t hash \t lat \t lon \t locDone */
class MediaIndexer(context: Context) {
    private val resolver = context.applicationContext.contentResolver
    private val file = File(context.applicationContext.filesDir, "media_index.tsv")

    fun load(): Map<String, IndexEntry> {
        val out = LinkedHashMap<String, IndexEntry>()
        if (!file.exists()) return out
        var lines = 0
        runCatching {
            file.forEachLine { line ->
                lines++
                val p = line.split('\t')
                if (p.size >= 5) {
                    out[p[0]] = IndexEntry(
                        hash = p[1].takeIf { it != "-" }?.toULongOrNull(16)?.toLong(),
                        lat = p[2].toDoubleOrNull(),
                        lon = p[3].toDoubleOrNull(),
                        locDone = p[4] == "1",
                    )
                }
            }
        }
        if (lines > out.size * 2 + 1000) rewrite(out)
        return out
    }

    fun append(entries: Map<String, IndexEntry>) {
        if (entries.isEmpty()) return
        file.appendText(buildString { entries.forEach { (k, e) -> append(line(k, e)) } })
    }

    private fun rewrite(all: Map<String, IndexEntry>) {
        val tmp = File(file.parentFile, "media_index.tmp")
        tmp.writeText(buildString { all.forEach { (k, e) -> append(line(k, e)) } })
        tmp.renameTo(file)
    }

    private fun line(key: String, e: IndexEntry): String =
        "$key\t${e.hash?.toULong()?.toString(16) ?: "-"}\t${e.lat ?: "-"}\t${e.lon ?: "-"}\t${if (e.locDone) 1 else 0}\n"

    /** Runs on a background thread. Videos are not indexed. */
    fun compute(m: Media, existing: IndexEntry?, readLocation: Boolean): IndexEntry {
        var hash = existing?.hash
        if (existing == null) {
            hash = runCatching {
                resolver.loadThumbnail(m.uri, Size(64, 64), null)
            }.getOrNull()?.let { dHash(it) }
        }
        var lat = existing?.lat
        var lon = existing?.lon
        var locDone = existing?.locDone ?: false
        if (readLocation && !locDone) {
            runCatching {
                resolver.openInputStream(MediaStore.setRequireOriginal(m.uri))?.use { s ->
                    ExifInterface(s).latLong?.let { ll -> lat = ll[0]; lon = ll[1] }
                }
            }
            locDone = true
        }
        return IndexEntry(hash, lat, lon, locDone)
    }

    /** 64-bit difference hash: robust to resizing and recompression. */
    private fun dHash(src: Bitmap): Long {
        val soft = if (src.config == Bitmap.Config.HARDWARE) src.copy(Bitmap.Config.ARGB_8888, false) else src
        val small = Bitmap.createScaledBitmap(soft, 9, 8, true)
        var hash = 0L
        var bit = 0
        for (y in 0 until 8) {
            for (x in 0 until 8) {
                if (lum(small.getPixel(x, y)) > lum(small.getPixel(x + 1, y))) hash = hash or (1L shl bit)
                bit++
            }
        }
        return hash
    }

    private fun lum(c: Int): Double = 0.299 * Color.red(c) + 0.587 * Color.green(c) + 0.114 * Color.blue(c)
}
