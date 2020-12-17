package dk.sdu.cloud.app.orchestrator.rpc

import dk.sdu.cloud.app.orchestrator.api.LicenseControl
import dk.sdu.cloud.app.orchestrator.api.Licenses
import dk.sdu.cloud.app.orchestrator.api.LicensesCreateResponse
import dk.sdu.cloud.app.orchestrator.services.LicenseService
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.calls.server.project
import dk.sdu.cloud.calls.server.securityPrincipal
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.toActor

class LicenseController(
    private val ingressService: LicenseService
) : Controller {
    override fun configure(rpcServer: RpcServer) = with(rpcServer) {
        implement(Licenses.browse) {
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

        implement(Licenses.create) {
            ok(
                LicensesCreateResponse(
                    ingressService.create(
                        ctx.securityPrincipal.toActor(),
                        ctx.project,
                        request
                    )
                )
            )
        }

        implement(Licenses.delete) {
            ingressService.delete(ctx.securityPrincipal.toActor(), request)
            ok(Unit)
        }

        implement(Licenses.retrieve) {
            ok(ingressService.retrieve(ctx.securityPrincipal.toActor(), request, request))
        }

        implement(LicenseControl.update) {
            ingressService.update(ctx.securityPrincipal.toActor(), request)
            ok(Unit)
        }

        implement(LicenseControl.retrieve) {
            ok(ingressService.retrieve(ctx.securityPrincipal.toActor(), request, request))
        }

        implement(LicenseControl.chargeCredits) {
            ok(ingressService.charge(ctx.securityPrincipal.toActor(), request))
        }

        return@with
    }
}
