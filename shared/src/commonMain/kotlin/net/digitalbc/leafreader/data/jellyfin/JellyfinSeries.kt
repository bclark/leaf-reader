package net.digitalbc.leafreader.data.jellyfin

import net.digitalbc.leafreader.data.archive.NaturalOrderComparator

data class JellyfinSeriesGroup(
    val key: String,
    val name: String,
    val entries: List<JellyfinCatalogueEntry>,
)

/** Conservative run identity: never infer a series from titles or directory names. */
object JellyfinSeries {
    fun key(server: String, user: String, library: String, entry: JellyfinCatalogueEntry): String? {
        val name = entry.item.seriesName?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val run = entry.seriesId?.takeIf { it.isNotBlank() }?.let { "id:$it" }
            ?: entry.volumeNumber?.let { "metadata:$name:volume:$it" }
            ?: return null
        return listOf(server, user, library, run).joinToString(":") { "${it.length}:$it" }
    }

    val order: Comparator<JellyfinCatalogueEntry> = Comparator { a, b ->
        val volume = compareValues(a.volumeNumber ?: Int.MAX_VALUE, b.volumeNumber ?: Int.MAX_VALUE)
        if (volume != 0) volume else {
            val issue = compareValues(a.issueNumber?.takeIf { it.isFinite() } ?: Double.POSITIVE_INFINITY,
                b.issueNumber?.takeIf { it.isFinite() } ?: Double.POSITIVE_INFINITY)
            if (issue != 0) issue else {
                val title = NaturalOrderComparator.compare(a.item.name, b.item.name)
                if (title != 0) title else a.item.id.compareTo(b.item.id)
            }
        }
    }

    fun groups(server: String, user: String, library: String, entries: List<JellyfinCatalogueEntry>): List<JellyfinSeriesGroup> =
        groupsWithFolders(server, user, library, entries, entries.mapNotNull { entry ->
            entry.folder?.takeIf { it.libraryId == library }?.let { it.id to it.name }
        }.toMap())

    /** Callers supply only folders whose ancestry was verified in this library/account. */
    fun groupsWithFolders(server: String, user: String, library: String,
                         entries: List<JellyfinCatalogueEntry>, folders: Map<String, String>): List<JellyfinSeriesGroup> {
        fun folder(entry: JellyfinCatalogueEntry): String? = entry.item.parentId
            ?.takeIf { it != library && folders[it]?.isNotBlank() == true }
        fun groupKey(entry: JellyfinCatalogueEntry): String? {
            val metadata = key(server, user, library, entry)
            if (!entry.seriesId.isNullOrBlank() && metadata != null) return metadata
            // A verified run folder keeps collected volumes together; a book's volume number alone
            // cannot distinguish a collected-volume sequence from separate series runs.
            return folder(entry)?.let { id ->
                listOf(server, user, library, "folder:$id").joinToString(":") { "${it.length}:$it" }
            } ?: metadata
        }
        return entries.distinctBy { it.item.id }.groupBy(::groupKey).map { (groupKey, values) ->
            val first = values.first()
            val name = when {
                groupKey == null -> "Unsorted"
                groupKey == key(server, user, library, first) -> first.item.seriesName!!.trim()
                else -> folders[folder(first)]!!
            }
            JellyfinSeriesGroup(groupKey ?: ("unsorted:" + listOf(server, user, library).joinToString(":") { "${it.length}:$it" }),
                name, values.sortedWith(order))
        }.sortedWith(compareBy<JellyfinSeriesGroup> { it.key.startsWith("unsorted:") }.thenBy { it.name.lowercase() })
    }
}
