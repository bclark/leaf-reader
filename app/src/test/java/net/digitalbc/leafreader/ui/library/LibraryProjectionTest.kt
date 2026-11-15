package net.digitalbc.leafreader.ui.library

import net.digitalbc.leafreader.data.db.ComicEntity
import net.digitalbc.leafreader.data.db.ReadingListEntity
import org.junit.Assert.*
import org.junit.Test

class LibraryProjectionTest {
    private fun comic(id: Long, page: Int, series: String? = null) = ComicEntity(
        id = id, title = "Book $id", filePath = "comic-$id", coverPath = null, format = "cbz",
        pageCount = 20, lastPage = page, series = series, dateAdded = 0, lastOpened = 0)

    @Test fun offlineRequiresReadableCopyAndUnreadRemainsIndependentAcrossViews() {
        val input = LibraryProjectionInput(listOf(comic(1, 0), comic(2, 5), comic(3, 0)),
            emptyList(), emptyList(), "", LibraryFilter.OFFLINE, readableIds = setOf(1, 2))
        assertEquals(listOf(1L, 2L), projectLibrary(input).map { (it as LibraryItem.SingleComic).comic.id })
        assertEquals(listOf(1L), projectLibrary(input.copy(unreadOnly = true))
            .map { (it as LibraryItem.SingleComic).comic.id })
        assertEquals(listOf(1L, 3L), projectLibrary(input.copy(filter = LibraryFilter.ALL, unreadOnly = true))
            .map { (it as LibraryItem.SingleComic).comic.id })
        assertTrue(projectLibrary(input.copy(query = "Book 3")).isEmpty())
    }

    @Test fun unreadKeepsSeriesIdentityAndEmptyListsAccessible() {
        val input = LibraryProjectionInput(listOf(comic(1, 0, "Run"), comic(2, 19, "Run")),
            listOf(ReadingListEntity(1, "Empty list")), emptyList(), "", LibraryFilter.ALL, unreadOnly = true)
        val rows = projectLibrary(input)
        val series = rows.filterIsInstance<LibraryItem.SeriesStack>().single()
        assertEquals("Run", series.seriesName)
        assertEquals(listOf(1L), series.issues.map { it.id })
        assertEquals(0, rows.filterIsInstance<LibraryItem.ReadingListItem>().single().itemCount)
        assertEquals(series, projectLibrary(input.copy(filter = LibraryFilter.SERIES)).single())
        assertEquals(1, projectLibrary(input.copy(filter = LibraryFilter.READING_LISTS)).size)
    }
}
