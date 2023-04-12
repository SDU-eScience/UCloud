package dk.sdu.cloud.debugger

import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.util.date.*
import io.ktor.websocket.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration.Companion.minutes

data class TrackedService(val title: String, val generation: Long, val lastModified: Long)

val trackedServices = AtomicReference(emptyMap<String, TrackedService>())

val sessions = ArrayList<ClientSession>()
val sessionMutex = Mutex()
lateinit var directory: File

@Suppress("ExtractKtorModule")
fun main(args: Array<String>) {
    directory = args.getOrNull(0)?.let { File(it) } ?: error("Missing root directory")
    if (args.contains("--producer")) {
        exampleProducer(directory)
        return
    }

    @Suppress("OPT_IN_USAGE") GlobalScope.launch {
        coroutineScope {
            val serviceWatcher = launch(Dispatchers.IO) {
                while (isActive) {
                    val newServices =
                        (directory.listFiles() ?: emptyArray()).filter { it.isFile && it.name.endsWith(".service") }
                            .mapNotNull { serviceFile ->
                                runCatching {
                                    val lines = serviceFile.readText().lines()
                                    TrackedService(lines[0], lines[1].toLongOrNull() ?: 0, serviceFile.lastModified())
                                }.getOrNull()
                            }.groupBy { it.title }.mapValues { (_, group) ->
                                group.maxByOrNull { it.lastModified }!!
                            }

                    val oldServices = trackedServices.get()

                    // Notify clients of new services
                    val servicesWhichAreNew = newServices.filter { !oldServices.keys.contains(it.key) }
                    if (servicesWhichAreNew.isNotEmpty()) {
                        sessionMutex.withLock {
                            for (session in sessions) {
                                for ((_, service) in servicesWhichAreNew) {
                                    session.acceptService(service.title, service.generation)
                                }
                            }
                        }
                    }

                    trackedServices.set(newServices)
                    delay(50)
                }
            }

            val logWatcher = launch(Dispatchers.IO) {
                val openLogFiles = ArrayList<LogFileReader>()

                while (isActive) {
                    val currentServices = trackedServices.get()

                    // Close log files
                    run {
                        val iterator = openLogFiles.iterator()
                        while (iterator.hasNext()) {
                            val logFile = iterator.next()

                            val shouldClose =
                                // Close if generation is no longer valid
                                currentServices.none { it.value.generation == logFile.generation }
                            // TODO Close files which are no longer actively used

                            if (shouldClose) {
                                println("Closing service")
                                logFile.close()
                                iterator.remove()
                            }
                        }
                    }

                    // Open new log files
                    run {
                        for ((_, service) in currentServices) {
                            var idx = 0
                            while (true) {
                                if (!LogFileReader.exists(directory, service.generation, idx)) {
                                    idx--
                                    break
                                }
                                idx++
                            }

                            if (idx < 0) continue

                            val shouldOpen = openLogFiles.none {
                                it.generation == service.generation && it.idx == idx
                            }

                            if (shouldOpen) {
                                println("Opening $directory $service $idx")
                                val openFile = LogFileReader(directory, service.generation, idx)
                                openFile.seekToEnd()
                                openLogFiles.add(openFile)
                            }
                        }
                    }

                    // Find new messages from all log readers
                    run {
                        for (logFile in openLogFiles) {
                            while (logFile.next()) {
                                val message = logFile.retrieve() ?: break
                                sessionMutex.withLock {
                                    for (session in sessions) {
                                        session.acceptMessage(message, session.activeContexts)
                                    }
                                }
                            }
                        }
                    }

                    delay(50)
                }
            }

            val contextWatcher = launch(Dispatchers.IO) {
                val openContextFiles = ArrayList<ContextReader>()

                while (isActive) {
                    val currentServices = trackedServices.get()

                    // Close files
                    run {
                        val iterator = openContextFiles.iterator()
                        while (iterator.hasNext()) {
                            val contextFile = iterator.next()

                            // Close if generation is no longer valid
                            val shouldClose = currentServices.none { it.value.generation == contextFile.generation }
                            if (shouldClose) {
                                println("Closing context")
                                contextFile.close()
                                iterator.remove()
                            }
                        }
                    }

                    // Open new files
                    run {
                        for ((_, service) in currentServices) {
                            var idx = 1
                            while (true) {
                                if (!ContextReader.exists(directory, service.generation, idx)) {
                                    idx--
                                    break
                                }
                                idx++
                            }

                            if (idx < 0) continue

                            val shouldOpen = openContextFiles.none {
                                it.generation == service.generation && it.idx == idx
                            }

                            if (shouldOpen) {
                                println("Opening context $directory $service $idx")
                                val openFile = ContextReader(directory, service.generation, idx)
//                                openFile.seekToEnd()
                                openContextFiles.add(openFile)
                            }
                        }
                    }

                    // Find new messages from all readers
                    run {
                        for (contextFile in openContextFiles) {
                            while (contextFile.next()) {
                                val message = contextFile.retrieve() ?: break
                                // println("${message.importance} ${message.name} ${message.id} ${message.parent}")

                                sessionMutex.withLock {
                                    for (session in sessions) {
                                        session.acceptContext(contextFile.generation, message, session.activeContexts)
                                    }
                                }
                            }
                        }
                    }

                    delay(50)
                }
            }

            val logFlusher = launch(Dispatchers.IO) {
                while (isActive) {
                    sessionMutex.withLock {
                        for (session in sessions) {
                            session.flushServiceMessage()
                            session.flushContextMessage()
                            session.flushLogsMessage()
                        }
                    }
                    delay(100)
                }
            }
        }
    }

    embeddedServer(CIO, port = 5511) {
        install(WebSockets)

        routing {
            webSocket {
                val session = ClientSession(this)
                sessionMutex.withLock {
                    sessions.add(session)
                    println("Adding a client: ${sessions.size}")
                }

                // Send every registered service to the new session
                trackedServices.get().forEach { (_, service) ->
                    session.acceptService(service.title, service.generation)
                }

                try {
                    while (isActive) {
                        val frame = incoming.receiveCatching().getOrNull() ?: break
                        if (frame !is Frame.Text) continue
                        val frameText = frame.readText()

                        val request = runCatching {
                            defaultMapper.decodeFromString(ClientRequest.serializer(), frameText)
                        }.getOrNull() ?: continue

                        when (request) {
                            is ClientRequest.ClearActiveContext -> {
                                session.clearMessages()
                                session.clearContextMessages()
                                val generation = session.generation
                                if (generation != null) {
                                    val startTime = (System.currentTimeMillis() - 15.minutes.inWholeMilliseconds)
                                    val endTime = System.currentTimeMillis()
                                    session.activeContexts = arrayListOf(1L)
                                    sessionMutex.withLock {
                                        session.findAndEmitContexts(startTime, endTime, generation, 1L)
                                    }
                                }
                            }

                            is ClientRequest.ReplayMessages -> {
                                // Clear Log and Context Buffer
                                session.clearContextMessages()
                                session.clearMessages()

                                val generation = request.generation.toLong()
                                val startTime = request.timestamp
                                val endTime = startTime + 15.minutes.inWholeMilliseconds
                                sessionMutex.withLock {
                                    session.activeContexts = arrayListOf(request.context)
                                    val contexts =
                                        session.findAndEmitContexts(startTime, endTime, generation, request.context)
                                    session.activeContexts = contexts
                                    println("Found the following contexts: $contexts")
                                    session.findLogs(startTime, endTime, generation, contexts)
                                }
                            }

                            is ClientRequest.ActivateService -> {
                                val activeService = request.service
                                val generation = request.generation?.toLong()
                                session.activeService = activeService
                                session.generation = generation

                                if (generation != null) {
                                    sessionMutex.withLock {
                                        session.findAndEmitContexts(
                                            System.currentTimeMillis() - 15.minutes.inWholeMilliseconds,
                                            System.currentTimeMillis(),
                                            generation,
                                            1L
                                        )
                                    }
                                }
                            }

                            is ClientRequest.SetSessionState -> {
                                session.filterQuery = request.query
                                session.minimumLevel = request.level ?: MessageImportance.THIS_IS_NORMAL
                                session.filters = request.filters
                            }

                            is ClientRequest.FetchTextBlob -> {
                                val generation = request.generation.toLong()
                                val id = request.id.toInt()
                                val fileIndex = request.fileIndex.toInt()

                                useBlobEntry(generation, id, fileIndex) { buf ->
                                    val newBlobWriteBuffer =
                                        ByteBuffer.allocateDirect(buf.limit() + 8 + 4) // Add long (type), id for overflow identifier.
                                    newBlobWriteBuffer.putLong(4)
                                    newBlobWriteBuffer.putInt(id)
                                    newBlobWriteBuffer.put(buf)
                                    newBlobWriteBuffer.flip()
                                    session.session.send(Frame.Binary(true, newBlobWriteBuffer))
                                }
                            }

                            is ClientRequest.FetchPreviousMessage -> {
                                val generation = session.generation ?: continue
                                val startFile =
                                    findFirstContextIdContainingTimestamp(generation, request.timestamp) ?: continue
                                sessionMutex.withLock {
                                    session.findContextsBackwards(
                                        directory,
                                        generation,
                                        startFile,
                                        request.id,
                                        request.onlyFindSelf ?: false
                                    )
                                }
                            }
                        }
                    }
                } catch (ex: Throwable) {
                    println(ex.stackTraceToString())
                } finally {
                    sessionMutex.withLock {
                        sessions.remove(session)

                        println("Removing a client: ${sessions.size}")
                    }
                }
            }
        }
    }.start(wait = true)
}

const val CONTEXT_FIND_COUNT = 20
suspend fun ClientSession.findContextsBackwards(
    directory: File,
    generation: Long,
    fileIndex: Int,
    ctxId: Int,
    onlyFindSelf: Boolean = false
) {
    var remainingToFind = CONTEXT_FIND_COUNT
    var startAdding = false
    var currentFileIndex = fileIndex
    outer@ while (ContextReader.exists(directory, generation, currentFileIndex)) {
        val file = ContextReader(directory, generation, currentFileIndex)
        file.seekToEnd()
        if (file.retrieve()?.id == ctxId) startAdding = true

        while (file.previous()) {
            val ctx = file.retrieve() ?: continue
            if (ctx.id == ctxId) {
                startAdding = true
                if (onlyFindSelf) {
                    this.acceptContext(generation, ctx, arrayListOf(1L))
                    break@outer
                }
            } else if (ctx.parent == 1 && startAdding) {
                this.acceptContext(generation, ctx, arrayListOf(1L))
                remainingToFind--
                if (remainingToFind == 0) {
                    break@outer
                }
            }
        }
        currentFileIndex -= 1
    }

    this.flushContextMessage()
}

fun findFirstContextIdContainingTimestamp(generation: Long, timestamp: Long): Int? {
    var ctxFileId = 1

    while (ContextReader.exists(directory, generation, ctxFileId)) {
        val currentFile = ContextReader(directory, generation, ctxFileId)
        try {
            val fileEnd = currentFile.seekLastTimestamp() ?: return null
            if (timestamp < fileEnd) return ctxFileId
        } finally {
            currentFile.close()
            ctxFileId++
        }
    }
    return null
}

suspend fun ClientSession.emitNewestContext(
    directory: File,
    generation: Long?,
    service: String?
) {
    if (service == null || generation == null) return

    // Find the newest context file
    var highestCtxFile = 0
    while (ContextReader.exists(directory, generation, highestCtxFile + 1)) {
        highestCtxFile++
    }
    if (highestCtxFile == 0) return

    // Move through the entries backwards (potentially through files) to find the oldest root context
    while (ContextReader.exists(directory, generation, highestCtxFile)) {
        val ctxFile = ContextReader(directory, generation, highestCtxFile)
        try {
            ctxFile.seekToEnd()
            while (true) {
                val entry = ctxFile.retrieve() ?: break
                if (entry.parent == 1) {
                    acceptContext(generation, entry, listOf(1))
                    flushContextMessage()
                    return
                }

                if (!ctxFile.previous()) break
            }
        } finally {
            ctxFile.close()
            highestCtxFile--
        }
    }
}

suspend fun ClientSession.findAndEmitContexts(
    startTime: Long,
    endTime: Long,
    generation: Long,
    initialContext: Long
): ArrayList<Long> {
    val debug = initialContext == 1L
    val contextIds = arrayListOf(initialContext)
    var ctxFileId = findFirstContextIdContainingTimestamp(generation, startTime) ?: return contextIds

    while (ContextReader.exists(directory, generation, ctxFileId)) {
        if (debug) println("Looking in $directory $generation $ctxFileId $startTime $endTime ${endTime - startTime}")
        val currentFile = ContextReader(directory, generation, ctxFileId)
        try {
            while (currentFile.next()) {
                val currentEntry = currentFile.retrieve() ?: break
                if (currentEntry.timestamp < startTime) continue // keep looking
                if (currentEntry.timestamp > endTime) break // finished

                if (currentEntry.parent.toLong() in contextIds) {
                    contextIds.add(currentEntry.id.toLong())
                    if (debug) {
                        debugSkip = true
                        println("$generation ${currentEntry.id} ${currentEntry.parent} $contextIds")
                    } else {
                        debugSkip = false
                    }
                    acceptContext(generation, currentEntry, contextIds)
                }
            }
        } finally {
            currentFile.close()
            ctxFileId++
        }
    }

    flushContextMessage()
    return contextIds
}

suspend fun ClientSession.findLogs(
    startTime: Long,
    endTime: Long,
    generation: Long,
    contextIds: ArrayList<Long>
) {
    var logFileId = 0

    outer@ while (LogFileReader.exists(directory, generation, logFileId)) {
        val logFile = LogFileReader(directory, generation, logFileId)
        try {
            val fileEnd = logFile.seekLastTimestamp() ?: break@outer
            if (startTime > fileEnd) continue

            while (logFile.next()) {
                val currentEntry = logFile.retrieve() ?: break@outer
                if (currentEntry.timestamp < startTime) continue
                if (currentEntry.timestamp > endTime) break@outer
                if (currentEntry.ctxId.toLong() !in contextIds) continue

                acceptMessage(currentEntry, contextIds)
            }
        } finally {
            logFile.close()
            logFileId++
        }
    }
    flushLogsMessage()
}

inline fun useBlobEntry(generation: Long, blobId: Int, fileIndex: Int, fn: (ByteBuffer) -> Unit): Boolean {
    if (!BlobSystem.exists(directory, generation, fileIndex)) return false
    BlobSystem(directory, generation, fileIndex).use {
        fn(it.getBlob(blobId))
    }
    return true
}

@Serializable
sealed class ClientRequest {
    @Serializable
    @SerialName("replay_messages")
    data class ReplayMessages(val generation: String, val context: Long, val timestamp: Long) : ClientRequest()

    @Serializable
    @SerialName("activate_service")
    data class ActivateService(val service: String?, val generation: String?) : ClientRequest()

    @Serializable
    @SerialName("set_session_state")
    data class SetSessionState(
        val query: String?,
        val filters: List<DebugContextType>?,
        val level: MessageImportance?
    ) : ClientRequest()

    @Serializable
    @SerialName("clear_active_context")
    object ClearActiveContext : ClientRequest()

    @Serializable
    @SerialName("fetch_text_blob")
    data class FetchTextBlob(val id: String, val fileIndex: String, val generation: String) : ClientRequest()

    @Serializable
    @SerialName("fetch_previous_messages")
    data class FetchPreviousMessage(val timestamp: Long, val id: Int, val onlyFindSelf: Boolean?) : ClientRequest()
}

var debugSkip = false

data class ClientSession(
    // The WebSocket session itself, used for sending communication to the client.
    val session: WebSocketServerSession,

    // State, which is updated by messages from the client.
    var activeContexts: ArrayList<Long> = arrayListOf(1L),
    var minimumLevel: MessageImportance = MessageImportance.THIS_IS_NORMAL,
    var filterQuery: String? = null,
    var activeService: String? = null,
    var generation: Long? = null,
    var filters: List<DebugContextType>? = null,

    val writeMutex: Mutex = Mutex(),

    // Buffers used for various messages. We keep a separate buffer per message type to make it slightly easier to
    // handle concurrency.
    private val newContextWriteBuffer: ByteBuffer = ByteBuffer.allocateDirect(1024 * 512),
    private val newMessageWriteBuffer: ByteBuffer = ByteBuffer.allocateDirect(1024 * 512),
    private val newServiceWriteBuffer: ByteBuffer = ByteBuffer.allocateDirect(1024 * 32),
) {
    init {
        // NOTE(Dan): This will force initialize all buffers with their metadata
        runBlocking {
            clearServiceMessages()
            clearContextMessages()
            clearMessages()
        }
    }

    suspend fun flushServiceMessage() {
        writeMutex.withLock {
            newServiceWriteBuffer.flip()
            if (newServiceWriteBuffer.remaining() > 8) {
                session.send(Frame.Binary(true, newServiceWriteBuffer))
            }
            newServiceWriteBuffer.clear()
            newServiceWriteBuffer.putLong(1)
        }
    }

    suspend fun flushContextMessage() {
        writeMutex.withLock {
            newContextWriteBuffer.flip()
            if (newContextWriteBuffer.remaining() > 8) {
                session.send(Frame.Binary(true, newContextWriteBuffer))
            }
            newContextWriteBuffer.clear()
            newContextWriteBuffer.putLong(2)
        }
    }

    suspend fun flushLogsMessage() {
        writeMutex.withLock {
            newMessageWriteBuffer.flip()
            if (newMessageWriteBuffer.remaining() > 8) {
                session.send(Frame.Binary(true, newMessageWriteBuffer))
            }
            newMessageWriteBuffer.clear()
            newMessageWriteBuffer.putLong(3)
        }
    }

    suspend fun clearServiceMessages() {
        writeMutex.withLock {
            newServiceWriteBuffer.clear()
            newServiceWriteBuffer.putLong(1)
        }
    }

    suspend fun clearContextMessages() {
        writeMutex.withLock {
            newContextWriteBuffer.clear()
            newContextWriteBuffer.putLong(2)
        }
    }

    suspend fun clearMessages() {
        writeMutex.withLock {
            newMessageWriteBuffer.clear()
            newMessageWriteBuffer.putLong(3)
        }
    }

    // NOTE(Dan): opcode 4 is used for blobs
    // TODO(Dan): move opcode 4 down here?

    suspend fun acceptMessage(message: BinaryDebugMessage<*>, contextIds: ArrayList<Long>) {
        val service = activeService ?: return
        if (message.importance.ordinal < minimumLevel.ordinal) return
        val services = trackedServices.get()
        val trackedService = services.values.find { it.title == service }
        if (trackedService == null || trackedService.generation != message.ctxGeneration) return
        if (!contextIds.contains(message.ctxId.toLong())) return

        writeMutex.withLock {
            if (newMessageWriteBuffer.remaining() < FRAME_SIZE) return
            val oldPos = message.buf.position()
            val oldLim = message.buf.limit()

            message.buf.position(message.offset)
            message.buf.limit(message.offset + FRAME_SIZE)

            newMessageWriteBuffer.put(message.buf)

            message.buf.position(oldPos)
            message.buf.limit(oldLim)
        }
    }

    fun skipContext(generation: Long, context: DebugContextDescriptor, contexts: List<Long>): Boolean {
        val service = activeService ?: run {
            if (debugSkip) println("skipping entry - no active service")
            return true
        }
        val services = trackedServices.get()
        val trackedService = services.values.find { it.title == service }
        if (trackedService == null || trackedService.generation != generation) {
            if (debugSkip) println("skipping entry - bad tracked")
            return true
        }
        if (!contexts.contains(context.parent.toLong())) {
            if (debugSkip) println("skipping entry - bad parent")
            return true
        }
        if (context.importance < this.minimumLevel) {
            if (debugSkip) println("skipping entry - unimportant")
            return true
        }
        if (this.activeContexts.singleOrNull() == 1L && context.parent != 1) {
            if (debugSkip) println("skipping entry - not root")
            return true
        }

        // NOTE(Jonas): Context is root, which is needed to filter based on query
        val queryFilter = this.filterQuery
        if (queryFilter != null && context.parent == 1) {
            if (!context.name.contains(queryFilter, ignoreCase = true)) {
                if (debugSkip) println("skipping entry - bad query")
                return true
            }
        }

        val filters = this.filters
        if (filters != null && !filters.contains(context.type)) {
            if (debugSkip) println("skipping entry - bad filter")
            return true
        }
        return false
    }

    suspend fun acceptContext(generation: Long, context: DebugContextDescriptor, contextIds: List<Long>) {
        if (skipContext(generation, context, contextIds)) return

        writeMutex.withLock {
            if (newContextWriteBuffer.remaining() < DebugContextDescriptor.size) return
            val oldPos = context.buf.position()
            val oldLim = context.buf.limit()

            context.buf.position(context.offset)
            context.buf.limit(context.offset + DebugContextDescriptor.size)

            newContextWriteBuffer.put(context.buf)

            context.buf.position(oldPos)
            context.buf.limit(oldLim)
        }
    }

    suspend fun acceptService(serviceName: String, generation: Long) {
        writeMutex.withLock {
            val encodedServiceName = serviceName.encodeToByteArray()
            if (encodedServiceName.size >= MAX_SERVICENAME_LENGTH) return
            if (newServiceWriteBuffer.remaining() < MAX_SERVICENAME_LENGTH) return

            val encodedGeneration = generation.toString().encodeToByteArray()
            if (encodedGeneration.size >= MAX_GENERATION_LENGTH) return

            newServiceWriteBuffer.put(encodedServiceName)

            val emptyBytes = ByteArray(MAX_SERVICENAME_LENGTH - encodedServiceName.size)
            newServiceWriteBuffer.put(emptyBytes)

            newServiceWriteBuffer.put(encodedGeneration)
            val emptyGenerationBytes = ByteArray(MAX_GENERATION_LENGTH - encodedGeneration.size)
            newServiceWriteBuffer.put(emptyGenerationBytes)
        }
    }
}

const val MAX_GENERATION_LENGTH = 16
const val MAX_SERVICENAME_LENGTH = 256 - MAX_GENERATION_LENGTH
