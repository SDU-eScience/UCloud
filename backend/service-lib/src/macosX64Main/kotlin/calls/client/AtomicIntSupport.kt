package dk.sdu.cloud.calls.client

import kotlinx.atomicfu.atomic

actual fun atomicInt(initialValue: Int): AtomicInteger = AtomicInteger(initialValue)

actual class AtomicInteger(initialValue: Int = 0) {
    private val value = atomic(initialValue)

    actual fun incrementAndGet(): Int = value.incrementAndGet()
    actual fun getAndIncrement(): Int = value.getAndIncrement()
}
