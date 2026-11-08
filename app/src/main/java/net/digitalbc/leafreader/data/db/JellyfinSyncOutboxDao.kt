package net.digitalbc.leafreader.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface JellyfinSyncOutboxDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: JellyfinSyncOutboxEntity): Long

    @Query("SELECT * FROM jellyfin_sync_outbox WHERE serverId = :serverId AND userId = :userId ORDER BY updatedAt ASC")
    suspend fun getPendingForUser(serverId: String, userId: String): List<JellyfinSyncOutboxEntity>

    @Query("SELECT * FROM jellyfin_sync_outbox WHERE comicId = :comicId LIMIT 1")
    suspend fun getPendingForComic(comicId: Long): JellyfinSyncOutboxEntity?

    @Query("DELETE FROM jellyfin_sync_outbox WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM jellyfin_sync_outbox WHERE comicId = :comicId")
    suspend fun deleteByComicId(comicId: Long)

    @Query("DELETE FROM jellyfin_sync_outbox WHERE serverId = :serverId AND userId = :userId")
    suspend fun clearForUser(serverId: String, userId: String)

    @Query("SELECT COUNT(*) FROM jellyfin_sync_outbox WHERE serverId = :serverId AND userId = :userId")
    suspend fun countPendingForUser(serverId: String, userId: String): Int
}
