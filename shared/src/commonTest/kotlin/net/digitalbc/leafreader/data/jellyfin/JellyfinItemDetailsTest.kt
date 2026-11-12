package net.digitalbc.leafreader.data.jellyfin

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class JellyfinItemDetailsTest {
    @Test
    fun identityIncludesServerAccountAndItemWithoutUsingTheTitle() {
        val identity = JellyfinItemIdentity("server", "user", "item")
        assertEquals(identity, JellyfinItemIdentity("server", "user", "item"))
        assertNotEquals(identity, identity.copy(serverId = "other"))
        assertNotEquals(identity, identity.copy(userId = "other"))
        assertNotEquals(identity, identity.copy(itemId = "other"))
        assertFailsWith<IllegalArgumentException> { identity.copy(userId = "") }
    }

    @Test
    fun missingMetadataStaysAbsentAndDetailsCannotReferenceAnotherItem() {
        val identity = JellyfinItemIdentity("server", "user", "item")
        val item = JellyfinItem("item", "Dotted title - Vol. 01", false, container = "cbz")
        val details = JellyfinItemDetails(identity, item)
        assertNull(details.overview)
        assertNull(details.indexNumber)
        assertNull(details.playedPercentage)
        assertEquals(emptyList(), details.people)
        assertFailsWith<IllegalArgumentException> {
            JellyfinItemDetails(identity.copy(itemId = "different"), item)
        }
    }

    @Test
    fun creditedNamesMatchExactlyAndDistinctPersonIdsStaySeparate() {
        kotlin.test.assertTrue(JellyfinPerson("  A.   Example ").matches(JellyfinPerson("a. example")))
        kotlin.test.assertFalse(JellyfinPerson("A. Example").matches(JellyfinPerson("A. Examples")))
        kotlin.test.assertFalse(JellyfinPerson("Same", id = "one").matches(JellyfinPerson("Same", id = "two")))
        kotlin.test.assertTrue(JellyfinPerson("Old name", id = "one").matches(JellyfinPerson("New name", id = "one")))
    }

    @Test
    fun detailEndpointPreservesReverseProxyPathAndExplicitAccount() {
        assertEquals(
            "https://example.test/jellyfin/Items/item?UserId=user",
            JellyfinEndpoints.itemDetails("https://example.test/jellyfin/", "user", "item"),
        )
    }
}
