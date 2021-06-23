package dk.sdu.cloud.file.synchronization.http

import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.Actor
import dk.sdu.cloud.calls.server.securityPrincipal
import dk.sdu.cloud.file.synchronization.api.FileSynchronizationDescriptions
import dk.sdu.cloud.file.synchronization.services.SynchronizationService

class SynchronizationController(
    private val synchronizationService: SynchronizationService
) : Controller {
    override fun configure(rpcServer: RpcServer) = with(rpcServer) {
        implement(FileSynchronizationDescriptions.retrieveFolder) {
            ok(synchronizationService.retrieveFolder(Actor.SystemOnBehalfOfUser(ctx.securityPrincipal.username), request.path))
        }

        implement(FileSynchronizationDescriptions.addFolder) {
            synchronizationService.addFolder(Actor.SystemOnBehalfOfUser(ctx.securityPrincipal.username), request)
            ok(Unit)
        }

        implement(FileSynchronizationDescriptions.removeFolder) {
            synchronizationService.removeFolder(Actor.SystemOnBehalfOfUser(ctx.securityPrincipal.username), request)
            ok(Unit)
        }

        implement(FileSynchronizationDescriptions.addDevice) {
            synchronizationService.addDevice(Actor.SystemOnBehalfOfUser(ctx.securityPrincipal.username), request)
            ok(Unit)
        }

        implement(FileSynchronizationDescriptions.removeDevice) {
            synchronizationService.removeDevice(Actor.SystemOnBehalfOfUser(ctx.securityPrincipal.username), request)
            ok(Unit)
        }

        implement(FileSynchronizationDescriptions.browseDevices) {
            ok(synchronizationService.browseDevices(Actor.SystemOnBehalfOfUser(ctx.securityPrincipal.username)))
        }
    }
}

