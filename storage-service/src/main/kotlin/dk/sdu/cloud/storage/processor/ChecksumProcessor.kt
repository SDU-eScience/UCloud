package dk.sdu.cloud.storage.processor

import dk.sdu.cloud.file.api.FileChecksum
import dk.sdu.cloud.file.api.StorageEvent
import dk.sdu.cloud.storage.SERVICE_USER
import dk.sdu.cloud.storage.services.*
import dk.sdu.cloud.storage.util.FSException
import dk.sdu.cloud.storage.util.unwrap
import org.slf4j.LoggerFactory
import java.math.BigInteger
import java.security.MessageDigest

class ChecksumProcessor<Ctx : FSUserContext>(
    private val commandRunnerFactory: FSCommandRunnerFactory<Ctx>,
    private val fs: LowLevelFileSystemInterface<Ctx>,
    private val coreFs: CoreFileSystemService<Ctx>
) {
    fun handleEvents(chunk: List<StorageEvent>) {
        // How will this implementation handle very frequent updates to a file?
        commandRunnerFactory.withContext(SERVICE_USER) { ctx ->
            chunk
                .asSequence()
                .filterIsInstance<StorageEvent.CreatedOrRefreshed>()
                .distinctBy { it.path }
                .forEach {
                    computeAndAttachChecksum(ctx, it.path)
                }
        }
    }

    private fun computeChecksum(
        ctx: Ctx,
        path: String,
        algorithm: String = DEFAULT_CHECKSUM_ALGORITHM
    ): ByteArray {
        return coreFs.read(ctx, path) {
            var bytesRead = 0L
            val digest = getMessageDigest(algorithm)
            val buffer = ByteArray(4096 * 1024)
            while (true) {
                val read = read(buffer).takeIf { it != -1 } ?: break
                bytesRead += read
                digest.update(buffer, 0, read)
            }

            digest.digest()
        }
    }

    fun computeAndAttachChecksum(
        ctx: Ctx,
        path: String,
        algorithm: String = DEFAULT_CHECKSUM_ALGORITHM
    ): FileChecksum {
        val checksum = computeChecksum(ctx, path, algorithm)
        attachChecksumToObject(ctx, path, checksum, algorithm)
        return FileChecksum(algorithm, checksum.toHexString())
    }

    private fun attachChecksumToObject(
        ctx: Ctx,
        path: String,
        checksum: ByteArray,
        algorithm: String = DEFAULT_CHECKSUM_ALGORITHM
    ) {
        fs.setExtendedAttribute(
            ctx, path,
            CHECKSUM_KEY, checksum.toHexString()
        )
        fs.setExtendedAttribute(
            ctx, path,
            CHECKSUM_TYPE_KEY, algorithm
        )
    }

    fun getChecksum(ctx: Ctx, path: String): FileChecksum {
        return try {
            val checksum = fs.getExtendedAttribute(
                ctx, path,
                CHECKSUM_KEY
            ).unwrap()
            val type = fs.getExtendedAttribute(
                ctx, path,
                CHECKSUM_TYPE_KEY
            ).unwrap()
            FileChecksum(type, checksum)
        } catch (ex: FSException.NotFound) {
            log.info("Checksum did not already exist for $ctx.user}, $path. Attempting to recompute")
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

        private val log = LoggerFactory.getLogger(ChecksumProcessor::class.java)

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
    }
}

