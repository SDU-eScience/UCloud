package dk.sdu.cloud.file.ucloud.rpc

import dk.sdu.cloud.Actor
import dk.sdu.cloud.accounting.api.UCLOUD_PROVIDER
import dk.sdu.cloud.calls.BulkResponse
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.bulkRequestOf
import dk.sdu.cloud.calls.server.HttpCall
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.calls.server.withContext
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.file.orchestrator.api.*
import dk.sdu.cloud.file.ucloud.api.UCloudFiles
import dk.sdu.cloud.file.ucloud.services.*
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.actorAndProject
import io.ktor.application.*
import io.ktor.http.*
import io.ktor.request.*
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement

class FilesController(
    private val fileQueries: FileQueries,
    private val taskSystem: TaskSystem,
    private val chunkedUploadService: ChunkedUploadService,
) : Controller {
    private val chunkedProtocol = ChunkedUploadProtocol(UCLOUD_PROVIDER, "/ucloud/ucloud/chunked")

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

        implement(UCloudFiles.createUpload) {
            val actor = Actor.SystemOnBehalfOfUser(request.username)
            for (reqItem in request.request.items) {
                if (UploadProtocol.CHUNKED !in reqItem.supportedProtocols) {
                    throw RPCException("No protocols supported", HttpStatusCode.BadRequest)
                }
            }

            val responses = ArrayList<FilesCreateUploadResponseItem>()
            for (reqItem in request.request.items) {
                val id = chunkedUploadService.createSession(
                    actor,
                    UCloudFile.create(reqItem.path),
                    reqItem.conflictPolicy
                )

                responses.add(FilesCreateUploadResponseItem(chunkedProtocol.endpoint, UploadProtocol.CHUNKED, id))
            }

            ok(BulkResponse(responses))
        }

        implement(chunkedProtocol.uploadChunk) {
            withContext<HttpCall> {
                val contentLength = ctx.call.request.header(HttpHeaders.ContentLength)?.toLongOrNull()
                    ?: throw FSException.BadRequest()
                val channel = ctx.context.request.receiveChannel()

                chunkedUploadService.receiveChunk(request, contentLength, channel)
                ok(Unit)
            }
        }
        return@with
    }
}
