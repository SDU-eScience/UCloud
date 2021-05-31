package dk.sdu.cloud.file.ucloud.rpc

import dk.sdu.cloud.Actor
import dk.sdu.cloud.ActorAndProject
import dk.sdu.cloud.accounting.api.ProductReference
import dk.sdu.cloud.accounting.api.UCLOUD_PROVIDER
import dk.sdu.cloud.calls.server.CallHandler
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.file.orchestrator.api.*
import dk.sdu.cloud.file.ucloud.api.UCloudFileCollections
import dk.sdu.cloud.file.ucloud.api.UCloudFiles
import dk.sdu.cloud.file.ucloud.services.FileCollectionsService
import dk.sdu.cloud.file.ucloud.services.UCloudFile
import dk.sdu.cloud.file.ucloud.services.productSupport
import dk.sdu.cloud.service.Controller

class FileCollectionsController(
    private val fileCollections: FileCollectionsService,
) : Controller {
    override fun configure(rpcServer: RpcServer) = with(rpcServer) {
        implement(UCloudFileCollections.retrieveManifest) {
            // TODO We probably need to split the home and project partition into two different products
            ok(FileCollectionsProviderRetrieveManifestResponse(listOf(productSupport)))
        }

        /*
        implement(UCloudFileCollections.browse) {
            ok(fileCollections.browse(retrieveActorAndProject(), request.request.normalize()))
        }

        implement(UCloudFileCollections.retrieve) {
            ok(fileCollections.retrieve(retrieveActorAndProject(), request.request.id))
        }
         */

        implement(UCloudFileCollections.create) {
            ok(fileCollections.create(retrieveActorAndProject(), request.request))
        }

        implement(UCloudFileCollections.rename) {
            ok(fileCollections.rename(retrieveActorAndProject(), request.request))
        }

        implement(UCloudFileCollections.delete) {
            ok(fileCollections.delete(retrieveActorAndProject(), request.request))
        }

        implement(UCloudFileCollections.updateAcl) {
            ok(fileCollections.updateAcl(retrieveActorAndProject(), request.request))
        }

        return@with
    }

    private fun <T> CallHandler<ProxiedRequest<T>, *, *>.retrieveActorAndProject(): ActorAndProject {
        return ActorAndProject(Actor.SystemOnBehalfOfUser(request.username), request.project)
    }
}
