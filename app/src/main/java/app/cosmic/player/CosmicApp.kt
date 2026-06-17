package app.cosmic.player

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import app.cosmic.core.extractor.ytdlp.YtDlpInitializer
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import dagger.hilt.android.HiltAndroidApp
import okhttp3.OkHttpClient
import javax.inject.Inject

@HiltAndroidApp
class CosmicApp : Application(), Configuration.Provider, SingletonImageLoader.Factory {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var ytDlpInitializer: YtDlpInitializer
    @Inject lateinit var sharedHttp: OkHttpClient

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()

    override fun onCreate() {
        super.onCreate()
        // Warm yt-dlp in the background so the first paste-and-download
        // doesn't pay the 10-15s cold-start. Idempotent + non-blocking.
        ytDlpInitializer.warmUp()
    }

    /**
     * Coil 3's process-wide ImageLoader. Sharing the OkHttp instance gives
     * us cookie / cache reuse with the rest of the app and a single
     * connection pool. Crossfade enabled for the fade-in feel on
     * thumbnail loads in the YT search list.
     */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { sharedHttp }))
            }
            .crossfade(true)
            .build()
}
