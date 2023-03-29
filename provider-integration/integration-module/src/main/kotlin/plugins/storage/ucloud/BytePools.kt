package dk.sdu.cloud.plugins.storage.ucloud

import io.ktor.utils.io.pool.*
import java.nio.ByteBuffer
import java.util.*

val DefaultByteArrayPool = ByteArrayPool()

class ByteArrayPool : DefaultPool<ByteArray>(32) {
    override fun produceInstance(): ByteArray = ByteArray(1024 * 1024 * 2)
    override fun clearInstance(instance: ByteArray): ByteArray {
        Arrays.fill(instance, 0)
        return instance
    }
}

val DefaultSmallByteArrayPool = SmallByteArrayPool()
class SmallByteArrayPool : DefaultPool<ByteArray>(32) {
    override fun produceInstance(): ByteArray = ByteArray(2048)
    override fun clearInstance(instance: ByteArray): ByteArray {
        Arrays.fill(instance, 0)
        return instance
    }
}

val DefaultDirectBufferPool = DirectBufferPool()
class DirectBufferPool : DefaultPool<ByteBuffer>(32) {
    override fun produceInstance(): ByteBuffer = ByteBuffer.allocateDirect(2048)
    override fun clearInstance(instance: ByteBuffer): ByteBuffer {
        instance.clear()
        return instance
    }
}

val DefaultDirectBufferPoolForFileIo = DirectBufferPoolForFileIo()
class DirectBufferPoolForFileIo : DefaultPool<ByteBuffer>(128) {
    override fun produceInstance(): ByteBuffer = ByteBuffer.allocateDirect(1024 * 64)
    override fun clearInstance(instance: ByteBuffer): ByteBuffer {
        instance.clear()
        return instance
    }
}

val DefaultDirectBufferPoolLarge = DirectBufferPoolLarge()
class DirectBufferPoolLarge : DefaultPool<ByteBuffer>(32) {
    override fun produceInstance(): ByteBuffer = ByteBuffer.allocateDirect(1024 * 256)
    override fun clearInstance(instance: ByteBuffer): ByteBuffer {
        instance.clear()
        return instance
    }
}
