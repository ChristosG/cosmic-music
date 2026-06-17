package app.cosmic.core.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "download_jobs")
data class DownloadJob(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceUrl: String,
    val sourceKind: String,
    val status: String,           // QUEUED, RUNNING, COMPLETED, FAILED, CANCELED
    val progress: Float,          // 0..1
    val error: String?,
    val resultTrackId: Long?,
    val queuedAt: Long,
    val updatedAt: Long,
    /**
     * Display title — written by the worker once extraction resolves the
     * track's name. Null while QUEUED (we only know the source URL at that
     * point); UI falls back to sourceUrl until this populates.
     */
    val title: String? = null,
)
