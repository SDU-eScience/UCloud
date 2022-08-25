package dk.sdu.cloud.app.kubernetes.rpc

import dk.sdu.cloud.app.kubernetes.services.*
import dk.sdu.cloud.app.orchestrator.api.*
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.service.Controller

class SyncthingController(
    private val providerId: String,
    private val service: SyncthingService,
) : Controller {
    override fun configure(rpcServer: RpcServer) = with(rpcServer) {
        val api = SyncthingProvider(providerId)

        implement(api.retrieveConfiguration) {
            ok(service.retrieveConfiguration(request))
        }

        implement(api.updateConfiguration) {
            ok(service.updateConfiguration(request))
        }

        implement(api.resetConfiguration) {
            ok(service.resetConfiguration(request))
        }

        implement(api.restart) {
            ok(service.restart(request))
        }

        return@with
    }
}
