package dk.sdu.cloud.controllers

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.FindByStringId
import dk.sdu.cloud.ProcessingScope
import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.accounting.api.providers.ResourceBrowseRequest
import dk.sdu.cloud.accounting.api.providers.ResourceRetrieveRequest
import dk.sdu.cloud.app.orchestrator.api.*
import dk.sdu.cloud.calls.BulkRequest
import dk.sdu.cloud.calls.BulkResponse
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orNull
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.calls.server.WSCall
import dk.sdu.cloud.dbConnection
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.ipc.IpcContainer
import dk.sdu.cloud.ipc.IpcServer
import dk.sdu.cloud.ipc.handler
import dk.sdu.cloud.ipc.sendRequest
import dk.sdu.cloud.plugins.ComputePlugin
import dk.sdu.cloud.plugins.ipcClient
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.Logger
import dk.sdu.cloud.service.Time
import dk.sdu.cloud.sql.bindStringNullable
import dk.sdu.cloud.sql.useAndInvoke
import dk.sdu.cloud.sql.useAndInvokeAndDiscard
import dk.sdu.cloud.sql.withSession
import dk.sdu.cloud.utils.secureToken
import dk.sdu.cloud.plugins.SyncthingPlugin
import dk.sdu.cloud.plugins.rpcClient
import dk.sdu.cloud.service.SimpleCache
import dk.sdu.cloud.toReadableStacktrace
import dk.sdu.cloud.utils.shellTracer
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.util.date.*
import io.ktor.util.pipeline.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import java.net.URLDecoder
import java.util.concurrent.atomic.AtomicReference

class ComputeController(
    controllerContext: ControllerContext,
    private val envoyConfig: EnvoyConfigurationService?,
    private val ktor: Application,
) : BaseResourceController<Product.Compute, ComputeSupport, Job, ComputePlugin, JobsProvider>(controllerContext),
    IpcController {
    override fun retrievePlugins() = controllerContext.configuration.plugins.jobs.values
    override fun retrieveApi(providerId: String): JobsProvider = JobsProvider(providerId)

    override fun RpcServer.configureCustomEndpoints(plugins: Collection<ComputePlugin>, api: JobsProvider) {
        val config = controllerContext.configuration
        val providerId = controllerContext.configuration.core.providerId
        val shells = Shells(providerId)

        ProcessingScope.launch(Dispatchers.IO) {
            reinitializeAllComputeSessions()
        }

        implement(api.extend) {
            if (!config.shouldRunUserCode()) throw RPCException.fromStatusCode(HttpStatusCode.NotFound)

            ok(
                dispatchToPlugin(plugins, request.items, { it.job }) { plugin, request ->
                    with(plugin) { extendBulk(request) }
                }
            )
        }

        val maxStreams = 1024 * 32
        val streams = Array<AtomicReference<String?>>(maxStreams) { AtomicReference(null) }

        implement(api.follow) {
            if (!config.shouldRunUserCode()) throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
            val wsContext = ctx as WSCall

            when (request) {
                is JobsProviderFollowRequest.Init -> {
                    val plugin = lookupPlugin(request.job.specification.product)

                    val token = secureToken(64)
                    var streamId: Int? = null
                    for (i in 0 until maxStreams) {
                        if (streams[i].compareAndSet(null, token)) {
                            streamId = i
                            break
                        }
                    }

                    if (streamId == null) {
                        throw RPCException("Server is too busy", HttpStatusCode.BadGateway)
                    }

                    wsContext.sendMessage(
                        JobsProviderFollowResponse(streamId.toString(), -1),
                        JobsProviderFollowResponse.serializer()
                    )

                    val ctx = ComputePlugin.FollowLogsContext(
                        requestContext(controllerContext),
                        isActive = { streams[streamId].compareAndSet(token, token) && wsContext.session.isActive },
                        emitStdout = { rank, message ->
                            wsContext.sendMessage(
                                JobsProviderFollowResponse(
                                    streamId.toString(),
                                    rank,
                                    stdout = message
                                ),
                                JobsProviderFollowResponse.serializer()
                            )
                        },
                        emitStderr = { rank, message ->
                            wsContext.sendMessage(
                                JobsProviderFollowResponse(
                                    streamId.toString(),
                                    rank,
                                    stderr = message
                                ),
                                JobsProviderFollowResponse.serializer()
                            )
                        }
                    )

                    with(ctx) {
                        with(plugin) {
                            follow(request.job)
                        }
                    }

                    ok(JobsProviderFollowResponse("", -1))
                }

                is JobsProviderFollowRequest.CancelStream -> {
                    var idx = -1
                    for (i in 0 until maxStreams) {
                        if (streams[i].get() == request.streamId) {
                            idx = i
                            break
                        }
                    }
                    if (idx == -1) throw RPCException.fromStatusCode(HttpStatusCode.BadRequest)

                    streams[idx].compareAndSet(request.streamId, null)
                    ok(JobsProviderFollowResponse("", -1))
                }
            }
        }

        implement(api.openInteractiveSession) {
            shellTracer { "Entering openInteractiveSession $request" }
            if (!config.shouldRunUserCode()) throw RPCException.fromStatusCode(HttpStatusCode.NotFound)

            val pluginResults = dispatchToPlugin(plugins, request.items, { it.job }) { plugin, request ->
                with(plugin) {
                    BulkResponse(
                        openInteractiveSessionBulk(request).responses.map { plugin.pluginName to it }
                    )
                }
            }

            val results = ArrayList<OpenSession>()
            val ipcClient = controllerContext.pluginContext.ipcClient

            for ((request, nameAndResponse) in request.items.zip(pluginResults.responses)) {
                val (pluginName, response) = nameAndResponse
                shellTracer { "Configuring session $pluginName $response" }

                val sessionId = ipcClient.sendRequest(
                    ComputeSessionIpc.create,
                    ComputeSessionIpc.Session(
                        request.sessionType,
                        request.job.id,
                        request.rank,
                        pluginName,
                        response.pluginData,
                        response.target
                    )
                ).id

                val session = when (request.sessionType) {
                    InteractiveSessionType.WEB -> {
                        val target = response.target!!
                        if (target.webSessionIsPublic) {
                            OpenSession.Web(
                                request.job.id,
                                request.rank,
                                "http://${target.ingress}"
                            )
                        } else {
                            OpenSession.Web(
                                request.job.id,
                                request.rank,
                                "http://${target.ingress}/ucloud/$providerId/authorize-app?token=${sessionId}",
                                config.core.hosts.self?.toStringOmitDefaultPort()
                            )
                        }
                    }

                    InteractiveSessionType.VNC -> {
                        val password = request.job.status.resolvedApplication?.invocation?.vnc?.password

                        OpenSession.Vnc(
                            request.job.id,
                            request.rank,
                            "${config.core.hosts.self?.toStringOmitDefaultPort() ?: ""}/ucloud/$providerId/vnc?token=${sessionId}",
                            password,
                            config.core.hosts.self?.toStringOmitDefaultPort()
                        )
                    }

                    InteractiveSessionType.SHELL -> {
                        OpenSession.Shell(
                            request.job.id,
                            request.rank,
                            sessionId,
                            config.core.hosts.self?.toStringOmitDefaultPort()
                        )
                    }
                }

                results.add(session)
            }

            ok(BulkResponse(results))
        }

        implement(api.retrieveUtilization) {
            if (!config.shouldRunServerCode()) throw RPCException.fromStatusCode(HttpStatusCode.NotFound)

            for (plugin in retrievePlugins()) {
                if (plugin.productAllocationResolved.any { it.category.name == request.categoryId }) {
                    val result = with(requestContext(controllerContext)) {
                        with(plugin) {
                            retrieveClusterUtilization(request.categoryId)
                        }
                    }

                    ok(result)
                    return@implement
                }
            }

            throw RPCException("Not supported", HttpStatusCode.BadRequest)
        }

        implement(api.suspend) {
            if (!config.shouldRunUserCode()) throw RPCException.fromStatusCode(HttpStatusCode.NotFound)

            val result = dispatchToPlugin(plugins, request.items, { it.job }) { plugin, request ->
                with(plugin) { suspendBulk(request) }
            }

            ok(result)
        }

        implement(api.unsuspend) {
            dispatchToPlugin(plugins, request.items, { it.job }) { plugin, request ->
                with(plugin) { createBulk(BulkRequest(request.items.map { it.job })) }
            }
            ok(BulkResponse(request.items.map { null }))
        }

        implement(api.terminate) {
            if (!config.shouldRunUserCode()) throw RPCException.fromStatusCode(HttpStatusCode.NotFound)

            ok(
                dispatchToPlugin(plugins, request.items, { it }) { plugin, request ->
                    with(plugin) { terminateBulk(request) }
                }
            )
        }

        implement(shells.open) {
            shellTracer { "Reaching shells.open ($request)" }
            if (!config.shouldRunUserCode()) throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
            shellTracer { "Arrived at the correct instance!" }
            val wsContext = ctx as WSCall
            val ipcClient = controllerContext.pluginContext.ipcClient

            when (val req = request) {
                is ShellRequest.Initialize -> {
                    shellTracer { "Initialize start" }
                    val sessionInformation = runCatching {
                        ipcClient.sendRequest(
                            ComputeSessionIpc.retrieve,
                            FindByStringId(req.sessionIdentifier)
                        )
                    }.getOrNull()

                    shellTracer { "Session information is: $sessionInformation" }

                    if (sessionInformation?.sessionType != InteractiveSessionType.SHELL) {
                        shellTracer { "Bad shell type" }
                        throw RPCException.fromStatusCode(HttpStatusCode.Unauthorized)
                    }

                    shellTracer { "Looking for plugin handler" }
                    val pluginHandler = controllerContext.configuration.plugins.jobs[sessionInformation.pluginName]
                        ?: throw RPCException("Bad session identifier supplied", HttpStatusCode.Unauthorized)
                    shellTracer { "Plugin handler is $pluginHandler" }

                    val channel = Channel<ShellRequest>(Channel.BUFFERED)
                    val ctx = ComputePlugin.ShellContext(
                        requestContext(controllerContext),
                        sessionInformation.pluginData,
                        sessionInformation.jobId,
                        sessionInformation.jobRank,
                        { wsContext.session.isActive },
                        channel,
                        emitData = { data ->
                            wsContext.sendMessage(ShellResponse.Data(data), ShellResponse.serializer())
                        }
                    )

                    ProcessingScope.launch {
                        shellTracer { "Ready to spin up shell session" }
                        with(ctx) {
                            with(pluginHandler) {
                                handleShellSession(req)
                            }
                        }
                    }

                    sessionMapMutex.withLock {
                        sessionMap[wsContext.session.id] = channel
                    }

                    // NOTE(Dan): We do not want to send a response yet. Which is why we tell the RPC system that
                    // we have already sent what we need to send, which is nothing.
                    okContentAlreadyDelivered()
                    return@implement
                }

                is ShellRequest.Input, is ShellRequest.Resize -> {
                    shellTracer { "Ready to send input or resize" }
                    val sendChannel = sessionMapMutex.withLock {
                        sessionMap[wsContext.session.id]
                    } ?: throw RPCException(
                        "This session is not ready to accept such a request",
                        HttpStatusCode.BadRequest
                    )
                    shellTracer { "Got channel!" }

                    sendChannel.send(request)
                    ok(ShellResponse.Acknowledged())
                }
            }
        }

        val syncthingProvider = SyncthingProvider(controllerContext.configuration.core.providerId)
        implement(syncthingProvider.retrieveConfiguration) {
            val plugin = lookupPluginByProduct(request.productId)

            if (plugin == null) {
                log.error("Unable to find Syncthing product in configuration")
                throw RPCException.fromStatusCode(
                    HttpStatusCode.BadRequest,
                    "Syncthing is not supported by this provider"
                )
            }

            if (plugin !is SyncthingPlugin) {
                log.error("Plugin ${plugin.pluginTitle} does not support Syncthing")
                throw RPCException.fromStatusCode(
                    HttpStatusCode.InternalServerError
                )
            }

            ok(
                with(requestContext(controllerContext)) {
                    with(plugin) {
                        retrieveSyncthingConfiguration(request)
                    }
                }
            )
        }

        implement(syncthingProvider.updateConfiguration) {
            val plugin = lookupPluginByProduct(request.productId)

            if (plugin == null) {
                log.error("Unable to find Syncthing product in configuration")
                throw RPCException.fromStatusCode(
                    HttpStatusCode.BadRequest,
                    "Syncthing is not supported by this provider"
                )
            }

            if (plugin !is SyncthingPlugin) {
                log.error("Plugin ${plugin.pluginTitle} does not support Syncthing")
                throw RPCException.fromStatusCode(
                    HttpStatusCode.InternalServerError
                )
            }

            ok(
                with(requestContext(controllerContext)) {
                    with(plugin) {
                        updateSyncthingConfiguration(request)
                    }
                }
            )
        }

        implement(syncthingProvider.resetConfiguration) {
            val plugin = lookupPluginByProduct(request.productId)

            if (plugin == null) {
                log.error("Unable to find Syncthing product in configuration")
                throw RPCException.fromStatusCode(
                    HttpStatusCode.BadRequest,
                    "Syncthing is not supported by this provider"
                )
            }

            if (plugin !is SyncthingPlugin) {
                log.error("Plugin ${plugin.pluginTitle} does not support Syncthing")
                throw RPCException.fromStatusCode(
                    HttpStatusCode.InternalServerError
                )
            }

            ok(
                with(requestContext(controllerContext)) {
                    with(plugin) {
                        resetSyncthingConfiguration(request)
                    }
                }
            )
        }

        implement(syncthingProvider.restart) {
            val plugin = lookupPluginByProduct(request.productId)

            if (plugin == null) {
                log.error("Unable to find Syncthing product in configuration")
                throw RPCException.fromStatusCode(
                    HttpStatusCode.BadRequest,
                    "Syncthing is not supported by this provider"
                )
            }

            if (plugin !is SyncthingPlugin) {
                log.error("Plugin ${plugin.pluginTitle} does not support Syncthing")
                throw RPCException.fromStatusCode(
                    HttpStatusCode.InternalServerError
                )
            }

            ok(
                with(requestContext(controllerContext)) {
                    with(plugin) {
                        restartSyncthing(request)
                    }
                }
            )
        }
    }

    private val jobIsRunningCache = SimpleCache<String, Boolean>(maxAge = 100_000) { id ->
        JobsControl.retrieve.call(
            ResourceRetrieveRequest(JobIncludeFlags(), id),
            controllerContext.pluginContext.rpcClient,
        ).orNull()?.status?.state == JobState.RUNNING
    }

    override fun onServerReady(rpcServer: RpcServer) {
        val providerId = controllerContext.configuration.core.providerId

        ktor.routing {
            if (!controllerContext.configuration.shouldRunServerCode()) return@routing
            val ipcClient = controllerContext.pluginContext.ipcClient

            get("/ucloud/$providerId/authorize-app") {
                try {
                    val token = call.request.queryParameters["token"]
                        ?: throw RPCException.fromStatusCode(HttpStatusCode.BadRequest)

                    val sessionInformation = runCatching {
                        ipcClient.sendRequest(
                            ComputeSessionIpc.retrieve,
                            FindByStringId(token)
                        )
                    }.getOrNull()

                    if (sessionInformation?.sessionType != InteractiveSessionType.WEB) {
                        throw RPCException.fromStatusCode(HttpStatusCode.Unauthorized)
                    }

                    val target = sessionInformation.target!!
                    val ingressDomain = target.ingress.removePrefix("https://").removePrefix("http://")

                    call.response.cookies.append(
                        name = cookieName + target.ingress,
                        value = token,
                        secure = call.request.origin.scheme == "https",
                        httpOnly = true,
                        expires = GMTDate(Time.now() + (1000L * 60 * 60 * 24 * 30)),
                        path = "/",
                        domain = ingressDomain
                    )

                    call.respondRedirect("http://$ingressDomain")
                } catch (ex: RPCException) {
                    call.respondText(
                        defaultMapper.encodeToString(
                            CommonErrorMessage.serializer(),
                            CommonErrorMessage(ex.why, ex.errorCode)
                        ),
                        ContentType.Application.Json,
                        io.ktor.http.HttpStatusCode.fromValue(ex.httpStatusCode.value)
                    )
                } catch (ex: Throwable) {
                    call.respondText(
                        defaultMapper.encodeToString(
                            CommonErrorMessage.serializer(),
                            CommonErrorMessage("Internal server error")
                        ),
                        ContentType.Application.Json,
                        io.ktor.http.HttpStatusCode.InternalServerError
                    )
                }
            }

            webSocket("/ucloud/$providerId/vnc", protocol = "binary") {
                val token = call.request.queryParameters["token"]
                    ?: throw RPCException.fromStatusCode(HttpStatusCode.BadRequest)

                val sessionInformation = runCatching {
                    ipcClient.sendRequest(
                        ComputeSessionIpc.retrieve,
                        FindByStringId(token)
                    )
                }.getOrNull()

                if (sessionInformation?.sessionType != InteractiveSessionType.VNC) {
                    throw RPCException.fromStatusCode(HttpStatusCode.Unauthorized)
                }

                val target = sessionInformation.target!!
                runWSProxy(target.clusterAddress, target.clusterPort, "/websockify")
            }
        }
    }

    override fun configureIpc(server: IpcServer) {
        val envoy = envoyConfig ?: return
        val realUserMode = controllerContext.configuration.core.launchRealUserInstances

        fun noSuchUser(): Nothing = throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)

        server.addHandler(ComputeSessionIpc.create.handler { user, request ->
            val ucloudIdentity =
                if (realUserMode) UserMapping.localIdToUCloudId(user.uid) ?: noSuchUser()
                else null

            val generatedSessionId = secureToken(32)
            dbConnection.withSession { session ->
                session.prepareStatement(
                    //language=postgresql
                    """
                        insert into compute_sessions(session, session_type, job_id, job_rank, plugin_name, plugin_data,
                            target)
                        values (:session, :session_type, :job_id, :job_rank, :plugin_name, :plugin_data, :target::text)
                    """
                ).useAndInvokeAndDiscard(
                    prepare = {
                        bindString("session", generatedSessionId)
                        bindString("session_type", request.sessionType.name)
                        bindString("job_id", request.jobId)
                        bindInt("job_rank", request.jobRank)
                        bindString("plugin_name", request.pluginName)
                        bindString("plugin_data", request.pluginData)
                        bindStringNullable("target", request.target?.let {
                            defaultMapper.encodeToString(ComputeSessionIpc.SessionTarget.serializer(), it)
                        })
                    }
                )
            }

            when (request.sessionType) {
                InteractiveSessionType.WEB -> {
                    val target = request.target!!

                    envoy.requestConfiguration(
                        EnvoyRoute.WebIngressSession(
                            generatedSessionId,
                            target.ingress,
                            tokensRequired = if (target.webSessionIsPublic) null else listOf(generatedSessionId),
                            "_$generatedSessionId"
                        ),
                        EnvoyCluster.create(
                            "_$generatedSessionId",
                            target.clusterAddress,
                            target.clusterPort,
                            useDns = target.useDnsForAddressLookup
                        )
                    )
                }

                InteractiveSessionType.VNC -> {
                    envoy.requestConfiguration(
                        EnvoyRoute.VncSession(
                            generatedSessionId,
                            controllerContext.configuration.core.providerId,
                            ucloudIdentity ?: EnvoyConfigurationService.IM_SERVER_CLUSTER
                        )
                    )
                }

                InteractiveSessionType.SHELL -> {
                    // Do nothing
                }
            }

            FindByStringId(generatedSessionId)
        })

        server.addHandler(ComputeSessionIpc.retrieve.handler { _, request ->
            var response: ComputeSessionIpc.Session? = null
            dbConnection.withSession { session ->
                session.prepareStatement(
                    //language=postgresql
                    """
                        select session_type, job_id, job_rank, plugin_name, plugin_data, target
                        from compute_sessions
                        where session = :id
                    """
                ).useAndInvoke(
                    prepare = {
                        bindString("id", request.id)
                    },
                    readRow = { row ->
                        response = ComputeSessionIpc.Session(
                            InteractiveSessionType.valueOf(row.getString(0)!!),
                            row.getString(1)!!,
                            row.getInt(2)!!,
                            row.getString(3)!!,
                            row.getString(4)!!,
                            row.getString(5)?.let {
                                defaultMapper.decodeFromString(ComputeSessionIpc.SessionTarget.serializer(), it)
                            }
                        )
                    }
                )
            }

            response ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
        })
    }

    private suspend fun reinitializeAllComputeSessions() {
        if (!controllerContext.configuration.shouldRunServerCode()) return
        val envoy = envoyConfig ?: run {
            log.info("Did not find envoy while trying to reinitialize compute sessions!")
            return
        }

        envoy.disableAutoConfigure()

        try {
            val items = ArrayList<Job>()
            var next: String? = null
            while (true) {
                val page = JobsControl.browse.call(
                    ResourceBrowseRequest(
                        JobIncludeFlags(filterState = JobState.RUNNING),
                        next = next,
                        itemsPerPage = 250,
                    ),
                    controllerContext.pluginContext.rpcClient
                ).orNull()

                if (page == null) {
                    log.info("Could not fetch list of running jobs. Retrying...")
                    delay(1000)
                    continue
                }

                items.addAll(page.items)
                next = page.next ?: break
            }

            val jobIds = items.map { it.id }.toSet()

            log.debug("Found the following active jobs: $jobIds")
            val sessions = ArrayList<ComputeSessionIpc.Session>()
            dbConnection.withSession { session ->
                session.prepareStatement(
                    """
                    select session_type, job_id, job_rank, plugin_name, plugin_data, target, session
                    from compute_sessions
                    where
                        job_id = some(:ids::text[]) and
                        session_type = 'WEB'
                """
                ).useAndInvoke(
                    prepare = { bindList("ids", jobIds.toList()) },
                    readRow = { row ->
                        sessions.add(
                            ComputeSessionIpc.Session(
                                InteractiveSessionType.valueOf(row.getString(0)!!),
                                row.getString(1)!!,
                                row.getInt(2)!!,
                                row.getString(3)!!,
                                row.getString(4)!!,
                                row.getString(5)?.let {
                                    defaultMapper.decodeFromString(ComputeSessionIpc.SessionTarget.serializer(), it)
                                },
                                row.getString(6)!!,
                            )
                        )
                    }
                )
            }

            log.debug("We found ${sessions.size} sessions for ${jobIds.size} active jobs")

            for (session in sessions) {
                val target = session.target ?: continue
                log.debug("Init route for: ${target.ingress}")
                envoy.requestConfiguration(
                    EnvoyRoute.WebIngressSession(
                        session.sessionId,
                        target.ingress,
                        tokensRequired = if (target.webSessionIsPublic) null else listOf(session.sessionId),
                        "_${session.sessionId}"
                    ),
                    EnvoyCluster.create(
                        "_${session.sessionId}",
                        target.clusterAddress,
                        target.clusterPort,
                        useDns = target.useDnsForAddressLookup
                    )
                )
            }
        } finally {
            envoy.enableAutoConfigure()
        }
    }

    companion object : Loggable {
        override val log: Logger = logger()

        private const val cookieName = "ucloud-compute-session-"
    }
}

// TODO(Dan): Not a great idea, probably leaks memory.
val sessionMap = HashMap<String, SendChannel<ShellRequest>>()
val sessionMapMutex = Mutex()

object ComputeSessionIpc : IpcContainer("compute_sessions") {
    @Serializable
    data class Session(
        val sessionType: InteractiveSessionType,
        val jobId: String,
        val jobRank: Int,
        val pluginName: String,
        val pluginData: String,
        val target: SessionTarget? = null,
        val sessionId: String = "",
    ) {
        init {
            if (target == null) {
                require(sessionType in setOf(InteractiveSessionType.SHELL)) {
                    "target information must be present for sessions of type $sessionType"
                }
            } else {
                require(sessionType in setOf(InteractiveSessionType.WEB, InteractiveSessionType.VNC)) {
                    "target information must not be present for sessions of type $sessionType"
                }
            }
        }
    }

    @Serializable
    data class SessionTarget(
        val ingress: String,
        val clusterAddress: String,
        val clusterPort: Int,
        // TODO(Dan): Might want to make this a sealed class at this point. It is ignored for anything but web
        val webSessionIsPublic: Boolean = false,
        val useDnsForAddressLookup: Boolean = false,
    )

    val create = createHandler(Session.serializer(), FindByStringId.serializer())
    val retrieve = retrieveHandler(FindByStringId.serializer(), Session.serializer())
}
