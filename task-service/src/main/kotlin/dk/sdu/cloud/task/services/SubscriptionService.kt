package dk.sdu.cloud.task.services

import com.fasterxml.jackson.module.kotlin.jacksonTypeRef
import dk.sdu.cloud.calls.server.CallHandler
import dk.sdu.cloud.calls.server.WSCall
import dk.sdu.cloud.calls.server.WSSession
import dk.sdu.cloud.calls.server.sendWSMessage
import dk.sdu.cloud.events.EventStream
import dk.sdu.cloud.events.JsonEventStream
import dk.sdu.cloud.service.BroadcastingStream
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.task.api.TaskUpdate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.*

private typealias Socket = CallHandler<*, TaskUpdate, *>

private class UserHandler(
    val user: String,
    private val stream: EventStream<TaskUpdate>,
    private val broadcastingStream: BroadcastingStream,
    private val scope: CoroutineScope
) {
    private val mutex = Mutex()
    private val sockets = HashMap<String, Socket>()

    suspend fun addSocket(socketId: String, socket: Socket) {
        mutex.withLock {
            sockets[socketId] = socket
            if (sockets.size == 1) {
                initialize()
            }
        }
    }

    suspend fun removeSocketAndReturnIfEmpty(socketId: String): Boolean {
        mutex.withLock {
            sockets.remove(socketId)

            if (sockets.isEmpty()) {
                broadcastingStream.unsubscribe(stream)
                return true
            }

            return false
        }
    }

    private suspend fun initialize() {
        broadcastingStream.subscribe(stream) { update ->
            scope.launch {
                mutex.withLock {
                    sockets.forEach { it.value.sendWSMessage(update) }
                }
            }
        }
    }
}

class SubscriptionService(
    private val broadcastingStream: BroadcastingStream,
    private val scopeForWSMessages: CoroutineScope
) {
    private val userToHandler = HashMap<String, UserHandler>()
    private val socketIdToHandler = HashMap<String, UserHandler>()

    private val globalLock = Mutex()

    suspend fun onConnection(user: String, socket: Socket) {
        val socketId = (socket.ctx as WSCall).session.id

        globalLock.withLock {
            val existing = userToHandler[user]
            val userHandler = if (existing != null) {
                existing.addSocket(socketId, socket)
                existing
            } else {
                val userHandler = UserHandler(user, eventStream(user), broadcastingStream, scopeForWSMessages)
                userHandler.addSocket(socketId, socket)
                userToHandler[user] = userHandler
                userHandler
            }

            socketIdToHandler[socketId] = userHandler
        }
    }

    private fun eventStream(user: String) =
        JsonEventStream<TaskUpdate>("task-sub-${user}", jacksonTypeRef(), { it.jobId })

    suspend fun onDisconnect(session: WSSession) {
        globalLock.withLock {
            val handler = socketIdToHandler.remove(session.id) ?: return

            if (handler.removeSocketAndReturnIfEmpty(session.id)) {
                userToHandler.remove(handler.user)
            }
        }
    }

    suspend fun onTaskUpdate(user: String, update: TaskUpdate) {
        broadcastingStream.broadcast(update, eventStream(user))
    }

    companion object : Loggable {
        override val log = logger()
    }
}
