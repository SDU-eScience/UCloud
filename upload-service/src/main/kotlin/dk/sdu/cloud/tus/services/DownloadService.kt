package dk.sdu.cloud.tus.services

import com.ceph.rados.IoCTX
import com.ceph.rados.exceptions.RadosNotFoundException
import dk.sdu.cloud.tus.services.RadosStorage.Companion.BLOCK_SIZE
import io.ktor.cio.use
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.experimental.io.ByteWriteChannel
import kotlin.math.min

sealed class ObjectStoreException(message: String, val statusCode: HttpStatusCode) : Exception(message) {
    data class NotFound(val name: String) : ObjectStoreException("Not found: $name", HttpStatusCode.NotFound)
}

class DownloadService(private val ioCtx: IoCTX) {
    suspend fun download(
        oid: String,
        outputChannel: ByteWriteChannel,
        objectOffset: Long = 0L,
        maxBytesToRead: Long? = null
    ) {
        outputChannel.use {
            var bytesRead = 0L
            var currentOffset = objectOffset

            val bufferSize =
                min(1024 * 4096, maxBytesToRead ?: Long.MAX_VALUE).also { assert(it < Int.MAX_VALUE) }.toInt()
            val buffer = ByteArray(bufferSize)

            while (true) {
                val currentOidIndex = (currentOffset / BLOCK_SIZE).toInt()
                val resolvedOid = if (currentOidIndex == 0) oid else "$oid-$currentOidIndex"
                val currentObjectOffset = currentOffset % BLOCK_SIZE
                val read = try {
                    ioCtx.aRead(resolvedOid, buffer, currentObjectOffset)
                } catch (ex: RadosNotFoundException) {
                    if (currentOidIndex == 0) {
                        throw ObjectStoreException.NotFound(oid)
                    } else {
                        break
                    }
                }

                if (read <= 0) {
                    break
                } else {
                    val maxBytesToWrite =
                        if (maxBytesToRead == null) read
                        else min(read, (maxBytesToRead - currentOffset).toInt())

                    bytesRead += read
                    currentOffset += read
                    outputChannel.writeFully(buffer, 0, maxBytesToWrite)
                }
            }
        }
    }
}