package app.cosmic.core.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "tags", indices = [Index(value = ["name"], unique = true)])
data class Tag(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val colorArgb: Int? = null,
)

@Entity(
    tableName = "track_tags",
    primaryKeys = ["trackId", "tagId"],
    foreignKeys = [
        ForeignKey(Track::class, ["id"], ["trackId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(Tag::class, ["id"], ["tagId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("tagId")],
)
data class TrackTag(
    val trackId: Long,
    val tagId: Long,
)
