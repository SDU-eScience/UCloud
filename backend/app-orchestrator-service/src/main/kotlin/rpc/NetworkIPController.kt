package dk.sdu.cloud.app.orchestrator.rpc

import dk.sdu.cloud.app.orchestrator.api.*
import dk.sdu.cloud.app.orchestrator.services.NetworkIPService
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.calls.server.project
import dk.sdu.cloud.calls.server.securityPrincipal
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.toActor

class NetworkIPController(
    private val service: NetworkIPService
) : Controller {
    override fun configure(rpcServer: RpcServer) = with(rpcServer) {
        implement(NetworkIPs.browse) {
            ok(
                service.browse(
                    ctx.securityPrincipal.toActor(),
                    ctx.project,
                    request.normalize(),
                    request,
                    request
                )
            )
        }

        implement(NetworkIPs.create) {
            ok(
                NetworkIPsCreateResponse(
                    service.create(
                        ctx.securityPrincipal.toActor(),
                        ctx.project,
                        request
                    )
                )
            )
        }

        implement(NetworkIPs.delete) {
            service.delete(ctx.securityPrincipal.toActor(), request)
            ok(Unit)
        }

        implement(NetworkIPs.retrieve) {
            ok(service.retrieve(ctx.securityPrincipal.toActor(), request, request))
        }

        implement(NetworkIPs.updateAcl) {
            service.updateAcl(ctx.securityPrincipal.toActor(), request)
            ok(Unit)
        }

        implement(NetworkIPs.updateFirewall) {
            service.updateFirewall(ctx.securityPrincipal.toActor(), request)
            ok(Unit)
        }

        implement(NetworkIPControl.update) {
            service.update(ctx.securityPrincipal.toActor(), request)
            ok(Unit)
        }

        implement(NetworkIPControl.retrieve) {
            ok(service.retrieve(ctx.securityPrincipal.toActor(), request, request))
        }

        implement(NetworkIPControl.chargeCredits) {
            ok(service.charge(ctx.securityPrincipal.toActor(), request))
        }

        return@with
    }
}
