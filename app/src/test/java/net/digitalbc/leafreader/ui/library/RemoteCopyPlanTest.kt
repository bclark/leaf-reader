package net.digitalbc.leafreader.ui.library

import net.digitalbc.leafreader.data.db.ComicEntity
import net.digitalbc.leafreader.data.jellyfin.*
import org.junit.Assert.*
import org.junit.Test

class RemoteCopyPlanTest {
    private fun selection(id: String, extension: String = "cbz") = RemoteListSelection(
        JellyfinCatalogueEntry(JellyfinItem(id, id, false, container = extension), "Book", false),
        JellyfinItemIdentity("server", "user", id))
    private fun copy(source: String) = ComicEntity(id = 1, title = "Retained", filePath = "retained.cbz",
        coverPath = null, format = "cbz", pageCount = 20, lastPage = 7, source = source,
        dateAdded = 0, lastOpened = 3)

    @Test fun mixedSelectionSeparatesDownloadsCopiesAndProtectedOriginals() {
        val local = copy("local")
        val retained = copy("jellyfin")
        val selected = listOf(selection("remote"), selection("saved"), selection("original"), selection("unsupported", "epub"))
        val plan = planRemoteCopies(selected, mapOf("saved" to retained, "original" to local))
        assertEquals(listOf("remote"), plan.downloads.map { it.identity.itemId })
        assertEquals(listOf("saved"), plan.removals.map { it.identity.itemId })
        assertEquals(2, plan.unaffected)
        assertEquals(7, retained.lastPage)
        assertEquals("local", local.source)
    }

    @Test fun staleCopyStateIsReplannedAndMismatchedOrDuplicateReferencesCannotInflateActions() {
        val selected = selection("one")
        val wrong = selection("two").copy(identity = JellyfinItemIdentity("server", "user", "other"))
        assertEquals(1, planRemoteCopies(listOf(selected, selected), emptyMap()).downloads.size)
        assertEquals(0, planRemoteCopies(listOf(selected), mapOf("one" to copy("jellyfin"))).downloads.size)
        assertTrue(planRemoteCopies(listOf(wrong), emptyMap()).downloads.isEmpty())
        assertEquals(1, planRemoteCopies(listOf(wrong), emptyMap()).unaffected)
    }
}
