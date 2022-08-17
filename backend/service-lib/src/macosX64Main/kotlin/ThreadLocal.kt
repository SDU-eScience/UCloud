package dk.sdu.cloud

import kotlinx.atomicfu.atomic
import kotlin.native.concurrent.ThreadLocal

// TODO(Dan): Leaks memory
@ThreadLocal
private val threadLocalMap = HashMap<Int, Any>()

actual class ThreadLocal<T : Any> actual constructor(private val initializer: () -> T) {
    val id = idGenerator.getAndIncrement()

    @Suppress("UNCHECKED_CAST")
    actual fun get(): T = threadLocalMap.getOrPut(id) { initializer() } as T

    companion object {
        private val idGenerator = atomic(0)
    }
}
