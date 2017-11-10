package org.esciencecloud.storage.processor.tus

import com.ceph.rados.Completion
import com.ceph.rados.IoCTX
import com.ceph.rados.Rados
import kotlinx.coroutines.experimental.Job
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.selects.select
import org.slf4j.LoggerFactory
import java.io.Closeable
import java.io.File
import kotlin.coroutines.experimental.suspendCoroutine

interface IReadChannel : Closeable {
    /**
     * Reads from the channel and places the information in th [dst] array.
     *
     * @return The amount of bytes read or -1 if none were read and no more data is available
     */
    suspend fun read(dst: ByteArray): Int
}

class RadosStorage(clientName: String, configurationFile: File, pool: String) {
    private val cluster: Rados = Rados("ceph", clientName, 0)
    private val ioCtx: IoCTX
    private val log = LoggerFactory.getLogger(RadosStorage::class.java)

    companion object {
        const val BLOCK_SIZE = 1024 * 4096
    }

    init {
        if (!configurationFile.exists()) {
            throw IllegalStateException("Could not find configuration file. Expected it to be found " +
                    "at ${configurationFile.absolutePath}")
        }

        log.info("Reading Rados configuration")
        cluster.confReadFile(configurationFile)
        log.info("Connecting to cluster")
        cluster.connect()
        log.info("Connected!")

        ioCtx = cluster.ioCtxCreate(pool)
    }

    fun createUpload(objectId: String, readChannel: IReadChannel, offset: Long, length: Long): RadosUpload =
            RadosUpload(objectId, offset, length, readChannel, ioCtx)
}

class RadosUpload(
        val objectId: String,
        private var offset: Long,
        val length: Long,
        val readChannel: IReadChannel,
        private val ioCtx: IoCTX
) {
    private var started = false
    var onProgress: ((Long) -> Unit)? = null

    suspend fun upload() {
        if (started) throw IllegalStateException("Cannot start upload twice!")
        started = true

        // Read from channel in blocks
        var idx = (offset / RadosStorage.BLOCK_SIZE).toInt()
        var hasMoreData = true

        val startBlock = idx
        // TODO There must be some better data structure for this, can't come up with one right now.
        // This should also be fine, we don't really need it to be super performing
        val ackLock = Any()
        val acknowledged = hashSetOf<Int>()
        (0 until startBlock).forEach { acknowledged.add(it) }

        // TODO This code really is fine-tuned for producers that are significantly faster than our Ceph cluster
        // This is almost only the case if we are migrating over a dedicated line. When uploading over the Internet
        // it becomes highly unlikely that more than a few blocks will be in use (most likely just one)

        // TODO Should vary depending on speed too
        // TODO Should we allow going below BLOCK_SIZE if we only need a small part of a single block?

        // Pre-allocate for performance and to avoid potential leaks
        val maxInstances = Math.min(
                Math.ceil(length / RadosStorage.BLOCK_SIZE.toDouble()).toInt(),
                32 // 128MB
        )
        val preAllocatedBlocks = Array(maxInstances) { ByteArray(RadosStorage.BLOCK_SIZE) }
        val jobs = Array<Job?>(maxInstances) { null }
        while (hasMoreData) {
            val objectOffset = offset % RadosStorage.BLOCK_SIZE
            val maxSize = RadosStorage.BLOCK_SIZE - objectOffset

            var internalPtr = 0

            var freeIndex = -1
            for ((i, value) in jobs.withIndex()) {
                if (value == null) {
                    freeIndex = i
                } else if (!value.isActive) {
                    jobs[i] = null
                    freeIndex = i
                }
            }

            if (freeIndex == -1) {
                val notNull = jobs.filterNotNull()
                select<Unit> { notNull.forEach { it.onJoin { } } }
                freeIndex = jobs.indexOfFirst { it != null && !it.isActive }
                // TODO Maybe we could scale up if we hit this case.
            }

            val buffer = preAllocatedBlocks[freeIndex]
            while (internalPtr < maxSize && hasMoreData) {
                val read = readChannel.read(buffer)

                if (read == -1) {
                    hasMoreData = false
                } else {
                    internalPtr += read
                }
            }

            //
            //
            // This will only happen at the start, if we have a non-zero offset, or at the end, if the file doesn't
            // match up exactly with block size. Be very careful not to make changes to the above code that changes
            // this! The allocations made here are not cheap! There is a reason that buffers are being pre-allocated.
            //
            //
            val resizedBuffer = if (internalPtr == RadosStorage.BLOCK_SIZE) buffer else
                buffer.sliceArray(0 until internalPtr)

            // Used to have a crash here, we could solve this by retrieving the cluster.instanceId

            // Async launch job
            val oid = if (idx == 0) objectId else "$objectId-$idx"
            val savedIdx = idx
            jobs[freeIndex] = launch {
                ioCtx.aWrite(oid, resizedBuffer, objectOffset = objectOffset, awaitSafe = false)
                // TODO We should acknowledge that this block has been written here.
                // This will be needed for the resumable part. Remember that blocks can be written out-of-order

                val callback = onProgress
                if (callback != null) {
                    synchronized(ackLock) {
                        acknowledged.add(savedIdx)
                        val shouldNotify = (0..savedIdx).all { acknowledged.contains(it) }
                        if (shouldNotify) {
                            callback(savedIdx.toLong() * RadosStorage.BLOCK_SIZE)
                        }
                    }
                }
            }

            offset += internalPtr
            idx++
        }

        // Await all launched jobs
        jobs.forEach { it?.join() }
    }
}

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

suspend fun IoCTX.aRead(
        oid: String
) = suspendCoroutine<Unit> { continuation ->
    // rados_aio_read not actually supported by rados-java
    // TODO We will have to fork it for this functionality, it makes no sense that it isn't included already
    TODO()
}
