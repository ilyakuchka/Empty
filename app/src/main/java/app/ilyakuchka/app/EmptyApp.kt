package app.ilyakuchka.app

import android.app.Application
import app.ilyakuchka.app.data.Api
import app.ilyakuchka.app.data.Operations
import app.ilyakuchka.app.data.Prefs
import coil.ImageLoader
import coil.ImageLoaderFactory

class EmptyApp : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        Operations.load(this)
    }

    override fun newImageLoader(): ImageLoader {
        val http = Api.client.newBuilder()
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("Referer", BuildConfig.SITE_URL + "/")
                        .header("User-Agent", Api.UA)
                        .build()
                )
            }
            .build()
        return ImageLoader.Builder(this)
            .okHttpClient(http)
            .crossfade(true)
            .build()
    }
}
