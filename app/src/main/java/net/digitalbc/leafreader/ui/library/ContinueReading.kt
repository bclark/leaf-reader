package net.digitalbc.leafreader.ui.library

import net.digitalbc.leafreader.data.db.ComicEntity
import net.digitalbc.leafreader.data.jellyfin.JellyfinCatalogueEntry
import net.digitalbc.leafreader.data.jellyfin.JellyfinProgress
import java.time.Instant

internal data class ContinueReading(
    val key: String,
    val title: String,
    val page: Int,
    val total: Int?,
    val fraction: Float?,
    val opened: Long,
    val local: ComicEntity? = null,
    val remote: JellyfinCatalogueEntry? = null,
)

// Browsing the saved index is also used for normal connected tab changes; it is not
// evidence that the server is unreachable. Preserve the independent launch/offline result.
internal val CatalogueState.canContinueRemote: Boolean
    get() = loaded && launchFailure == null && !offline

/** Inputs are unfiltered, readable local copies and the current account's catalogue. */
internal fun continueReading(
    readable: List<ComicEntity>, remote: List<JellyfinCatalogueEntry>,
    downloaded: Map<String, ComicEntity>, remoteReachable: Boolean,
    excludedRemoteIds: Set<String> = emptySet(),
): List<ContinueReading> {
    val local = readable.filter { it.pageCount > 0 && !it.isCompleted &&
        (it.lastPage > 0 || it.lastOpened > it.dateAdded) }.map {
        ContinueReading("local:${it.id}", it.title, (it.lastPage + 1).coerceIn(1, it.pageCount),
            it.pageCount, (it.lastPage + 1).toFloat().div(it.pageCount).coerceIn(0f, 1f), it.lastOpened, local = it)
    }
    val server = if (!remoteReachable) emptyList() else remote.distinctBy { it.item.id }.mapNotNull { entry ->
        // A linked local copy owns its progress, including completion and pending offline writes.
        if (!entry.type.equals("Book", true) || entry.item.id in downloaded || entry.item.id in excludedRemoteIds ||
            !entry.item.isImportable || entry.played != false || entry.item.playbackPositionTicks <= 0L) return@mapNotNull null
        val opened = runCatching { Instant.parse(entry.lastPlayedDate).toEpochMilli() }.getOrNull() ?: return@mapNotNull null
        val percent = entry.playedPercentage?.takeIf { it.isFinite() && it in 0.0..100.0 }
        if (percent == 100.0) return@mapNotNull null
        ContinueReading("remote:${entry.item.id}", entry.item.name,
            JellyfinProgress.ticksToPage(entry.item.playbackPositionTicks).let { if (it == Int.MAX_VALUE) it else it + 1 },
            null, percent?.div(100)?.toFloat(), opened, remote = entry)
    }
    return (local + server).sortedWith(compareByDescending<ContinueReading> { it.opened }.thenBy { it.key }).take(5)
}
