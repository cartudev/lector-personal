package dev.cartu.lector

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class NavigatorDecorationQueueTest {
    @Test
    fun serializesOverlappingReadiumDecorationUpdates() = runBlocking {
        val queue = NavigatorDecorationQueue()
        val activeUpdates = AtomicInteger()
        val maximumConcurrentUpdates = AtomicInteger()

        coroutineScope {
            repeat(12) {
                launch(Dispatchers.Default) {
                    queue.run {
                        val active = activeUpdates.incrementAndGet()
                        maximumConcurrentUpdates.updateAndGet { maxOf(it, active) }
                        delay(5)
                        activeUpdates.decrementAndGet()
                    }
                }
            }
        }

        assertEquals(1, maximumConcurrentUpdates.get())
    }

    @Test
    fun clearsOldDecorationGroupBeforeApplyingTheNewWindow() = runBlocking {
        val queue = NavigatorDecorationQueue()
        val events = mutableListOf<String>()

        queue.replace(
            clear = { events += "clear" },
            update = { events += "apply-window" },
        )

        assertEquals(listOf("clear", "apply-window"), events)
    }
}
