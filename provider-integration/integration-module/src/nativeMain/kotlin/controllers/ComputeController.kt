package dk.sdu.cloud.controllers

import dk.sdu.cloud.ProcessingScope
import dk.sdu.cloud.ServerMode
import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.app.orchestrator.api.*
import dk.sdu.cloud.calls.BulkResponse
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.freeze
import dk.sdu.cloud.http.H2OServer
import dk.sdu.cloud.http.OutgoingCallResponse
import dk.sdu.cloud.http.wsContext
import dk.sdu.cloud.plugins.ComputePlugin
import dk.sdu.cloud.plugins.ProductBasedPlugins
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.Logger
import dk.sdu.cloud.utils.secureToken
import io.ktor.http.*
import kotlinx.atomicfu.atomicArrayOfNulls
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class ComputeController(
    controllerContext: ControllerContext,
) : BaseResourceController<Product.Compute, ComputeSupport, Job, ComputePlugin, JobsProvider>(controllerContext) {
    override fun retrievePlugins(): ProductBasedPlugins<ComputePlugin>? = controllerContext.plugins.compute
    override fun retrieveApi(providerId: String): JobsProvider = JobsProvider(providerId)

    override fun H2OServer.configureCustomEndpoints(plugins: ProductBasedPlugins<ComputePlugin>, api: JobsProvider) {
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

            OutgoingCallResponse.Ok(
                dispatchToPlugin(plugins, request.items, { it.job }) { plugin, request ->
                    with(plugin) { openInteractiveSessionBulk(request) }
                }
            )
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
                if (serverMode == ServerMode.User) throw RPCException.fromStatusCode(HttpStatusCode.NotFound)

                when (request) {
                    is ShellRequest.Initialize -> {
                        currentShellSessionValidForSocket = wsContext.internalId

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
                                    handleShellSession(request.cols, request.rows)
                                }
                            }
                        }

                        currentShellSession = channel
                    }

                    is ShellRequest.Input, is ShellRequest.Resize -> {
                        if (currentShellSessionValidForSocket != wsContext.internalId) {
                            throw RPCException(
                                "This session is not ready to accept such a request",
                                HttpStatusCode.BadRequest
                            )
                        }

                        val sendChannel = currentShellSession ?: run {
                            throw RPCException(
                                "This session is not ready to accept such a request",
                                HttpStatusCode.BadRequest
                            )
                        }

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


// NOTE(Dan): This is extremely volatile and only works because the websocket handler is running in the same thread
@ThreadLocal
var currentShellSession: SendChannel<ShellRequest>? = null

@ThreadLocal
var currentShellSessionValidForSocket: Int = -1