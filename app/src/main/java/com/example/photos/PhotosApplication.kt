package com.example.photos

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.ImageDecoderDecoder
import coil.memory.MemoryCache
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors

class PhotosApplication : Application(), ImageLoaderFactory {
    // ImageDecoderDecoder enables animated GIF / WebP playback in the viewer.
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .components { add(ImageDecoderDecoder.Factory()) }
        .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.30).build() }
        .decoderDispatcher(Executors.newFixedThreadPool(3).asCoroutineDispatcher())
        .build()
}
