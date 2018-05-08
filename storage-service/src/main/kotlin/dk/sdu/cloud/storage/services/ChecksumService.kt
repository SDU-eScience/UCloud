package dk.sdu.cloud.storage.services

import org.slf4j.LoggerFactory
import java.io.InputStream
import java.math.BigInteger
import java.security.MessageDigest

class ChecksumService(
    private val fs: FileSystemService
) {
    fun computeChecksum(
        ctx: FSUserContext,
        path: String,
        algorithm: String = DEFAULT_CHECKSUM_ALGORITHM
    ): ByteArray {
        return fs.read(ctx, path).use<InputStream, ByteArray> {
            var bytesRead = 0L
            val digest = getMessageDigest(algorithm)
            val buffer = ByteArray(4096 * 1024)
            while (true) {
                val read = it.read(buffer).takeIf { it != -1 } ?: break
                bytesRead += read
                digest.update(buffer, 0, read)
            }

            digest.digest()
        }
    }

    fun computeAndAttachChecksum(
        ctx: FSUserContext,
        path: String,
        algorithm: String = DEFAULT_CHECKSUM_ALGORITHM
    ): FileChecksum {
        val checksum = computeChecksum(ctx, path, algorithm)
        attachChecksumToObject(ctx, path, checksum, algorithm)
        return FileChecksum(algorithm, checksum.toHexString())
    }

    fun attachChecksumToObject(
        ctx: FSUserContext,
        path: String,
        checksum: ByteArray,
        algorithm: String = DEFAULT_CHECKSUM_ALGORITHM
    ) {
        fs.setMetaValue(ctx, path, CHECKSUM_KEY, checksum.toHexString())
        fs.setMetaValue(ctx, path, CHECKSUM_TYPE_KEY, algorithm)
    }

    fun getChecksum(ctx: FSUserContext, path: String): FileChecksum {
        return try {
            val checksum = fs.getMetaValue(ctx, path, CHECKSUM_KEY)
            val type = fs.getMetaValue(ctx, path, CHECKSUM_TYPE_KEY)
            FileChecksum(type, checksum)
        } catch (ex: FileSystemException.NotFound) {
            log.info("Checksum did not already exist for ${ctx.user}, $path. Attempting to recompute")
            return computeAndAttachChecksum(ctx, path)
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

        private val log = LoggerFactory.getLogger(ChecksumService::class.java)
    }
}

private fun ByteArray.toHexString(): String {
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