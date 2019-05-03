package dk.sdu.cloud.app.kubernetes.rpc

import dk.sdu.cloud.app.api.InternalStdStreamsResponse
import dk.sdu.cloud.app.api.QueryInternalVncParametersResponse
import dk.sdu.cloud.app.kubernetes.api.AppKubernetesDescriptions
import dk.sdu.cloud.app.kubernetes.services.PodService
import dk.sdu.cloud.app.kubernetes.services.VncService
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.Loggable
import io.ktor.http.HttpStatusCode

class AppKubernetesController(
    private val podService: PodService,
    private val vncService: VncService
) : Controller {
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

        implement(AppKubernetesDescriptions.jobVerified) {
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

        return@configure
    }

    companion object : Loggable {
        override val log = logger()
    }
}
