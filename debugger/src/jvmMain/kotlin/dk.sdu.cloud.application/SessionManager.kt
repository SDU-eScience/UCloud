package dk.sdu.cloud.application

import dk.sdu.cloud.debug.CircularList
import dk.sdu.cloud.debug.ClientToServer
import dk.sdu.cloud.debug.DebugMessage
import dk.sdu.cloud.debug.MessageImportance
import dk.sdu.cloud.debug.ServerToClient
import dk.sdu.cloud.debug.ServiceMetadata
import dk.sdu.cloud.debug.Time
import dk.sdu.cluod.debug.defaultMapper
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.selects.select
import java.util.NavigableSet
import java.util.SortedSet
import java.util.TreeSet
import kotlin.math.floor

const val MAX_SERVICE_ID = 1024 * 16

class SessionManager(watchedFolders: List<String>) {
    private val watcher = LogWatcher(watchedFolders)

    private val sessions = ArrayList<Session>()
    private val incomingClients = Channel<Session>()
    private val clientsClosing = Channel<Session>()

    private val metadataByService = arrayOfNulls<ServiceMetadata>(MAX_SERVICE_ID)
    private val messagesByService = arrayOfNulls<NavigableSet<DebugMessage>>(MAX_SERVICE_ID)
    private val pathsByService = arrayOfNulls<HashMap<String, List<String>>>(MAX_SERVICE_ID)
    private val clientStatsByService = arrayOfNulls<CircularList<ResponseStat>>(MAX_SERVICE_ID)
    private val serverStatsByService = arrayOfNulls<CircularList<ResponseStat>>(MAX_SERVICE_ID)
    private val logStatsByService = arrayOfNulls<ServerToClient.Statistics.LogStats>(MAX_SERVICE_ID)

    private var anyChanges = false

    private data class ResponseStat(
        val success: Boolean,
        val responseTime: Long,
    )

    private fun Sequence<ResponseStat>.toResponseStats(): ServerToClient.Statistics.ResponseStats {
        val asList = toList()
        val sortedResponseTimes = asList.map { it.responseTime }.sorted()
        if (sortedResponseTimes.isEmpty()) {
            return ServerToClient.Statistics.ResponseStats(
                p25 = 0.0,
                p50 = 0.0,
                p75 = 0.0,
                p99 = 0.0,
                min = 0.0,
                avg = 0.0,
                max = 0.0,
                count = 0,
                successes = 0,
                errors = 0,
            )
        }
        val average = sortedResponseTimes.average()
        val p25 = sortedResponseTimes[floor(sortedResponseTimes.size * 0.25).toInt()]
        val p50 = sortedResponseTimes[floor(sortedResponseTimes.size * 0.50).toInt()]
        val p75 = sortedResponseTimes[floor(sortedResponseTimes.size * 0.75).toInt()]
        val p99 = sortedResponseTimes[floor(sortedResponseTimes.size * 0.99).toInt()]
        val min = sortedResponseTimes.minOrNull() ?: 0L
        val max = sortedResponseTimes.maxOrNull() ?: 0L
        val successes = asList.count { it.success }
        val errors = sortedResponseTimes.size - successes

        return ServerToClient.Statistics.ResponseStats(
            p25.toDouble(),
            p50.toDouble(),
            p75.toDouble(),
            p99.toDouble(),
            min.toDouble(),
            average,
            max.toDouble(),
            sortedResponseTimes.size.toLong(),
            successes.toLong(),
            errors.toLong()
        )
    }

    @OptIn(DelicateCoroutinesApi::class, ExperimentalCoroutinesApi::class)
    fun start() {
        watcher.start()

        GlobalScope.launch(Dispatchers.Default) {
            val messageChannel = watcher.messageChannel
            val metadataChannel = watcher.metadataChannel
            val outgoingMessages = ArrayList<MessageAndId>()

            while (isActive) {
                select<Unit> {
                    messageChannel.onReceive { messageAndId ->
                        anyChanges = true
                        val (message, id) = messageAndId

                        run {
                            var buffer = messagesByService[id]
                            if (buffer == null) {
                                val newBuffer = TreeSet<DebugMessage>()
                                messagesByService[id] = newBuffer
                                buffer = newBuffer
                            }

                            buffer.add(message)
                        }

                        run {
                            val stats = logStatsByService[id] ?: run {
                                val newStats = ServerToClient.Statistics.LogStats()
                                logStatsByService[id] = newStats
                                newStats
                            }

                            when (message.importance) {
                                MessageImportance.TELL_ME_EVERYTHING -> stats.everything++
                                MessageImportance.IMPLEMENTATION_DETAIL -> stats.details++
                                MessageImportance.THIS_IS_NORMAL -> stats.normal++
                                MessageImportance.THIS_IS_ODD -> stats.odd++
                                MessageImportance.THIS_IS_WRONG -> stats.wrong++
                                MessageImportance.THIS_IS_DANGEROUS -> stats.dangerous++
                            }
                        }

                        if (message is DebugMessage.ClientResponse || message is DebugMessage.ServerResponse) {
                            val previousMessages = messagesByService[id]
                            val statsMap = if (message is DebugMessage.ClientResponse) {
                                clientStatsByService
                            } else {
                                serverStatsByService
                            }

                            if (previousMessages != null) {
                                for (prev in previousMessages.descendingIterator()) {
                                    val isRequest =
                                        (prev is DebugMessage.ClientRequest || prev is DebugMessage.ServerRequest) &&
                                                (prev.context.id == message.context.id ||
                                                        prev.context.id == message.context.parent)

                                    if (isRequest) {
                                        var stats = statsMap[id]
                                        if (stats == null) {
                                            stats = CircularList(SCROLL_BACK_SIZE)
                                            statsMap[id] = stats
                                        }

                                        val responseCode = when (message) {
                                            is DebugMessage.ClientResponse -> message.responseCode
                                            is DebugMessage.ServerResponse -> message.responseCode
                                            else -> 500
                                        }

                                        stats.add(
                                            ResponseStat(
                                                responseCode !in 500..599,
                                                message.timestamp - prev.timestamp
                                            )
                                        )
                                        break
                                    }
                                }
                            }
                        }

                        var paths = pathsByService[id]
                        if (paths == null) {
                            val newPaths = hashMapOf<String, List<String>>()
                            pathsByService[id] = newPaths
                            paths = newPaths
                        }

                        val newPath = buildList {
                            val parent = message.context.parent
                            if (parent != null) {
                                addAll(paths[parent] ?: emptyList())
                            }
                            add(message.context.id)
                        }
                        paths[message.context.id] = newPath

                        message.context.path = newPath
                        message.context.depth = newPath.size

                        outgoingMessages.add(messageAndId)
                    }

                    metadataChannel.onReceive { metadata ->
                        if (metadata != metadataByService[metadata.id]) {
                            metadataByService[metadata.id] = metadata
                            for (session in sessions) {
                                session.send(ServerToClient.NewService(metadata))
                            }
                        }
                    }

                    incomingClients.onReceive { session ->
                        sessions.add(session)

                        for (metadata in metadataByService) {
                            if (metadata == null) continue
                            session.send(ServerToClient.NewService(metadata))
                        }
                    }

                    clientsClosing.onReceive { session ->
                        sessions.remove(session)
                    }

                    onTimeout(500) {
                        val hadAnyChanges = anyChanges
                        anyChanges = false

                        for (session in sessions) {
                            val hadScrollbackRequested = session.requestScrollback
                            if (hadScrollbackRequested) {
                                val messages = messagesByService.getOrNull(session.currentService)
                                messages
                                    ?.tailSet(DebugMessage.sortingKey(Time.now() - (1000L * 60 * 30)))
                                    ?.iterator()
                                    ?.asSequence()
                                    ?.chunked(500)
                                    ?.forEachIndexed { index, chunk ->
                                        session.send(ServerToClient.Log(index == 0, chunk))
                                    }
                                if (messages == null) {
                                    session.send(ServerToClient.Log(true, emptyList()))
                                }
                                session.requestScrollback = false
                            }

                            if (hadAnyChanges || hadScrollbackRequested) {
                                for (interest in session.interests) {
                                    var clientResponse = emptySequence<ResponseStat>()
                                    var serverResponse = emptySequence<ResponseStat>()
                                    val logResponse = ServerToClient.Statistics.LogStats()
                                    for ((serviceIdx, metadata) in metadataByService.withIndex()) {
                                        if (metadata == null) break
                                        if (interest == metadata.path || metadata.path.startsWith("$interest/")) {
                                            clientResponse += clientStatsByService[serviceIdx]?.iterator()?.asSequence()
                                                ?: emptySequence()
                                            serverResponse += serverStatsByService[serviceIdx]?.iterator()?.asSequence()
                                                ?: emptySequence()
                                            logResponse += logStatsByService[serviceIdx]
                                                ?: ServerToClient.Statistics.LogStats()
                                        }
                                    }

                                    session.send(ServerToClient.Statistics(
                                        interest,
                                        clientResponse.toResponseStats(),
                                        serverResponse.toResponseStats(),
                                        logResponse
                                    ))
                                }
                            }
                        }

                        if (outgoingMessages.isNotEmpty()) {
                            for (session in sessions) {
                                val messages = outgoingMessages
                                    .asSequence()
                                    .filter { it.id == session.currentService }
                                    .map { it.message }
                                    .toList()

                                if (messages.isNotEmpty()) {
                                    session.send(ServerToClient.Log(false, messages))
                                }
                            }

                            outgoingMessages.clear()
                        }
                    }
                }
            }
        }
    }

    suspend fun registerAndHandle(session: DefaultWebSocketSession) {
        val newSession = Session(session)
        incomingClients.send(newSession)
        newSession.handle(
            onClose = { clientsClosing.send(newSession) }
        )
    }

    companion object {
        private const val SCROLL_BACK_SIZE = 1024 * 16
    }
}

class Session(val session: DefaultWebSocketSession) {
    var currentService = -1
    var requestScrollback = true
    var interests: List<String> = emptyList()

    suspend fun handle(onClose: suspend () -> Unit) {
        try {
            for (frame in session.incoming) {
                if (frame !is Frame.Text) continue
                val text = frame.readText()
                val message = try {
                    defaultMapper.decodeFromString(ClientToServer.serializer(), text)
                } catch (ex: Throwable) {
                    println(
                        "Invalid message from client!" +
                                "\n  Message: ${text.removeSuffix("\n")}" +
                                "\n  ${ex::class.simpleName}: ${ex.message?.prependIndent("    ")?.trim()}"
                    )
                    continue
                }

                handleMessage(message)
            }
        } finally {
            onClose()
        }
    }

    private suspend fun handleMessage(message: ClientToServer) {
        when (message) {
            is ClientToServer.OpenService -> {
                currentService = message.service
                requestScrollback = true
            }

            is ClientToServer.UpdateInterests -> {
                interests = message.interests
            }
        }
    }

    suspend fun send(message: ServerToClient) {
        val frame = Frame.Text(
            defaultMapper.encodeToString(
                ServerToClient.serializer(),
                message,
            )
        )

        coroutineScope {
            launch(Dispatchers.Default) {
                session.send(frame)
            }
        }
    }
}
