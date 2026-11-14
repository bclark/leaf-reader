package net.digitalbc.leafreader.ui.library

import net.digitalbc.leafreader.data.db.ComicEntity
import net.digitalbc.leafreader.data.jellyfin.*
import org.junit.Assert.*
import org.junit.Test

class ContinueReadingTest {
    private fun comic(id: Long, page: Int = 2, opened: Long = id * 100, completed: Boolean? = null) =
        ComicEntity(id, "Comic $id", "/private/$id.cbz", null, "cbz", 10,
            lastPage = page, dateAdded = 0, lastOpened = opened, completionOverride = completed)
    private fun remote(id: String = "remote", date: String? = "2026-11-14T10:00:00Z", played: Boolean? = false) =
        JellyfinCatalogueEntry(JellyfinItem(id, "Remote", false, "cbz", playbackPositionTicks = JellyfinProgress.pageToTicks(4)),
            "Book", played, lastPlayedDate = date, playedPercentage = 40.0)

    @Test fun mostRecentUnfinishedCopiesWinAndCompletedUnreadImportsAreAbsent() {
        val rows = listOf(comic(1), comic(2, page = 9), comic(3, completed = true), comic(4, page = 0, opened = 0), comic(5))
        val candidates = continueReading(rows, emptyList(), emptyMap(), false)
        assertEquals(listOf("local:5", "local:1"), candidates.map { it.key })
        assertEquals(3, candidates.first().page)
        assertEquals(10, candidates.first().total)
        assertEquals(0.3f, candidates.first().fraction!!, 0.0001f)
    }

    @Test fun savedIndexTabNavigationDoesNotPretendAConnectedServerIsOffline() {
        assertTrue(CatalogueState(loaded = true, browsingSaved = true).canContinueRemote)
        assertFalse(CatalogueState(loaded = true, browsingSaved = true, offline = true).canContinueRemote)
        assertFalse(CatalogueState(loaded = true, launchFailure = JellyfinFailureKind.UNAVAILABLE).canContinueRemote)
        assertFalse(CatalogueState(loaded = true, launchFailure = JellyfinFailureKind.AUTHENTICATION).canContinueRemote)
        assertFalse(CatalogueState(loading = true).canContinueRemote)
    }

    @Test fun capAtFiveAndOpeningPageOneCountsAsStarted() {
        val candidates = continueReading((1L..8L).map { comic(it, page = 0) }, emptyList(), emptyMap(), false)
        assertEquals(listOf(8L,7L,6L,5L,4L), candidates.map { it.local!!.id })
    }

    @Test fun reachableServerOnlyProgressUsesServerRecencyWithoutInventingPageTotal() {
        val candidates = continueReading(listOf(comic(1)), listOf(remote(), remote()), emptyMap(), true)
        assertEquals(listOf("remote:remote", "local:1"), candidates.map { it.key })
        assertEquals(5, candidates.first().page)
        assertNull(candidates.first().total)
        assertEquals(0.4f, candidates.first().fraction!!, 0.0001f)
        assertTrue(continueReading(emptyList(), listOf(remote()), emptyMap(), false).isEmpty())
    }

    @Test fun pendingOrCompletedLocalCopyOwnsProgressInsteadOfDuplicatingServerState() {
        val completed = comic(1, completed = true)
        assertTrue(continueReading(listOf(completed), listOf(remote()), mapOf("remote" to completed), true).isEmpty())
        val pending = comic(2)
        assertEquals(listOf("local:2"), continueReading(listOf(pending), listOf(remote()), mapOf("remote" to pending), true).map { it.key })
        // Unreadable linked copies are omitted rather than retried as remote downloads.
        assertTrue(continueReading(emptyList(), listOf(remote()), mapOf("remote" to pending), true).isEmpty())
    }

    @Test fun missingUnknownCompletedUnsupportedAndUnorderedServerItemsAreExcluded() {
        val entries = listOf(remote("missing"), remote("unknown", played = null), remote("completed", played = true),
            remote("undated", date = null), remote("invalid", date = "bad"),
            remote("finished").copy(playedPercentage = 100.0), remote("collection").copy(type = "BoxSet"),
            remote("epub").let { it.copy(item = it.item.copy(container = "epub")) })
        assertTrue(continueReading(emptyList(), entries, emptyMap(), true, setOf("missing")).isEmpty())
    }
}
