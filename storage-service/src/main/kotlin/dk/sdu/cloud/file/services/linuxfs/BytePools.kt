package dk.sdu.cloud.file.services.linuxfs

import kotlinx.io.pool.DefaultPool
import java.nio.ByteBuffer
import java.util.*

val DefaultByteArrayPool = ByteArrayPool()

class ByteArrayPool : DefaultPool<ByteArray>(128) {
    override fun produceInstance(): ByteArray = ByteArray(1024 * 64)
    override fun clearInstance(instance: ByteArray): ByteArray {
        Arrays.fill(instance, 0)
        return instance
    }
}
