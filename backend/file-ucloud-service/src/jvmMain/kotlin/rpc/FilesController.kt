package dk.sdu.cloud.file.ucloud.rpc

import dk.sdu.cloud.Actor
import dk.sdu.cloud.calls.BulkResponse
import dk.sdu.cloud.calls.bulkRequestOf
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.file.orchestrator.api.Files
import dk.sdu.cloud.file.orchestrator.api.FindByPath
import dk.sdu.cloud.file.orchestrator.api.LongRunningTask
import dk.sdu.cloud.file.ucloud.api.UCloudFiles
import dk.sdu.cloud.file.ucloud.services.FileQueries
import dk.sdu.cloud.file.ucloud.services.TaskSystem
import dk.sdu.cloud.file.ucloud.services.UCloudFile
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.actorAndProject
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement

class FilesController(
    private val fileQueries: FileQueries,
    private val taskSystem: TaskSystem,
) : Controller {
    override fun configure(rpcServer: RpcServer) = with(rpcServer) {
        implement(UCloudFiles.retrieve) {
            ok(
                fileQueries.retrieve(
                    Actor.SystemOnBehalfOfUser(request.username),
                    UCloudFile.create(request.request.path),
                    request.request
                )
            )
        }

        implement(UCloudFiles.browse) {
            ok(
                fileQueries.browseFiles(
                    Actor.SystemOnBehalfOfUser(request.username),
                    UCloudFile.create(request.request.path),
                    request.request,
                    request.request.normalize()
                )
            )
        }

        implement(UCloudFiles.copy) {
            ok(
                BulkResponse(
                    request.request.items.map { reqItem ->
                        taskSystem.submitTask(
                            Actor.SystemOnBehalfOfUser(request.username),
                            Files.copy.fullName,
                            defaultMapper.encodeToJsonElement(bulkRequestOf(reqItem)) as JsonObject
                        ) as LongRunningTask<FindByPath> // TODO That is not true
                    }
                )
            )
        }
        return@with
    }
}