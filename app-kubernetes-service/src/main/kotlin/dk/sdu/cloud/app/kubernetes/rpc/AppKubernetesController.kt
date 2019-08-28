package dk.sdu.cloud.app.kubernetes.rpc

import dk.sdu.cloud.app.kubernetes.api.AppKubernetesDescriptions
import dk.sdu.cloud.app.kubernetes.services.PodService
import dk.sdu.cloud.app.kubernetes.services.VncService
import dk.sdu.cloud.app.kubernetes.services.WebService
import dk.sdu.cloud.app.orchestrator.api.InternalFollowWSStreamResponse
import dk.sdu.cloud.app.orchestrator.api.InternalStdStreamsResponse
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.calls.server.WSCall
import dk.sdu.cloud.calls.server.sendWSMessage
import dk.sdu.cloud.calls.server.withContext
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.Loggable
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.io.Closeable
import java.util.*
import kotlin.collections.HashMap

class AppKubernetesController(
    private val podService: PodService,
    private val vncService: VncService,
    private val webService: WebService
) : Controller {
    private val streams = HashMap<String, Closeable>()

    override fun configure(rpcServer: RpcServer): Unit = with(rpcServer) {
        implement(AppKubernetesDescriptions.cleanup) {
            podService.cleanup(request.id)
            ok(Unit)
        }

        implement(AppKubernetesDescriptions.follow) {
            val (log, nextLine) = podService.retrieveLogs(
                request.job.id,
                request.stdoutLineStart,
                request.stdoutMaxLines
            )

            ok(InternalStdStreamsResponse(log, nextLine, "", 0))
        }

        implement(AppKubernetesDescriptions.cancelWSStream) {
            streams.remove(request.streamId)?.close()
            ok(Unit)
        }

        implement(AppKubernetesDescriptions.followWSStream) {
            val streamId = UUID.randomUUID().toString()
            sendWSMessage(InternalFollowWSStreamResponse(streamId))

            // We send all the lines that the client has missed
//            val (log, _) = podService.retrieveLogs(
//                request.job.id,
//                request.stdoutLineStart,
//                Int.MAX_VALUE
//            )

            sendWSMessage(InternalFollowWSStreamResponse(streamId, null, null))

            // Then we set up the subscription
            coroutineScope {
                launch(Dispatchers.IO) {
                    log.info("We are now watching the log of ${request.job.id}")
                    val (resource, logStream) = podService.watchLog(request.job.id) ?: return@launch
                    streams[streamId] = resource
                    log.info("Watch stream obtained!")

                    withContext<WSCall> {
                        ctx.session.addOnCloseHandler {
                            log.info("Closing connection!")
                            resource.close()
                        }
                    }

                    val buffer = CharArray(4096)
                    val reader = logStream.reader()
                    while (streams[streamId] != null) {
                        val read = reader.read(buffer)
                        if (read == -1) break
                        log.info("Read $read bytes from stream...")

                        sendWSMessage(InternalFollowWSStreamResponse(streamId, String(buffer, 0, read), null))
                    }

                    log.info("End of log")
                }.join()
            }

            ok(InternalFollowWSStreamResponse(streamId))
        }

        implement(AppKubernetesDescriptions.jobVerified) {
            val sharedFileSystemMountsAreSupported =
                request.sharedFileSystemMounts.all { it.sharedFileSystem.backend == "kubernetes" }

            if (!sharedFileSystemMountsAreSupported) {
                throw RPCException(
                    "A file system mount was attempted which this backend does not support",
                    HttpStatusCode.BadRequest
                )
            }

            ok(Unit)
        }

        implement(AppKubernetesDescriptions.submitFile) {
            throw RPCException.fromStatusCode(HttpStatusCode.BadRequest) // Not supported
        }

        implement(AppKubernetesDescriptions.jobPrepared) {
            podService.create(request)
            ok(Unit)
        }

        implement(AppKubernetesDescriptions.queryInternalVncParameters) {
            ok(vncService.queryParameters(request.verifiedJob))
        }

        implement(AppKubernetesDescriptions.queryInternalWebParameters) {
            ok(webService.queryParameters(request.verifiedJob))
        }

        implement(AppKubernetesDescriptions.cancel) {
            podService.cancel(request.verifiedJob)
            ok(Unit)
        }

        return@configure
    }

    companion object : Loggable {
        override val log = logger()
    }
}
