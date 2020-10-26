package dk.sdu.cloud.app.kubernetes.rpc

import dk.sdu.cloud.FindByStringId
import dk.sdu.cloud.app.kubernetes.api.AppKubernetesDescriptions
import dk.sdu.cloud.app.kubernetes.services.JobManagement
import dk.sdu.cloud.app.kubernetes.services.K8LogService
import dk.sdu.cloud.app.kubernetes.services.proxy.VncService
import dk.sdu.cloud.app.kubernetes.services.proxy.WebService
import dk.sdu.cloud.app.orchestrator.api.InternalFollowWSStreamResponse
import dk.sdu.cloud.app.orchestrator.api.InternalStdStreamsResponse
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.calls.server.sendWSMessage
import dk.sdu.cloud.events.EventStreamContainer
import dk.sdu.cloud.service.BroadcastingStream
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.Loggable
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.receiveOrNull
import java.util.*
import kotlin.collections.HashMap

private object CancelWSStream : EventStreamContainer() {
    val events = stream<FindByStringId>("appk8-ws-cancel", { "id" })
}

class AppKubernetesController(
    private val jobManagement: JobManagement,
    private val logService: K8LogService,
    private val vncService: VncService,
    private val webService: WebService,
    private val broadcastStream: BroadcastingStream
) : Controller {
    private val streams = HashMap<String, ReceiveChannel<*>>()

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun configure(rpcServer: RpcServer): Unit = with(rpcServer) {
        runBlocking {
            broadcastStream.subscribe(CancelWSStream.events) { (id) ->
                streams.remove(id)?.cancel()
            }
        }

        implement(AppKubernetesDescriptions.cleanup) {
            jobManagement.cleanup(request.id)
            ok(Unit)
        }

        implement(AppKubernetesDescriptions.follow) {
            val (log, nextLine) = logService.retrieveLogs(
                request.job.id,
                request.stdoutLineStart,
                request.stdoutMaxLines
            )

            ok(InternalStdStreamsResponse(log, nextLine, "", 0))
        }

        implement(AppKubernetesDescriptions.cancelWSStream) {
            broadcastStream.broadcast(FindByStringId(request.streamId), CancelWSStream.events)
            ok(Unit)
        }

        implement(AppKubernetesDescriptions.followWSStream) {
            val streamId = UUID.randomUUID().toString()
            sendWSMessage(InternalFollowWSStreamResponse(streamId))
            val logWatch = logService.useLogWatch(request.job.id)
            streams[streamId] = logWatch
            try {
                while (streams[streamId] != null) {
                    val ev = logWatch.receiveOrNull() ?: break
                    sendWSMessage(InternalFollowWSStreamResponse(streamId, ev, null))
                }
            } finally {
                streams.remove(streamId)?.cancel()
            }

            ok(InternalFollowWSStreamResponse(streamId))
        }

        implement(AppKubernetesDescriptions.jobVerified) {
            ok(Unit)
        }

        implement(AppKubernetesDescriptions.submitFile) {
            throw RPCException.fromStatusCode(HttpStatusCode.BadRequest) // Not supported
        }

        implement(AppKubernetesDescriptions.jobPrepared) {
            jobManagement.create(request)
            ok(Unit)
        }

        implement(AppKubernetesDescriptions.queryInternalVncParameters) {
            ok(vncService.queryParameters(request.verifiedJob))
        }

        implement(AppKubernetesDescriptions.queryInternalWebParameters) {
            ok(webService.queryParameters(request.verifiedJob))
        }

        implement(AppKubernetesDescriptions.cancel) {
            jobManagement.cancel(request.verifiedJob)
            ok(Unit)
        }

        implement(AppKubernetesDescriptions.updateJobDeadline) {
            jobManagement.extend(request.verifiedJob, request.newMaxTime)
            ok(Unit)
        }

        implement(AppKubernetesDescriptions.verifyJobs) {
            jobManagement.verifyJobs(request.jobs)
            ok(Unit)
        }

        return@configure
    }

    companion object : Loggable {
        override val log = logger()
    }
}
