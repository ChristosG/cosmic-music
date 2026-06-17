package app.cosmic.core.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import app.cosmic.core.db.dao.DownloadDao
import app.cosmic.core.db.dao.TrackDao
import app.cosmic.core.db.entity.DownloadJob
import app.cosmic.core.db.entity.Track
import app.cosmic.core.extractor.ExtractionResult
import app.cosmic.core.extractor.ExtractorRegistry
import app.cosmic.core.db.dao.PlaylistDao
import app.cosmic.core.metadata.ArtworkCache
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.OkHttpClient

private const val DL_CHANNEL_ID = "cosmic_downloads"
private const val DL_CHANNEL_NAME = "Downloads"
const val DOWNLOAD_INPUT_KEY_JOB_ID = "jobId"
const val DOWNLOAD_INPUT_KEY_ATTACH_PLAYLIST = "attachPlaylistId"

/**
 * Global concurrency cap for the download pipeline. Limits the number of
 * parallel yt-dlp invocations and HTTP byte-streams to avoid:
 *   1. Spawning N Python subprocesses (huge CPU + battery cost)
 *   2. YouTube IP-throttling that fires when too many parallel requests
 *      come from one device — the most common cause of "X out of Y tracks
 *      failed" on a 100-track playlist.
 *
 * Three is the sweet spot from yt-dlp community lore: enough parallelism
 * to use available bandwidth, low enough that YT doesn't notice.
 */
private val downloadConcurrency = Semaphore(permits = 3)

/**
 * One worker instance == one downloaded track. The job row is the single
 * source of truth — UI observes it via Flow. We use a foreground notification
 * (mandatory for >10 min on API 31+ and required for FOREGROUND_SERVICE_DATA_SYNC)
 * so the system doesn't kill us mid-stream.
 */
@HiltWorker
class DownloadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val extractor: ExtractorRegistry,
    private val mediaStoreWriter: MediaStoreWriter,
    private val downloadDao: DownloadDao,
    private val trackDao: TrackDao,
    private val playlistDao: PlaylistDao,
    private val repo: DownloadRepository,
    private val httpClient: OkHttpClient,
    private val artworkCache: ArtworkCache,
) : CoroutineWorker(appContext, params) {

    override suspend fun getForegroundInfo(): ForegroundInfo {
        ensureChannel()
        val notif = baseNotificationBuilder("Preparing download…", 0).build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIF_ID, notif)
        }
    }

    override suspend fun doWork(): Result = downloadConcurrency.withPermit {
        val jobId = inputData.getLong(DOWNLOAD_INPUT_KEY_JOB_ID, -1L)
        if (jobId < 0) return@withPermit Result.failure()
        val attachPlaylistId = inputData.getLong(DOWNLOAD_INPUT_KEY_ATTACH_PLAYLIST, -1L)
            .takeIf { it > 0 }
        val initial = downloadDao.get(jobId) ?: return@withPermit Result.failure()

        // setForeground can throw on API 33+ if POST_NOTIFICATIONS is denied,
        // or ForegroundServiceStartNotAllowedException on some OEMs. Either
        // way, the download continues silently rather than crashing.
        runCatching { setForeground(getForegroundInfo()) }
        markRunning(initial)

        val candidates = when (val r = extractor.extract(initial.sourceUrl)) {
            is ExtractionResult.Failure -> {
                markFailed(initial, r.message)
                return@withPermit Result.failure()
            }
            is ExtractionResult.Success -> r.candidates
        }

        // Fan-out: a playlist URL produced N children. Create a Cosmic
        // Playlist seeded with the YT playlist's title, then enqueue each
        // child download with that playlistId attached. Each child appends
        // its resulting Track to the playlist on completion.
        if (candidates.size > 1) {
            val playlistName = candidates.firstOrNull()?.album?.takeIf { it.isNotBlank() }
                ?: "Imported playlist"
            val newPlaylistId = runCatching {
                playlistDao.insertPlaylist(
                    app.cosmic.core.db.entity.Playlist(
                        name = playlistName,
                        createdAt = System.currentTimeMillis(),
                    ),
                )
            }.getOrNull()
            // The parent fan-out job's "title" is the playlist name so the
            // Downloads UI shows something useful for it too.
            updateTitle(initial.id, playlistName)
            candidates.forEach { c ->
                runCatching { repo.enqueue(c.sourceUrl, attachToPlaylistId = newPlaylistId) }
            }
            markCompleted(initial, trackId = null)
            return@withPermit Result.success()
        }

        val candidate = candidates.firstOrNull() ?: run {
            markFailed(initial, "Extractor returned no candidates")
            return@withPermit Result.failure()
        }
        // Persist the resolved track title so the Downloads UI displays it
        // immediately, before the actual byte-streaming completes.
        updateTitle(initial.id, candidate.title)

        return@withPermit try {
            // Stream via short ranged requests instead of one long GET. A single
            // sustained connection to googlevideo.com gets throttled to ~real-time
            // (or slower); chunked Range requests restore full speed. See
            // RangedHttpInputStream for the full rationale.
            RangedHttpInputStream(httpClient, candidate.streamUrl).use { ranged ->
                val totalBytes = candidate.sizeBytes

                val written = mediaStoreWriter.write(
                    input = ranged,
                    filename = candidate.filename,
                    artist = candidate.artist,
                    mimeType = candidate.mimeType,
                    totalBytes = totalBytes,
                ) { fraction ->
                    updateProgress(initial.id, fraction)
                    runCatching { setForeground(progressForeground(candidate.title, fraction)) }
                }

                // Best-effort thumbnail fetch — never block the download on
                // image errors. A null result just means the row keeps its
                // generated gradient placeholder.
                val artUri = runCatching { artworkCache.cacheRemote(candidate.thumbnailUrl) }.getOrNull()

                val track = Track(
                    id = 0,
                    mediaStoreId = written.mediaStoreId,
                    title = candidate.title,
                    artist = candidate.artist,
                    album = candidate.album,
                    durationMs = candidate.durationMs ?: 0L,
                    filePath = written.uri.toString(),
                    codec = candidate.codec,
                    bitrate = candidate.bitrateKbps,
                    sourceUrl = candidate.sourceUrl,
                    sourceKind = candidate.sourceKind.name,
                    addedAt = System.currentTimeMillis(),
                    replayGainTrackDb = null,
                    replayGainAlbumDb = null,
                    embeddedArtUri = artUri,
                )
                val newTrackId = trackDao.upsert(track)

                // If this download was spawned by a YT playlist fan-out,
                // attach the resulting Track to the auto-created Cosmic
                // playlist so the user sees them together.
                if (attachPlaylistId != null) {
                    runCatching { playlistDao.appendTrack(attachPlaylistId, newTrackId) }
                }

                markCompleted(initial, newTrackId)
            }
            Result.success()
        } catch (t: Throwable) {
            markFailed(initial, t.message ?: t::class.java.simpleName)
            // WorkManager handles retry policy; transient errors warrant retry.
            if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
        }
    }

    // The state-mutation helpers all re-read the row before writing, so
    // concurrent writes from the worker (e.g. `updateTitle` populating the
    // resolved track name post-extraction) aren't clobbered when we later
    // mark the job COMPLETED/FAILED. Without this re-read, the captured
    // snapshot taken at the top of `doWork()` (title = null) wins on the
    // final update and the UI flips back to showing the source URL.
    private suspend fun markRunning(job: DownloadJob) {
        val current = downloadDao.get(job.id) ?: job
        downloadDao.update(current.copy(status = DownloadStatus.RUNNING, updatedAt = System.currentTimeMillis()))
    }

    private suspend fun markCompleted(job: DownloadJob, trackId: Long?) {
        val current = downloadDao.get(job.id) ?: job
        downloadDao.update(
            current.copy(
                status = DownloadStatus.COMPLETED,
                progress = 1f,
                resultTrackId = trackId,
                error = null,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    private suspend fun markFailed(job: DownloadJob, message: String) {
        val current = downloadDao.get(job.id) ?: job
        downloadDao.update(
            current.copy(
                status = DownloadStatus.FAILED,
                error = message,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    private suspend fun updateProgress(jobId: Long, fraction: Float) {
        val current = downloadDao.get(jobId) ?: return
        downloadDao.update(current.copy(progress = fraction, updatedAt = System.currentTimeMillis()))
    }

    private suspend fun updateTitle(jobId: Long, title: String?) {
        val resolved = title?.trim()?.takeIf { it.isNotBlank() } ?: return
        val current = downloadDao.get(jobId) ?: return
        downloadDao.update(current.copy(title = resolved, updatedAt = System.currentTimeMillis()))
    }

    private fun progressForeground(title: String, fraction: Float): ForegroundInfo {
        val pct = (fraction * 100).toInt().coerceIn(0, 100)
        val notif = baseNotificationBuilder("$title — $pct%", pct).build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIF_ID, notif)
        }
    }

    private fun baseNotificationBuilder(text: String, progressPct: Int): NotificationCompat.Builder =
        NotificationCompat.Builder(applicationContext, DL_CHANNEL_ID)
            .setContentTitle("Cosmic")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, progressPct, progressPct == 0)

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = applicationContext.getSystemService(NotificationManager::class.java) ?: return
        if (mgr.getNotificationChannel(DL_CHANNEL_ID) != null) return
        mgr.createNotificationChannel(
            NotificationChannel(DL_CHANNEL_ID, DL_CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW),
        )
    }

    companion object {
        private const val NOTIF_ID = 1001
        private const val MAX_RETRIES = 3
    }
}
