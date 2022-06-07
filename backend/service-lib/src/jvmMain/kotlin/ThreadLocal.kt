package dk.sdu.cloud

actual class ThreadLocal<T : Any> actual constructor(initializer: () -> T) {
    val local = java.lang.ThreadLocal.withInitial { initializer() }
    actual fun get(): T = local.get()
}
