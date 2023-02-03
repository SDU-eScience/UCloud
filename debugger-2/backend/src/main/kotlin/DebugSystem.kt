package dk.sdu.cloud.debugger

import io.ktor.http.*
import io.ktor.util.date.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import java.io.File
import java.lang.ref.WeakReference
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext
import kotlin.math.max
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime

class BinaryFrameAllocator(
    private val directory: File,
    private val generation: Long,
    val fileIndex: Int
) {
    private val channel = FileChannel.open(
        File(directory, "$generation-$fileIndex.log").toPath(),
        StandardOpenOption.CREATE,
        StandardOpenOption.READ,
        StandardOpenOption.WRITE,
        StandardOpenOption.SPARSE
    )

    @PublishedApi
    internal val buf = channel.map(FileChannel.MapMode.READ_WRITE, 0, LOG_FILE_SIZE)

    @PublishedApi
    internal var ptr = 0

    private var lastFlushPtr = 0

    val frameIndex: Int
        get() = ptr / FRAME_SIZE

    fun <T : BinaryDebugMessage<T>> allocateOrNull(stub: T): T? {
        if (ptr + FRAME_SIZE >= buf.capacity()) {
            return null
        }

        val result = stub.create(buf, ptr) as T
        ptr += FRAME_SIZE
        return result
    }

    fun flush() {
        if (lastFlushPtr != ptr) buf.force(lastFlushPtr, ptr - lastFlushPtr)
        lastFlushPtr = ptr
    }

    fun isFull(): Boolean {
        return ptr + FRAME_SIZE >= LOG_FILE_SIZE
    }

    fun close() {
        runCatching { channel.close() }
    }

    val clientRequest = BinaryDebugMessage.ClientRequest(ByteBuffer.allocate(0))
    val clientResponse = BinaryDebugMessage.ClientResponse(ByteBuffer.allocate(0))
    val serverRequest = BinaryDebugMessage.ServerRequest(ByteBuffer.allocate(0))
    val serverResponse = BinaryDebugMessage.ServerResponse(ByteBuffer.allocate(0))
    val databaseConnection = BinaryDebugMessage.DatabaseConnection(ByteBuffer.allocate(0))
    val databaseTransaction = BinaryDebugMessage.DatabaseTransaction(ByteBuffer.allocate(0))
    val databaseQuery = BinaryDebugMessage.DatabaseQuery(ByteBuffer.allocate(0))
    val databaseResponse = BinaryDebugMessage.DatabaseResponse(ByteBuffer.allocate(0))
    val log = BinaryDebugMessage.Log(ByteBuffer.allocate(0))

    init {
        listOf(
            clientRequest,
            clientResponse,
            serverRequest,
            serverResponse,
            databaseConnection,
            databaseTransaction,
            databaseQuery,
            databaseResponse,
            log
        ).forEach {
            check(it.schema.size <= FRAME_SIZE) { "${it::class} size exceeds frame (${it.schema.size})" }
        }
    }
}

fun buildContextFilePath(generation: Long, fileIdx: Int): String {
    return "$generation-$fileIdx.ctx"
}

class ContextDescriptorFile(
    directory: File,
    generation: Long,
    fileIdx: Int,
) {
    private val trackedDescriptors = ArrayList<WeakReference<DebugContextDescriptor>>()

    private val channel = FileChannel.open(
        File(directory, buildContextFilePath(generation, fileIdx)).toPath(),
        StandardOpenOption.CREATE,
        StandardOpenOption.READ,
        StandardOpenOption.WRITE,
        StandardOpenOption.SPARSE
    )

    @PublishedApi
    internal val buf = channel.map(FileChannel.MapMode.READ_WRITE, 0, 1024 * 1024 * 16)

    private var ptr: Int = 4096

    fun findTrackedContext(context: Int): DebugContextDescriptor? {
        for (ref in trackedDescriptors) {
            val descriptor = ref.get()
            if (descriptor?.id == context) return descriptor
        }
        return null
    }

    fun next(descriptor: DebugContextDescriptor? = null): DebugContextDescriptor? {
        if (descriptor == null) return DebugContextDescriptor(buf, 4096)
        if (descriptor.offset + DebugContextDescriptor.size >= buf.capacity()) return null
        descriptor.offset += DebugContextDescriptor.size
        if (descriptor.id == 0) return null
        return descriptor
    }

    fun findContext(context: Int, after: DebugContextDescriptor? = null): DebugContextDescriptor? {
        var aligned = (max(4096, after?.offset ?: 0) + 1) / DebugContextDescriptor.size
        if (aligned <= (after?.offset ?: 0)) aligned += DebugContextDescriptor.size

        val descriptor = DebugContextDescriptor(buf, aligned)
        while (descriptor.offset + DebugContextDescriptor.size < buf.capacity()) {
            if (descriptor.id == context) return descriptor
            descriptor.offset += DebugContextDescriptor.size
        }
        return null
    }

    fun allocate(): DebugContextDescriptor? {
        if (ptr + DebugContextDescriptor.size >= buf.capacity()) return null
        val descriptor = DebugContextDescriptor(buf, ptr)
        trackedDescriptors.add(WeakReference(descriptor))
        ptr += DebugContextDescriptor.size
        return descriptor
    }

    fun flush() {
        buf.force(0, ptr)
    }

    fun attemptClose(): Boolean {
        for (descriptor in trackedDescriptors) {
            if (descriptor.get()?.isOpen == true) return false
        }
        channel.close()
        return true
    }
}

class BinaryDebugSystem(
    private val directory: String,
    private val serviceName: String,
) {
    private val lock = Mutex()

    private var buffer = BinaryFrameAllocator(File(directory), generation, fileIdAcc.getAndIncrement())
    private var blobs = BlobSystem(File(directory), generation, buffer.fileIndex)

    private val closeSignal = Channel<Unit>(Channel.CONFLATED)

    private val oldContextFiles = ArrayList<ContextDescriptorFile>()
    private val contextFileIdAcc = AtomicInteger(1)
    private val contextIdAcc = AtomicInteger(2)
    private var contextFile = createContextDescriptorFile()

    private fun createContextDescriptorFile(): ContextDescriptorFile =
        ContextDescriptorFile(File(directory), generation, contextFileIdAcc.getAndIncrement())

    fun text(text: String, field: BinaryFrameField.Text): LargeText {
        val encoded = text.encodeToByteArray()
        return if (encoded.size >= field.maxSize) {
            val id = blobs.storeBlob(encoded)
            val prefix = LargeText.OVERFLOW_PREFIX + id + LargeText.OVERFLOW_SEP
            val previewSize = field.maxSize - prefix.length
            val arr = ByteArray(field.maxSize)
            prefix.encodeToByteArray().copyInto(arr)
            encoded.copyInto(arr, prefix.length, endIndex = previewSize)

            LargeText(arr)
        } else {
            return LargeText(encoded)
        }
    }

    suspend fun emit(fn: suspend BinaryFrameAllocator.() -> BinaryDebugMessage<*>?) {
        var success = false
        while (coroutineContext.isActive && !success) {
            var requestFlush: Boolean
            lock.withLock {
                val result = fn(buffer)
                success = result != null
                requestFlush = buffer.isFull()
            }
            if (requestFlush) closeSignal.send(Unit)
        }
    }

    private suspend fun allocateContext(): DebugContextDescriptor {
        val currentContext = debugContextOrNull()

        lock.withLock {
            val result = contextFile.allocate()
            if (result != null) {
                val parent = currentContext?.id ?: 1
                val id = contextIdAcc.getAndIncrement()
                result.parent = parent
                result.id = id
                if (parent != 1) {
                    var ctx = contextFile.findTrackedContext(parent)
                    if (ctx == null) {
                        ctx = oldContextFiles.firstNotNullOfOrNull { it.findTrackedContext(parent) }
                    }

                    ctx?.appendChild(id)
                }
                return result
            }

            if (!contextFile.attemptClose()) {
                oldContextFiles.add(contextFile)
            }

            contextFile = createContextDescriptorFile()
        }
        return allocateContext()
    }

    suspend fun <T> useContext(
        type: DebugContextType,
        initialName: String? = null,
        initialImportance: MessageImportance = MessageImportance.THIS_IS_NORMAL,
        block: suspend () -> T
    ) {
        val descriptor = allocateContext()
        return withContext(BinaryDebugCoroutineContext(descriptor)) {
            try {
                descriptor.type = type
                descriptor.importance = initialImportance
                descriptor.timestamp = getTimeMillis()
                descriptor.name = when {
                    initialName != null -> initialName
                    type == DebugContextType.BACKGROUND_TASK -> "Task"
                    type == DebugContextType.CLIENT_REQUEST -> "Client request"
                    type == DebugContextType.SERVER_REQUEST -> "Server request"
                    type == DebugContextType.DATABASE_TRANSACTION -> "Database transaction"
                    type == DebugContextType.OTHER -> "Other task"
                    else -> "Other task"
                }
                block()
            } finally {
                descriptor.isOpen = false
            }
        }
    }

    fun start(scope: CoroutineScope): Job {
        File(directory, "$generation.service").writeText(buildString {
            appendLine(serviceName)
            appendLine(generation)
        })

        return scope.launch(Dispatchers.IO) {
            try {
                while (isActive) {
                    select {
                        closeSignal.onReceive {
                            lock.withLock {
                                if (!buffer.isFull()) return@withLock
                                blobs.close()
                                buffer.close()

                                buffer = BinaryFrameAllocator(File(directory), generation, fileIdAcc.getAndIncrement())
                                blobs = BlobSystem(File(directory), generation, buffer.fileIndex)
                            }
                        }

                        onTimeout(500) {
                            lock.withLock {
                                buffer.flush()
                                contextFile.flush()

                                val iterator = oldContextFiles.iterator()
                                while (iterator.hasNext()) {
                                    val n = iterator.next()
                                    if (n.attemptClose()) {
                                        iterator.remove()
                                    }
                                }
                            }
                        }
                    }
                }

                lock.withLock {
                    buffer.close()
                }
            } finally {
                runCatching { buffer.close() }
                runCatching { blobs.close() }
            }
        }
    }

    companion object {
        private val fileIdAcc = AtomicInteger(0)

        val generation = System.currentTimeMillis()
        private val idAcc = AtomicInteger(0)
        fun id(): Int {
            return idAcc.getAndIncrement()
        }
    }
}

class BinaryDebugCoroutineContext(
    val descriptorOrNull: DebugContextDescriptor?,
) : AbstractCoroutineContextElement(BinaryDebugCoroutineContext) {
    companion object : CoroutineContext.Key<BinaryDebugCoroutineContext>, BinaryFrameSchema() {
        val root = BinaryDebugCoroutineContext(null)
    }
}

val BinaryDebugCoroutineContext.descriptor: DebugContextDescriptor get() = descriptorOrNull!!
val BinaryDebugCoroutineContext.parent: Int get() = descriptorOrNull?.parent ?: 1
val BinaryDebugCoroutineContext.id: Int get() = descriptorOrNull?.id ?: 1

suspend fun debugContextOrNull(): DebugContextDescriptor? {
    return coroutineContext[BinaryDebugCoroutineContext]?.descriptorOrNull
}

suspend fun debugContext(): DebugContextDescriptor = debugContextOrNull() ?: error("No valid debug context")

enum class DebugContextType {
    CLIENT_REQUEST,
    SERVER_REQUEST,
    DATABASE_TRANSACTION,
    BACKGROUND_TASK,
    OTHER,
}

class DebugContextDescriptor(buf: ByteBuffer, ptr: Int) : BinaryFrame(buf, ptr) {
    var isOpen: Boolean = true

    var parent by Schema.parent
    var id by Schema.id
    var importance by Schema.importance
    var type by Schema.type

    var timestamp by Schema.timestamp

    var name: String
        get() = Schema.name.getValue(this, this::name).decodeToString()
        set(value) {
            val shortName = value.take(100)
            Schema.name.setValue(this, this::name, shortName.encodeToByteArray())
        }

    private fun setChild(idx: Int, childContext: Int) {
        require(idx in 0..255) { "index out of bounds $idx not in 0..255" }
        val relativeContext = childContext - id
        if (relativeContext >= Short.MAX_VALUE || relativeContext <= 0) return
        buf.putShort(children.offset + idx * 2, relativeContext.toShort())
    }

    private fun getChild(idx: Int): Int {
        require(idx in 0..255) { "index out of bounds $idx not in 0..255" }
        return id + buf.getShort(children.offset + idx * 2)
    }

    fun appendChild(child: Int) {
        for (i in 0..255) {
            if (getChild(i) == 0) {
                setChild(i, child)
                break
            }
        }
    }

    override val schema = Schema

    companion object Schema : BinaryFrameSchema() {
        val parent = int4()
        val id = int4()

        val importance = enum<MessageImportance>()
        val type = enum<DebugContextType>()
        val timestamp = int8()
        val rsv1 = int1()
        val rsv2 = int1()

        val name = bytes(108)
        val children = bytes(256)
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
        val message = allocateOrNull(clientRequest) ?: return@emit null
        message.ctxGeneration = BinaryDebugSystem.generation
        message.ctxParent = ctx.parent
        message.ctxId = ctx.id
        message.timestamp = System.currentTimeMillis()
        message.importance = importance
        message.id = BinaryDebugSystem.id()

        message.call = text(call ?: "", BinaryDebugMessage.ClientRequest.call)
        message.payload = text(payloadEncoded, BinaryDebugMessage.ClientRequest.payload)
        message
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
        val message = allocateOrNull(clientResponse) ?: return@emit null
        message.ctxGeneration = BinaryDebugSystem.generation
        message.ctxParent = ctx.parent
        message.ctxId = ctx.id
        message.timestamp = System.currentTimeMillis()
        message.importance = importance
        message.id = BinaryDebugSystem.id()

        message.call = text(call ?: "", BinaryDebugMessage.ClientResponse.call)
        message.response = text(responseEncoded, BinaryDebugMessage.ClientResponse.response)
        message.responseCode = responseCode.value.toByte()
        message.responseTime = responseTime.toInt()
        message
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
        val message = allocateOrNull(serverRequest) ?: return@emit null
        message.ctxGeneration = BinaryDebugSystem.generation
        message.ctxParent = ctx.parent
        message.ctxId = ctx.id
        message.timestamp = System.currentTimeMillis()
        message.importance = importance
        message.id = BinaryDebugSystem.id()

        message.call = text(call ?: "", BinaryDebugMessage.ServerRequest.call)
        message.payload = text(payloadEncoded, BinaryDebugMessage.ServerRequest.payload)
        message
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
        val message = allocateOrNull(serverResponse) ?: return@emit null
        message.ctxGeneration = BinaryDebugSystem.generation
        message.ctxParent = ctx.parent
        message.ctxId = ctx.id
        message.timestamp = System.currentTimeMillis()
        message.importance = importance
        message.id = BinaryDebugSystem.id()

        message.call = text(call ?: "", BinaryDebugMessage.ServerResponse.call)
        message.response = text(responseEncoded, BinaryDebugMessage.ServerResponse.response)
        message.responseCode = responseCode.value.toByte()
        message.responseTime = responseTime.toInt()
        message
    }
}

suspend fun BinaryDebugSystem.databaseConnection(
    importance: MessageImportance,

    isOpen: Boolean
) {
    val ctx = coroutineContext[BinaryDebugCoroutineContext] ?: BinaryDebugCoroutineContext.root

    emit {
        val message = allocateOrNull(databaseConnection) ?: return@emit null
        message.ctxGeneration = BinaryDebugSystem.generation
        message.ctxParent = ctx.parent
        message.ctxId = ctx.id
        message.timestamp = System.currentTimeMillis()
        message.importance = importance
        message.id = BinaryDebugSystem.id()

        message.isOpen = isOpen
        message
    }
}

suspend fun BinaryDebugSystem.databaseTransaction(
    importance: MessageImportance,

    event: DBTransactionEvent
) {
    val ctx = coroutineContext[BinaryDebugCoroutineContext] ?: BinaryDebugCoroutineContext.root

    emit {
        val message = allocateOrNull(databaseTransaction) ?: return@emit null
        message.ctxGeneration = BinaryDebugSystem.generation
        message.ctxParent = ctx.parent
        message.ctxId = ctx.id
        message.timestamp = System.currentTimeMillis()
        message.importance = importance
        message.id = BinaryDebugSystem.id()

        message.event = event
        message
    }
}

suspend fun BinaryDebugSystem.databaseQuery(
    importance: MessageImportance,

    parameters: JsonElement?,
    query: String,
) {
    val ctx = coroutineContext[BinaryDebugCoroutineContext] ?: BinaryDebugCoroutineContext.root
    val parametersEncoded =
        if (parameters == null) "" else defaultMapper.encodeToString(JsonElement.serializer(), parameters)

    emit {
        val message = allocateOrNull(databaseQuery) ?: return@emit null
        message.ctxGeneration = BinaryDebugSystem.generation
        message.ctxParent = ctx.parent
        message.ctxId = ctx.id
        message.timestamp = System.currentTimeMillis()
        message.importance = importance
        message.id = BinaryDebugSystem.id()

        message.parameters = text(parametersEncoded, BinaryDebugMessage.DatabaseQuery.parameters)
        message.query = text(query, BinaryDebugMessage.DatabaseQuery.query)
        message
    }
}

suspend fun BinaryDebugSystem.databaseResponse(
    importance: MessageImportance,

    responseTime: Long
) {
    val ctx = coroutineContext[BinaryDebugCoroutineContext] ?: BinaryDebugCoroutineContext.root

    emit {
        val message = allocateOrNull(databaseResponse) ?: return@emit null
        message.ctxGeneration = BinaryDebugSystem.generation
        message.ctxParent = ctx.parent
        message.ctxId = ctx.id
        message.timestamp = System.currentTimeMillis()
        message.importance = importance
        message.id = BinaryDebugSystem.id()

        message.responseTime = responseTime.toInt()
        message
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
        val message = allocateOrNull(this.log) ?: return@emit null
        message.ctxGeneration = BinaryDebugSystem.generation
        message.ctxParent = ctx.parent
        message.ctxId = ctx.id
        message.timestamp = System.currentTimeMillis()
        message.importance = importance
        message.id = BinaryDebugSystem.id()

        message.message = text(log, BinaryDebugMessage.Log.message)
        message.extra = text(extraEncoded, BinaryDebugMessage.Log.extra)
        message
    }
}

class BlobSystem(
    private val directory: File,
    private val generation: Long,
    private val fileIndex: Int
) {
    private val channel = FileChannel.open(
        File(directory, "$generation-$fileIndex.blob").toPath(),
        StandardOpenOption.READ,
        StandardOpenOption.WRITE,
        StandardOpenOption.CREATE,
        StandardOpenOption.SPARSE,
    )
    private val buf = channel.map(FileChannel.MapMode.READ_WRITE, 0, 1024 * 1024 * 512)

    fun storeBlob(blob: ByteArray): String {
        if (buf.position() + blob.size + 4 > buf.capacity()) return "invalid"

        val pos = buf.position()
        buf.putInt(blob.size)
        buf.put(blob)
        return pos.toString()
    }

    fun close() {
        channel.truncate(buf.position().toLong())
        channel.close()
    }
}

var ctxId = 0

@Suppress("OPT_IN_USAGE")
fun exampleProducer(logFolder: File) {
    runCatching { logFolder.deleteRecursively() }
    logFolder.mkdirs()

    val debug = BinaryDebugSystem(logFolder.absolutePath, "UCloud/Core")
    val j = debug.start(GlobalScope)

    runBlocking {
        (0 until 1).map {
            GlobalScope.launch {
                while (isActive) {
                    debug.useContext(DebugContextType.BACKGROUND_TASK, "📯 Context $it, ${ctxId++}") {
                        repeat(10) {
                            debug.log(MessageImportance.THIS_IS_NORMAL, "📜 Log $it")
                            delay(50)
                        }
                        debug.useContext(DebugContextType.DATABASE_TRANSACTION, "💽 Database transaction $ctxId") {
                            debug.log(MessageImportance.THIS_IS_NORMAL, "📤 sending query select * from fie.dog")
                            debug.log(MessageImportance.THIS_IS_NORMAL, "📥 got a response from the database")
                        }

                        debug.useContext(DebugContextType.BACKGROUND_TASK, "🎤 Singing cool stuff") {

                        }
                        debug.useContext(DebugContextType.BACKGROUND_TASK, "🤐 Zipping files") {
                            debug.useContext(DebugContextType.SERVER_REQUEST, "😱 No! I won't zip!") {}
                            debug.useContext(DebugContextType.SERVER_REQUEST, "😇 OK! I will!") {
                                debug.log(MessageImportance.THIS_IS_NORMAL, "🎉 Finished!")
                            }
                        }
                    }
                }
            }
        }.joinAll()

        j.cancelAndJoin()
    }
}

val defaultMapper = Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
    classDiscriminator = "type"
    isLenient = true
    coerceInputValues = true
}
