package app.cosmic.core.extractor.newpipe

import okhttp3.OkHttpClient
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.Localization
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * NewPipe is a global singleton — calling NewPipe.init twice is undefined.
 * This wraps init in an idempotent guard so any extractor that needs it
 * can call ensureInitialized() before its first use.
 */
@Singleton
class NewPipeInitializer @Inject constructor(
    private val client: OkHttpClient,
) {
    private val started = AtomicBoolean(false)

    fun ensureInitialized() {
        if (started.compareAndSet(false, true)) {
            val locale = Locale.getDefault()
            val localization = runCatching {
                Localization(locale.language, locale.country.ifBlank { null })
            }.getOrDefault(Localization.DEFAULT)
            NewPipe.init(CosmicNewPipeDownloader(client), localization)
        }
    }
}
