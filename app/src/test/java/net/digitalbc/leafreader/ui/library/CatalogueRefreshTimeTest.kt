package net.digitalbc.leafreader.ui.library

import net.digitalbc.leafreader.data.jellyfin.JellyfinView
import org.junit.Assert.assertEquals
import org.junit.Test

class CatalogueRefreshTimeTest {
    @Test fun settingsOnlyShowsTheSelectedConnectionsSavedLibraryDate() {
        val saved = CatalogueState(library = JellyfinView("books", "Comics", "books"),
            accountScope = "server/user", updatedAt = 1234L)
        assertEquals(1234L, saved.refreshTimeFor("server/user"))
        assertEquals(0L, saved.refreshTimeFor("other-server/user"))
        assertEquals(0L, saved.refreshTimeFor("server/other-user"))
        assertEquals(0L, saved.refreshTimeFor(""))
        assertEquals(0L, saved.copy(accountScope = "").refreshTimeFor(""))
        assertEquals(0L, saved.copy(library = null).refreshTimeFor("server/user"))
        assertEquals(0L, saved.copy(updatedAt = 0).refreshTimeFor("server/user"))
    }
}
