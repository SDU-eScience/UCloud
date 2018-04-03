package dk.sdu.cloud.tus.services

import kotlinx.coroutines.experimental.Job
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.selects.select
import org.slf4j.LoggerFactory
import java.io.Closeable

interface IReadChannel : Closeable {
    /**
     * Reads from the channel and places the information in the [dst] array.
     *
     * @return The amount of bytes read or -1 if none were read and no more data is available
     */
    suspend fun read(dst: ByteArray, offset: Int): Int
}

class UploadService(
    private val objectId: String,
    var offset: Long,
    private val length: Long,
    private val readChannel: IReadChannel,
    private val store: ObjectStore
) {
    private var started = false
    var onProgress: ((Long) -> Unit)? = null

    suspend fun upload() {
        if (started) throw IllegalStateException("Cannot start upload twice!")
        started = true
        log.info("Starting upload... objectID: $objectId, offset: $offset, length: $length")

        // idx points to the current block index - this is based off our initial offset
        var idx = (offset / BLOCK_SIZE).toInt()
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
            Math.ceil(length / BLOCK_SIZE.toDouble()).toInt(),
            32 // 128MB
        )
        var currentNumInstances = 1 // Must be 1 initially. Can't be changed without additional changes to code.
        val preAllocatedBlocks = Array<ByteArray?>(maxInstances) { null }
        (0 until currentNumInstances).forEach {
            preAllocatedBlocks[it] = ByteArray(BLOCK_SIZE)
        }
        log.debug("Pre-allocating $maxInstances blocks")

        // We read data from a single thread and spin up co-routines to write this data to Ceph
        val jobs = Array<Job?>(maxInstances) { null }
        while (hasMoreData) {
            // Start by calculating object offset and maximum object size.
            // This will usually just be at offset 0 and max size, but the first and last object can be different
            val objectOffset = offset % BLOCK_SIZE
            val maxSize = (BLOCK_SIZE - objectOffset).toInt()

            log.debug("Starting at $objectOffset with size $maxSize")

            // Keeps track of how much we have read internally in this object
            //
            // This is relevant on the last block, which might not line up with BLOCK_SIZE.
            // It is also relevant on the first block if we start at a non BLOCK_SIZE boundary.
            var internalPtr = 0

            // First attempt to find a job index which is free (without suspending)
            val indexReadyNow: Int = run {
                for (i in 0 until currentNumInstances) {
                    val value = jobs[i]

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

                if (currentNumInstances * 2 <= maxInstances) {
                    log.debug("Could not find a free index, but we can still scale.")
                    // We are still allowed to scale. So let us do that!
                    val oldNumInstances = currentNumInstances
                    currentNumInstances *= 2

                    // Initialize new blocks
                    (oldNumInstances until currentNumInstances).forEach {
                        preAllocatedBlocks[it] = ByteArray(BLOCK_SIZE)
                    }

                    log.debug("Now using $currentNumInstances instances")
                    oldNumInstances // Just pick the first. Only one thread is doing this so it is safe.
                } else {
                    log.debug("No job-index is free right now. Waiting for one to become open")
                    val notNull = jobs.filterIndexed { index, _ -> index < currentNumInstances }.filterNotNull()
                    select<Unit> { notNull.forEach { it.onJoin { } } }
                    jobs.indexOfFirst { it != null && !it.isActive }
                }
            } else {
                log.debug("Job-index is free right now")
                indexReadyNow
            }

            log.debug("Using job-index: $freeIndex")

            // Start reading data into the free buffer
            // We have potential resizing here if we start at non block boundary
            val buffer =
                if (maxSize == BLOCK_SIZE) preAllocatedBlocks[freeIndex]!!
                else ByteArray(maxSize)

            // internalPtr should be used to where in the buffer it should place the data.
            while (internalPtr < maxSize && hasMoreData) {
                val read = readChannel.read(buffer, internalPtr)

                if (read == -1) {
                    hasMoreData = false
                } else {
                    internalPtr += read
                }
            }
            offset += internalPtr
            log.debug("Read $internalPtr data from block - hasMoreData: $hasMoreData")

            //
            //
            // This will only happen at the start, if we have a non-zero offset, or at the end, if the file doesn't
            // match up exactly with block size. Be very careful not to make changes to the above code that changes
            // this! The allocations made here are not cheap! There is a reason that buffers are being pre-allocated.
            //
            //
            val resizedBuffer = if (internalPtr == BLOCK_SIZE) {
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

            // We should only write internalPtr = maxSize (i.e. block is done) or offset = length (i.e.
            // upload is done). Otherwise the block hasn't been completed, and as a result we don't need to write
            // and certainly not verify the block.
            if (internalPtr != 0 && (internalPtr == maxSize || offset == length)) {
                log.debug("[$freeIndex] Writing to oid: $oid")
                jobs[freeIndex] = launch {
                    store.write(oid, resizedBuffer, offset = objectOffset)
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
                log.debug("Skipping write of incomplete block")
                offset -= internalPtr
                // Block not complete. This should be reflected in offset (which should
                // reflect how much data we have successfully accepted)
            }

            idx++
        }

        // Await all launched jobs
        log.debug("Reading has finished. Awaiting writing jobs to finish...")
        jobs.forEach { it?.join() }
        log.debug("Writing jobs are done.")

        // Since jobs can finish out of order we must notify again down here
        val callback = onProgress
        if (callback != null && acknowledged.isNotEmpty()) { // If ack set is empty then there is nothing to ack
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
        log.info("Upload complete: ${acknowledged.max() ?: -1}.")
    }

    companion object {
        private val log = LoggerFactory.getLogger(UploadService::class.java)
        const val BLOCK_SIZE = 1024 * 4096
    }
}
