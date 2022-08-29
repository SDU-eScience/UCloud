package dk.sdu.cloud.calls.client

import java.util.concurrent.atomic.AtomicInteger

typealias AtomicInteger = AtomicInteger
fun atomicInt(initialValue: Int): AtomicInteger = AtomicInteger(initialValue)

fun atomicString(initialValue: String): AtomicString = AtomicString(initialValue)

class AtomicString(initialValue: String) {
    // Really stupid implementation
    private var value = initialValue
    private val lock = Any()

    fun compareAndSet(expected: String, newValue: String): Boolean {
        synchronized(lock) {
            if (value == expected) {
                value = newValue
                return true
            } else {
                return false
            }
        }
    }

    fun getAndSet(newValue: String): String {
        synchronized(lock) {
            val currentValue = value
            value = newValue
            return currentValue
        }
    }

    fun getValue(): String {
        synchronized(lock) {
            return value
        }
    }
}
