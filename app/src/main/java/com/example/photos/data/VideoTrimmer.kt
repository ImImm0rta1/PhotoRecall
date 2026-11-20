package com.example.photos.data

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import java.io.File
import java.io.FileDescriptor
import java.nio.ByteBuffer

/**
 * Lossless trimming / re-muxing with the platform MediaExtractor + MediaMuxer (no extra library, no re-encode).
 * The cut starts at the nearest earlier keyframe. Re-muxing also drops location metadata.
 */
object VideoTrimmer {
    /** [endUs] < 0 means "to the end". */
    fun toFile(context: Context, src: Uri, startUs: Long, endUs: Long, out: File): Boolean =
        remux(context, src, startUs, endUs) { MediaMuxer(out.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4) }

    fun toDescriptor(context: Context, src: Uri, startUs: Long, endUs: Long, fd: FileDescriptor): Boolean =
        remux(context, src, startUs, endUs) { MediaMuxer(fd, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4) }

    private fun remux(context: Context, src: Uri, startUs: Long, endUs: Long, newMuxer: () -> MediaMuxer): Boolean {
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        var started = false
        return try {
            extractor.setDataSource(context, src, null)
            muxer = newMuxer()

            val rotation = MediaMetadataRetriever().run {
                try {
                    setDataSource(context, src)
                    extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
                } finally {
                    release()
                }
            }
            muxer.setOrientationHint(rotation)

            val trackMap = HashMap<Int, Int>()
            var maxSize = 1 shl 20
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("video/") || mime.startsWith("audio/")) {
                    extractor.selectTrack(i)
                    trackMap[i] = muxer.addTrack(format)
                    if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                        maxSize = maxOf(maxSize, format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE))
                    }
                }
            }
            if (trackMap.isEmpty()) return false

            muxer.start()
            started = true
            extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)

            val buffer = ByteBuffer.allocate(maxSize)
            val info = MediaCodec.BufferInfo()
            var firstTs = -1L
            while (true) {
                buffer.clear()
                val size = extractor.readSampleData(buffer, 0)
                if (size < 0) break
                val ts = extractor.sampleTime
                if (endUs >= 0 && ts > endUs) break
                if (firstTs < 0) firstTs = ts
                val track = trackMap[extractor.sampleTrackIndex]
                if (track != null) {
                    val flags = if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) {
                        MediaCodec.BUFFER_FLAG_KEY_FRAME
                    } else 0
                    info.set(0, size, maxOf(0L, ts - firstTs), flags)
                    muxer.writeSampleData(track, buffer, info)
                }
                extractor.advance()
            }
            true
        } catch (e: Exception) {
            false
        } finally {
            runCatching { if (started) muxer?.stop() }
            runCatching { muxer?.release() }
            runCatching { extractor.release() }
        }
    }
}
