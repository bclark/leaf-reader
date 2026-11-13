package net.digitalbc.leafreader.data.jellyfin

data class JellyfinProgressSnapshot(val positionTicks: Long?, val played: Boolean?) {
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
