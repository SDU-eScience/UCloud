package dk.sdu.cloud.file.orchestrator.rpc

import dk.sdu.cloud.accounting.util.asController
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.bulkRequestOf
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.file.orchestrator.api.FileMetadataTemplateNamespaces
import dk.sdu.cloud.file.orchestrator.service.MetadataTemplateNamespaces
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.actorAndProject
import io.ktor.http.*

class FileMetadataTemplateController(
    private val namespaces: MetadataTemplateNamespaces,
) : Controller {
    override fun configure(rpcServer: RpcServer) = with(rpcServer) {
        namespaces.asController().configure(rpcServer)
        implement(FileMetadataTemplateNamespaces.deprecate) {
            TODO()
        }

        implement(FileMetadataTemplateNamespaces.browseTemplates) {
            ok(namespaces.browseTemplates(actorAndProject, request))
        }

        implement(FileMetadataTemplateNamespaces.retrieveLatest) {
            ok(namespaces.retrieveLatest(actorAndProject, request))
        }

        implement(FileMetadataTemplateNamespaces.retrieveTemplate) {
            ok(namespaces.retrieveTemplate(actorAndProject, bulkRequestOf(request)).responses.singleOrNull()
                ?: throw RPCException("Unknown template or no versions exist", HttpStatusCode.NotFound))
        }

        implement(FileMetadataTemplateNamespaces.createTemplate) {
            ok(namespaces.createTemplate(actorAndProject, request))
        }
        return@with
    }
}