package net.digitalbc.leafreader.data.jellyfin

/** A server observation, never an optimistic copy of pending device progress. */
data class JellyfinProgressUpdate(
    val identity: JellyfinItemIdentity,
    val serverUrl: String,
    val snapshot: JellyfinProgressSnapshot,
    internal val generation: Long,
) {
    init { require(snapshot.isAvailable) }

    fun apply(item: JellyfinItem): JellyfinItem = if (item.id != identity.itemId) item else
        item.copy(playbackPositionTicks = checkNotNull(snapshot.positionTicks), played = checkNotNull(snapshot.played))

    fun apply(details: JellyfinItemDetails): JellyfinItemDetails =
        if (details.identity != identity) details else
            details.copy(item = apply(details.item), playedPercentage = snapshot.playedPercentage)

    fun apply(entries: List<JellyfinCatalogueEntry>): List<JellyfinCatalogueEntry> =
        if (entries.none { it.item.id == identity.itemId }) entries else entries.map {
            if (it.item.id == identity.itemId) it.copy(item = apply(it.item), played = snapshot.played) else it
        }
}

data class JellyfinProgressSnapshot(val positionTicks: Long?, val played: Boolean?, val playedPercentage: Double? = null) {
    val isAvailable: Boolean get() = positionTicks != null && positionTicks >= 0 && played != null
}

data class JellyfinProgressConflict(
    val comicId: Long,
    val title: String,
    val identity: JellyfinItemIdentity,
    val serverUrl: String,
    val pageCount: Int,
    val localPage: Int,
    val localPlayed: Boolean,
    val localOpenedAt: Long,
    val pendingRevision: Long?,
    val remote: JellyfinProgressSnapshot,
    val deferred: Boolean = false,
) {
    val key: String get() = listOf(JellyfinSnapshots.reference(identity.serverId, identity.userId, identity.itemId), serverUrl, pageCount, localPage, localPlayed,
        localOpenedAt, pendingRevision, remote.positionTicks, remote.played).joinToString("") { value ->
            val text = value.toString()
            "${text.length}:$text"
        }
}

enum class JellyfinProgressChoice { DEVICE, SERVER }
