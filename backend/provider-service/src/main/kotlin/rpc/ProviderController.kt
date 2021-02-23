package dk.sdu.cloud.provider.rpc

import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.provider.api.Providers
import dk.sdu.cloud.provider.services.ProviderService
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.actorAndProject

class ProviderController(private val service: ProviderService) : Controller {
    override fun configure(rpcServer: RpcServer) = with(rpcServer) {
        implement(Providers.create) {
            ok(service.create(actorAndProject, request))
        }

        implement(Providers.updateManifest) {
            service.updateManifest(actorAndProject.actor, request)
            ok(Unit)
        }

        implement(Providers.updateAcl) {
            service.updateAcl(actorAndProject.actor, request)
            ok(Unit)
        }

        implement(Providers.renewToken) {
            service.renewToken(actorAndProject.actor, request)
            ok(Unit)
        }

        implement(Providers.retrieve) {
            ok(service.retrieveProvider(actorAndProject.actor, request.id))
        }

        implement(Providers.browse) {
            ok(service.browseProviders(actorAndProject, request.normalize()))
        }

        return@with
    }
}
