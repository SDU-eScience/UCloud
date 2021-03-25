package dk.sdu.cloud.app.kubernetes.rpc

import dk.sdu.cloud.Roles
import dk.sdu.cloud.app.kubernetes.api.KubernetesNetworkIP
import dk.sdu.cloud.app.kubernetes.api.KubernetesNetworkIPMaintenance
import dk.sdu.cloud.app.kubernetes.services.NetworkIPService
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.server.CallHandler
import dk.sdu.cloud.calls.server.HttpCall
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.calls.server.withContext
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.TokenValidation
import dk.sdu.cloud.service.validateAndDecodeOrNull
import io.ktor.http.*

class NetworkIPController(
    private val service: NetworkIPService,
    private val tokenValidation: TokenValidation<*>,
) : Controller {
    override fun configure(rpcServer: RpcServer) = with(rpcServer) {
        implement(KubernetesNetworkIP.create) {
            ok(service.create(request))
        }

        implement(KubernetesNetworkIP.delete) {
            ok(service.delete(request))
        }

        implement(KubernetesNetworkIP.verify) {
            ok(Unit)
        }

        implement(KubernetesNetworkIP.updateFirewall) {
            ok(Unit)
        }

        implement(KubernetesNetworkIPMaintenance.create) {
            verifyUser()
            ok(service.addToPool(request))
        }

        implement(KubernetesNetworkIPMaintenance.browse) {
            verifyUser()
            ok(service.browsePool(request.normalize()))
        }

        implement(KubernetesNetworkIPMaintenance.retrieveStatus) {
            verifyUser()
            ok(service.retrieveStatus())
        }
        return@with
    }

    private fun CallHandler<*, *, *>.verifyUser() {
        withContext<HttpCall> {
            val bearer = ctx.context.request.headers[HttpHeaders.Authorization]?.removePrefix("Bearer ")
                ?: throw RPCException.fromStatusCode(HttpStatusCode.Unauthorized)
            val principal = tokenValidation.validateAndDecodeOrNull(bearer)?.principal
                ?: throw RPCException.fromStatusCode(HttpStatusCode.Unauthorized)
            if (principal.role !in Roles.PRIVILEGED) throw RPCException.fromStatusCode(HttpStatusCode.Unauthorized)
        }
    }
}
