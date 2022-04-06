package dk.sdu.cloud.app.orchestrator.rpc

import dk.sdu.cloud.accounting.util.asController
import dk.sdu.cloud.app.orchestrator.api.*
import dk.sdu.cloud.app.orchestrator.services.*
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.actorAndProject

class SyncthingController(
    private val service: SyncthingService
) : Controller {
    override fun configure(rpcServer: RpcServer) = with(rpcServer) {
        implement(Syncthing.retrieveConfiguration) {
            ok(service.retrieveConfiguration(actorAndProject, request))
        }

        implement(Syncthing.updateConfiguration) {
            ok(service.updateConfiguration(actorAndProject, request))
        }

        implement(Syncthing.resetConfiguration) {
            ok(service.resetConfiguration(actorAndProject, request))
        }

        return@with
    }
}
