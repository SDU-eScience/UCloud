package dk.sdu.escience.tus

import com.ceph.rados.Completion
import com.ceph.rados.IoCTX
import com.ceph.rados.Rados
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.selects.select
import java.io.Closeable
import java.io.File
import kotlin.coroutines.experimental.Continuation
import kotlin.coroutines.experimental.suspendCoroutine

interface IReadChannel : Closeable {
    /**
     * Returns how many bytes are remaining in this channel. The channel should return -1 if the amount
     * of bytes remaining is unknown.
     */
    fun remaining(): Long

    /**
     * Reads from the channel and places the information in th [dst] array.
     *
     * @return The amount of bytes read or -1 if none were read and no more data is available
     */
    suspend fun read(dst: ByteArray): Int
}

interface TemporaryStorage {
    // An interface for some temporary storage we can use. This is essentially stuff I would like to move into
    // the storage interface, but it would be too big a job (right now) to implement it directly.
    //
    // This we can use for small experiments.
    suspend fun put(objectId: String, readChannel: IReadChannel)
}

class RadosTemporaryStorage : TemporaryStorage {
    private val cluster: Rados
    private val ioCtx: IoCTX
    private val BLOCK_SIZE = 1024 * 4096

    init {
        cluster = Rados("ceph", "client.development", 0)
        val configurationFile = File("ceph-upload.conf")
        if (!configurationFile.exists()) {
            throw IllegalStateException("Could not find configuration file. Expected it to be found " +
                    "at ${configurationFile.absolutePath}")
        }

        println("Reading configuration file")
        cluster.confReadFile(configurationFile)
        println("Connecting to cluster")
        cluster.connect()
        println("Connected!")

        ioCtx = cluster.ioCtxCreate("development")
    }

    suspend override fun put(objectId: String, readChannel: IReadChannel) {
        // Read from channel in blocks
        var idx = 0
        var hasMoreData = true
        var sumOther = 0L
        var sumUpload = 0L

        // Pre-allocate for performance and to avoid potential leaks
        val maxInstances = 64
        val preallocatedBlocks = Array(maxInstances) { ByteArray(BLOCK_SIZE) }
        val jobs = Array<Job?>(maxInstances) { null }
        var timer = System.currentTimeMillis()
        while (hasMoreData) {
            val start = System.currentTimeMillis()
            var ptr = 0

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
            }

            val buffer = preallocatedBlocks[freeIndex]
            while (ptr < buffer.size && hasMoreData) {
                val read = readChannel.read(buffer)

                if (read == -1) {
                    hasMoreData = false
                } else {
                    ptr += read
                }
            }

            // This only happens once though
            val resizedBuffer = if (ptr == BLOCK_SIZE) buffer else buffer.sliceArray(0 until ptr)

            // Async launch job
            val oid = if (idx == 0) objectId else "$objectId-$idx"

            // We used to at least, no idea why this has stopped
            //cluster.instanceId // TODO Without this we crash. Looks like the connection dies without it
            jobs[freeIndex] = launch {
                val uploadStart = System.currentTimeMillis()
                ioCtx.aWrite(oid, resizedBuffer, awaitSafe = false)
                sumUpload += System.currentTimeMillis() - uploadStart
            }
            if (idx % 100 == 0 && idx > 0) {
                println(idx)
                println("Time since last: ${System.currentTimeMillis() - timer}")
                timer = System.currentTimeMillis()
                println("Avg other: ${sumOther / idx}")
                println("Avg upload: ${sumUpload / idx}")
            }
            idx++
            sumOther += System.currentTimeMillis() - start
        }

        // Await all launched jobs
        jobs.filterNotNull().forEach { it.join() }
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
}

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        println("Usage: app <file>")
        System.exit(1)
    }
    val storage = RadosTemporaryStorage()
    val file = File(args[0])
    if (!file.exists()) {
        println("Could not find file!")
        System.exit(1)
    }

    val inputstream = file.inputStream()
    val channel = object : IReadChannel {
        override fun remaining(): Long {
            return -1 // TODO!
        }

        suspend override fun read(dst: ByteArray): Int {
            // TODO!!!
            return inputstream.read(dst)
        }

        override fun close() {
            inputstream.close()
        }
    }

    println("Ready!")
    runBlocking { storage.put("test-object", channel) }
    println("Done!")
}
