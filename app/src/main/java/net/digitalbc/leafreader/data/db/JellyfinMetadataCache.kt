package net.digitalbc.leafreader.data.db

import androidx.room.*

@Entity(tableName = "jellyfin_metadata_cache", primaryKeys = ["serverId", "userId", "scope", "cacheKey"])
data class JellyfinMetadataCache(
    val serverId: String, val userId: String, val scope: String, val cacheKey: String, val payload: String,
)

@Dao
interface JellyfinMetadataCacheDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(value: JellyfinMetadataCache)
    @Query("SELECT payload FROM jellyfin_metadata_cache WHERE serverId=:server AND userId=:user AND scope=:scope AND cacheKey=:key")
    suspend fun get(server: String, user: String, scope: String, key: String): String?
}
