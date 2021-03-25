package dk.sdu.cloud.app.kubernetes.rpc

import dk.sdu.cloud.Roles
import dk.sdu.cloud.app.kubernetes.api.KubernetesLicenseMaintenance
import dk.sdu.cloud.app.kubernetes.api.KubernetesLicenses
import dk.sdu.cloud.app.kubernetes.services.LicenseService
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.server.CallHandler
import dk.sdu.cloud.calls.server.HttpCall
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.calls.server.withContext
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.TokenValidation
import dk.sdu.cloud.service.validateAndDecodeOrNull
import io.ktor.http.*

class LicenseController(
    private val service: LicenseService,
    private val tokenValidation: TokenValidation<*>,
) : Controller {
    override fun configure(rpcServer: RpcServer) = with(rpcServer) {
        implement(KubernetesLicenses.create) {
            service.createInstance(request)
            ok(Unit)
        }

        implement(KubernetesLicenses.delete) {
            service.deleteInstance(request)
            ok(Unit)
        }

        implement(KubernetesLicenses.verify) {
            ok(Unit)
        }

        implement(KubernetesLicenseMaintenance.create) {
            verifyUser()
            service.createServer(request)
            ok(Unit)
        }

        implement(KubernetesLicenseMaintenance.browse) {
            verifyUser()
            ok(service.browseServers(request))
        }

        implement(KubernetesLicenseMaintenance.update) {
            verifyUser()
            service.updateServer(request)
            ok(Unit)
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