package app.cosmic.core.extractor.ytdlp

import android.app.Application
import android.util.Log
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One-time initialiser for yt-dlp on Android. The library extracts a
 * Python+yt-dlp tarball into the app's internal storage on first init —
 * a 10-15 second cost that we want to pay once at app start, not when the
 * user pastes their first URL.
 *
 * `ensureReady()` is safe to call concurrently: a Mutex serialises the init,
 * and once `ready` flips true subsequent calls return without locking.
 *
 * Failures are sticky-ish: if init throws (e.g. no storage), we log and
 * leave `ready=false` so the next call re-tries. yt-dlp's library is
 * generally bulletproof here, but emulators with weird storage layouts can
 * trip it up.
 */
@Singleton
class YtDlpInitializer @Inject constructor(
    @ApplicationContext private val context: android.content.Context,
) {
    private val mutex = Mutex()
    @Volatile private var ready: Boolean = false

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Kick off init in the background. Idempotent. Call from
     * Application.onCreate so the user's first paste is fast.
     */
    fun warmUp() {
        if (ready) return
        scope.launch {
            runCatching { ensureReady() }
                .onFailure { Log.w(TAG, "yt-dlp warm-up failed: $it") }
                .onSuccess {
                    // The youtubedl-android 0.15.0 wrapper ships an older
                    // bundled yt-dlp. Pull the latest at runtime so YouTube
                    // parser fixes land without an APK rebuild. Best-effort:
                    // a failed update keeps whatever version is on disk.
                    runCatching {
                        val app = context.applicationContext as Application
                        val status = YoutubeDL.getInstance().updateYoutubeDL(app)
                        Log.i(TAG, "yt-dlp self-update status: $status")
                    }.onFailure { Log.w(TAG, "yt-dlp self-update failed: $it") }
                }
        }
    }

    /**
     * Suspends until yt-dlp is ready. Throws if init failed.
     */
    suspend fun ensureReady() {
        if (ready) return
        mutex.withLock {
            if (ready) return
            withContext(Dispatchers.IO) {
                try {
                    val app = context.applicationContext as Application
                    YoutubeDL.getInstance().init(app)
                    ready = true
                    Log.i(TAG, "yt-dlp initialised; version=${runCatching { YoutubeDL.getInstance().version(app) }.getOrNull()}")
                } catch (t: YoutubeDLException) {
                    Log.e(TAG, "yt-dlp init failed", t)
                    throw IllegalStateException("yt-dlp init failed: ${t.message}", t)
                } catch (t: Throwable) {
                    Log.e(TAG, "yt-dlp init unexpected failure", t)
                    throw IllegalStateException("yt-dlp init failed: ${t.message}", t)
                }
            }
        }
    }

    /**
     * Best-effort updater. Async; failures are logged but never thrown to
     * caller. Useful to call manually if the user reports breakage; otherwise
     * `warmUp()` already self-updates on every cold launch.
     */
    fun updateInBackground() {
        scope.launch {
            try {
                ensureReady()
                val app = context.applicationContext as Application
                val status = YoutubeDL.getInstance().updateYoutubeDL(app)
                Log.i(TAG, "yt-dlp update status: $status")
            } catch (t: Throwable) {
                Log.w(TAG, "yt-dlp update failed: $t")
            }
        }
    }

    private companion object { const val TAG = "YtDlpInit" }
}
