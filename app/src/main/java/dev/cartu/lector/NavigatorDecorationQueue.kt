package dev.cartu.lector

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class NavigatorDecorationQueue {
    private val mutex = Mutex()

    suspend fun run(update: suspend () -> Unit) {
        mutex.withLock { update() }
    }

    suspend fun replace(clear: suspend () -> Unit, update: suspend () -> Unit) {
        mutex.withLock {
            clear()
            update()
        }
    }
}
