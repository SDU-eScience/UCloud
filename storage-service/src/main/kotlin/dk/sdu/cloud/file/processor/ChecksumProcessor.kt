package dk.sdu.cloud.file.processor

import dk.sdu.cloud.file.api.FileChecksum
import dk.sdu.cloud.file.api.FileType
import dk.sdu.cloud.file.api.StorageEvent
import dk.sdu.cloud.service.stackTraceToString
import dk.sdu.cloud.file.SERVICE_USER
import dk.sdu.cloud.file.services.CoreFileSystemService
import dk.sdu.cloud.file.services.FSCommandRunnerFactory
import dk.sdu.cloud.file.services.FSUserContext
import dk.sdu.cloud.file.services.FileAttribute
import dk.sdu.cloud.file.services.LowLevelFileSystemInterface
import dk.sdu.cloud.file.services.withContext
import dk.sdu.cloud.file.util.FSException
import org.slf4j.LoggerFactory
import java.math.BigInteger
import java.security.MessageDigest

private const val BUFFER_SIZE = 4096 * 1024
private const val HEX_INTEGER = 16

class ChecksumProcessor<Ctx : FSUserContext>(
    private val commandRunnerFactory: FSCommandRunnerFactory<Ctx>,
    private val fs: LowLevelFileSystemInterface<Ctx>,
    private val coreFs: CoreFileSystemService<Ctx>
) {
    fun handleEvents(chunk: List<StorageEvent>) {
        log.debug("Handling another batch of events: $chunk")

        // How will this implementation handle very frequent updates to a file?
        commandRunnerFactory.withContext(SERVICE_USER) { ctx ->
            chunk
                .asSequence()
                .filterIsInstance<StorageEvent.CreatedOrRefreshed>()
                .distinctBy { it.path }
                .forEach {
                    try {
                        log.debug("Computing check for for ${it.path}")
                        computeAndAttachChecksum(ctx, it.path)
                    } catch (ex: FSException) {
                        log.info("Caught exception while attempting to attach checksum")
                        log.info(ex.stackTraceToString())

                        if (ex is FSException.CriticalException) throw ex
                        else log.info("Exception was ignored.")
                    }
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
            val buffer = ByteArray(BUFFER_SIZE)
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
    ): FileChecksum? {
        val stat = coreFs.stat(ctx, path, setOf(FileAttribute.FILE_TYPE))
        if (stat.fileType != FileType.FILE) return null

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
            val hex = bi.toString(HEX_INTEGER)
            val paddingLength = this.size * 2 - hex.length
            return if (paddingLength > 0) {
                String.format("%0" + paddingLength + "d", 0) + hex
            } else {
                hex
            }
        }
    }
}

