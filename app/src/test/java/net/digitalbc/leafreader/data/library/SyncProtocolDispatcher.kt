package net.digitalbc.leafreader.data.library

import net.digitalbc.leafreader.data.jellyfin.JellyfinProgressSnapshot
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.QueueDispatcher
import okhttp3.mockwebserver.RecordedRequest
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/** A server with per-item progress and observable writes, using the real HTTP client. */
internal open class SyncProtocolDispatcher(private val queuedResponses: Boolean = false) : QueueDispatcher() {
    private val positions = ConcurrentHashMap<String, JellyfinProgressSnapshot>()
    val writes = CopyOnWriteArrayList<RecordedRequest>()
    @Volatile var acknowledgeWrites = true
    @Volatile var failNextStateWrite = false
    @Volatile var resetPositionOnPlayed = false
    @Volatile var playedOnProgress: Boolean? = null
    @Volatile var onPlayedWrite: (() -> Unit)? = null
    @Volatile var onProgressWrite: (() -> Unit)? = null
    @Volatile var onProgressRead: (() -> Unit)? = null

    fun position(item: String) = positions[item] ?: JellyfinProgressSnapshot(0, false)
    fun position(item: String, ticks: Long?, played: Boolean?, lastPlayedDate: String? = null) {
        positions[item] = JellyfinProgressSnapshot(ticks, played, lastPlayedDate = lastPlayedDate)
    }

    override fun dispatch(request: RecordedRequest): MockResponse {
        val url = request.requestUrl!!
        val segments = url.pathSegments
        if (request.method == "GET" && segments.size == 2 && segments[0] == "Items") {
            val snapshot = position(segments[1])
            val body = JSONObject().put("Id", segments[1]).put("UserData", JSONObject()
                .put("PlaybackPositionTicks", snapshot.positionTicks).put("Played", snapshot.played)
                .put("LastPlayedDate", snapshot.lastPlayedDate))
            val callback = onProgressRead
            onProgressRead = null
            callback?.invoke()
            return MockResponse().setBody(body.toString())
        }
        val progressWrite = url.encodedPath == "/Sessions/Playing/Progress"
        val playedWrite = url.encodedPath.contains("/PlayedItems/")
        val stateWrite = request.method == "POST" && segments.size == 3 && segments[0] == "UserItems" && segments[2] == "UserData"
        if (progressWrite || playedWrite || stateWrite) writes += request
        val response = if (queuedResponses) super.dispatch(request) else MockResponse().setResponseCode(204)
        if ((playedWrite || stateWrite) && failNextStateWrite) {
            failNextStateWrite = false
            return MockResponse().setResponseCode(500)
        }
        if (response.status.split(' ')[1].toInt() in 200..299 && acknowledgeWrites) {
            if (progressWrite || stateWrite) {
                val body = JSONObject(request.body.clone().readUtf8())
                val item = if (stateWrite) segments[1] else body.getString("ItemId")
                positions[item] = position(item).copy(positionTicks = body.getLong(if (stateWrite) "PlaybackPositionTicks" else "PositionTicks"),
                    played = if (stateWrite) body.getBoolean("Played") else playedOnProgress ?: position(item).played)
                val callback = onProgressWrite
                onProgressWrite = null
                callback?.invoke()
            } else if (playedWrite) {
                val item = segments.last()
                positions[item] = position(item).copy(
                    positionTicks = if (resetPositionOnPlayed) 0 else position(item).positionTicks,
                    played = request.method == "POST",
                )
                val callback = onPlayedWrite
                onPlayedWrite = null
                callback?.invoke()
            }
        }
        return response
    }
}
