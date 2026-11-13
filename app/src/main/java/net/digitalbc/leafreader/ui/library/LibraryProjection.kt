package net.digitalbc.leafreader.ui.library

import net.digitalbc.leafreader.data.db.ComicEntity
import net.digitalbc.leafreader.data.db.ReadingListEntity
import net.digitalbc.leafreader.data.db.ReadingListItemEntity

internal data class LibraryProjectionInput(
    val allComics: List<ComicEntity>, val lists: List<ReadingListEntity>,
    val allListItems: List<ReadingListItemEntity>, val query: String, val filter: LibraryFilter,
    val unreadOnly: Boolean = false, val readableIds: Set<Long> = emptySet(),
)

/** Pure row preparation, executed on Default by LibraryViewModel, never by a lazy item. */
internal fun projectLibrary(input: LibraryProjectionInput): List<LibraryItem> = with(input) {
        val filteredComics = if (query.isBlank()) {
            allComics
        } else {
            val q = query.trim()
            allComics.filter {
                it.title.contains(q, ignoreCase = true) ||
                (it.series?.contains(q, ignoreCase = true) == true)
            }
        }

        val itemsByList = allListItems.groupBy { it.listId }
        val readingListCards = if (filter == LibraryFilter.ALL || filter == LibraryFilter.READING_LISTS) {
            val filteredLists = if (query.isBlank()) {
                lists
            } else {
                lists.filter { it.name.contains(query.trim(), ignoreCase = true) }
            }
            val comicMap = allComics.associateBy { it.id }
            filteredLists.map { list ->
                val listItems = itemsByList[list.id].orEmpty().sortedBy { it.sortOrder }
                val listComics = listItems.mapNotNull { comicMap[it.comicId] }
                LibraryItem.ReadingListItem(
                    readingList = list,
                    previewComics = listComics.take(4),
                    itemCount = listItems.size,
                )
            }
        } else emptyList()

        val projected = when (filter) {
            LibraryFilter.ALL -> {
                val seriesGroups = filteredComics
                    .filter { !it.series.isNullOrBlank() }
                    .groupBy { "${it.series}__${it.volume.orEmpty()}".lowercase() }

                val handledComicIds = mutableSetOf<Long>()
                val items = mutableListOf<LibraryItem>()

                for (comic in filteredComics) {
                    if (comic.id in handledComicIds) continue

                    val seriesKey = if (!comic.series.isNullOrBlank()) {
                        "${comic.series}__${comic.volume.orEmpty()}".lowercase()
                    } else null

                    val group = if (seriesKey != null) seriesGroups[seriesKey] else null
                    if (group != null && group.size >= 2) {
                        val sortedIssues = group.sortedWith(
                            compareBy<ComicEntity> { it.issueNumber ?: Float.MAX_VALUE }
                                .thenBy { it.title }
                        )
                        val nextIssue = sortedIssues.firstOrNull { it.pageCount > 0 && it.lastPage < it.pageCount - 1 }
                            ?: sortedIssues.first()
                        val completedCount = sortedIssues.count { it.pageCount > 0 && it.lastPage >= it.pageCount - 1 }
                        val unreadCount = sortedIssues.count { it.lastPage == 0 }

                        items.add(
                            LibraryItem.SeriesStack(
                                seriesName = group.first().series ?: "Series",
                                volume = group.first().volume,
                                issues = sortedIssues,
                                topCoverComic = nextIssue,
                                totalIssues = sortedIssues.size,
                                unreadIssues = unreadCount,
                                progressFraction = if (sortedIssues.isNotEmpty()) completedCount.toFloat() / sortedIssues.size else 0f,
                            )
                        )
                        for (c in group) handledComicIds.add(c.id)
                    } else {
                        items.add(LibraryItem.SingleComic(comic))
                        handledComicIds.add(comic.id)
                    }
                }
                items + readingListCards
            }
            LibraryFilter.SERIES -> {
                val seriesGroups = filteredComics
                    .filter { !it.series.isNullOrBlank() }
                    .groupBy { "${it.series}__${it.volume.orEmpty()}".lowercase() }

                seriesGroups.values.map { group ->
                    val sortedIssues = group.sortedWith(
                        compareBy<ComicEntity> { it.issueNumber ?: Float.MAX_VALUE }
                            .thenBy { it.title }
                    )
                    val nextIssue = sortedIssues.firstOrNull { it.pageCount > 0 && it.lastPage < it.pageCount - 1 }
                        ?: sortedIssues.first()
                    val completedCount = sortedIssues.count { it.pageCount > 0 && it.lastPage >= it.pageCount - 1 }
                    val unreadCount = sortedIssues.count { it.lastPage == 0 }

                    LibraryItem.SeriesStack(
                        seriesName = group.first().series ?: "Series",
                        volume = group.first().volume,
                        issues = sortedIssues,
                        topCoverComic = nextIssue,
                        totalIssues = sortedIssues.size,
                        unreadIssues = unreadCount,
                        progressFraction = if (sortedIssues.isNotEmpty()) completedCount.toFloat() / sortedIssues.size else 0f,
                    )
                }.sortedBy { it.seriesName.lowercase() }
            }
            LibraryFilter.READING_LISTS -> readingListCards
            LibraryFilter.OFFLINE -> filteredComics.filter { it.id in readableIds }
                .map { LibraryItem.SingleComic(it) }
        }
        if (!unreadOnly) projected else projected.mapNotNull { item ->
            when (item) {
                is LibraryItem.SingleComic -> item.takeIf { it.comic.lastPage == 0 }
                is LibraryItem.SeriesStack -> item.issues.filter { it.lastPage == 0 }
                    .takeIf { it.isNotEmpty() }?.let { issues ->
                        item.copy(issues = issues, topCoverComic = issues.first(),
                            totalIssues = issues.size, unreadIssues = issues.size)
                    }
                is LibraryItem.ReadingListItem -> item
            }
        }

}
