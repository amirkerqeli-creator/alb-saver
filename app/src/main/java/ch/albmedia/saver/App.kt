package ch.albmedia.saver

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder

class App : Application(), ImageLoaderFactory {
    // Coil: Bilder + Vorschaubilder aus Videos, mit weichem Einblenden
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .components { add(VideoFrameDecoder.Factory()) }
        .crossfade(true)
        .build()
}
