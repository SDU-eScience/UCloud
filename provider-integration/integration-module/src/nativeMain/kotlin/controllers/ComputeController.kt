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

import dk.sdu.cloud.plugins.* //delete later
import dk.sdu.cloud.utils.*


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
            //TODO("Issue #2425")


            //TODO: find a way to call the plugin from SampleComputePlugin

            //val mPlugin = plugins as ProductBasedPlugins
            //val samplePlugin = mPlugin.plugins["sample"]
            // with(controllerContext.pluginContext) {
            //     with(samplePlugin) {
            //          OutgoingCallResponse.Ok(this!!.retrieveClusterUtilization())
            //    }
            // }



            // squeue --format '%A|%m|%C|%T' --noheader --states running,pending --noconvert
            // 26|50M|1|PENDING
            // 27|50M|1|PENDING

            //get pending cpu/mem jobs
            val (_, jobs, _) = CmdBuilder("/usr/bin/squeue")
                                    .addArg("--format","%A|%m|%C|%T")
                                    .addArg("--noheader")
                                    .addArg("--noconvert")
                                    .addArg("--states", "running,pending")
                                    .addEnv("SLURM_CONF",  "/etc/slurm/slurm.conf")
                                    .execute()

            val mList = jobs.lines().map{
                it.trimIndent()
                it.trim()
                it.split("|")
            }.toList()

            var usedCpu = 0;
            var usedMem = 0;
            var pendingJobs = 0;
            var runningJobs = 0;

            mList.forEach{ line -> 

                    if(  line[3].equals("PENDING") ) {
                       pendingJobs++
                    }

                    if(  line[3].equals("RUNNING")  ) {
                        usedCpu = usedCpu + line[2].toInt()
                        usedMem = usedMem + line[1].replace("M", "").toInt()
                        runningJobs++
                    }

            }

            //println("$usedCpu $usedMem $pendingJobs $runningJobs")


            // sinfo --format='%n|%c|%m' --noconvert --noheader
            // c1|1|1000
            // c2|1|1000

            //get cluster overall cpu/mem
            val (_, nodes, _) = CmdBuilder("/usr/bin/sinfo")
                                    .addArg("--format","%n|%c|%m")
                                    .addArg("--noheader")
                                    .addArg("--noconvert")
                                    .addEnv("SLURM_CONF",  "/etc/slurm/slurm.conf")
                                    .execute()

            val nList = nodes.lines().map{
                it.trimIndent()
                it.trim()
                it.split("|")
            }.toList()

            var clusterCpu = 0;
            var clusterMem = 0;

            nList.forEach{ line -> 
                        clusterCpu = clusterCpu + line[1].toInt()
                        clusterMem = clusterMem + line[2].replace("M", "").toInt()
            }

            //println("$clusterCpu $clusterMem")

           OutgoingCallResponse.Ok( JobsProviderUtilizationResponse(   CpuAndMemory(clusterCpu.toDouble(), clusterMem.toLong()), CpuAndMemory(usedCpu.toDouble(), usedMem.toLong()), QueueStatus(runningJobs, pendingJobs))   )

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
