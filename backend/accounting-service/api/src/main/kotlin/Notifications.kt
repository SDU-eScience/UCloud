package dk.sdu.cloud.accounting.api

import dk.sdu.cloud.auth.api.RefreshingJWTAuthenticator
import dk.sdu.cloud.calls.client.OutgoingWSCall
import dk.sdu.cloud.calls.client.OutgoingWSRequestInterceptor
import dk.sdu.cloud.calls.client.RpcClient
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.project.api.Project
import dk.sdu.cloud.service.Logger
import dk.sdu.cloud.toReadableStacktrace
import io.ktor.network.util.*
import io.ktor.util.*
import io.ktor.utils.io.pool.*
import io.ktor.websocket.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicLong

object ApmNotifications {
    fun subscribe(
        scope: CoroutineScope,
        auth: RefreshingJWTAuthenticator,
        rpcClient: RpcClient,
        receiveUpdatesFrom: AtomicLong,
    ): ReceiveChannel<NotificationMessage> {
        val interceptor = (rpcClient.getInterceptor(OutgoingWSCall) as? OutgoingWSRequestInterceptor?)
            ?: error("Could not get WS connection!")

        val channel = Channel<NotificationMessage>(Channel.BUFFERED)

        scope.launch {
            while (coroutineContext.isActive) {
                try {
                    val connection = interceptor.connectionPool.retrieveConnection(PATH, null)
                    val session = connection.underlyingSession

                    val projects = HashMap<Int, Project>()
                    val products = HashMap<Int, ProductCategory>()
                    val users = HashMap<Int, String>()

                    bufferPool.useInstance { buf ->
                        buf.put(OP_AUTH)
                        buf.putLong(receiveUpdatesFrom.get())
                        buf.putLong(0) // Flags
                        buf.putString(auth.retrieveTokenRefreshIfNeeded())
                        session.send(Frame.Binary(true, buf))
                    }

                    while (session.isActive) {
                        val nextFrame = session.incoming.receiveCatching().getOrNull() ?: break
                        while (nextFrame.buffer.hasRemaining()) {
                            val message = NotificationMessage.read(
                                nextFrame.buffer,
                                projects,
                                products,
                                users,
                            ) ?: continue
                            channel.send(message)
                        }
                    }

                    log.info("ApmNotifications terminated normally! Re-opening connection in 5 seconds.")
                    delay(5000)
                } catch (ex: Throwable) {
                    log.warn("Caught exception while monitoring events! ${ex.toReadableStacktrace()}")
                    delay(5000)
                }
            }
        }

        return channel
    }

    const val PATH = "/api/accounting/notifications"

    const val OP_AUTH = 0.toByte()
    const val OP_WALLET = 1.toByte()
    const val OP_PROJECT = 2.toByte()
    const val OP_CATEGORY_INFO = 3.toByte()
    const val OP_USER_INFO = 4.toByte()

    private val log = Logger("ApmNotifications")
    private val bufferPool by lazy { DirectByteBufferPool(4, 1024 * 1024 * 16) }
}

private fun ByteBuffer.putString(text: String) {
    val encoded = text.encodeToByteArray()
    putInt(encoded.size)
    put(encoded)
}

private fun ByteBuffer.getString(): String {
    val size = getInt()
    val bytes = ByteArray(size)
    return get(bytes).decodeString()
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
                        WalletOwner.User(users[workspaceRef] ?: error("Unknown user: $workspaceRef"))
                    } else {
                        WalletOwner.Project(projects[workspaceRef]?.id ?: error("Unknown project: $workspaceRef"))
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
