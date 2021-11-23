package dk.sdu.cloud.file.ucloud.rpc

import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.file.orchestrator.api.UFileIncludeFlags
import dk.sdu.cloud.file.ucloud.api.FileSearchDescriptions
import dk.sdu.cloud.file.ucloud.services.*
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.actorAndProject
import dk.sdu.cloud.service.db.async.mapItems

class FileSearchController (
    private val fileSearchService: FileSearchService,
    private val pathConverter: PathConverter,
    private val fileQueries: FileQueries
) : Controller {
    override fun configure(rpcServer: RpcServer) = with(rpcServer) {
        implement(FileSearchDescriptions.advancedSearch) {
            ok(
                fileSearchService.search(
                    request,
                    actorAndProject
                ).mapItems {
                    val file = pathConverter.internalToUCloud(InternalFile(it.path))
                    fileQueries.retrieve(file, UFileIncludeFlags())
                }
            )
        }
    }
}
