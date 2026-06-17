package app.cosmic.core.download

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import app.cosmic.core.db.dao.DownloadDao
import app.cosmic.core.db.entity.DownloadJob
import app.cosmic.core.extractor.LinkResolver
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/** Single API surface UI uses to enqueue + observe downloads. */
@Singleton
class DownloadRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadDao: DownloadDao,
    private val resolver: LinkResolver,
) {

    fun observeAll(): Flow<List<DownloadJob>> = downloadDao.observeAll()

    /**
     * @param attachToPlaylistId when set, the worker will append the
     *   resulting Track to that playlist on successful download. Used by
     *   the YT-playlist fan-out path so all downloaded items end up in a
     *   single Cosmic playlist.
     */
    suspend fun enqueue(rawUrl: String, attachToPlaylistId: Long? = null): Long {
        val url = rawUrl.trim()
        require(url.isNotEmpty()) { "URL is required" }
        val kind = resolver.resolve(url)
        val now = System.currentTimeMillis()
        val id = downloadDao.insert(
            DownloadJob(
                id = 0,
                sourceUrl = url,
                sourceKind = kind.name,
                status = DownloadStatus.QUEUED,
                progress = 0f,
                error = null,
                resultTrackId = null,
                queuedAt = now,
                updatedAt = now,
            ),
        )
        val data = Data.Builder()
            .putLong(DOWNLOAD_INPUT_KEY_JOB_ID, id)
            .apply {
                if (attachToPlaylistId != null) {
                    putLong(DOWNLOAD_INPUT_KEY_ATTACH_PLAYLIST, attachToPlaylistId)
                }
            }
            .build()
        val req = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(data)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .addTag(WORK_TAG)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            uniqueName(id),
            ExistingWorkPolicy.KEEP,
            req,
        )
        return id
    }

    suspend fun cancel(jobId: Long) {
        WorkManager.getInstance(context).cancelUniqueWork(uniqueName(jobId))
        val job = downloadDao.get(jobId) ?: return
        if (job.status != DownloadStatus.COMPLETED) {
            downloadDao.update(
                job.copy(status = DownloadStatus.CANCELED, updatedAt = System.currentTimeMillis()),
            )
        }
    }

    suspend fun retry(jobId: Long) {
        val job = downloadDao.get(jobId) ?: return
        downloadDao.update(
            job.copy(
                status = DownloadStatus.QUEUED,
                progress = 0f,
                error = null,
                updatedAt = System.currentTimeMillis(),
            ),
        )
        // Re-enqueue under the same unique name; REPLACE so any leftover work is dropped.
        val req = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(Data.Builder().putLong(DOWNLOAD_INPUT_KEY_JOB_ID, jobId).build())
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .addTag(WORK_TAG)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            uniqueName(jobId),
            ExistingWorkPolicy.REPLACE,
            req,
        )
    }

    suspend fun delete(jobId: Long) {
        WorkManager.getInstance(context).cancelUniqueWork(uniqueName(jobId))
        downloadDao.delete(jobId)
    }

    companion object {
        const val WORK_TAG = "cosmic_download"
        private fun uniqueName(jobId: Long) = "cosmic_download_$jobId"
    }
}
