package dk.sdu.cloud.debug

import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.service.Time
import io.ktor.util.cio.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonElement
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext
import kotlin.random.Random
import kotlin.reflect.KProperty
import kotlin.system.exitProcess
import kotlin.time.measureTime

@Suppress("unused")
abstract class BinaryFrameSchema(parent: BinaryFrameSchema? = null) {
    var size: Int = (parent?.size ?: 0) + 1

    protected fun int1() = BinaryFrameField.Int1(size).also { size += 1 }
    protected fun int2() = BinaryFrameField.Int2(size).also { size += 2 }
    protected fun int4() = BinaryFrameField.Int4(size).also { size += 4 }
    protected fun int8() = BinaryFrameField.Int8(size).also { size += 8 }
    protected fun float4() = BinaryFrameField.Float4(size).also { size += 4 }
    protected fun float8() = BinaryFrameField.Float8(size).also { size += 8 }
    protected fun bool() = BinaryFrameField.Bool(size).also { size += 1 }
    protected fun bytes(size: Short) = BinaryFrameField.Bytes(this.size, size).also { this.size += size.toInt() + 2 }
    protected fun text() = BinaryFrameField.Text(this.size).also { this.size += LargeText.SIZE }
    protected inline fun <reified E : Enum<E>> enum() = BinaryFrameField.Enumeration(this.size, enumValues<E>())
        .also { this.size += 1 }
}

abstract class BinaryFrame(val buf: ByteBuffer, val offset: Int = 0, val type: Byte = -1) {
    abstract val schema: BinaryFrameSchema

    init {
        if (buf.capacity() != 0 && type != (-1).toByte()) buf.put(offset, type)
    }
}

sealed class BinaryFrameField(val offset: Int) {
    class Int1(offset: Int) : BinaryFrameField(offset) {
        operator fun getValue(thisRef: BinaryFrame, property: KProperty<*>): Byte {
            return thisRef.buf.get(thisRef.offset + offset)
        }

        operator fun setValue(thisRef: BinaryFrame, property: KProperty<*>, value: Byte) {
            thisRef.buf.put(thisRef.offset + offset, value)
        }
    }

    class Int2(offset: Int) : BinaryFrameField(offset) {
        operator fun getValue(thisRef: BinaryFrame, property: KProperty<*>): Short {
            return thisRef.buf.getShort(thisRef.offset + offset)
        }

        operator fun setValue(thisRef: BinaryFrame, property: KProperty<*>, value: Short) {
            thisRef.buf.putShort(thisRef.offset + offset, value)
        }
    }

    class Int4(offset: Int) : BinaryFrameField(offset) {
        operator fun getValue(thisRef: BinaryFrame, property: KProperty<*>): Int {
            return thisRef.buf.getInt(thisRef.offset + offset)
        }

        operator fun setValue(thisRef: BinaryFrame, property: KProperty<*>, value: Int) {
            thisRef.buf.putInt(thisRef.offset + offset, value)
        }
    }

    class Int8(offset: Int) : BinaryFrameField(offset) {
        operator fun getValue(thisRef: BinaryFrame, property: KProperty<*>): Long {
            return thisRef.buf.getLong(thisRef.offset + offset)
        }

        operator fun setValue(thisRef: BinaryFrame, property: KProperty<*>, value: Long) {
            thisRef.buf.putLong(thisRef.offset + offset, value)
        }
    }

    class Float4(offset: Int) : BinaryFrameField(offset) {
        operator fun getValue(thisRef: BinaryFrame, property: KProperty<*>): Float {
            return thisRef.buf.getFloat(thisRef.offset + offset)
        }

        operator fun setValue(thisRef: BinaryFrame, property: KProperty<*>, value: Float) {
            thisRef.buf.putFloat(thisRef.offset + offset, value)
        }
    }

    class Float8(offset: Int) : BinaryFrameField(offset) {
        operator fun getValue(thisRef: BinaryFrame, property: KProperty<*>): Double {
            return thisRef.buf.getDouble(thisRef.offset + offset)
        }

        operator fun setValue(thisRef: BinaryFrame, property: KProperty<*>, value: Double) {
            thisRef.buf.putDouble(thisRef.offset + offset, value)
        }
    }

    class Bool(offset: Int) : BinaryFrameField(offset) {
        operator fun getValue(thisRef: BinaryFrame, property: KProperty<*>): Boolean {
            return thisRef.buf.get(thisRef.offset + offset) != 0.toByte()
        }

        operator fun setValue(thisRef: BinaryFrame, property: KProperty<*>, value: Boolean) {
            thisRef.buf.put(thisRef.offset + offset, if (value) 1 else 0)
        }
    }

    class Bytes(offset: Int, val size: Short) : BinaryFrameField(offset) {
        operator fun getValue(thisRef: BinaryFrame, property: KProperty<*>): ByteArray {
            val length = thisRef.buf.getShort(thisRef.offset + offset)
            val output = ByteArray(length.toInt())
            thisRef.buf.get(thisRef.offset + offset + 2, output, 0, length.toInt())
            return output
        }

        operator fun setValue(thisRef: BinaryFrame, property: KProperty<*>, value: ByteArray) {
            val length = if (value.size >= size) size else value.size.toShort()
            thisRef.buf.putShort(thisRef.offset + offset, length)
            thisRef.buf.put(thisRef.offset + offset + 2, value, 0, length.toInt())
        }
    }

    class Text(offset: Int) : BinaryFrameField(offset) {
        private val delegate = Bytes(offset, LargeText.SIZE.toShort())

        operator fun getValue(thisRef: BinaryFrame, property: KProperty<*>): LargeText {
            return LargeText(delegate.getValue(thisRef, property))
        }

        operator fun setValue(thisRef: BinaryFrame, property: KProperty<*>, value: LargeText) {
            delegate.setValue(thisRef, property, value.value)
        }
    }

    class Enumeration<E : Enum<E>>(offset: Int, val enumeration: Array<E>) : BinaryFrameField(offset) {
        operator fun getValue(thisRef: BinaryFrame, property: KProperty<*>): E {
            val ordinal = thisRef.buf.get(thisRef.offset + offset).toInt() and 0xFF
            return enumeration[ordinal]
        }

        operator fun setValue(thisRef: BinaryFrame, property: KProperty<*>, value: E) {
            thisRef.buf.put(thisRef.offset + offset, value.ordinal.toByte())
        }
    }
}

sealed class BinaryDebugMessage<Self : BinaryDebugMessage<Self>>(
    type: Byte,
    buf: ByteBuffer,
    offset: Int = 0
) : BinaryFrame(buf, offset, type) {
    var ctxGeneration by Schema.ctxGeneration
    var ctxParent by Schema.ctxParent
    var ctxId by Schema.ctxId
    var timestamp by Schema.timestamp
    var importance by Schema.importance
    var id by Schema.id

    private var rsv1 by Schema.rsv1
    private var rsv2 by Schema.rsv2
    private var rsv3 by Schema.rsv3

    companion object Schema : BinaryFrameSchema() {
        private val ctxGeneration = int8()
        private val ctxParent = int4()
        private val ctxId = int4()
        private val timestamp = int8()
        private val importance = enum<MessageImportance>()
        private val id = int4()

        private val rsv1 = int2()
        private val rsv2 = int4()
        private val rsv3 = int4()
    }

    abstract fun create(buf: ByteBuffer, offset: Int): Self

    class ClientRequest(buf: ByteBuffer, offset: Int = 0) : BinaryDebugMessage<ClientRequest>(1.toByte(), buf, offset) {
        var call by Schema.call
        var payload by Schema.payload

        override val schema = Schema

        override fun create(buf: ByteBuffer, offset: Int): ClientRequest = ClientRequest(buf, offset)

        companion object Schema : BinaryFrameSchema(BinaryDebugMessage) {
            private val call = text()
            private val payload = text()
        }
    }

    class ClientResponse(buf: ByteBuffer, offset: Int = 0) : BinaryDebugMessage<ClientResponse>(2.toByte(), buf, offset) {
        var responseCode by Schema.responseCode
        var responseTime by Schema.responseTime
        var call by Schema.call
        var response by Schema.response

        override val schema = Schema
        override fun create(buf: ByteBuffer, offset: Int): ClientResponse = ClientResponse(buf, offset)

        companion object Schema : BinaryFrameSchema(BinaryDebugMessage) {
            private val responseCode = int1()
            private val responseTime = int4()
            private val call = text()
            private val response = text()
        }
    }

    class ServerRequest(buf: ByteBuffer, offset: Int = 0) : BinaryDebugMessage<ServerRequest>(3.toByte(), buf, offset) {
        var call by Schema.call
        var payload by Schema.payload

        override val schema = Schema
        override fun create(buf: ByteBuffer, offset: Int): ServerRequest = ServerRequest(buf, offset)

        companion object Schema : BinaryFrameSchema(BinaryDebugMessage) {
            private val call = text()
            private val payload = text()
        }
    }

    class ServerResponse(buf: ByteBuffer, offset: Int = 0) : BinaryDebugMessage<ServerResponse>(4.toByte(), buf, offset) {
        var responseCode by Schema.responseCode
        var responseTime by Schema.responseTime
        var call by Schema.call
        var response by Schema.response

        override val schema = Schema
        override fun create(buf: ByteBuffer, offset: Int): ServerResponse = ServerResponse(buf, offset)

        companion object Schema : BinaryFrameSchema(BinaryDebugMessage) {
            private val responseCode = int1()
            private val responseTime = int4()
            private val call = text()
            private val response = text()
        }
    }

     class DatabaseConnection(buf: ByteBuffer, offset: Int = 0) : BinaryDebugMessage<DatabaseConnection>(5.toByte(), buf, offset) {
        var isOpen by Schema.isOpen

         override val schema = Schema
        override fun create(buf: ByteBuffer, offset: Int): DatabaseConnection = DatabaseConnection(buf, offset)

         companion object Schema : BinaryFrameSchema(BinaryDebugMessage) {
             private val isOpen = bool()
         }
    }

    class DatabaseTransaction(buf: ByteBuffer, offset: Int = 0) : BinaryDebugMessage<DatabaseTransaction>(6.toByte(), buf, offset) {
        var event by Schema.event

        override val schema = Schema
        override fun create(buf: ByteBuffer, offset: Int): DatabaseTransaction = DatabaseTransaction(buf, offset)

        companion object Schema : BinaryFrameSchema(BinaryDebugMessage) {
            private val event = enum<DebugMessage.DBTransactionEvent>()
        }
    }

    class DatabaseQuery(buf: ByteBuffer, offset: Int = 0) : BinaryDebugMessage<DatabaseQuery>(7.toByte(), buf, offset) {
        var parameters by Schema.parameters
        var query by Schema.query

        override val schema = Schema
        override fun create(buf: ByteBuffer, offset: Int): DatabaseQuery = DatabaseQuery(buf, offset)

        companion object Schema : BinaryFrameSchema(BinaryDebugMessage) {
            private val parameters = text()
            private val query = text()
        }
    }

    class DatabaseResponse(buf: ByteBuffer, offset: Int = 0) : BinaryDebugMessage<DatabaseResponse>(8.toByte(), buf, offset) {
        var responseTime by Schema.responseTime

        override val schema = Schema
        override fun create(buf: ByteBuffer, offset: Int): DatabaseResponse = DatabaseResponse(buf, offset)

        companion object Schema : BinaryFrameSchema(BinaryDebugMessage) {
            private val responseTime = int4()
        }
    }

    class Log(buf: ByteBuffer, offset: Int = 0) : BinaryDebugMessage<Log>(9.toByte(), buf, offset) {
        var message by Schema.message
        var extra by Schema.extra

        override val schema = Schema
        override fun create(buf: ByteBuffer, offset: Int): Log = Log(buf, offset)

        companion object Schema : BinaryFrameSchema(BinaryDebugMessage) {
            private val message = text()
            private val extra = text()
        }
    }
}

val oomCount = AtomicInteger(0)

class BinaryFrameReader(val file: File) {
    val raf = RandomAccessFile(file, "r")
    val fc = raf.channel
    val buf = fc.map(FileChannel.MapMode.READ_ONLY, 0, fc.size())

    fun retrieve(idx: Int) {

    }
}


class BinaryFrameAllocator {
    @PublishedApi
    internal val buf = ByteBuffer.allocateDirect(Short.MAX_VALUE.toInt() * 3)

    @PublishedApi
    internal var ptr = 0

    inline fun <reified T : BinaryDebugMessage<T>> allocateOrNull(): T? {
        for (stub in stubs) {
            if (stub::class == T::class) {
                if (ptr + stub.schema.size >= buf.capacity()) {
                    oomCount.incrementAndGet()
                    return null
                }

                val result = stub.create(buf, ptr) as T
                ptr += stub.schema.size
                return result
            }
        }

        error("unknown type ${T::class}. please add it to stubs in BinaryFrameAllocator")
    }

    inline fun flush(fn: (buf: ByteBuffer) -> Unit) {
        if (ptr == 0) return

        buf.position(0)
        buf.limit(ptr)
        try {
            fn(buf)
        } finally {
            buf.position(0)
            buf.limit(buf.capacity())
            ptr = 0
        }
    }

    companion object {
        @PublishedApi
        internal val stubs = listOf(
            BinaryDebugMessage.ClientRequest(ByteBuffer.allocate(0)),
            BinaryDebugMessage.ClientResponse(ByteBuffer.allocate(0)),
            BinaryDebugMessage.ServerRequest(ByteBuffer.allocate(0)),
            BinaryDebugMessage.ServerResponse(ByteBuffer.allocate(0)),
            BinaryDebugMessage.DatabaseConnection(ByteBuffer.allocate(0)),
            BinaryDebugMessage.DatabaseTransaction(ByteBuffer.allocate(0)),
            BinaryDebugMessage.DatabaseQuery(ByteBuffer.allocate(0)),
            BinaryDebugMessage.DatabaseResponse(ByteBuffer.allocate(0)),
            BinaryDebugMessage.Log(ByteBuffer.allocate(0)),
        )
    }
}

class BinaryDebugSystem {
    private val bufferCount = Runtime.getRuntime().availableProcessors()
    private val locks = Array(bufferCount) { Mutex() }
    private val buffers = Array(bufferCount) { BinaryFrameAllocator() }
    private val flushSignal = Channel<Int>(bufferCount, BufferOverflow.DROP_LATEST)
    private lateinit var blobStorage: BlobSystem

    private suspend fun lock(): Int {
        for (attempt in 0 until 5) {
            val idx = Random.nextInt(0, bufferCount)
            if (locks[idx].tryLock()) return idx
        }

        val idx = Random.nextInt(0, bufferCount)
        locks[idx].lock()
        return idx
    }

    suspend fun text(text: String): LargeText {
        val encoded = text.encodeToByteArray()
        return if (encoded.size >= LargeText.SIZE) {
            val id = blobStorage.storeBlob(encoded)
            LargeText("\$\$\$overflow-$id".encodeToByteArray())
        } else {
            return LargeText(encoded)
        }
    }

    suspend fun emit(fn: suspend BinaryFrameAllocator.() -> Boolean) {
        while (coroutineContext.isActive) {
            val idx = lock()
            val lock = locks[idx]
            try {
                val buffer = buffers[idx]
                if (fn(buffer)) break
            } finally {
                lock.unlock()
            }
            flushSignal.send(idx)
        }
    }

    fun start(scope: CoroutineScope): Job {
        blobStorage = BlobSystem(scope)
        return scope.launch(Dispatchers.IO) {
            val outputLog = File("/tmp/log-${generation}.log").toPath()
            val out = Files.newByteChannel(outputLog, StandardOpenOption.WRITE, StandardOpenOption.APPEND)
            try {
                while (isActive) {
                    select {
                        flushSignal.onReceive {
                            locks[it].withLock {
                                buffers[it].flush { buf -> out.write(buf) }
                            }
                        }

                        onTimeout(500) {
                            for (i in 0 until bufferCount) {
                                locks[i].withLock {
                                    buffers[i].flush { buf ->
                                        out.write(buf)
                                    }
                                }
                            }
                        }
                    }
                }

                for (i in 0 until bufferCount) {
                    locks[i].withLock {
                        buffers[i].flush { buf ->
                            out.write(buf)
                        }
                    }
                }
            } finally {
                runCatching { out.close() }
                runCatching { blobStorage.close() }
            }
        }
    }

    companion object {
        val generation = Time.now()
        private val idAcc = AtomicInteger(0)
        fun id(): Int {
            return idAcc.getAndIncrement()
        }
    }
}

class BinaryDebugCoroutineContext(
    val parent: Int,
    val id: Int,
) : AbstractCoroutineContextElement(BinaryDebugCoroutineContext) {
    companion object Key : CoroutineContext.Key<BinaryDebugCoroutineContext> {
        val root = BinaryDebugCoroutineContext(0, 0)
    }
}

suspend fun BinaryDebugSystem.clientRequest(
    importance: MessageImportance,

    call: String?,
    payload: JsonElement?,
) {
    val ctx = coroutineContext[BinaryDebugCoroutineContext] ?: BinaryDebugCoroutineContext.root
    val payloadEncoded = if (payload == null) "" else defaultMapper.encodeToString(JsonElement.serializer(), payload)

    emit {
        val message = allocateOrNull<BinaryDebugMessage.ClientRequest>() ?: return@emit false
        message.ctxGeneration = BinaryDebugSystem.generation
        message.ctxParent = ctx.parent
        message.ctxId = ctx.id
        message.timestamp = Time.now()
        message.importance = importance
        message.id = BinaryDebugSystem.id()

        message.call = text(call ?: "")
        message.payload = text(payloadEncoded)
        true
    }
}

suspend fun BinaryDebugSystem.clientResponse(
    importance: MessageImportance,

    call: String?,
    response: JsonElement?,

    responseCode: HttpStatusCode,
    responseTime: Long,
) {
    val ctx = coroutineContext[BinaryDebugCoroutineContext] ?: BinaryDebugCoroutineContext.root
    val responseEncoded = if (response == null) "" else defaultMapper.encodeToString(JsonElement.serializer(), response)

    emit {
        val message = allocateOrNull<BinaryDebugMessage.ClientResponse>() ?: return@emit false
        message.ctxGeneration = BinaryDebugSystem.generation
        message.ctxParent = ctx.parent
        message.ctxId = ctx.id
        message.timestamp = Time.now()
        message.importance = importance
        message.id = BinaryDebugSystem.id()

        message.call = text(call ?: "")
        message.response = text(responseEncoded)
        message.responseCode = responseCode.value.toByte()
        message.responseTime = responseTime.toInt()
        true
    }
}

suspend fun BinaryDebugSystem.serverRequest(
    importance: MessageImportance,

    call: String?,
    payload: JsonElement?,
) {
    val ctx = coroutineContext[BinaryDebugCoroutineContext] ?: BinaryDebugCoroutineContext.root
    val payloadEncoded = if (payload == null) "" else defaultMapper.encodeToString(JsonElement.serializer(), payload)

    emit {
        val message = allocateOrNull<BinaryDebugMessage.ServerRequest>() ?: return@emit false
        message.ctxGeneration = BinaryDebugSystem.generation
        message.ctxParent = ctx.parent
        message.ctxId = ctx.id
        message.timestamp = Time.now()
        message.importance = importance
        message.id = BinaryDebugSystem.id()

        message.call = text(call ?: "")
        message.payload = text(payloadEncoded)
        true
    }
}

suspend fun BinaryDebugSystem.serverResponse(
    importance: MessageImportance,

    call: String?,
    response: JsonElement?,

    responseCode: HttpStatusCode,
    responseTime: Long,
) {
    val ctx = coroutineContext[BinaryDebugCoroutineContext] ?: BinaryDebugCoroutineContext.root
    val responseEncoded = if (response == null) "" else defaultMapper.encodeToString(JsonElement.serializer(), response)

    emit {
        val message = allocateOrNull<BinaryDebugMessage.ServerResponse>() ?: return@emit false
        message.ctxGeneration = BinaryDebugSystem.generation
        message.ctxParent = ctx.parent
        message.ctxId = ctx.id
        message.timestamp = Time.now()
        message.importance = importance
        message.id = BinaryDebugSystem.id()

        message.call = text(call ?: "")
        message.response = text(responseEncoded)
        message.responseCode = responseCode.value.toByte()
        message.responseTime = responseTime.toInt()
        true
    }
}

suspend fun BinaryDebugSystem.databaseConnection(
    importance: MessageImportance,

    isOpen: Boolean
) {
    val ctx = coroutineContext[BinaryDebugCoroutineContext] ?: BinaryDebugCoroutineContext.root

    emit {
        val message = allocateOrNull<BinaryDebugMessage.DatabaseConnection>() ?: return@emit false
        message.ctxGeneration = BinaryDebugSystem.generation
        message.ctxParent = ctx.parent
        message.ctxId = ctx.id
        message.timestamp = Time.now()
        message.importance = importance
        message.id = BinaryDebugSystem.id()

        message.isOpen = isOpen
        true
    }
}

suspend fun BinaryDebugSystem.databaseTransaction(
    importance: MessageImportance,

    event: DebugMessage.DBTransactionEvent
) {
    val ctx = coroutineContext[BinaryDebugCoroutineContext] ?: BinaryDebugCoroutineContext.root

    emit {
        val message = allocateOrNull<BinaryDebugMessage.DatabaseTransaction>() ?: return@emit false
        message.ctxGeneration = BinaryDebugSystem.generation
        message.ctxParent = ctx.parent
        message.ctxId = ctx.id
        message.timestamp = Time.now()
        message.importance = importance
        message.id = BinaryDebugSystem.id()

        message.event = event
        true
    }
}

suspend fun BinaryDebugSystem.databaseQuery(
    importance: MessageImportance,

    parameters: JsonElement?,
    query: String,
) {
    val ctx = coroutineContext[BinaryDebugCoroutineContext] ?: BinaryDebugCoroutineContext.root
    val parametersEncoded = if (parameters == null) "" else defaultMapper.encodeToString(JsonElement.serializer(), parameters)

    emit {
        val message = allocateOrNull<BinaryDebugMessage.DatabaseQuery>() ?: return@emit false
        message.ctxGeneration = BinaryDebugSystem.generation
        message.ctxParent = ctx.parent
        message.ctxId = ctx.id
        message.timestamp = Time.now()
        message.importance = importance
        message.id = BinaryDebugSystem.id()

        message.parameters = text(parametersEncoded)
        message.query = text(query)
        true
    }
}

suspend fun BinaryDebugSystem.databaseResponse(
    importance: MessageImportance,

    responseTime: Long
) {
    val ctx = coroutineContext[BinaryDebugCoroutineContext] ?: BinaryDebugCoroutineContext.root

    emit {
        val message = allocateOrNull<BinaryDebugMessage.DatabaseResponse>() ?: return@emit false
        message.ctxGeneration = BinaryDebugSystem.generation
        message.ctxParent = ctx.parent
        message.ctxId = ctx.id
        message.timestamp = Time.now()
        message.importance = importance
        message.id = BinaryDebugSystem.id()

        message.responseTime = responseTime.toInt()
        true
    }
}

suspend fun BinaryDebugSystem.log(
    importance: MessageImportance,

    log: String,
    extra: JsonElement? = null,
) {
    val ctx = coroutineContext[BinaryDebugCoroutineContext] ?: BinaryDebugCoroutineContext.root
    val extraEncoded = if (extra == null) "" else defaultMapper.encodeToString(JsonElement.serializer(), extra)

    emit {
        val message = allocateOrNull<BinaryDebugMessage.Log>() ?: return@emit false
        message.ctxGeneration = BinaryDebugSystem.generation
        message.ctxParent = ctx.parent
        message.ctxId = ctx.id
        message.timestamp = Time.now()
        message.importance = importance
        message.id = BinaryDebugSystem.id()

        message.message = text(log)
        message.extra = text(extraEncoded)
        true
    }
}

class BlobSystem(private val scope: CoroutineScope) {
    private val mutex = Mutex()
    private val channel = Files.newByteChannel(
        File("/tmp/blob-${BinaryDebugSystem.generation}").toPath(),
        StandardOpenOption.WRITE
    )

    suspend fun storeBlob(blob: ByteArray): String {
       return withContext(scope.coroutineContext + Dispatchers.IO) {
           mutex.withLock {
               if (!channel.isOpen) error("channel has been closed")

               val pos = channel.position()
               channel.write(ByteBuffer.allocate(4).putInt(4).flip())
               val buffer = ByteBuffer.wrap(blob)
               while (buffer.hasRemaining()) {
                   channel.write(buffer)
               }

               pos.toString()
           }
       }
    }

    suspend fun close() {
        mutex.withLock { channel.close() }
    }
}

fun main() {
    if (true) {
        val raf = RandomAccessFile("/tmp/mm", "r")
        val buf = raf.channel.map(FileChannel.MapMode.READ_ONLY, 0, 1024 * 1024 * 64)
        println(Char(buf.get(300_000).toInt()))
    }
    if (true) {
        val raf = RandomAccessFile("/tmp/mm", "rw")
        val buf = raf.channel.map(FileChannel.MapMode.READ_WRITE, 0, 1024 * 1024 * 64)
        println(buf.limit())
        println(buf.capacity())
        buf.put(400_000, 'b'.code.toByte())

        exitProcess(0)
    }
    val debug = BinaryDebugSystem()
    val j = debug.start(GlobalScope)

    runBlocking {
        val warmup = measureTime {
            (0 until 8).map {
                GlobalScope.launch {
                    repeat(1000) {
                        debug.log(MessageImportance.THIS_IS_NORMAL, "Log $it")
                    }
                }
            }.joinAll()
        }
        val time = measureTime {
            val long = CharArray(125) { 'b' }.concatToString()
            (0 until 8).map {
                GlobalScope.launch {
                    repeat(10_000) {
                        debug.log(MessageImportance.THIS_IS_NORMAL, "$long $it")
                    }
                }
            }.joinAll()
        }
        println("All are done! ${oomCount.get()} $warmup $time")
        j.cancelAndJoin()
        println("last sync done")
    }
}

@JvmInline
value class LargeText internal constructor(val value: ByteArray) {
    companion object {
        const val SIZE = 126
    }
}

