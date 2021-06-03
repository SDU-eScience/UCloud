package dk.sdu.cloud.file.orchestrator.rpc

import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.file.orchestrator.api.FileCollections
import dk.sdu.cloud.file.orchestrator.service.FileCollectionService
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.actorAndProject

class FileCollectionController(
    private val fileCollections: FileCollectionService
) : Controller {
    override fun configure(rpcServer: RpcServer) = with(rpcServer) {
        /*
        implement(FileCollections.browse) {
            ok(fileCollections.browse(actorAndProject, request))
        }

        implement(FileCollections.create) {
            ok(fileCollections.create(actorAndProject, request))
        }

        implement(FileCollections.delete) {
            fileCollections.delete(actorAndProject, request)
            ok(Unit)
        }

        implement(FileCollections.rename) {
            fileCollections.rename(actorAndProject, request)
            ok(Unit)
        }

        implement(FileCollections.retrieve) {
            ok(fileCollections.retrieve(actorAndProject, request))
        }

        implement(FileCollections.retrieveManifest) {
            ok(fileCollections.retrieveManifest(request))
        }

        implement(FileCollections.updateAcl) {
            fileCollections.updateAcl(actorAndProject, request)
            ok(Unit)
        }

         */

        return@with
    }
}