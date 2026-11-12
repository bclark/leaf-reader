package net.digitalbc.leafreader.data.jellyfin

import kotlin.test.*

class JellyfinCatalogueTest {
    @Test fun recursiveBooksUseScopedEncodedQueryAndPagination() {
        val url = JellyfinCatalogueQuery("library&other", search = "été & manga", startIndex = 100)
            .url("https://example.test/jellyfin/", "user")
        assertTrue(url.startsWith("https://example.test/jellyfin/Items?"))
        assertTrue("ParentId=library%26other" in url)
        assertTrue("Recursive=true" in url && "IncludeItemTypes=Book" in url)
        assertTrue("StartIndex=100" in url && "SearchTerm=%C3%A9t%C3%A9%20%26%20manga" in url)
        assertTrue("EnableUserData=true" in url)
        assertFalse("IsPlayed" in url)
        assertTrue("IsPlayed=false" in JellyfinCatalogueQuery("library", JellyfinCatalogueView.UNREAD).url("https://test", "user"))
        assertFailsWith<IllegalArgumentException> { JellyfinCatalogueQuery("library", limit = 0) }
    }

    @Test fun creatorQueriesUseScopedPersonIdentityInsteadOfTitleSearch() {
        val id = JellyfinCatalogueQuery("books", startIndex = 30,
            creator = JellyfinPerson("Same name", id = "person-id")).url("https://test/proxy", "u")
        assertTrue("PersonIds=person-id" in id && "ParentId=books" in id && "StartIndex=30" in id)
        assertFalse("SearchTerm" in id || "&Person=" in id)
        val name = JellyfinCatalogueQuery("books", creator = JellyfinPerson("A & B"))
            .url("https://test", "u")
        assertTrue("Person=A%20%26%20B" in name && "People" in name)
        assertFalse("SearchTerm" in name)
    }

    @Test fun collectionDiscoveryIsAccountWideButMembershipUsesCollectionParent() {
        val discovery = JellyfinCatalogueQuery("books", JellyfinCatalogueView.LISTS).url("https://test", "u")
        assertTrue("IncludeItemTypes=BoxSet" in discovery)
        assertFalse("&ParentId=" in discovery)
        val members = JellyfinCatalogueQuery("books", JellyfinCatalogueView.LISTS, "set")
            .url("https://test", "u")
        assertTrue("ParentId=set" in members && "IncludeItemTypes=Book" in members)
    }

    @Test fun missingPlayedStateIsNeverUnreadAndSeriesIsNotInferredFromTitle() {
        val item = JellyfinItem("one", "Series-looking title", false, container = "cbz")
        val unknown = JellyfinCatalogueEntry(item, "Book", null)
        val unread = JellyfinCatalogueEntry(item.copy(id = "two", seriesName = "Actual series"), "Book", false)
        val movie = JellyfinCatalogueEntry(item.copy(id = "three"), "Movie", false)
        assertEquals(listOf(unread), JellyfinCatalogueLogic.visible(listOf(unknown, unread, movie), JellyfinCatalogueView.UNREAD))
        val groups = JellyfinCatalogueLogic.series(listOf(unknown, unread))
        assertEquals(listOf(unknown), groups[null])
        assertEquals(listOf(unread), groups["Actual series"])
        assertEquals(listOf(unknown), JellyfinCatalogueLogic.visible(listOf(unknown, unknown), JellyfinCatalogueView.ALL))
    }
}
