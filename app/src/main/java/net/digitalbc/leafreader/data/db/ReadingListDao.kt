package net.digitalbc.leafreader.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface ReadingListDao {

    @Query("SELECT * FROM reading_lists ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<ReadingListEntity>>

    @Query("SELECT * FROM reading_lists")
    suspend fun getAllLists(): List<ReadingListEntity>

    @Query("SELECT * FROM reading_list_items")
    fun observeAllItems(): Flow<List<ReadingListItemEntity>>

    @Query("SELECT * FROM reading_list_items")
    suspend fun getAllItems(): List<ReadingListItemEntity>

    @Query("SELECT * FROM reading_lists WHERE id = :id")
    fun observeById(id: Long): Flow<ReadingListEntity?>

    @Query("SELECT * FROM reading_lists WHERE id = :id")
    suspend fun getById(id: Long): ReadingListEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertList(list: ReadingListEntity): Long

    @Query("UPDATE reading_lists SET name = :name, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateName(id: Long, name: String, updatedAt: Long = System.currentTimeMillis()): Int

    @Query("DELETE FROM reading_lists WHERE id = :id")
    suspend fun deleteList(id: Long): Int

    @Query("""
        SELECT c.* FROM comics c
        INNER JOIN reading_list_items rli ON c.id = rli.comicId
        WHERE rli.listId = :listId
        ORDER BY rli.sortOrder ASC
    """)
    fun observeComicsForList(listId: Long): Flow<List<ComicEntity>>

    @Query("SELECT COUNT(*) FROM reading_list_items WHERE listId = :listId")
    fun observeItemCount(listId: Long): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: ReadingListItemEntity)

    @Query("UPDATE reading_list_items SET sortOrder = :sortOrder WHERE listId = :listId AND comicId = :comicId")
    suspend fun updateItemOrder(listId: Long, comicId: Long, sortOrder: Int): Int

    @Transaction
    suspend fun reorderItems(listId: Long, comicIdsInOrder: List<Long>) {
        comicIdsInOrder.forEachIndexed { index, comicId ->
            updateItemOrder(listId, comicId, index)
        }
    }

    @Query("DELETE FROM reading_list_items WHERE listId = :listId AND comicId = :comicId")
    suspend fun removeItem(listId: Long, comicId: Long): Int

    @Query("DELETE FROM reading_list_items WHERE listId = :listId")
    suspend fun clearItems(listId: Long): Int
}
