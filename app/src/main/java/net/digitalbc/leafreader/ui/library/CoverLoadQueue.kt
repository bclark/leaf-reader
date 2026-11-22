package net.digitalbc.leafreader.ui.library

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Semaphore

/** Front artwork precedes queued rear work; active loads finish without interruption. */
internal class CoverLoadQueue(concurrency: Int = 2) {
    private val lock = Any()
    private val changes = MutableStateFlow(0L)
    private val slots = Semaphore(concurrency)
    private var fronts = 0

    fun request(foreground: Boolean) = Request(foreground)

    inner class Request internal constructor(private var foreground: Boolean) {
        private var started = false
        private var finished = false

        fun promote() = synchronized(lock) {
            if (!foreground && !finished) {
                foreground = true
                if (started) fronts++
                changes.value++
            }
        }

        suspend fun <T> load(block: suspend () -> T): T {
            synchronized(lock) {
                check(!started)
                started = true
                if (foreground) fronts++
                changes.value++
            }
            var acquired = false
            try {
                while (true) {
                    changes.first { synchronized(lock) { foreground || fronts == 0 } }
                    slots.acquire()
                    acquired = true
                    // A front may have arrived while this rear request waited for a slot.
                    if (synchronized(lock) { foreground || fronts == 0 }) break
                    acquired = false
                    slots.release()
                }
                return block()
            } finally {
                synchronized(lock) {
                    finished = true
                    if (foreground) fronts--
                    changes.value++
                }
                if (acquired) slots.release()
            }
        }
    }
}
