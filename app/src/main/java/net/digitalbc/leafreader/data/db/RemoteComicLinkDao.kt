package net.digitalbc.leafreader.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RemoteComicLinkDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(link: RemoteComicLinkEntity): Long

    @Query("SELECT * FROM remote_comic_links WHERE provider = :provider AND serverId = :serverId AND userId = :userId AND itemId = :itemId LIMIT 1")
    suspend fun findLink(provider: String, serverId: String, userId: String, itemId: String): RemoteComicLinkEntity?

    @Query("SELECT * FROM remote_comic_links WHERE comicId = :comicId LIMIT 1")
    suspend fun findByComicId(comicId: Long): RemoteComicLinkEntity?

    @Query("SELECT * FROM remote_comic_links WHERE provider = :provider AND serverId = :serverId AND userId = :userId")
    fun observeLinksForUser(provider: String, serverId: String, userId: String): Flow<List<RemoteComicLinkEntity>>

    @Query("SELECT * FROM remote_comic_links WHERE provider = :provider AND serverId = :serverId AND userId = :userId")
    suspend fun getLinksForUser(provider: String, serverId: String, userId: String): List<RemoteComicLinkEntity>

    @Query("DELETE FROM remote_comic_links WHERE provider = :provider AND serverId = :serverId AND userId = :userId AND itemId = :itemId")
    suspend fun delete(provider: String, serverId: String, userId: String, itemId: String)

    @Query("DELETE FROM remote_comic_links WHERE comicId = :comicId")
    suspend fun deleteByComicId(comicId: Long)
}
