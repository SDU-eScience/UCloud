package dk.sdu.cloud.controllers

import dk.sdu.cloud.accounting.api.ProductReference
import dk.sdu.cloud.app.orchestrator.api.*
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.http.H2OServer
import dk.sdu.cloud.http.OutgoingCallResponse
import dk.sdu.cloud.http.wsContext
import dk.sdu.cloud.plugins.FollowLogsContext
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.Logger
import io.ktor.http.*
import kotlinx.atomicfu.atomicArrayOfNulls

class ComputeController(
    private val controllerContext: ControllerContext,
) : Controller {
    override fun H2OServer.configure() {
        val computePlugin = controllerContext.plugins.compute ?: return

        val PROVIDER_ID = "im-test"
        val jobs = JobsProvider(PROVIDER_ID)
        val knownProducts = listOf(
            ProductReference("im1", "im1", PROVIDER_ID),
        )

        implement(jobs.create) {
            with(controllerContext.pluginContext) {
                with (computePlugin) {
                    createBulk(request)
                }
            }

            OutgoingCallResponse.Ok(Unit)
        }

        implement(jobs.retrieveProducts) {
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
            with(controllerContext.pluginContext) {
                with(computePlugin) {
                    deleteBulk(request)
                }
            }

            OutgoingCallResponse.Ok(Unit)
        }

        implement(jobs.extend) {
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

                    val ctx = FollowLogsContext(
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
            log.info("open interactive session $request")
            TODO()
        }

        implement(jobs.retrieveUtilization) {
            OutgoingCallResponse.Ok(
                JobsProviderUtilizationResponse(CpuAndMemory(0.0, 0L), CpuAndMemory(0.0, 0L), QueueStatus(0, 0))
            )
        }

        implement(jobs.suspend) {
            with(controllerContext.pluginContext) {
                with(computePlugin) {
                    suspendBulk(request)
                }
            }
            OutgoingCallResponse.Ok(Unit)
        }

        implement(jobs.verify) {
            log.info("Verifying some jobs $request")
            OutgoingCallResponse.Ok(Unit)
        }
    }

    companion object : Loggable {
        override val log: Logger = logger()
    }
}
