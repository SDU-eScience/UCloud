package dk.sdu.cloud.file.http

import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.file.api.FileSynchronization
import dk.sdu.cloud.file.services.SynchronizationService
import dk.sdu.cloud.service.actorAndProject

class SynchronizationController(
    private val synchronizationService: SynchronizationService
) : Controller {
    override fun configure(rpcServer: RpcServer) = with(rpcServer) {
        implement(FileSynchronization.retrieveFolder) {
            ok(synchronizationService.retrieveFolder(actorAndProject.actor, request.path))
        }

        implement(FileSynchronization.addFolder) {
            synchronizationService.addFolder(actorAndProject.actor, request)
            ok(Unit)
        }

        implement(FileSynchronization.removeFolder) {
            synchronizationService.removeFolder(actorAndProject.actor, request)
            ok(Unit)
        }

        implement(FileSynchronization.browseFolders) {
            ok(synchronizationService.browseFolders(request))
        }

        implement(FileSynchronization.addDevice) {
            synchronizationService.addDevice(actorAndProject.actor, request)
            ok(Unit)
        }

        implement(FileSynchronization.removeDevice) {
            synchronizationService.removeDevice(actorAndProject.actor, request)
            ok(Unit)
        }

        implement(FileSynchronization.browseDevices) {
            ok(synchronizationService.browseDevices(actorAndProject.actor, request))
        }
    }
}

