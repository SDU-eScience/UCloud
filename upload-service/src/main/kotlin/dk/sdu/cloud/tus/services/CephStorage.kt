package dk.sdu.cloud.tus.services

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
     * Reads from the channel and places the information in the [dst] array.
     *
     * @return The amount of bytes read or -1 if none were read and no more data is available
     */
    suspend fun read(dst: ByteArray): Int
}

class RadosStorage(clientName: String, configurationFile: File, pool: String) {
    private val cluster: Rados = Rados("ceph", clientName, 0)
    private val ioCtx: IoCTX

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

    companion object {
        const val BLOCK_SIZE = 1024 * 4096
        private val log = LoggerFactory.getLogger(RadosStorage::class.java)
    }
}

class RadosUpload(
        private val objectId: String,
        var offset: Long,
        private val length: Long,
        private val readChannel: IReadChannel,
        private val ioCtx: IoCTX
) {
    private var started = false
    var onProgress: ((Long) -> Unit)? = null

    // TODO This code really is fine-tuned for producers that are significantly faster than our Ceph cluster
    // This is almost only the case if we are migrating over a dedicated line. When uploading over the Internet
    // it becomes highly unlikely that more than a few blocks will be in use (most likely just one)

    // TODO Should vary depending on speed too
    // TODO Should we allow going below BLOCK_SIZE if we only need a small part of a single block?

    suspend fun upload() {
        if (started) throw IllegalStateException("Cannot start upload twice!")
        started = true
        log.debug("Starting upload...")
        log.debug("objectID: $objectId, offset: $offset, length: $length")

        // idx points to the current block index - this is based off our initial offset
        var idx = (offset / RadosStorage.BLOCK_SIZE).toInt()
        log.debug("Start index is: $idx")

        // flag to indicate if we have more data
        var hasMoreData = true

        // We keep a set of acknowledged blocks. We initialize this based on previous state.
        val ackLock = Any()
        val acknowledged = hashSetOf<Int>()
        (0 until idx).forEach { acknowledged.add(it) }
        val previouslyNotified = hashSetOf(*acknowledged.toTypedArray())
        log.debug("ack set: $acknowledged")

        // Pre-allocate for performance and to avoid potential leaks
        // We can have at most 32 instances, but we will not allocate more than we actually need.
        val maxInstances = Math.min(
                Math.ceil(length / RadosStorage.BLOCK_SIZE.toDouble()).toInt(),
                32 // 128MB
        )
        val preAllocatedBlocks = Array(maxInstances) { ByteArray(RadosStorage.BLOCK_SIZE) }
        log.debug("Pre-allocating $maxInstances blocks")

        // We read data from a single thread and spin up coroutines to write this data to Ceph
        val jobs = Array<Job?>(maxInstances) { null }
        while (hasMoreData) {
            // Start by calculating object offset and maximum object size.
            // This will usually just be at offset 0 and max size, but the first and last object can be different
            val objectOffset = offset % RadosStorage.BLOCK_SIZE
            val maxSize = RadosStorage.BLOCK_SIZE - objectOffset

            log.debug("Starting at $objectOffset with size $maxSize")

            // Keeps track of how much we have read internally in this object
            //
            // This is relevant on the last block, which might not line up with BLOCK_SIZE.
            // It is also relevant on the first block if we start at a non BLOCK_SIZE boundary.
            var internalPtr = 0

            // First attempt to find a job index which is free (without suspending)
            val indexReadyNow: Int = run {
                for ((i, value) in jobs.withIndex()) {
                    if (value == null) {
                        return@run i
                    } else if (!value.isActive) {
                        jobs[i] = null
                        return@run i
                    }
                }
                return@run -1
            }

            // Use that index, if available, otherwise wait for a job to finish
            val freeIndex = if (indexReadyNow == -1) {
                // There is no index free right now, so we wait for a job to finish
                // This will happen if we are reading data from the upload source faster than we can write it
                // TODO Maybe we could scale up if we hit this case.
                log.debug("No job-index is free right now. Waiting for one to become open")
                val notNull = jobs.filterNotNull()
                select<Unit> { notNull.forEach { it.onJoin { } } }
                jobs.indexOfFirst { it != null && !it.isActive }
            } else {
                log.debug("Job-index is free right now")
                indexReadyNow
            }

            log.debug("Using job-index: $freeIndex")

            // Start reading data into the free buffer
            // We have potential resizing here if we start at non-block boundary
            val buffer =
                    if (maxSize.toInt() == RadosStorage.BLOCK_SIZE) preAllocatedBlocks[freeIndex]
                    else ByteArray(maxSize.toInt())

            while (internalPtr < maxSize && hasMoreData) {
                val read = readChannel.read(buffer)

                if (read == -1) {
                    hasMoreData = false
                } else {
                    internalPtr += read
                }
            }
            log.debug("Read $internalPtr data from block - hasMoreData: $hasMoreData")

            //
            //
            // This will only happen at the start, if we have a non-zero offset, or at the end, if the file doesn't
            // match up exactly with block size. Be very careful not to make changes to the above code that changes
            // this! The allocations made here are not cheap! There is a reason that buffers are being pre-allocated.
            //
            //
            val resizedBuffer = if (internalPtr == RadosStorage.BLOCK_SIZE) {
                log.debug("Buffer does not need resizing")
                buffer
            } else {
                log.debug("Resizing buffer")
                buffer.sliceArray(0 until internalPtr)
            }

            // Used to have a crash here, we could solve this by retrieving the cluster.instanceId

            // Launch a writing job
            val oid = if (idx == 0) objectId else "$objectId-$idx"
            val savedIdx = idx

            if (internalPtr != 0) {
                log.debug("[$freeIndex] Writing to oid: $oid")
                jobs[freeIndex] = launch {
                    ioCtx.aWrite(oid, resizedBuffer, objectOffset = objectOffset, awaitSafe = false)
                    log.debug("[$freeIndex] Finished writing $oid")
                    val callback = onProgress
                    if (callback != null) {
                        synchronized(ackLock) {
                            acknowledged.add(savedIdx)
                            val shouldNotify = (0..savedIdx).all { acknowledged.contains(it) }
                            log.debug("[$freeIndex] Notifying - ack set: $acknowledged - shouldNotify: $shouldNotify")
                            if (shouldNotify && savedIdx !in previouslyNotified) {
                                previouslyNotified.add(savedIdx)
                                callback(savedIdx.toLong())
                            }
                        }
                    }
                }
            } else {
                log.debug("Skipping zero size block")
            }

            offset += internalPtr
            idx++
        }

        // Await all launched jobs
        log.debug("Reading has finished. Awaiting writing jobs to finish...")
        jobs.forEach { it?.join() }
        log.debug("Writing jobs are done.")

        // Since jobs can finish out of order we must notify again down here
        val callback = onProgress
        if (callback != null) {
            var maxAck = 0
            while (true) {
                if (acknowledged.contains(maxAck)) {
                    maxAck++
                } else {
                    log.debug("Notifying that block ${maxAck.toLong() - 1} has been uploaded")
                    if (maxAck - 1 !in previouslyNotified) {
                        callback((maxAck.toLong() - 1))
                    }
                    break
                }
            }
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(RadosUpload::class.java)
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
) = suspendCoroutine<Unit> { _ ->
    // rados_aio_read not actually supported by rados-java
    // TODO We will have to fork it for this functionality, it makes no sense that it isn't included already
    TODO()
}
