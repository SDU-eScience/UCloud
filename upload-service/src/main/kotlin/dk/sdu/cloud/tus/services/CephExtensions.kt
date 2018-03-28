package dk.sdu.cloud.tus.services

import com.ceph.rados.Completion
import com.ceph.rados.IoCTX
import kotlin.coroutines.experimental.suspendCoroutine

const val CephExtensionsFullJvmName = "dk.sdu.cloud.tus.services.CephExtensionsKt"

suspend fun IoCTX.aWrite(
    oid: String,
    buffer: ByteArray,
    objectOffset: Long = 0L,
    awaitSafe: Boolean = false
) = suspendCoroutine<Unit> { continuation ->
    val callback = if (!awaitSafe) {
        object : Completion(true, false) {
            override fun onComplete() {
                continuation.resume(Unit)
            }
        }
    } else {
        object : Completion(false, true) {
            override fun onSafe() {
                continuation.resume(Unit)
            }
        }
    }

    aioWrite(oid, callback, buffer, objectOffset)
}

@Suppress("RedundantSuspendModifier")
suspend fun IoCTX.aRead(
    oid: String,
    buffer: ByteArray,
    objectOffset: Long = 0L
): Int {
    // TODO aioRead is not being passed the correct return value from librados. This needs to be fixed in java-rados
    return read(oid, buffer.size, objectOffset, buffer)
}
