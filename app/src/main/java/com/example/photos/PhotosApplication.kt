package com.example.photos

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.ImageDecoderDecoder

class PhotosApplication : Application(), ImageLoaderFactory {
    // ImageDecoderDecoder enables animated GIF / WebP playback in the viewer.
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .components { add(ImageDecoderDecoder.Factory()) }
        .build()
}
