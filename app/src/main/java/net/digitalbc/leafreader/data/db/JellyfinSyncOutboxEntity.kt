package net.digitalbc.leafreader.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Durable outbox tracking pending progress and read-status updates to be synced to Jellyfin.
 *
 * Entries are created immediately on local page turns and deleted upon confirmed remote sync.
 * Deleting a local comic cascades cleanly to remove its pending sync entries.
 */
@Entity(
    tableName = "jellyfin_sync_outbox",
    indices = [
        Index(value = ["comicId"], unique = true),
        Index(value = ["serverId", "userId"]),
    ],
    foreignKeys = [
        ForeignKey(
            entity = ComicEntity::class,
            parentColumns = ["id"],
            childColumns = ["comicId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class JellyfinSyncOutboxEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val comicId: Long,
    val serverId: String,
    val userId: String,
    val itemId: String,
    val pageIndex: Int,
    val markPlayed: Boolean? = null,
    val updatedAt: Long = System.currentTimeMillis(),
)
