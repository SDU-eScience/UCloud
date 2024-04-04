package dk.sdu.cloud.controllers

import dk.sdu.cloud.*
import dk.sdu.cloud.accounting.api.*
import dk.sdu.cloud.auth.api.RefreshingJWTAuthenticator
import dk.sdu.cloud.calls.client.*
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.plugins.AllocationPlugin
import dk.sdu.cloud.project.api.v2.*
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.Logger
import dk.sdu.cloud.service.Time
import dk.sdu.cloud.sql.useAndInvoke
import dk.sdu.cloud.sql.useAndInvokeAndDiscard
import dk.sdu.cloud.sql.withSession
import dk.sdu.cloud.utils.toV2Id
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.selects.onTimeout
import kotlinx.coroutines.selects.select
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max

class EventController(
    private val controllerContext: ControllerContext,
) : Controller {
    private val replayRequests = Channel<WalletOwner.User>(Channel.BUFFERED)

    override fun configure(rpcServer: RpcServer) {
        if (!controllerContext.configuration.shouldRunServerCode()) return

        controllerContext.configuration.plugins.temporary
            .onConnectionCompleteHandlers.add(this@EventController::onConnectionComplete)

        startLoop()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun startLoop() {
        ProcessingScope.launch {
            val replayFrom = AtomicLong(0L)
            dbConnection.withSession { session ->
                session.prepareStatement(
                    """
                        select replay_from
                        from events.replay_from
                        limit 1
                    """
                ).useAndInvoke(
                    readRow = { row ->
                        replayFrom.set(row.getLong(0)!!)
                    }
                )
            }

            // Replay users who connected in proximity to the "replayFrom" value
            launch {
                val usersToReplay = HashSet<String>()
                dbConnection.withSession { session ->
                    session.prepareStatement(
                        """
                            select ucloud_id
                            from user_mapping
                            where
                                created_at < to_timestamp(:replay_before::int8 / 1000.0)
                                and created_at > to_timestamp(:replay_after::int8 / 1000.0)
                        """
                    ).useAndInvoke(
                        prepare = {
                            bindLong("replay_before", replayFrom.get() + (1000L * 60 * 10))
                            bindLong("replay_after", replayFrom.get() - (1000L * 60 * 10))
                        },
                        readRow = { row ->
                            usersToReplay.add(row.getString(0)!!)
                        }
                    )
                }

                // NOTE(Dan): We do this without holding on to the session in case we hit the replayRequests buffer
                // limit. In some rare circumstances that might lead to starvation of the connection pool.
                for (user in usersToReplay) {
                    replayRequests.send(WalletOwner.User(user))
                }
            }

            launch {
                while (isActive) {
                    dbConnection.withSession { session ->
                        session.prepareStatement(
                            """
                                insert into events.replay_from(always_one, replay_from)
                                values (1, :replay_from)
                                on conflict (always_one) do update set replay_from = excluded.replay_from
                            """
                        ).useAndInvokeAndDiscard(
                            prepare = {
                                bindLong("replay_from", replayFrom.get())
                            }
                        )
                    }
                    delay(60_000)
                }
            }

            while (isActive) {
                try {
                    val config = controllerContext.configuration
                    val info = HostInfo(
                        config.core.hosts.ucloud.host,
                        config.core.hosts.ucloud.scheme,
                        config.core.hosts.ucloud.port
                    )
                    val webSocketClient = HttpClient(CIO) {
                        install(WebSockets)
                        expectSuccess = false
                        engine {
                            requestTimeout = 0
                        }
                    }

                    val auth = controllerContext.pluginContext.authenticator!!
                    val session = ApmNotifications.subscribe(
                        info,
                        ProcessingScope,
                        auth,
                        webSocketClient,
                        replayFrom,
                    )

                    while (isActive) {
                        select {
                            replayRequests.onReceive { message ->
                                session.replayRequests.send(message)
                            }

                            session.messages.onReceive { message ->
                                replayFrom.set(message.lastUpdate)
                                println(message)

                                when (message) {
                                    is NotificationMessage.ProjectUpdated -> {
                                        synchronizeProjects(listOf(message.project))
                                    }

                                    is NotificationMessage.WalletUpdated -> {
                                        synchronizeWallet(message)
                                    }
                                }
                            }

                            onTimeout(60_000) {
                                // NOTE(Dan): We increment replayFrom ourselves in quiet periods. We set it to some
                                // value lower than the current time to allow for time differences between UCloud/Core
                                // and the provider.

                                val oldValue = replayFrom.get()
                                val now = Time.now()
                                val accountingForTimeDifference = now - 60_000

                                replayFrom.compareAndSet(oldValue, max(oldValue, accountingForTimeDifference))
                            }
                        }
                    }
                } catch (ex: Throwable) {
                    log.warn("Caught exception in EventController: ${ex.toReadableStacktrace()}")
                }
                delay(1000)
            }
        }
    }

    private suspend fun synchronizeProjects(projects: List<Project>) {
        val projectPlugin = controllerContext.configuration.plugins.projects
        val projectsToIgnore = HashSet<String>()

        for (project in projects) {
            try {
                if (!project.specification.canConsumeResources) {
                    projectsToIgnore.add(project.id)
                } else {
                    if (projectPlugin != null) {
                        with(controllerContext.pluginContext) {
                            with(projectPlugin) {
                                onProjectUpdated(project)
                            }
                        }
                    }
                }
            } catch (ex: Throwable) {
                log.warn(
                    "Caught an exception while handling project update: ${project}\n" +
                            ex.toReadableStacktrace().toString()
                )
            }
        }

        if (projectsToIgnore.isNotEmpty()) {
            dbConnection.withSession { session ->
                session.prepareStatement(
                    """
                        insert into events.projects_to_ignore(project_id)
                        select unnest(:project_ids::text[]) on conflict do nothing
                    """
                ).useAndInvokeAndDiscard(
                    prepare = {
                        bindList("project_ids", projectsToIgnore.toList())
                    }
                )
            }
        }
    }

    private suspend fun shouldIgnoreProject(projectId: String): Boolean {
        var shouldIgnore = false
        dbConnection.withSession { session ->
            session.prepareStatement(
                """
                    select project_id
                    from events.projects_to_ignore
                    where project_id = :project_id
                """
            ).useAndInvoke(
                prepare = {
                    bindString("project_id", projectId)
                },
                readRow = { _ ->
                    shouldIgnore = true
                }
            )
        }
        return shouldIgnore
    }

    private suspend fun synchronizeWallet(wallet: NotificationMessage.WalletUpdated) {
        val plugins = controllerContext.configuration.plugins
        val allocationPlugin = plugins.allocations
        val owner = ResourceOwnerWithId.load(wallet.owner, controllerContext.pluginContext) ?: return
        if (owner is ResourceOwnerWithId.Project && shouldIgnoreProject(owner.projectId)) return

        val message = AllocationPlugin.Message(
            owner,
            wallet.category,
            wallet.combinedQuota,
            wallet.locked,
            wallet.lastUpdate,
        )

        if (allocationPlugin != null) {
            with(controllerContext.pluginContext) {
                with(allocationPlugin) {
                    onWalletUpdated(listOf(message))
                }
            }
        }

        plugins.resourcePlugins().forEach { plugin ->
            if (plugin.productAllocationResolved.any { it.category.toV2Id() == wallet.category.toV2Id() }) {
                with(controllerContext.pluginContext) {
                    with(plugin) {
                        onWalletSynchronized(message)
                    }
                }
            }
        }
    }

    private suspend fun onConnectionComplete(ucloudId: String, localId: Int) {
        // TODO Load the projects and all wallets associated to this user (personal workspace and from associated
        //  projects). Replay all of it.
        replayRequests.send(WalletOwner.User(ucloudId))
    }

    companion object : Loggable {
        override val log = logger()
    }
}

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
    abstract val lastUpdate: Long
    data class WalletUpdated(
        val owner: WalletOwner,
        val category: ProductCategory,
        val combinedQuota: Long,
        val locked: Boolean,
        override val lastUpdate: Long,
    ) : NotificationMessage()

    data class ProjectUpdated(
        override val lastUpdate: Long,
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
