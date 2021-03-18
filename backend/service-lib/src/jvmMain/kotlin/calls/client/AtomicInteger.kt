package dk.sdu.cloud.calls.client

import java.util.concurrent.atomic.AtomicInteger

actual typealias AtomicInteger = AtomicInteger
actual fun atomicInt(initialValue: Int): AtomicInteger = AtomicInteger(initialValue)

actual fun atomicString(initialValue: String): AtomicString = AtomicString(initialValue)

actual class AtomicString(initialValue: String) {
    // Really stupid implementation
    private var value = initialValue
    private val lock = Any()

    actual fun compareAndSet(expected: String, newValue: String): Boolean {
        synchronized(lock) {
            if (value == expected) {
                value = newValue
                return true
            } else {
                return false
            }
        }
    }

    actual fun getAndSet(newValue: String): String {
        synchronized(lock) {
            val currentValue = value
            value = newValue
            return currentValue
        }
    }

    actual fun getValue(): String {
        synchronized(lock) {
            return value
        }
    }
}
