package dk.sdu.cloud

expect class ThreadLocal<T : Any>(initializer: () -> T) {
    fun get(): T
}
