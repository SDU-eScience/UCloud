package dk.sdu.cloud.file.ucloud.services

import io.ktor.utils.io.pool.*
import java.util.*

val DefaultByteArrayPool = ByteArrayPool()

class ByteArrayPool : DefaultPool<ByteArray>(32) {
    override fun produceInstance(): ByteArray = ByteArray(1024 * 1024 * 2)
    override fun clearInstance(instance: ByteArray): ByteArray {
        Arrays.fill(instance, 0)
        return instance
    }
}
