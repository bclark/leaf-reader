package net.digitalbc.leafreader.data.library

import android.graphics.Bitmap
import android.os.Build
import android.os.Trace
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/** Local system-trace diagnostics only; no identities, URLs, logs or persistent collection. */
internal object CoverPerformanceTrace {
    private val counts = ConcurrentHashMap<String, AtomicLong>()

    fun event(name: String) {
        if (Build.VERSION.SDK_INT >= 29 && Trace.isEnabled())
            Trace.setCounter("LeafCover.$name", counts.getOrPut(name) { AtomicLong() }.incrementAndGet())
    }

    fun resident(bytes: Int) {
        if (Build.VERSION.SDK_INT >= 29 && Trace.isEnabled()) Trace.setCounter("LeafCover.residentBytes", bytes.toLong())
    }

    fun decoded(requestWidth: Int, bitmap: Bitmap?) {
        if (Build.VERSION.SDK_INT < 29 || !Trace.isEnabled() || bitmap == null) return
        event("decoded")
        Trace.setCounter("LeafCover.requestWidth", requestWidth.toLong())
        Trace.setCounter("LeafCover.decodedWidth", bitmap.width.toLong())
        Trace.setCounter("LeafCover.decodedHeight", bitmap.height.toLong())
        Trace.setCounter("LeafCover.decodedBytes", bitmap.allocationByteCount.toLong())
    }

    inline fun <T> auth(block: () -> T): T {
        Trace.beginSection("LeafCover.auth")
        return try { block() } finally { Trace.endSection() }
    }

    inline fun <T> decode(block: () -> T): T {
        Trace.beginSection("LeafCover.decode")
        return try { block() } finally { Trace.endSection() }
    }
}
