package dk.sdu.cloud.storage.services

import dk.sdu.cloud.storage.api.StorageEvent
import dk.sdu.cloud.storage.services.cephfs.FSCommandRunnerFactory
import dk.sdu.cloud.storage.util.FSException
import dk.sdu.cloud.storage.util.FSUserContext
import dk.sdu.cloud.storage.util.ticker
import dk.sdu.cloud.storage.util.unwrap
import kotlinx.coroutines.experimental.channels.*
import kotlinx.coroutines.experimental.selects.select
import org.slf4j.LoggerFactory
import java.math.BigInteger
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

class ChecksumService(
    private val commandRunnerFactory: FSCommandRunnerFactory,
    private val fs: LowLevelFileSystemInterface,
    private val coreFs: CoreFileSystemService
) : FileSystemListener {

    override suspend fun attachToFSChannel(channel: ReceiveChannel<StorageEvent>) {
        // How will this implementation handle very frequent updates to a file?
        channel.windowed(500).consumeEach {
            it.filterIsInstance<StorageEvent.CreatedOrModified>().distinctBy { it.path }.forEach {
                commandRunnerFactory.withContext(it.owner) { ctx ->
                    computeAndAttachChecksum(ctx, it.path)
                }
            }
        }
    }

    fun computeChecksum(
        ctx: FSUserContext,
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
        fs.setExtendedAttribute(ctx, path, CHECKSUM_KEY, checksum.toHexString())
        fs.setExtendedAttribute(ctx, path, CHECKSUM_TYPE_KEY, algorithm)
    }

    fun getChecksum(ctx: FSUserContext, path: String): FileChecksum {
        return try {
            val checksum = fs.getExtendedAttribute(ctx, path, CHECKSUM_KEY).unwrap()
            val type = fs.getExtendedAttribute(ctx, path, CHECKSUM_TYPE_KEY).unwrap()
            FileChecksum(type, checksum)
        } catch (ex: FSException.NotFound) {
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

fun <E> ReceiveChannel<E>.windowed(delay: Long, unit: TimeUnit = TimeUnit.MILLISECONDS): ReceiveChannel<List<E>> {
    // TODO This is probably very bad
    val dataChannel = this
    val ticker = ticker(delay, unit)

    return produce {
        val buffered = ArrayList<E>()

        try {
            while (!dataChannel.isClosedForReceive) {
                select<Unit> {
                    dataChannel.onReceive {
                        buffered.add(it)
                    }

                    ticker.onReceive {
                        if (buffered.isNotEmpty()) {
                            send(buffered.toList())
                            buffered.clear()
                        }
                    }
                }
            }
        } finally {
            ticker.cancel()
        }
    }
}
