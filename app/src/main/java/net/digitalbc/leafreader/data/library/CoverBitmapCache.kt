package net.digitalbc.leafreader.data.library

import android.graphics.Bitmap
import android.util.LruCache
import kotlinx.coroutines.*

/** Byte-bounded memory with optional private disk thumbnails. Never recycles displayed bitmaps. */
internal class CoverBitmapCache(
    private val scope: CoroutineScope,
    maxBytes: Int = minOf(32 * 1024 * 1024, minOf(Int.MAX_VALUE.toLong(), Runtime.getRuntime().maxMemory() / 16).toInt()),
    private val thumbnails: CoverThumbnailStore? = null,
) {
    data class Key(val url: String, val server: String, val user: String, val session: Long,
                   val item: String, val tag: String?, val width: Int)

    private class Flight(val result: Deferred<Result<Bitmap?>>, var readers: Int = 0)
    private val lock = Any()
    private var epoch = 0L
    private val pending = mutableMapOf<Key, Flight>()
    private val cache = object : LruCache<Key, Bitmap>(maxBytes.coerceAtLeast(1)) {
        override fun sizeOf(key: Key, value: Bitmap) = value.allocationByteCount
        override fun entryRemoved(evicted: Boolean, key: Key, oldValue: Bitmap, newValue: Bitmap?) {
            if (evicted) CoverPerformanceTrace.event("evicted")
        }
    }

    fun peek(key: Key): Bitmap? = synchronized(lock) { cache.get(key) }

    suspend fun get(key: Key, load: suspend () -> Bitmap?): Bitmap? {
        val flight = synchronized(lock) {
            cache.get(key)?.let { CoverPerformanceTrace.event("hit"); return it }
            CoverPerformanceTrace.event(if (key in pending) "shared" else "miss")
            val captured = epoch
            pending.getOrPut(key) {
                Flight(scope.async(start = CoroutineStart.LAZY) {
                    try {
                        val diskGeneration = thumbnails?.generation() ?: 0L
                        val stored = thumbnails?.read(key, diskGeneration)
                        val bitmap = stored ?: load()
                        currentCoroutineContext().ensureActive()
                        if (stored == null && bitmap != null) thumbnails?.write(key, bitmap, diskGeneration)
                        currentCoroutineContext().ensureActive()
                        synchronized(lock) {
                            if (captured == epoch && bitmap != null) {
                                cache.put(key, bitmap)
                                CoverPerformanceTrace.resident(cache.size())
                            }
                        }
                        Result.success(bitmap)
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (error: Exception) { Result.failure(error) }
                })
            }.also { it.readers++ }
        }
        flight.result.start()
        return try {
            flight.result.await().getOrThrow()
        } finally {
            synchronized(lock) {
                flight.readers--
                if (flight.readers == 0) {
                    if (pending[key] === flight) pending.remove(key)
                    // One disappearing card must not cancel a request another card still needs.
                    if (!flight.result.isCompleted) {
                        CoverPerformanceTrace.event("cancelled")
                        flight.result.cancel()
                    }
                }
            }
        }
    }

    fun clear() {
        val work = synchronized(lock) {
            epoch++
            cache.evictAll()
            pending.values.map { it.result }.also { pending.clear() }
        }
        work.forEach { it.cancel() }
        thumbnails?.clear()
    }
}
