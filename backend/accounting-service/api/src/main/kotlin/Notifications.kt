package dk.sdu.cloud.accounting.api

import dk.sdu.cloud.auth.api.RefreshingJWTAuthenticator
import dk.sdu.cloud.calls.client.*
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.project.api.v2.Project
import dk.sdu.cloud.service.Logger
import dk.sdu.cloud.toReadableStacktrace
import io.ktor.client.*
import io.ktor.client.plugins.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicLong

object ApmNotifications {
    data class NotificationSession(
        val replayRequests: SendChannel<WalletOwner.User>,
        val messages: ReceiveChannel<NotificationMessage>,
    )

    suspend fun subscribe(
        targetHost: HostInfo,
        scope: CoroutineScope,
        auth: RefreshingJWTAuthenticator,
        client: HttpClient,
        receiveUpdatesFrom: AtomicLong,
    ): NotificationSession {
        val channel = Channel<NotificationMessage>(Channel.BUFFERED)
        val replayRequests = Channel<WalletOwner.User>(Channel.BUFFERED)

        scope.launch {
            val buf = ByteBuffer.allocateDirect(4096)

            while (coroutineContext.isActive) {
                try {
                    val url = run {
                        val host = targetHost.host.removeSuffix("/")

                        // For some reason ktor's websocket client does not currently work when pointed at WSS, but works fine
                        // when redirected from WS to WSS.
                        val port = targetHost.port ?: if (targetHost.scheme == "https") 443 else 80
                        val scheme = when {
                            targetHost.scheme == "http" -> "ws"
                            targetHost.scheme == "https" -> "wss"
                            port == 80 -> "ws"
                            port == 443 -> "wss"
                            else -> "ws"
                        }

                        val path = PATH.removePrefix("/")
                        "$scheme://$host:$port/$path"
                    }

                    val session = client.webSocketSession(url)
                    val projects = HashMap<Int, Project>()
                    val products = HashMap<Int, ProductCategory>()
                    val users = HashMap<Int, String>()

                    buf.clear()
                    buf.put(OP_AUTH)
                    buf.putLong(receiveUpdatesFrom.get())
                    buf.putLong(0) // Flags
                    buf.putString(auth.retrieveTokenRefreshIfNeeded())
                    buf.flip()
                    session.send(Frame.Binary(true, buf))

                    while (session.isActive) {
                        select {
                            replayRequests.onReceive { request ->
                                buf.clear()
                                buf.put(OP_REPLAY_USER)
                                buf.putString(request.username)
                                buf.flip()
                                session.send(Frame.Binary(true, buf))
                            }

                            session.incoming.onReceive { nextFrame ->
                                val buffer = nextFrame.buffer
                                while (buffer.hasRemaining()) {
                                    val message = NotificationMessage.read(
                                        buffer,
                                        projects,
                                        products,
                                        users,
                                    ) ?: continue
                                    channel.send(message)
                                }
                            }
                        }
                    }

                    log.info("ApmNotifications terminated normally! Re-opening connection in 5 seconds.")
                    delay(5000)
                } catch (ex: Throwable) {
                    ex.printStackTrace()
                    log.warn("Caught exception while monitoring events! ${ex.toReadableStacktrace()}")
                    delay(5000)
                }
            }
        }

        return NotificationSession(replayRequests, channel)
    }

    const val PATH = "/api/accounting/notifications"

    const val OP_AUTH = 0.toByte()
    const val OP_WALLET = 1.toByte()
    const val OP_PROJECT = 2.toByte()
    const val OP_CATEGORY_INFO = 3.toByte()
    const val OP_USER_INFO = 4.toByte()
    const val OP_REPLAY_USER = 5.toByte()

    private val log = Logger("ApmNotifications")
}

private fun ByteBuffer.putString(text: String) {
    val encoded = text.encodeToByteArray()
    putInt(encoded.size)
    put(encoded)
}

private fun ByteBuffer.getString(): String {
    val size = getInt()
    val bytes = ByteArray(size)
    get(bytes)
    return bytes.decodeToString()
}

sealed class NotificationMessage {
    data class WalletUpdated(
        val owner: WalletOwner,
        val category: ProductCategory,
        val combinedQuota: Long,
        val locked: Boolean,
        val lastUpdate: Long,
    ) : NotificationMessage()

    data class ProjectUpdated(
        val lastUpdate: Long,
        val project: Project,
    ) : NotificationMessage()

    companion object {
        fun read(
            buf: ByteBuffer,
            projects: HashMap<Int, Project>,
            products: HashMap<Int, ProductCategory>,
            users: HashMap<Int, String>,
        ): NotificationMessage? {
            when (val opcode = buf.get()) {
                ApmNotifications.OP_WALLET -> {
                    val workspaceRef = buf.getInt()
                    val categoryRef = buf.getInt()
                    val combinedQuota = buf.getLong()
                    val flags = buf.getInt()
                    val lastUpdate = buf.getLong()

                    val isLocked = (flags and 0x1) != 0
                    val isProject = (flags and 0x2) != 0

                    val category = products[categoryRef] ?: error("Unknown category: $categoryRef")
                    val owner = if (isProject) {
                        WalletOwner.Project(projects[workspaceRef]?.id ?: error("Unknown project: $workspaceRef"))
                    } else {
                        WalletOwner.User(users[workspaceRef] ?: error("Unknown user: $workspaceRef"))
                    }

                    return WalletUpdated(owner, category, combinedQuota, isLocked, lastUpdate)
                }

                ApmNotifications.OP_PROJECT -> {
                    // NOTE(Dan): This can be optimized later if it turns out we have a meaningful amount of projects
                    // being transferred through this mechanism. Right now we are assuming that only wallet updates
                    // are likely to be of a significant size.

                    val ref = buf.getInt()
                    val lastUpdated = buf.getLong()
                    val projectJson = buf.getString()
                    val project = defaultMapper.decodeFromString(Project.serializer(), projectJson)

                    projects[ref] = project
                    return ProjectUpdated(lastUpdated, project)
                }

                ApmNotifications.OP_CATEGORY_INFO -> {
                    // NOTE(Dan): This can be optimized later if it turns out we have a meaningful amount of projects
                    // being transferred through this mechanism. Right now we are assuming that only wallet updates
                    // are likely to be of a significant size.

                    val ref = buf.getInt()
                    val categoryJson = buf.getString()
                    val category = defaultMapper.decodeFromString(ProductCategory.serializer(), categoryJson)
                    products[ref] = category
                    return null
                }

                ApmNotifications.OP_USER_INFO -> {
                    val ref = buf.getInt()
                    val username = buf.getString()
                    users[ref] = username
                    return null
                }

                else -> {
                    error("Unknown opcode: $opcode")
                }
            }
        }
    }
}
