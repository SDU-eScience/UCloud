package dk.sdu.cloud.app.kubernetes.rpc

import dk.sdu.cloud.app.kubernetes.api.AppKubernetesDescriptions
import dk.sdu.cloud.app.kubernetes.services.PodService
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.Loggable
import io.ktor.http.HttpStatusCode

class AppKubernetesController(
    private val podService: PodService
) : Controller {
    override fun configure(rpcServer: RpcServer): Unit = with(rpcServer) {
        implement(AppKubernetesDescriptions.cleanup) {}
        implement(AppKubernetesDescriptions.follow) {}

        implement(AppKubernetesDescriptions.jobVerified) {
            podService.create(request.id)
            ok(Unit)
        }

        implement(AppKubernetesDescriptions.submitFile) {
            throw RPCException.fromStatusCode(HttpStatusCode.BadRequest) // Not supported
        }

        implement(AppKubernetesDescriptions.jobPrepared) {
//            podService.startContainer(request)
            ok(Unit)
        }
        return@configure
    }

    companion object : Loggable {
        override val log = logger()
    }
}
