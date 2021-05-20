package dk.sdu.cloud.controllers

import dk.sdu.cloud.ServerMode
import dk.sdu.cloud.accounting.api.ProductReference
import dk.sdu.cloud.app.orchestrator.api.*
import dk.sdu.cloud.calls.BulkRequest
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.freeze
import dk.sdu.cloud.http.H2OServer
import dk.sdu.cloud.http.OutgoingCallResponse
import dk.sdu.cloud.http.wsContext
import dk.sdu.cloud.plugins.ComputePlugin
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.Logger
import dk.sdu.cloud.utils.secureToken
import io.ktor.http.*
import kotlinx.atomicfu.atomicArrayOfNulls

class ComputeController(
    private val controllerContext: ControllerContext,
) : Controller {
    override fun H2OServer.configure() {
        val plugins = controllerContext.plugins.compute ?: return
        val serverMode = controllerContext.configuration.serverMode

        fun findPlugin(job: Job): ComputePlugin {
            return plugins.lookup(job.specification.product)
        }

        val jobs = JobsProvider(controllerContext.configuration.core.providerId)

        fun <T> groupJobs(
            items: List<T>,
            jobSelector: (T) -> Job,
        ): Map<ComputePlugin, List<T>> {
            val result = HashMap<ComputePlugin, ArrayList<T>>()
            for (item in items) {
                val job = jobSelector(item)
                val plugin = findPlugin(job)
                val existing = result[plugin] ?: ArrayList()
                existing.add(item)
                result[plugin] = existing
            }
            return result
        }

        implement(jobs.create) {
            if (serverMode != ServerMode.User) throw RPCException.fromStatusCode(HttpStatusCode.NotFound)

            groupJobs(request.items, { it }).forEach { (plugin, group) ->
                with(controllerContext.pluginContext) {
                    with(plugin) {
                        createBulk(BulkRequest(group))
                    }
                }
            }

            OutgoingCallResponse.Ok(Unit)
        }

        implement(jobs.retrieveProducts) {
            if (serverMode != ServerMode.Server) throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
            OutgoingCallResponse.Ok(
                JobsProviderRetrieveProductsResponse(
                    plugins.allProducts
                        .map { it to plugins.lookup(it) }
                        .groupBy { it.second }
                        .flatMap { (plugin, products) ->
                            val support = with(controllerContext.pluginContext) {
                                with(plugin) {
                                    retrieveSupport()
                                }
                            }

                            products.map { (product) ->
                                ComputeProductSupport(
                                    ProductReference(
                                        product.id,
                                        product.category,
                                        controllerContext.configuration.core.providerId
                                    ),
                                    support
                                )
                            }
                        }
                )
            )
        }

        implement(jobs.delete) {
            if (serverMode != ServerMode.User) throw RPCException.fromStatusCode(HttpStatusCode.NotFound)

            groupJobs(request.items, { it }).forEach { (plugin, group) ->
                with(controllerContext.pluginContext) {
                    with(plugin) {
                        deleteBulk(BulkRequest(group))
                    }
                }
            }

            OutgoingCallResponse.Ok(Unit)
        }

        implement(jobs.extend) {
            if (serverMode != ServerMode.User) throw RPCException.fromStatusCode(HttpStatusCode.NotFound)

            groupJobs(request.items, { it.job }).forEach { (plugin, group) ->
                with(controllerContext.pluginContext) {
                    with(plugin) {
                        extendBulk(BulkRequest(group))
                    }
                }
            }

            OutgoingCallResponse.Ok(Unit)
        }

        val maxStreams = 1024 * 32
        val streams = atomicArrayOfNulls<String>(maxStreams)

        implement(jobs.follow) {
            if (serverMode != ServerMode.User) throw RPCException.fromStatusCode(HttpStatusCode.NotFound)

            when (request) {
                is JobsProviderFollowRequest.Init -> {
                    val plugin = findPlugin(request.job)

                    val token = secureToken(64).freeze()
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
                            followLogs(request.job)
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

        implement(jobs.openInteractiveSession) {
            if (serverMode != ServerMode.User) throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
            val responses = ArrayList<OpenSession>()
            groupJobs(request.items, { it.job }).forEach { (plugin, group) ->
                with(controllerContext.pluginContext) {
                    with(plugin) {
                        responses.addAll(openInteractiveSessionBulk(BulkRequest(group)).sessions)
                    }
                }
            }

            OutgoingCallResponse.Ok(JobsProviderOpenInteractiveSessionResponse(responses))
        }

        implement(jobs.retrieveUtilization) {
            if (serverMode != ServerMode.Server) throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
            TODO("Issue #2425")
            /*
            with(controllerContext.pluginContext) {
                with(computePlugin) {
                    OutgoingCallResponse.Ok(retrieveClusterUtilization())
                }
            }
             */
        }

        implement(jobs.suspend) {
            if (serverMode != ServerMode.User) throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
            groupJobs(request.items, { it }).forEach { (plugin, group) ->
                with(controllerContext.pluginContext) {
                    with(plugin) {
                        suspendBulk(BulkRequest(group))
                    }
                }
            }

            OutgoingCallResponse.Ok(Unit)
        }

        implement(jobs.verify) {
            if (serverMode != ServerMode.Server) throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
            groupJobs(request.items, { it }).forEach { (plugin, group) ->
                with(controllerContext.pluginContext) {
                    with(plugin) {
                        verify(group)
                    }
                }
            }

            OutgoingCallResponse.Ok(Unit)
        }
    }

    companion object : Loggable {
        override val log: Logger = logger()
    }
}
