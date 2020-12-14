package dk.sdu.cloud.app.orchestrator.rpc

import dk.sdu.cloud.app.orchestrator.api.IngressControl
import dk.sdu.cloud.app.orchestrator.api.Ingresses
import dk.sdu.cloud.app.orchestrator.api.IngressesCreateResponse
import dk.sdu.cloud.app.orchestrator.services.IngressService
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.calls.server.project
import dk.sdu.cloud.calls.server.securityPrincipal
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.toActor

class IngressController(
    private val ingressService: IngressService
) : Controller {
    override fun configure(rpcServer: RpcServer) = with(rpcServer) {
        implement(Ingresses.browse) {
            ok(
                ingressService.browse(
                    ctx.securityPrincipal.toActor(),
                    ctx.project,
                    request.normalize(),
                    request,
                    request
                )
            )
        }

        implement(Ingresses.create) {
            ok(
                IngressesCreateResponse(
                    ingressService.create(
                        ctx.securityPrincipal.toActor(),
                        ctx.project,
                        request
                    )
                )
            )
        }

        implement(Ingresses.delete) {
            ingressService.delete(ctx.securityPrincipal.toActor(), request)
            ok(Unit)
        }

        implement(Ingresses.retrieve) {
            ok(ingressService.retrieve(ctx.securityPrincipal.toActor(), request, request))
        }

        implement(Ingresses.retrieveSettings) {
            ok(ingressService.retrieveSettings(ctx.securityPrincipal.toActor(), request))
        }

        implement(IngressControl.update) {
            ingressService.update(ctx.securityPrincipal.toActor(), request)
            ok(Unit)
        }

        implement(IngressControl.retrieve) {
            ok(ingressService.retrieve(ctx.securityPrincipal.toActor(), request, request))
        }

        implement(IngressControl.chargeCredits) {
            ok(ingressService.charge(ctx.securityPrincipal.toActor(), request))
        }

        return@with
    }
}
