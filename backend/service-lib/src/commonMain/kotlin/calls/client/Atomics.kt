package dk.sdu.cloud.calls.client

expect fun atomicString(initialValue: String): AtomicString
expect class AtomicString{
    fun compareAndSet(expected: String, newValue: String): Boolean
    fun getAndSet(newValue: String): String
    fun getValue(): String
}
expect fun atomicInt(initialValue: Int): AtomicInteger
expect class AtomicInteger {
    fun incrementAndGet(): Int
    fun getAndIncrement(): Int
}

