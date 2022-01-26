package dk.sdu.cloud.utils

import dk.sdu.cloud.service.Loggable
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import platform.posix.usleep
import kotlin.random.Random
import kotlin.random.nextUInt

abstract class ObjectPool<T>(private val maxCapacity: Int) {
    private val mutex = Mutex()
    private val inUse = Array<Boolean>(maxCapacity) { false }
    private val items = Array<Any?>(maxCapacity) { null }

    protected abstract fun produceItem(): T
    protected abstract fun isValid(item: T): Boolean
    protected abstract fun reset(item: T)
    protected open fun onDelete(item: T) {
        // Do nothing
    }

    suspend fun borrow(): Pair<Int, T> {
        while (true) {
            mutex.withLock {
                for ((idx, item) in items.withIndex()) {
                    @Suppress("UNCHECKED_CAST")
                    item as T

                    if (!inUse[idx]) {
                        val itemToReturn = if (item == null) {
                            log.debug("Creating new item")
                            val producedItem = produceItem()
                            items[idx] = producedItem
                            producedItem
                        } else {
                            if (isValid(item)) {
                                log.debug("Reusing item")
                                reset(item)
                                item
                            } else {
                                log.debug("Deleting and producing new item")
                                onDelete(item)
                                val producedItem = produceItem()
                                items[idx] = producedItem
                                producedItem
                            }
                        }

                        inUse[idx] = true
                        return Pair(idx, itemToReturn)
                    }
                }
            }

            delay(10 + Random.nextLong(40))
        }
    }

    suspend fun recycleInstance(ticket: Int) {
        mutex.withLock {
            inUse[ticket] = false
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}
