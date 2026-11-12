package net.digitalbc.leafreader.ui.library

import net.digitalbc.leafreader.data.jellyfin.*

/** Confined to the serial mapLatest transform. Scalar loading/freshness changes reuse prepared rows. */
internal class CatalogueProjector {
    private var previous: CatalogueState? = null
    private var result: CatalogueState? = null
    private var downloads: Set<String> = emptySet()

    fun prepare(base: CatalogueState, downloadedIds: Set<String>): CatalogueState {
        val old = previous
        val reusable = old != null && old.entries === base.entries && old.indexedEntries === base.indexedEntries &&
            old.accountScope == base.accountScope && old.library?.id == base.library?.id &&
            old.view == base.view && old.usesIndex == base.usesIndex && old.launchFailure == base.launchFailure
        val prepared = if (reusable) result!! else prepareCatalogue(base)
        val downloaded = if (reusable && downloads == downloadedIds) prepared.downloadedSeriesGroups else
            prepared.seriesGroups.mapNotNull { group ->
                group.entries.filter { it.item.id in downloadedIds }.takeIf { it.isNotEmpty() }
                    ?.let { group.copy(entries = it) }
            }
        previous = base
        downloads = downloadedIds
        return base.copy(entries = prepared.entries, seriesGroups = prepared.seriesGroups,
            downloadedSeriesGroups = downloaded).also { result = it }
    }
}

/** Pure CPU work. MainCatalogueGrid only consumes prepared groups. */
private fun prepareCatalogue(base: CatalogueState): CatalogueState {
    if (base.launchFailure == JellyfinFailureKind.AUTHENTICATION ||
        base.launchFailure == JellyfinFailureKind.ACCESS_DENIED)
        return base.copy(entries = emptyList(), seriesGroups = emptyList(), downloadedSeriesGroups = emptyList())
    val entries = if (base.usesIndex) {
        if (base.view == JellyfinCatalogueView.UNREAD) base.entries.filter { it.played == false } else base.entries
    } else if (base.indexedEntries.isEmpty()) base.entries else {
        val known = base.indexedEntries.associateBy { it.item.id }
        base.entries.map { it.copy(folder = known[it.item.id]?.folder ?: it.folder) }
    }
    val groups = if (base.view == JellyfinCatalogueView.SERIES)
        JellyfinSeries.groups(base.accountScope, "", base.library?.id.orEmpty(), entries) else emptyList()
    return base.copy(entries = entries, seriesGroups = groups)
}
