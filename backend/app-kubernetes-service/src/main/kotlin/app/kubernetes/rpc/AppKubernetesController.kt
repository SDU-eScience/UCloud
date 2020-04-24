package dk.sdu.cloud.app.kubernetes.rpc

import dk.sdu.cloud.FindByStringId
import dk.sdu.cloud.app.kubernetes.api.AppKubernetesDescriptions
import dk.sdu.cloud.app.kubernetes.services.K8JobCreationService
import dk.sdu.cloud.app.kubernetes.services.K8JobMonitoringService
import dk.sdu.cloud.app.kubernetes.services.K8LogService
import dk.sdu.cloud.app.kubernetes.services.VncService
import dk.sdu.cloud.app.kubernetes.services.WebService
import dk.sdu.cloud.app.orchestrator.api.InternalFollowWSStreamResponse
import dk.sdu.cloud.app.orchestrator.api.InternalStdStreamsResponse
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.calls.server.WSCall
import dk.sdu.cloud.calls.server.sendWSMessage
import dk.sdu.cloud.calls.server.withContext
import dk.sdu.cloud.events.EventStreamContainer
import dk.sdu.cloud.service.BroadcastingStream
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.Loggable
import io.ktor.http.HttpStatusCode
import io.ktor.util.cio.ChannelWriteException
import kotlinx.coroutines.runBlocking
import java.io.Closeable
import java.util.*
import kotlin.collections.HashMap

private object CancelWSStream : EventStreamContainer() {
    val events = stream<FindByStringId>("appk8-ws-cancel", { "id" })
}

class AppKubernetesController(
    private val jobMonitoringService: K8JobMonitoringService,
    private val jobCreationService: K8JobCreationService,
    private val logService: K8LogService,
    private val vncService: VncService,
    private val webService: WebService,
    private val broadcastStream: BroadcastingStream
) : Controller {
    private val streams = HashMap<String, Closeable>()

    override fun configure(rpcServer: RpcServer): Unit = with(rpcServer) {
        runBlocking {
            broadcastStream.subscribe(CancelWSStream.events) { (id) ->
                streams.remove(id)?.close()
            }
        }

        implement(AppKubernetesDescriptions.cleanup) {
            jobCreationService.cleanup(request.id)
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

            logService.useLogWatch(request.job.id) { resource, logStream ->
                log.debug("Following log for ${request.job.id}")
                streams[streamId] = resource

                withContext<WSCall> {
                    ctx.session.addOnCloseHandler {
                        log.debug("[Log: ${request.job.id}] Client has closed connection")
                        resource.close()
                    }
                }

                val buffer = CharArray(4096)
                val reader = logStream.reader()
                while (streams[streamId] != null) {
                    val read = reader.read(buffer)
                    if (read == -1) {
                        log.debug("[Log: ${request.job.id}] EOF reached")
                        resource.close()
                        streams.remove(streamId)
                        break
                    }
                    val stdout = String(buffer, 0, read)
                    log.debug("[Log: ${request.job.id}] stdout: $stdout")

                    try {
                        sendWSMessage(InternalFollowWSStreamResponse(streamId, stdout, null))
                    } catch (ex: ChannelWriteException) {
                        resource.close()
                        streams.remove(streamId)
                        break
                    }
                }
            }.join()

            ok(InternalFollowWSStreamResponse(streamId))
        }

        implement(AppKubernetesDescriptions.jobVerified) {
            ok(Unit)
        }

        implement(AppKubernetesDescriptions.submitFile) {
            throw RPCException.fromStatusCode(HttpStatusCode.BadRequest) // Not supported
        }

        implement(AppKubernetesDescriptions.jobPrepared) {
            jobCreationService.create(request)
            ok(Unit)
        }

        implement(AppKubernetesDescriptions.queryInternalVncParameters) {
            ok(vncService.queryParameters(request.verifiedJob))
        }

        implement(AppKubernetesDescriptions.queryInternalWebParameters) {
            ok(webService.queryParameters(request.verifiedJob))
        }

        implement(AppKubernetesDescriptions.cancel) {
            jobMonitoringService.cancel(request.verifiedJob)
            ok(Unit)
        }

        return@configure
    }

    companion object : Loggable {
        override val log = logger()
    }
}
