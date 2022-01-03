package dk.sdu.cloud.accounting.rpc

import dk.sdu.cloud.accounting.services.providers.ProviderIntegrationService
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.provider.api.Integration
import dk.sdu.cloud.provider.api.IntegrationControl
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.actorAndProject

class IntegrationController(
    private val integrationService: ProviderIntegrationService,
) : Controller {
    override fun configure(rpcServer: RpcServer) = with(rpcServer) {
        implement(IntegrationControl.approveConnection) {
            integrationService.approveConnection(actorAndProject.actor, request.username)
            ok(Unit)
        }

        implement(Integration.browse) {
            ok(integrationService.browse(actorAndProject, request.normalize()))
        }

        implement(Integration.clearConnection) {
            integrationService.clearConnection(request.username, request.provider)
            ok(Unit)
        }

        implement(Integration.connect) {
            ok(integrationService.connect(actorAndProject, request.provider))
        }

        return@with
    }
}