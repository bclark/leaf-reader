package net.digitalbc.leafreader.ui.library

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class CoverLoadQueueTest {
    @Test fun frontsCompleteBeforeQueuedRearWorkAndConcurrencyIsBounded() = runTest {
        val queue = CoverLoadQueue(2)
        val release = CompletableDeferred<Unit>()
        val order = mutableListOf<String>()
        repeat(2) { i -> launch { queue.request(true).load { order += "front$i"; release.await() } } }
        runCurrent()
        launch { queue.request(false).load { order += "rear" } }
        launch { queue.request(true).load { order += "next-front" } }
        runCurrent()
        assertEquals(listOf("front0", "front1"), order)
        release.complete(Unit)
        runCurrent()
        assertEquals(listOf("front0", "front1", "next-front", "rear"), order)
    }

    @Test fun queuedRearCanBePromotedWithoutRestartingItsRequest() = runTest {
        val queue = CoverLoadQueue(1)
        val release = CompletableDeferred<Unit>()
        val order = mutableListOf<String>()
        launch { queue.request(true).load { release.await() } }
        runCurrent()
        launch { queue.request(false).load { order += "ordinary-rear" } }
        val next = queue.request(false)
        launch { next.load { order += "revealed-next" } }
        runCurrent()
        next.promote()
        release.complete(Unit)
        runCurrent()
        assertEquals(listOf("revealed-next", "ordinary-rear"), order)
    }

    @Test fun cancelledFrontAndRearRequestsReleasePriorityAndCapacity() = runTest {
        val queue = CoverLoadQueue(1)
        val release = CompletableDeferred<Unit>()
        val active = launch { queue.request(true).load { release.await() } }
        runCurrent()
        val waitingFront = launch { queue.request(true).load { fail("Cancelled front ran") } }
        val waitingRear = launch { queue.request(false).load { fail("Cancelled rear ran") } }
        runCurrent()
        waitingFront.cancel(); waitingRear.cancel(); active.cancel()
        runCurrent()
        var completed = false
        launch { queue.request(false).load { completed = true } }
        runCurrent()
        assertTrue("Cancelled fronts must not starve later rear work", completed)
    }

    @Test fun promotingActiveRearDoesNotInterruptOrDuplicateItsLoad() = runTest {
        val queue = CoverLoadQueue(1)
        val release = CompletableDeferred<Unit>()
        val request = queue.request(false)
        var starts = 0
        val job = launch { request.load { starts++; release.await() } }
        runCurrent()
        request.promote(); request.promote()
        release.complete(Unit)
        runCurrent()
        assertTrue(job.isCompleted)
        assertEquals(1, starts)
        request.promote()
        var laterRearRan = false
        launch { queue.request(false).load { laterRearRan = true } }
        runCurrent()
        assertTrue(laterRearRan)
    }
}
