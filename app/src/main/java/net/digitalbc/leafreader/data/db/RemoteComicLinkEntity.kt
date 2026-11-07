package net.digitalbc.leafreader.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Tracks the provenance of a comic downloaded from a remote provider (e.g. Jellyfin).
 *
 * This separates remote identity, server version tags, and sync metadata from the core
 * [ComicEntity], ensuring undownloaded items are never stored as dummy comics,
 * and deleting a local comic cascades cleanly to remove the link.
 */
@Entity(
    tableName = "remote_comic_links",
    indices = [
        Index(value = ["provider", "serverId", "userId", "itemId"], unique = true),
        Index(value = ["comicId"]),
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
data class RemoteComicLinkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val comicId: Long,
    val provider: String = "jellyfin",
    val serverId: String,
    val userId: String,
    val itemId: String,
    val parentId: String? = null,
    val downloadedAt: Long = System.currentTimeMillis(),
    val remoteVersionTag: String? = null,
    val remoteName: String? = null,
)
