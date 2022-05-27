package dk.sdu.cloud.application

import dk.sdu.cloud.debug.CircularList
import dk.sdu.cloud.debug.ClientToServer
import dk.sdu.cloud.debug.DebugMessage
import dk.sdu.cloud.debug.ServerToClient
import dk.sdu.cloud.debug.ServiceMetadata
import dk.sdu.cluod.debug.defaultMapper
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.selects.select

const val MAX_SERVICE_ID = 1024 * 16

class SessionManager(private val watchedFolders: List<String>) {
    private val watcher = LogWatcher(watchedFolders)

    private val sessions = ArrayList<Session>()
    private val incomingClients = Channel<Session>()
    private val clientsClosing = Channel<Session>()

    private val metadataByService = arrayOfNulls<ServiceMetadata>(MAX_SERVICE_ID)
    private val messagesByService = arrayOfNulls<CircularList<DebugMessage>>(MAX_SERVICE_ID)
    private val pathsByService = arrayOfNulls<HashMap<String, List<String>>>(MAX_SERVICE_ID)

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
                        val (message, id) = messageAndId
                        var buffer = messagesByService[id]
                        if (buffer == null) {
                            val newBuffer = CircularList<DebugMessage>(SCROLL_BACK_SIZE)
                            messagesByService[id] = newBuffer
                            buffer = newBuffer
                        }

                        buffer.add(message)

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
                        for (session in sessions) {
                            if (session.requestScrollback) {
                                val messages = messagesByService.getOrNull(session.currentService) ?: continue
                                messages.iterator().asSequence().chunked(500).forEachIndexed { index, chunk ->
                                    session.send(ServerToClient.Log(index == 0, chunk))
                                }
                                session.requestScrollback = false
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
