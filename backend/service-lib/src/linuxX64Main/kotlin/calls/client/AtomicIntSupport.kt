package dk.sdu.cloud.calls.client

import kotlinx.atomicfu.atomic

actual fun atomicInt(initialValue: Int): AtomicInteger = AtomicInteger(initialValue)

actual class AtomicInteger(initialValue: Int = 0) {
    private val value = atomic(initialValue)

    actual fun incrementAndGet(): Int = value.incrementAndGet()
    actual fun getAndIncrement(): Int = value.getAndIncrement()
}

actual fun atomicString(initialValue: String): AtomicString = AtomicString(initialValue)

actual class AtomicString(initialValue: String) {
    private val value = atomic(initialValue)

    actual fun compareAndSet(expected: String, newValue: String): Boolean {
        return value.compareAndSet(expected, newValue)
    }

    actual fun getAndSet(newValue: String): String {
        return value.getAndSet(newValue)
    }

    actual fun getValue(): String {
        return value.value
    }
}