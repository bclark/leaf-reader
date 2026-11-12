package net.digitalbc.leafreader.ui.library

import net.digitalbc.leafreader.data.jellyfin.*
import org.junit.Assert.*
import org.junit.Test

class CatalogueProjectionTest {
    private fun entry(id: Int) = JellyfinCatalogueEntry(
        JellyfinItem("$id", "Vol. $id", false, container = "cbz", seriesName = "Run"),
        "Book", false, seriesId = "run", volumeNumber = id)

    @Test fun tenThousandRowsPreserveOrderKeysAndDownloadedMembership() {
        val rows = (10000 downTo 1).map(::entry)
        val state = CatalogueState(accountScope = "server-user", library = JellyfinView("books", "Comics", "books"),
            view = JellyfinCatalogueView.SERIES, entries = rows)
        val projector = CatalogueProjector()
        val result = projector.prepare(state, setOf("1", "3"))
        assertEquals(10000, result.seriesGroups.single().entries.size)
        assertEquals("1", result.seriesGroups.single().entries.first().item.id)
        assertEquals("10000", result.seriesGroups.single().entries.last().item.id)
        assertEquals(listOf("1", "3"), result.downloadedSeriesGroups.single().entries.map { it.item.id })
        assertEquals(result.seriesGroups.single().key, result.downloadedSeriesGroups.single().key)
        val scalarUpdate = projector.prepare(state.copy(indexing = true, updatedAt = 123), setOf("1", "3"))
        assertSame(result.seriesGroups, scalarUpdate.seriesGroups)
        assertSame(result.downloadedSeriesGroups, scalarUpdate.downloadedSeriesGroups)
        val account = projector.prepare(state.copy(accountScope = "different-user"), emptySet())
        assertNotEquals(result.seriesGroups.single().key, account.seriesGroups.single().key)
    }

    @Test fun authDenialClearsPreparedRowsAndUnreadExcludesUnknownStatus() {
        val rows = listOf(entry(1), entry(2).copy(played = true), entry(3).copy(played = null))
        val projector = CatalogueProjector()
        val base = CatalogueState(entries = rows, view = JellyfinCatalogueView.UNREAD)
        assertEquals(listOf("1"), projector.prepare(base, emptySet()).entries.map { it.item.id })
        val denied = projector.prepare(base.copy(launchFailure = JellyfinFailureKind.ACCESS_DENIED), setOf("1"))
        assertTrue(denied.entries.isEmpty())
        assertTrue(denied.seriesGroups.isEmpty())
        assertTrue(denied.downloadedSeriesGroups.isEmpty())
    }

    @Test fun changedIndexReplacesFolderBackfillWithoutChangingQueryMembership() {
        val folder = JellyfinFolderIdentity("parent", "Verified", "books")
        val row = entry(1).copy(folder = null)
        val projector = CatalogueProjector()
        val base = CatalogueState(search = "Vol", entries = listOf(row), indexedEntries = listOf(row.copy(folder = folder)))
        assertEquals(folder, projector.prepare(base, emptySet()).entries.single().folder)
        val updated = projector.prepare(base.copy(indexedEntries = emptyList()), emptySet())
        assertNull(updated.entries.single().folder)
        assertEquals("1", updated.entries.single().item.id)
    }
}
