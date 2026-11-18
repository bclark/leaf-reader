package net.digitalbc.leafreader.ui.library

import net.digitalbc.leafreader.data.db.ComicEntity

internal data class RemoteCopyPlan(
    val downloads: List<RemoteListSelection>,
    val removals: List<RemoteListSelection>,
    val unaffected: Int,
)

/** A retained original can satisfy reading without being eligible for download removal. */
internal fun planRemoteCopies(
    selected: Collection<RemoteListSelection>, copies: Map<String, ComicEntity>,
): RemoteCopyPlan {
    val unique = selected.distinctBy { it.identity }
    val valid = unique.filter { it.entry.item.id == it.identity.itemId && it.entry.item.isImportable }
    val downloads = valid.filter { copies[it.identity.itemId] == null }
    val removals = valid.filter { copies[it.identity.itemId]?.source == "jellyfin" }
    return RemoteCopyPlan(downloads, removals, unique.size - downloads.size - removals.size)
}
