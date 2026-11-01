package com.chakra.comicreader.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A user-curated reading list / playlist. Comics can belong to multiple reading lists.
 */
@Entity(tableName = "reading_lists")
data class ReadingListEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

/**
 * Junction table mapping comics to reading lists with an explicit [sortOrder].
 */
@Entity(
    tableName = "reading_list_items",
    primaryKeys = ["listId", "comicId"],
)
data class ReadingListItemEntity(
    val listId: Long,
    val comicId: Long,
    val sortOrder: Int,
    val addedAt: Long = System.currentTimeMillis(),
)
