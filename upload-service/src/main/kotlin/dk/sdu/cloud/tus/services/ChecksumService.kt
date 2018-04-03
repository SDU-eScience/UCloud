package dk.sdu.cloud.tus.services

import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.io.ByteChannel
import kotlinx.coroutines.experimental.launch
import java.math.BigInteger
import java.security.MessageDigest

class ChecksumService(
    private val downloadService: ObjectDownloadService,
    private val store: ObjectStore
) {
    suspend fun computeChecksumAndFileSize(
        oid: String,
        algorithm: String = DEFAULT_CHECKSUM_ALGORITHM
    ): Pair<ByteArray, Long> {
        val channel = ByteChannel()
        val download = launch { downloadService.download(oid, channel) }
        val checksum = async {
            var bytesRead = 0L
            val digest = getMessageDigest(algorithm)
            val buffer = ByteArray(4096 * 1024)
            while (true) {
                val read = channel.readAvailable(buffer).takeIf { it != -1 } ?: break
                bytesRead += read
                digest.update(buffer, 0, read)
            }

            Pair(digest.digest(), bytesRead)
        }

        download.join()
        return checksum.await()
    }

    suspend fun computeChecksum(
        oid: String,
        algorithm: String = DEFAULT_CHECKSUM_ALGORITHM
    ): ByteArray = computeChecksumAndFileSize(oid, algorithm).first

    suspend fun computeFileSize(oid: String): Long = computeChecksumAndFileSize(oid).second

    suspend fun computeAndAttachChecksumAndFileSize(oid: String, algorithm: String = DEFAULT_CHECKSUM_ALGORITHM) {
        val (checksum, fileSize) = computeChecksumAndFileSize(oid, algorithm)
        attachChecksumToObject(oid, checksum, algorithm)
        attachFilesizeToObject(oid, fileSize)
    }

    suspend fun attachChecksumToObject(
        oid: String,
        checksum: ByteArray,
        algorithm: String = DEFAULT_CHECKSUM_ALGORITHM
    ) {
        store.setAttribute(oid, CHECKSUM_KEY, checksum.toHexString())
        store.setAttribute(oid, CHECKSUM_TYPE_KEY, algorithm)
    }

    suspend fun attachFilesizeToObject(oid: String, fileSize: Long) {
        store.setAttribute(oid, FILESIZE_KEY, fileSize.toString())
    }

    suspend fun getChecksum(oid: String): FileChecksum {
        try {
            val checksum = store.getAttribute(oid, CHECKSUM_KEY) ?: throw NotFoundObjectStoreException(oid)
            val checksumType = store.getAttribute(oid, CHECKSUM_TYPE_KEY) ?: throw NotFoundObjectStoreException(oid)

            return FileChecksum(checksumType, checksum)
        } catch (ex: NotFoundObjectStoreException) {
            throw NotFoundObjectStoreException(oid)
        }
    }

    suspend fun getFileSize(oid: String): Long {
        try {
            return store.getAttribute(oid, FILESIZE_KEY)?.toLong() ?: throw NotFoundObjectStoreException(oid)
        } catch (ex: NotFoundObjectStoreException) {
            throw NotFoundObjectStoreException(oid)
        } catch (ex: NumberFormatException) {
            store.removeAttribute(oid, FILESIZE_KEY)
            throw ex
        }
    }

    private fun getMessageDigest(algorithm: String): MessageDigest {
        return when (algorithm) {
            "sha1" -> MessageDigest.getInstance(algorithm)
            else -> throw IllegalArgumentException("Unsupported checksum algorithm")
        }
    }

    companion object {
        const val DEFAULT_CHECKSUM_ALGORITHM = "sha1"
        const val CHECKSUM_KEY = "checksum"
        const val CHECKSUM_TYPE_KEY = "checksum_type"
        const val FILESIZE_KEY = "filesize"
    }
}

fun ByteArray.toHexString(): String {
    val bi = BigInteger(1, this)
    val hex = bi.toString(16)
    val paddingLength = this.size * 2 - hex.length
    return if (paddingLength > 0) {
        String.format("%0" + paddingLength + "d", 0) + hex
    } else {
        hex
    }
}

data class FileChecksum(val algorithm: String, val checksum: String)