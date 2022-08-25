package dk.sdu.cloud

class ThreadLocal<T : Any> constructor(initializer: () -> T) {
    val local = java.lang.ThreadLocal.withInitial { initializer() }
    fun get(): T = local.get()
}
