package dk.sdu.cloud.controllers

import dk.sdu.cloud.ServerMode
import dk.sdu.cloud.accounting.api.ProductReference
import dk.sdu.cloud.app.orchestrator.api.*
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.http.H2OServer
import dk.sdu.cloud.http.OutgoingCallResponse
import dk.sdu.cloud.http.wsContext
import dk.sdu.cloud.plugins.ComputePlugin
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.Logger
import io.ktor.http.*
import kotlinx.atomicfu.atomicArrayOfNulls

class ComputeController(
    private val controllerContext: ControllerContext,
) : Controller {
    override fun H2OServer.configure() {
        val computePlugin = controllerContext.plugins.compute ?: return
        val serverMode = controllerContext.configuration.serverMode

        val jobs = JobsProvider(controllerContext.configuration.core.providerId)
        val knownProducts = listOf(
            ProductReference("im1", "im1", controllerContext.configuration.core.providerId),
        )

        implement(jobs.create) {
            if (serverMode != ServerMode.User) throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
            with(controllerContext.pluginContext) {
                with (computePlugin) {
                    createBulk(request)
                }
            }

            OutgoingCallResponse.Ok(Unit)
        }

        implement(jobs.retrieveProducts) {
            if (serverMode != ServerMode.Server) throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
            OutgoingCallResponse.Ok(
                JobsProviderRetrieveProductsResponse(
                    knownProducts.map { productRef ->
                        ComputeProductSupport(
                            productRef,
                            ComputeSupport(
                                ComputeSupport.Docker(
                                    enabled = true,
                                    terminal = true,
                                    logs = true,
                                    timeExtension = false
                                ),
                                ComputeSupport.VirtualMachine(
                                    enabled = false,
                                )
                            )
                        )
                    }
                )
            )
        }

        implement(jobs.delete) {
            if (serverMode != ServerMode.User) throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
            with(controllerContext.pluginContext) {
                with(computePlugin) {
                    deleteBulk(request)
                }
            }

            OutgoingCallResponse.Ok(Unit)
        }

        implement(jobs.extend) {
            if (serverMode != ServerMode.User) throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
            with(controllerContext.pluginContext) {
                with(computePlugin) {
                    extendBulk(request)
                }
            }

            OutgoingCallResponse.Ok(Unit)
        }

        val maxStreams = 1024 * 32
        val streams = atomicArrayOfNulls<Unit>(maxStreams)

        implement(jobs.follow) {
            if (serverMode != ServerMode.User) throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
            // TODO TODO TODO STREAM IDS NEEDS TO BE UNGUESSABLE THIS ALLOWS ANYONE TO CANCEL OTHER PEOPLES STREAMS
            when (request) {
                is JobsProviderFollowRequest.Init -> {
                    var streamId: Int? = null
                    for (i in 0 until maxStreams) {
                        if (streams[i].compareAndSet(null, Unit)) {
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
                        isActive = { streams[streamId].compareAndSet(Unit, Unit) && wsContext.isOpen() },
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
                        with(computePlugin) {
                            followLogs(request.job)
                        }
                    }

                    OutgoingCallResponse.Ok(JobsProviderFollowResponse("", -1))
                }

                is JobsProviderFollowRequest.CancelStream -> {
                    val id = request.streamId.toIntOrNull()
                        ?: throw RPCException("Bad stream id", HttpStatusCode.BadRequest)
                    if (id !in 0 until maxStreams) throw RPCException.fromStatusCode(HttpStatusCode.BadRequest)

                    streams[id].compareAndSet(Unit, null)
                    OutgoingCallResponse.Ok(JobsProviderFollowResponse("", -1))
                }
            }
        }

        implement(jobs.openInteractiveSession) {
            if (serverMode != ServerMode.User) throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
            log.info("open interactive session $request")
            TODO()
        }

        implement(jobs.retrieveUtilization) {
            if (serverMode != ServerMode.Server) throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
            OutgoingCallResponse.Ok(
                JobsProviderUtilizationResponse(CpuAndMemory(0.0, 0L), CpuAndMemory(0.0, 0L), QueueStatus(0, 0))
            )
        }

        implement(jobs.suspend) {
            if (serverMode != ServerMode.User) throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
            with(controllerContext.pluginContext) {
                with(computePlugin) {
                    suspendBulk(request)
                }
            }
            OutgoingCallResponse.Ok(Unit)
        }

        implement(jobs.verify) {
            if (serverMode != ServerMode.Server) throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
            log.info("Verifying some jobs $request")
            OutgoingCallResponse.Ok(Unit)
        }
    }

    companion object : Loggable {
        override val log: Logger = logger()
    }
}
