package dk.sdu.cloud.auth.http

import dk.sdu.cloud.auth.api.ServiceLicenseAgreement
import dk.sdu.cloud.auth.services.SLAService
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.calls.server.securityPrincipal
import dk.sdu.cloud.service.Controller

class SLAController(private val slaService: SLAService) : Controller {
    override fun configure(rpcServer: RpcServer) = with(rpcServer) {
        implement(ServiceLicenseAgreement.find) {
            ok(slaService.fetchText())
        }

        implement(ServiceLicenseAgreement.accept) {
            slaService.accept(request.version, ctx.securityPrincipal)
            ok(Unit)
        }
        return@with
    }
}
