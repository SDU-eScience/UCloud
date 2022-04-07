package dk.sdu.cloud.app.kubernetes.rpc

import dk.sdu.cloud.app.kubernetes.services.JobAndRank
import dk.sdu.cloud.app.kubernetes.services.JobManagement
import dk.sdu.cloud.app.kubernetes.services.K8LogService
import dk.sdu.cloud.app.kubernetes.services.UtilizationService
import dk.sdu.cloud.app.kubernetes.services.proxy.VncService
import dk.sdu.cloud.app.kubernetes.services.proxy.WebService
import dk.sdu.cloud.app.orchestrator.api.*
import dk.sdu.cloud.calls.BulkResponse
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.calls.server.sendWSMessage
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.Loggable
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.*
import kotlin.collections.HashMap

class AppKubernetesController(
    private val providerId: String,
    private val jobManagement: JobManagement,
    private val logService: K8LogService,
    private val webService: WebService,
    private val vncService: VncService,
    private val utilizationService: UtilizationService
) : Controller {
    private val streams = HashMap<String, ReceiveChannel<*>>()
    private val streamsMutex = Mutex()

    @OptIn(ExperimentalCoroutinesApi::class, ExperimentalStdlibApi::class)
    override fun configure(rpcServer: RpcServer): Unit = with(rpcServer) {
        val computeApi = JobsProvider(providerId)
        implement(computeApi.create) {
            jobManagement.create(request)
            ok(BulkResponse(request.items.map { null }))
        }

        implement(computeApi.terminate) {
            jobManagement.cancel(request)
            ok(BulkResponse(request.items.map { Unit }))
        }

        implement(computeApi.extend) {
            jobManagement.extend(request)
            ok(BulkResponse(request.items.map { }))
        }

        implement(computeApi.suspend) {
            throw RPCException("UCloud/Compute does not support job suspension", HttpStatusCode.BadRequest)
        }

        implement(computeApi.verify) {
            jobManagement.verifyJobs(request.items)
            ok(Unit)
        }

        implement(computeApi.openInteractiveSession) {
            val shellJobs = request.items.filter { it.sessionType == InteractiveSessionType.SHELL }.map {
                JobAndRank(it.job, it.rank)
            }
            val shellSessions = jobManagement.openShellSession(shellJobs)

            val webJobs = request.items.filter { it.sessionType == InteractiveSessionType.WEB }.map {
                JobAndRank(it.job, it.rank)
            }
            val webSessions = webJobs.map { webService.createSession(it) }

            val vncJobs = request.items.filter { it.sessionType == InteractiveSessionType.VNC }.map {
                JobAndRank(it.job, it.rank)
            }
            val vncSessions = vncJobs.map { vncService.createSession(it) }

            ok(JobsProviderOpenInteractiveSessionResponse(
                buildList {
                    addAll(
                        shellSessions.mapIndexed { idx, sessionIdentifier ->
                            OpenSession.Shell(shellJobs[idx].job.id, shellJobs[idx].rank, sessionIdentifier)
                        }
                    )

                    addAll(webSessions)

                    addAll(vncSessions)
                }
            ))
        }

        implement(computeApi.retrieveProducts) {
            ok(jobManagement.retrieveProductsTemporary())
        }

        implement(computeApi.updateAcl) {
            ok(BulkResponse(request.items.map {  }))
        }

        implement(computeApi.follow) {
            when (val request = request) {
                is JobsProviderFollowRequest.Init -> {
                    val streamId = UUID.randomUUID().toString()
                    try {
                        val logWatch = logService.useLogWatch(request.job.id)
                        streamsMutex.withLock {
                            streams[streamId] = logWatch
                        }

                        sendWSMessage(JobsProviderFollowResponse(streamId, -1, null, null))

                        while (!logWatch.isClosedForReceive) {
                            val nextMessage = logWatch.receiveCatching().getOrNull() ?: break
                            sendWSMessage(JobsProviderFollowResponse(streamId, nextMessage.rank, nextMessage.message, null))
                        }

                        streamsMutex.withLock { streams.remove(streamId) }
                        ok(JobsProviderFollowResponse(streamId, -1, null, null))
                    } catch (ex: Throwable) {
                        streamsMutex.withLock { streams.remove(streamId) }
                        ok(JobsProviderFollowResponse(streamId, -1, null, null))
                    }
                }

                is JobsProviderFollowRequest.CancelStream -> {
                    try {
                        streamsMutex.withLock {
                            streams.remove(request.streamId)?.cancel()
                        }
                        ok(JobsProviderFollowResponse("", -1, null, null))
                    } catch (ex: Throwable) {
                        okContentAlreadyDelivered()
                    }
                }
            }
        }

        implement(computeApi.retrieveUtilization) {
            ok(JobsProviderUtilizationResponse(
                utilizationService.retrieveCapacity(),
                utilizationService.retrieveUsedCapacity(),
                utilizationService.retrieveQueueStatus()
            ))
        }

        return@configure
    }

    companion object : Loggable {
        override val log = logger()
    }
}
