package dk.sdu.cloud.controllers

import dk.sdu.cloud.ProcessingScope
import dk.sdu.cloud.ServerMode
import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.http.RpcServer
import dk.sdu.cloud.app.orchestrator.api.*
import dk.sdu.cloud.calls.BulkResponse
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.http.OutgoingCallResponse
import dk.sdu.cloud.http.wsContext
import dk.sdu.cloud.ipc.sendRequest
import dk.sdu.cloud.plugins.ComputePlugin
import dk.sdu.cloud.plugins.ProductBasedPlugins
import dk.sdu.cloud.plugins.ipcClient
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.Logger
import dk.sdu.cloud.utils.secureToken
import kotlinx.atomicfu.atomicArrayOfNulls
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import platform.posix.usleep

class ComputeController(
    controllerContext: ControllerContext,
) : BaseResourceController<Product.Compute, ComputeSupport, Job, ComputePlugin, JobsProvider>(controllerContext) {
    override fun retrievePlugins(): ProductBasedPlugins<ComputePlugin>? = controllerContext.plugins.compute
    override fun retrieveApi(providerId: String): JobsProvider = JobsProvider(providerId)

    override fun RpcServer.configureCustomEndpoints(plugins: ProductBasedPlugins<ComputePlugin>, api: JobsProvider) {
        val serverMode = controllerContext.configuration.serverMode
        val shells = Shells(controllerContext.configuration.core.providerId)

        implement(api.extend) {
            if (serverMode != ServerMode.User) throw RPCException.fromStatusCode(HttpStatusCode.NotFound)

            OutgoingCallResponse.Ok(
                dispatchToPlugin(plugins, request.items, { it.job }) { plugin, request ->
                    with(plugin) { extendBulk(request) }
                }
            )
        }

        val maxStreams = 1024 * 32
        val streams = atomicArrayOfNulls<String>(maxStreams)

        implement(api.follow) {
            if (serverMode != ServerMode.User) throw RPCException.fromStatusCode(HttpStatusCode.NotFound)

            when (request) {
                is JobsProviderFollowRequest.Init -> {
                    val plugin = plugins.lookup(request.job.specification.product)

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

                    wsContext.sendMessage(JobsProviderFollowResponse(streamId.toString(), -1))

                    val ctx = ComputePlugin.FollowLogsContext(
                        controllerContext.pluginContext,
                        isActive = { streams[streamId].compareAndSet(token, token) && wsContext.isOpen() },
                        emitStdout = { rank, message ->
                            wsContext.sendMessage(
                                JobsProviderFollowResponse(
                                    streamId.toString(),
                                    rank,
                                    stdout = message
                                )
                            )
                        },
                        emitStderr = { rank, message ->
                            wsContext.sendMessage(
                                JobsProviderFollowResponse(
                                    streamId.toString(),
                                    rank,
                                    stderr = message
                                )
                            )
                        }
                    )

                    with(ctx) {
                        with(plugin) {
                            runBlocking { follow(request.job) }
                        }
                    }

                    OutgoingCallResponse.Ok(JobsProviderFollowResponse("", -1))
                }

                is JobsProviderFollowRequest.CancelStream -> {
                    var idx = -1
                    for (i in 0 until maxStreams) {
                        if (streams[i].value == request.streamId) {
                            idx = i
                            break
                        }
                    }
                    if (idx == -1) throw RPCException.fromStatusCode(HttpStatusCode.BadRequest)

                    streams[idx].compareAndSet(request.streamId, null)
                    OutgoingCallResponse.Ok(JobsProviderFollowResponse("", -1))
                }
            }
        }

        implement(api.openInteractiveSession) {
            if (serverMode != ServerMode.User) throw RPCException.fromStatusCode(HttpStatusCode.NotFound)

            val results = dispatchToPlugin(plugins, request.items, { it.job }) { plugin, request ->
                with(plugin) { openInteractiveSessionBulk(request) }
            }

            for (result in results.responses) {
                if (result != null && result is OpenSession.Shell) {
                    controllerContext.pluginContext.ipcClient.sendRequest(
                        ConnectionIpc.registerSessionProxy,
                        result
                    )
                }
            }

            OutgoingCallResponse.Ok(results)
        }

        implement(api.retrieveUtilization) {
            if (serverMode != ServerMode.Server) throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
            TODO("Issue #2425")
        }

        implement(api.suspend) {
            if (serverMode != ServerMode.User) throw RPCException.fromStatusCode(HttpStatusCode.NotFound)

            dispatchToPlugin(plugins, request.items, { it }) { plugin, request ->
                with(plugin) { suspendBulk(request) }
                BulkResponse(emptyList<Unit>())
            }

            OutgoingCallResponse.Ok(Unit)
        }

        implement(api.terminate) {
            if (serverMode != ServerMode.User) throw RPCException.fromStatusCode(HttpStatusCode.NotFound)

            OutgoingCallResponse.Ok(
                dispatchToPlugin(plugins, request.items, { it }) { plugin, request ->
                    with(plugin) { terminateBulk(request) }
                }
            )
        }

        implement(shells.open) {
            runBlocking {
                if (serverMode != ServerMode.User) throw RPCException.fromStatusCode(HttpStatusCode.NotFound)

                when (request) {
                    is ShellRequest.Initialize -> {
                        val pluginHandler = with(controllerContext.pluginContext) {
                            plugins.plugins.values.find { plugin ->
                                with(plugin) { canHandleShellSession(request) }
                            }
                        } ?: throw RPCException("Bad session identifier supplied", HttpStatusCode.Unauthorized)

                        val channel = Channel<ShellRequest>(Channel.BUFFERED)
                        val ctx = ComputePlugin.ShellContext(
                            controllerContext.pluginContext,
                            wsContext.isOpen,
                            channel,
                            emitData = { data ->
                                wsContext.sendMessage(ShellResponse.Data(data))
                            }
                        )

                        ProcessingScope.launch {
                            with(ctx) {
                                with(pluginHandler) {
                                    //handleShellSession(request.cols, request.rows)
                                    handleShellSession(request)
                                }
                            }
                        }

                        sessionMapMutex.withLock {
                            sessionMap[wsContext.internalId] = channel
                        }

                        // NOTE(Dan): We do not want to send a response yet. Which is why we tell the RPC system that
                        // we have already sent what we need to send, which is nothing.
                        return@runBlocking OutgoingCallResponse.AlreadyDelivered()
                    }

                    is ShellRequest.Input, is ShellRequest.Resize -> {
                        val sendChannel = sessionMapMutex.withLock {
                            sessionMap[wsContext.internalId]
                        } ?: throw RPCException(
                            "This session is not ready to accept such a request",
                            HttpStatusCode.BadRequest
                        )

                        sendChannel.send(request)
                    }
                }

                OutgoingCallResponse.Ok(ShellResponse.Acknowledged())
            }
        }
    }

    companion object : Loggable {
        override val log: Logger = logger()
    }
}

// TODO(Dan): Not a great idea, probably leaks memory.
val sessionMap = HashMap<Int, SendChannel<ShellRequest>>()
val sessionMapMutex = Mutex()