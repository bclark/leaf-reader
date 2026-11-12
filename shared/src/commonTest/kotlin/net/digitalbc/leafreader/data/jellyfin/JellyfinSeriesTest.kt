package net.digitalbc.leafreader.data.jellyfin

import kotlin.test.*

class JellyfinSeriesTest {
    private fun entry(id: String, title: String = id, run: String? = "run", volume: Int? = null, issue: Double? = null) =
        JellyfinCatalogueEntry(JellyfinItem(id, title, false, container = "cbz", seriesName = "Same name"),
            "Book", null, run, volume, issue)

    @Test fun runAndScopeIdentityNeverMergeByDisplayName() {
        val first = entry("a")
        val second = entry("b", run = "other")
        assertEquals(2, JellyfinSeries.groups("s", "u", "library", listOf(first, second)).size)
        val key = JellyfinSeries.key("s", "u", "library", first)
        assertNotEquals(key, JellyfinSeries.key("s", "other", "library", first))
        assertNotEquals(key, JellyfinSeries.key("s", "u", "other", first))
        assertNotEquals(key, JellyfinSeries.key("other", "u", "library", first))
    }

    @Test fun uncertainSeriesStayUnsortedDespiteMatchingNamesAndTitlePrefixes() {
        val groups = JellyfinSeries.groups("s", "u", "library",
            listOf(entry("a", "Same name 01", run = null), entry("b", "Same name 02", run = null)))
        assertEquals(listOf("Unsorted"), groups.map { it.name })
        assertEquals(2, groups.single().entries.size)
    }

    @Test fun volumeIssueThenNaturalTitleOrderIsIndependentOfPagination() {
        val pages = listOf(entry("ten", "Issue 10", issue = 10.0), entry("two", "Issue 2", issue = 2.0),
            entry("fallback10", "Extra 10"), entry("fallback2", "Extra 2"), entry("two", issue = 2.0))
        val group = JellyfinSeries.groups("s", "u", "library", pages).single()
        assertEquals(listOf("two", "ten", "fallback2", "fallback10"), group.entries.map { it.item.id })
        assertEquals(group.key, JellyfinSeries.groups("s", "u", "library", pages.take(2)).single().key)
    }

    @Test fun explicitVolumesDistinguishRunsWhenServerHasNoSeriesId() {
        assertEquals(2, JellyfinSeries.groups("s", "u", "library",
            listOf(entry("one", run = null, volume = 1), entry("two", run = null, volume = 2))).size)
    }
    @Test fun persistedVerifiedFolderKeepsGroupsAcrossOfflineFilteringAndRejectsAnotherLibrary() {
        val folder = JellyfinFolderIdentity("run", "B.P.R.D. – Hell on Earth", "books")
        val entries = listOf("1", "3").map { id ->
            JellyfinCatalogueEntry(JellyfinItem(id, "Vol. $id", false, parentId = folder.id),
                "Book", null, folder = folder)
        }
        val online = JellyfinSeries.groups("server", "account", "books", entries).single()
        val downloaded = JellyfinSeries.groups("server", "account", "books", entries.take(1)).single()
        assertEquals(online.key, downloaded.key)
        assertEquals(folder.name, online.name)
        assertEquals("Unsorted", JellyfinSeries.groups("server", "account", "other", entries).single().name)
        assertNotEquals(online.key, JellyfinSeries.groups("server", "another", "books", entries).single().key)
    }

    @Test fun collectedVolumeNumbersDoNotSplitAVerifiedRunFolder() {
        val folder = JellyfinFolderIdentity("run", "Hellboy in Hell", "books")
        val volumes = listOf(1, 2, 10).map { number ->
            JellyfinCatalogueEntry(JellyfinItem("$number", "Vol. $number", false,
                parentId = folder.id, seriesName = "Hellboy in Hell"), "Book", null,
                volumeNumber = number, folder = folder)
        }
        val group = JellyfinSeries.groups("s", "u", "books", volumes).single()
        assertEquals(folder.name, group.name)
        assertEquals(listOf("1", "2", "10"), group.entries.map { it.item.id })
        val namedRun = volumes.map { it.copy(seriesId = "stable-metadata-run") }
        assertNotEquals(group.key, JellyfinSeries.groups("s", "u", "books", namedRun).single().key)
    }

    @Test fun verifiedFoldersKeepIdentityAcrossAvailabilityAndNames() {
        fun book(id: String, parent: String) = JellyfinCatalogueEntry(
            JellyfinItem(id, "Volume $id", false, parentId = parent), "Book", null)
        val entries = listOf(book("10", "run-a"), book("2", "run-a"), book("3", "run-b"), book("4", "library"))
        val folders = mapOf("run-a" to "Hellboy", "run-b" to "Hellboy", "library" to "All Comics")
        val groups = JellyfinSeries.groupsWithFolders("s", "u", "library", entries, folders)
        assertEquals(3, groups.size)
        assertEquals(listOf("2", "10"), groups.first().entries.map { it.item.id })
        val offline = JellyfinSeries.groupsWithFolders("s", "u", "library", entries.take(1), folders).single()
        assertEquals(groups.first().key, offline.key)
        assertEquals(offline.key, JellyfinSeries.groupsWithFolders("s", "u", "library", entries.take(1),
            mapOf("run-a" to "Renamed")).single().key)
        assertEquals("Unsorted", groups.last().name)
        assertEquals("Unsorted", JellyfinSeries.groupsWithFolders("s", "u", "library", entries, emptyMap()).single().name)
    }
}
