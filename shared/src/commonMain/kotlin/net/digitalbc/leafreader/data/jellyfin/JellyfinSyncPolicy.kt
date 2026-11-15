package net.digitalbc.leafreader.data.jellyfin

enum class JellyfinSyncAction { UNAVAILABLE, EQUAL, UPLOAD, DOWNLOAD, CONFLICT }

/** Lossless three-way comparison. A conflict requires an explicit choice, never highest-page wins. */
object JellyfinSyncPolicy {
    fun decide(
        localTicks: Long, localPlayed: Boolean,
        remoteTicks: Long?, remotePlayed: Boolean?,
        baselineTicks: Long?, baselinePlayed: Boolean?,
        pending: Boolean, untrackedLocal: Boolean,
    ): JellyfinSyncAction {
        if (remoteTicks == null || remoteTicks < 0 || remotePlayed == null) return JellyfinSyncAction.UNAVAILABLE
        if (localTicks == remoteTicks && localPlayed == remotePlayed) return JellyfinSyncAction.EQUAL
        if (pending) {
            return if (baselineTicks == remoteTicks && baselinePlayed == remotePlayed) JellyfinSyncAction.UPLOAD
            else JellyfinSyncAction.CONFLICT
        }
        return if (untrackedLocal) JellyfinSyncAction.CONFLICT else JellyfinSyncAction.DOWNLOAD
    }
}
