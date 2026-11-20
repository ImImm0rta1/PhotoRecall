package com.example.photos.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.util.LruCache
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.photos.data.Media
import com.example.photos.ui.theme.CardColorHigh
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/** Limits parallel system-thumbnail decodes so fast scrolling never starves the CPU. */
private val thumbGate = Semaphore(4)

object ThumbCache : LruCache<String, Bitmap>(
    (Runtime.getRuntime().maxMemory() / 8).toInt().coerceAtLeast(8 * 1024 * 1024),
) {
    override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
}

/**
 * Grid thumbnail. Plain images go through Coil; videos, RAW and GIF use the
 * system thumbnail service (static frame, RAW embedded preview, no GIF animation in the grid).
 */
@Composable
fun MediaThumbnail(media: Media, px: Int, modifier: Modifier = Modifier) {
    if (media.isVideo || media.isRaw || media.isGif) {
        SystemThumbnail(media, px, modifier)
    } else {
        val context = LocalContext.current
        AsyncImage(
            model = remember(media.uri, px) {
                ImageRequest.Builder(context).data(media.uri).size(px).crossfade(120).build()
            },
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier,
        )
    }
}

@Composable
fun SystemThumbnail(
    media: Media,
    px: Int,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val context = LocalContext.current
    val cacheKey = "${media.key}@$px"
    var bitmap by remember(cacheKey) { mutableStateOf(ThumbCache.get(cacheKey)) }
    LaunchedEffect(cacheKey) {
        if (bitmap == null) {
            val loaded = withContext(Dispatchers.IO) {
                thumbGate.withPermit {
                    runCatching { context.contentResolver.loadThumbnail(media.uri, Size(px, px), null) }.getOrNull()
                }
            }
            if (loaded != null) {
                ThumbCache.put(cacheKey, loaded)
                bitmap = loaded
            }
        }
    }
    val bmp = bitmap
    if (bmp != null) {
        Image(bitmap = bmp.asImageBitmap(), contentDescription = null, contentScale = contentScale, modifier = modifier)
    } else {
        Box(modifier.background(CardColorHigh))
    }
}

/** Larger decode for RAW files in the viewer; falls back to the embedded preview. */
suspend fun loadRawFull(context: Context, media: Media, maxPx: Int): Bitmap? = withContext(Dispatchers.IO) {
    runCatching {
        val source = ImageDecoder.createSource(context.contentResolver, media.uri)
        ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val longest = maxOf(info.size.width, info.size.height)
            decoder.setTargetSampleSize((longest / maxPx).coerceAtLeast(1))
        }
    }.getOrNull() ?: runCatching {
        context.contentResolver.loadThumbnail(media.uri, Size(maxPx, maxPx), null)
    }.getOrNull()
}
