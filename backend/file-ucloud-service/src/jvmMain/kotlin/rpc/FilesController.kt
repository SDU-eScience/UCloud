package dk.sdu.cloud.file.ucloud.rpc

import dk.sdu.cloud.accounting.api.UCLOUD_PROVIDER
import dk.sdu.cloud.calls.BulkResponse
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.bulkRequestOf
import dk.sdu.cloud.calls.server.CallHandler
import dk.sdu.cloud.calls.server.HttpCall
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.calls.server.WSCall
import dk.sdu.cloud.calls.server.audit
import dk.sdu.cloud.calls.server.withContext
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.file.orchestrator.api.ChunkedUploadProtocol
import dk.sdu.cloud.file.orchestrator.api.Files
import dk.sdu.cloud.file.orchestrator.api.FilesCreateUploadResponseItem
import dk.sdu.cloud.file.orchestrator.api.FilesSortBy
import dk.sdu.cloud.file.orchestrator.api.UploadProtocol
import dk.sdu.cloud.file.ucloud.api.UCloudFileDownload
import dk.sdu.cloud.file.ucloud.api.UCloudFiles
import dk.sdu.cloud.file.ucloud.services.ChunkedUploadService
import dk.sdu.cloud.file.ucloud.services.DownloadService
import dk.sdu.cloud.file.ucloud.services.FSException
import dk.sdu.cloud.file.ucloud.services.FileQueries
import dk.sdu.cloud.file.ucloud.services.TaskSystem
import dk.sdu.cloud.file.ucloud.services.UCloudFile
import dk.sdu.cloud.file.ucloud.services.tasks.TrashRequestItem
import dk.sdu.cloud.provider.api.IntegrationProvider
import dk.sdu.cloud.service.Controller
import io.ktor.application.*
import io.ktor.http.*
import io.ktor.request.*
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement

val CallHandler<*, *, *>.ucloudUsername: String?
    get() {
        var username: String? = null
        withContext<HttpCall> {
            username = ctx.call.request.header(IntegrationProvider.UCLOUD_USERNAME_HEADER)
        }

        withContext<WSCall> {
            username = ctx.session.underlyingSession.call.request.header(IntegrationProvider.UCLOUD_USERNAME_HEADER)
        }
        return username
    }

class FilesController(
    private val fileQueries: FileQueries,
    private val taskSystem: TaskSystem,
    private val chunkedUploadService: ChunkedUploadService,
    private val downloadService: DownloadService,
) : Controller {
    private val chunkedProtocol = ChunkedUploadProtocol(UCLOUD_PROVIDER, "/ucloud/ucloud/chunked")

    override fun configure(rpcServer: RpcServer) = with(rpcServer) {
        implement(UCloudFiles.retrieve) {
            ok(
                fileQueries.retrieve(
                    UCloudFile.create(request.retrieve.id),
                    request.retrieve.flags
                )
            )
        }

        implement(UCloudFiles.browse) {
            val path = request.browse.flags.path ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound)

            ok(
                fileQueries.browseFiles(
                    UCloudFile.create(path),
                    request.browse.flags,
                    request.browse.normalize(),
                    FilesSortBy.valueOf(request.browse.sortBy ?: "PATH"),
                    request.browse.sortDirection
                )
            )
        }

        implement(UCloudFiles.copy) {
            ok(
                BulkResponse(
                    request.items.map { reqItem ->
                        taskSystem.submitTask(
                            Files.copy.fullName,
                            defaultMapper.encodeToJsonElement(bulkRequestOf(reqItem)) as JsonObject
                        )
                    }
                )
            )
        }

        implement(UCloudFiles.createUpload) {
            for (reqItem in request.items) {
                if (UploadProtocol.CHUNKED !in reqItem.supportedProtocols) {
                    throw RPCException("No protocols supported", HttpStatusCode.BadRequest)
                }
            }

            val responses = ArrayList<FilesCreateUploadResponseItem>()
            for (reqItem in request.items) {
                val id = chunkedUploadService.createSession(
                    UCloudFile.create(reqItem.id),
                    reqItem.conflictPolicy
                )

                responses.add(FilesCreateUploadResponseItem(chunkedProtocol.endpoint, UploadProtocol.CHUNKED, id))
            }

            ok(BulkResponse(responses))
        }

        implement(UCloudFiles.move) {
            ok(
                BulkResponse(
                    request.items.map { reqItem ->
                        taskSystem.submitTask(
                            Files.move.fullName,
                            defaultMapper.encodeToJsonElement(bulkRequestOf(reqItem)) as JsonObject
                        )
                    }
                )
            )
        }

        implement(UCloudFiles.delete) {
            ok(
                BulkResponse(
                    request.items.map { reqItem ->
                        taskSystem.submitTask(
                            Files.delete.fullName,
                            defaultMapper.encodeToJsonElement(bulkRequestOf(reqItem)) as JsonObject
                        )

                        Unit // TODO This is kind of annoying. LongRunningTasks as natively supported maybe?
                    }
                )
            )
        }

        implement(UCloudFiles.trash) {
            val username = ucloudUsername ?: throw RPCException("No username supplied", HttpStatusCode.BadRequest)
            ok(
                BulkResponse(
                    request.items.map { reqItem ->
                        taskSystem.submitTask(
                            Files.trash.fullName,
                            defaultMapper.encodeToJsonElement(
                                bulkRequestOf(TrashRequestItem(username, reqItem.id))
                            ) as JsonObject
                        )
                    }
                )
            )
        }

        implement(UCloudFiles.createFolder) {
            ok(
                BulkResponse(
                    request.items.map { reqItem ->
                        taskSystem.submitTask(
                            Files.createFolder.fullName,
                            defaultMapper.encodeToJsonElement(bulkRequestOf(reqItem)) as JsonObject
                        )
                    }
                )
            )
        }

        implement(UCloudFiles.updateAcl) {
            ok(BulkResponse(request.items.map { Unit }))
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

        implement(UCloudFiles.createDownload) {
            ok(downloadService.createSessions(request))
        }

        implement(UCloudFileDownload.download) {
            withContext<HttpCall> {
                audit(Unit)
                downloadService.download(request.token, ctx)
                okContentAlreadyDelivered()
            }
        }
        return@with
    }
}
