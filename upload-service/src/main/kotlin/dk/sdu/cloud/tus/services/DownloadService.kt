package dk.sdu.cloud.tus.services

import dk.sdu.cloud.tus.services.RadosStorage.Companion.BLOCK_SIZE
import io.ktor.cio.use
import kotlinx.coroutines.experimental.io.ByteWriteChannel
import kotlin.math.min

class DownloadService(private val store: ObjectStore) {
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
                    store.read(resolvedOid, buffer, currentObjectOffset)
                } catch (ex: NotFoundObjectStoreException) {
                    if (currentOidIndex == 0) {
                        throw NotFoundObjectStoreException(oid)
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