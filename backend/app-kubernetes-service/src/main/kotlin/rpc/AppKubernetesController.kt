package dk.sdu.cloud.app.kubernetes.rpc

import dk.sdu.cloud.app.kubernetes.api.KubernetesCompute
import dk.sdu.cloud.app.kubernetes.services.JobAndRank
import dk.sdu.cloud.app.kubernetes.services.JobManagement
import dk.sdu.cloud.app.kubernetes.services.K8LogService
import dk.sdu.cloud.app.kubernetes.services.UtilizationService
import dk.sdu.cloud.app.kubernetes.services.proxy.VncService
import dk.sdu.cloud.app.kubernetes.services.proxy.WebService
import dk.sdu.cloud.app.orchestrator.api.*
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.calls.server.sendWSMessage
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.Loggable
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.receiveOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.*
import java.util.concurrent.CancellationException
import kotlin.collections.HashMap

class AppKubernetesController(
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
        implement(KubernetesCompute.create) {
            jobManagement.create(request)
            ok(Unit)
        }

        implement(KubernetesCompute.delete) {
            jobManagement.cancel(request)
            ok(Unit)
        }

        implement(KubernetesCompute.extend) {
            jobManagement.extend(request)
            ok(Unit)
        }

        implement(KubernetesCompute.suspend) {
            throw RPCException("UCloud/Compute does not support job suspension", HttpStatusCode.BadRequest)
        }

        implement(KubernetesCompute.verify) {
            jobManagement.verifyJobs(request.items)
            ok(Unit)
        }

        implement(KubernetesCompute.openInteractiveSession) {
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

            ok(ComputeOpenInteractiveSessionResponse(
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

        implement(KubernetesCompute.retrieveManifest) {
            ok(
                ProviderManifest().apply {
                    with(features) {
                        with(compute) {
                            with(docker) {
                                enabled = true
                                web = true
                                vnc = true
                                batch = true
                                logs = true
                                terminal = true
                            }
                        }
                    }
                }
            )
        }

        implement(KubernetesCompute.follow) {
            when (val request = request) {
                is ComputeFollowRequest.Init -> {
                    val streamId = UUID.randomUUID().toString()
                    try {
                        val logWatch = logService.useLogWatch(request.job.id)
                        streamsMutex.withLock {
                            streams[streamId] = logWatch
                        }

                        sendWSMessage(ComputeFollowResponse(streamId, -1, null, null))

                        while (!logWatch.isClosedForReceive) {
                            val nextMessage = logWatch.receiveOrNull() ?: break
                            sendWSMessage(ComputeFollowResponse(streamId, nextMessage.rank, nextMessage.message, null))
                        }

                        streamsMutex.withLock { streams.remove(streamId) }
                        ok(ComputeFollowResponse(streamId, -1, null, null))
                    } catch (ex: Throwable) {
                        streamsMutex.withLock { streams.remove(streamId) }
                        okContentAlreadyDelivered()
                    }
                }

                is ComputeFollowRequest.CancelStream -> {
                    try {
                        streamsMutex.withLock {
                            streams.remove(request.streamId)?.cancel()
                        }
                        ok(ComputeFollowResponse("", -1, null, null))
                    } catch (ex: Throwable) {
                        okContentAlreadyDelivered()
                    }
                }
            }
        }

        implement(KubernetesCompute.utilization) {
            ok(UtilizationResponse(
                utilizationService.allocatable(),
                utilizationService.used(),
                utilizationService.jobs()
            ))
        }

        return@configure
    }

    companion object : Loggable {
        override val log = logger()
    }
}
