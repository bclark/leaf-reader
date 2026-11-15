package net.digitalbc.leafreader.data.jellyfin

import kotlin.test.*

class JellyfinCatalogueIndexTest {
    private fun entry(id: String) = JellyfinCatalogueEntry(JellyfinItem(id, id, false, container = "cbz"), "Book", null)
    private fun previous() = JellyfinCatalogueIndex(listOf(entry("retained"), entry("removed")), true, 2, 2, 5, 5)

    @Test fun partialOrInterruptedRefreshPreservesPreviousMembers() {
        val result = JellyfinIndexUpdate(previous()).append(JellyfinCataloguePage(listOf(entry("new")), 1, 2, true), 10)
        assertEquals(setOf("retained", "removed", "new"), result.entries.map { it.item.id }.toSet())
        assertFalse(result.complete)
        assertEquals(5L, result.lastCompletedAt)
    }

    @Test fun onlyCompleteFreshPassReconcilesRemovalsIncludingEmptyInventory() {
        val update = JellyfinIndexUpdate(previous())
        update.append(JellyfinCataloguePage(listOf(entry("retained")), 1, 2, true), 10)
        val completed = update.append(JellyfinCataloguePage(listOf(entry("new")), 2, 2, false), 11)
        assertEquals(listOf("retained", "new"), completed.entries.map { it.item.id })
        assertTrue(completed.complete)
        assertEquals(11L, completed.lastCompletedAt)
        assertTrue(JellyfinIndexUpdate(previous()).append(JellyfinCataloguePage(emptyList(), 0, 0, false), 12).entries.isEmpty())
    }

    @Test fun cachedChangedStalledAndRepeatedPagesCannotAuthorizeRemoval() {
        assertFailsWith<IllegalArgumentException> {
            JellyfinIndexUpdate(previous()).append(JellyfinCataloguePage(emptyList(), 0, 0, false, true), 10)
        }
        val update = JellyfinIndexUpdate(previous())
        update.append(JellyfinCataloguePage(listOf(entry("new")), 1, 3, true), 10)
        assertFailsWith<IllegalArgumentException> {
            update.append(JellyfinCataloguePage(listOf(entry("different")), 2, 4, true), 11)
        }
        assertFailsWith<IllegalArgumentException> {
            update.append(JellyfinCataloguePage(listOf(entry("new")), 2, 3, true), 11)
        }
        assertFailsWith<IllegalArgumentException> {
            update.append(JellyfinCataloguePage(emptyList(), 1, 3, true), 11)
        }
    }
}
