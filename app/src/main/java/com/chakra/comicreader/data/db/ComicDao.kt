package com.chakra.comicreader.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ComicDao {

    @Query("SELECT * FROM comics ORDER BY lastOpened DESC")
    fun observeAll(): Flow<List<ComicEntity>>

    @Query("SELECT * FROM comics WHERE id = :id")
    suspend fun getById(id: Long): ComicEntity?

    @Insert
    suspend fun insert(comic: ComicEntity): Long

    @Query("UPDATE comics SET lastPage = :page, lastSlot = :slot, lastOpened = :openedAt WHERE id = :id")
    suspend fun updateProgress(id: Long, page: Int, slot: Int, openedAt: Long): Int

    @Query("UPDATE comics SET rightToLeft = :rtl WHERE id = :id")
    suspend fun updateReadingDirection(id: Long, rtl: Boolean): Int

    @Query("UPDATE comics SET series = :series, volume = :volume, issueNumber = :issueNumber WHERE id = :id")
    suspend fun updateMetadata(id: Long, series: String?, volume: String?, issueNumber: Float?)

    @Query("SELECT * FROM comics WHERE series IS NULL")
    suspend fun getComicsWithoutSeries(): List<ComicEntity>

    @Query("SELECT * FROM comics WHERE series = :series AND (volume = :volume OR (:volume IS NULL AND volume IS NULL)) ORDER BY issueNumber ASC, title ASC")
    fun observeBySeries(series: String, volume: String?): Flow<List<ComicEntity>>

    @Query("DELETE FROM comics WHERE id = :id")
    suspend fun delete(id: Long): Int
}
