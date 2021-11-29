package dk.sdu.cloud.file.ucloud.rpc

import dk.sdu.cloud.accounting.api.UCLOUD_PROVIDER
import dk.sdu.cloud.calls.BulkResponse
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.bulkRequestOf
import dk.sdu.cloud.calls.server.*
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.file.orchestrator.api.*
import dk.sdu.cloud.file.ucloud.api.UCloudFileDownload
import dk.sdu.cloud.file.ucloud.api.UCloudFiles
import dk.sdu.cloud.file.ucloud.services.*
import dk.sdu.cloud.file.ucloud.services.tasks.EmptyTrashRequestItem
import dk.sdu.cloud.file.ucloud.services.tasks.TrashRequestItem
import dk.sdu.cloud.provider.api.IntegrationProvider
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.PageV2
import dk.sdu.cloud.service.Time
import dk.sdu.cloud.service.actorAndProject
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
    private val limitChecker: LimitChecker,
    private val elasticQueryService: ElasticQueryService
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

        implement(UCloudFiles.search) {
            // TODO(Dan): Obviously needs to be replaced by an actual implementation
            val result = elasticQueryService.query(request, actorAndProject)

            ok(
                PageV2(
                    50,
                    (0 until 50).map {
                        PartialUFile(
                            "/19/${request.query}_$it",
                            UFileStatus(),
                            Time.now()
                        )
                    },
                    "fie"
                )
            )
        }

        implement(UCloudFiles.copy) {
            request.items.forEach { req ->
                limitChecker.checkLimit(req.resolvedNewCollection)
            }

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

                limitChecker.checkLimit(reqItem.resolvedCollection)
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
            for (reqItem in request.items) {
                limitChecker.checkLimit(reqItem.resolvedNewCollection)
            }

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

        implement(UCloudFiles.emptyTrash) {
            val username = ucloudUsername ?: throw  RPCException("No username supplied", HttpStatusCode.BadRequest)
            ok(
                BulkResponse(
                    request.items.map { requestItem ->
                        taskSystem.submitTask(
                            Files.emptyTrash.fullName,
                            defaultMapper.encodeToJsonElement(
                                bulkRequestOf(EmptyTrashRequestItem(username, requestItem.id))
                            ) as JsonObject
                        )
                    }
                )
            )
        }

        implement(UCloudFiles.createFolder) {
            for (reqItem in request.items) {
                limitChecker.checkLimit(reqItem.resolvedCollection)
            }

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
