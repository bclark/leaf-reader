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
    @Test fun recentlyAddedUsesImportDateWithoutChangingDefaultOrIncludingDownloads() {
        val rows = listOf(
            comic(5, 0).copy(dateAdded = 0, lastOpened = 600),
            comic(4, 19).copy(dateAdded = 50, lastOpened = 100),
            comic(2, 0).copy(dateAdded = 50, lastOpened = 0, source = "smb"),
            comic(3, 0).copy(dateAdded = 90, lastOpened = 0, source = "jellyfin"),
            comic(1, 0).copy(dateAdded = 5, lastOpened = 900),
        )
        val input = LibraryProjectionInput(rows, emptyList(), emptyList(), "", LibraryFilter.ALL)
        fun ids(input: LibraryProjectionInput) =
            projectLibrary(input).filterIsInstance<LibraryItem.SingleComic>().map { it.comic.id }

        assertEquals(listOf(5L, 4L, 2L, 3L, 1L), ids(input))
        assertEquals(listOf(2L, 4L, 1L, 5L), ids(input.copy(recentlyAdded = true)))
        assertEquals(listOf(2L, 1L, 5L), ids(input.copy(recentlyAdded = true, unreadOnly = true)))
        assertEquals(listOf(1L), ids(input.copy(recentlyAdded = true, query = "Book 1")))
    }
}
