package dk.sdu.cloud.accounting.rpc

import dk.sdu.cloud.accounting.services.providers.ProviderService
import dk.sdu.cloud.accounting.util.asController
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.provider.api.Providers
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.actorAndProject

class ProviderController(
    private val service: ProviderService,
    private val devMode: Boolean
) : Controller {
    override fun configure(rpcServer: RpcServer) = with(rpcServer) {
        service.asController().configure(rpcServer)

        implement(Providers.renewToken) {
            ok(service.renewToken(actorAndProject, request))
        }

        implement(Providers.retrieveSpecification) {
            ok(service.retrieveSpecification(actorAndProject, request.id))
        }

        implement(Providers.requestApproval) {
            ok(service.requestApproval(actorAndProject.actor, request))
        }

        if (devMode) {
            // NOTE(Dan): I don't believe the UCloud role system is currently granular enough that we can simply allow
            // anyone with ADMIN privileges to run this command. In a production environment these can be approved
            // directly through the database function.
            implement(Providers.approve) {
                ok(service.approveRequest(request))
            }
        }

        return@with
    }
}
