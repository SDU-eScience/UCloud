package dk.sdu.cloud.file.ucloud.rpc

import dk.sdu.cloud.base64Decode
import dk.sdu.cloud.calls.BulkResponse
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.bulkRequestOf
import dk.sdu.cloud.calls.server.*
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.file.orchestrator.api.*
import dk.sdu.cloud.file.ucloud.api.UCloudFileDownload
import dk.sdu.cloud.file.ucloud.services.*
import dk.sdu.cloud.file.ucloud.services.tasks.EmptyTrashRequestItem
import dk.sdu.cloud.file.ucloud.services.tasks.TrashRequestItem
import dk.sdu.cloud.provider.api.FEATURE_NOT_SUPPORTED_BY_PROVIDER
import dk.sdu.cloud.provider.api.IntegrationProvider
import dk.sdu.cloud.service.Controller
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement

val CallHandler<*, *, *>.ucloudUsername: String?
    get() {
        var username: String? = null
        withContext<HttpCall> {
            username = ctx.ktor.call.request.header(IntegrationProvider.UCLOUD_USERNAME_HEADER)
                ?.let { base64Decode(it).decodeToString() }
        }

        withContext<WSCall> {
            username = ctx.session.underlyingSession.call.request.header(IntegrationProvider.UCLOUD_USERNAME_HEADER)
                ?.let { base64Decode(it).decodeToString() }
        }
        return username
    }

class FilesController(
    private val providerId: String,
    private val fileQueries: FileQueries,
    private val taskSystem: TaskSystem,
    private val chunkedUploadService: ChunkedUploadService,
    private val downloadService: DownloadService,
    private val limitChecker: LimitChecker,
    private val elasticQueryService: ElasticQueryService?,
    private val memberFiles: MemberFiles,
) : Controller {
    private val chunkedProtocol = ChunkedUploadProtocol(providerId, "/ucloud/$providerId/chunked")

    override fun configure(rpcServer: RpcServer) = with(rpcServer) {
        val filesApi = FilesProvider(providerId)

        implement(filesApi.init) {
            memberFiles.initializeMemberFiles(request.principal.createdBy, request.principal.project)
            ok(Unit)
        }

        implement(filesApi.retrieve) {
            ok(
                fileQueries.retrieve(
                    UCloudFile.create(request.retrieve.id),
                    request.retrieve.flags
                )
            )
        }

        implement(filesApi.browse) {
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

        implement(filesApi.search) {
            if (elasticQueryService == null) {
                throw RPCException(
                    "Search is not supported by this provider",
                    HttpStatusCode.BadRequest,
                    FEATURE_NOT_SUPPORTED_BY_PROVIDER
                )
            }

            ok(elasticQueryService.query(request))
        }

        implement(filesApi.copy) {
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

        implement(filesApi.createUpload) {
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

        implement(filesApi.move) {
            for (reqItem in request.items) {
                limitChecker.checkLimit(reqItem.resolvedNewCollection)
            }

            for (reqItem in request.items) {
                if (reqItem.oldId.substringBeforeLast('/') == reqItem.newId.substringBeforeLast('/')) {
                    if (reqItem.conflictPolicy == WriteConflictPolicy.REJECT &&
                        fileQueries.fileExists(UCloudFile.create(reqItem.newId))) {
                        throw RPCException("File or folder already exists", HttpStatusCode.Conflict)
                    }
                }
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

        implement(filesApi.delete) {
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

        implement(filesApi.trash) {
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

        implement(filesApi.emptyTrash) {
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

        implement(filesApi.createFolder) {
            for (reqItem in request.items) {
                limitChecker.checkLimit(reqItem.resolvedCollection)
            }

            for (reqItem in request.items) {
                if (reqItem.conflictPolicy == WriteConflictPolicy.REJECT &&
                    fileQueries.fileExists(UCloudFile.create(reqItem.id))) {
                    throw RPCException("Folder already exists", HttpStatusCode.Conflict)
                }
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

        implement(filesApi.updateAcl) {
            ok(BulkResponse(request.items.map { Unit }))
        }

        implement(chunkedProtocol.uploadChunk) {
            withContext<HttpCall> {
                val contentLength = ctx.ktor.call.request.header(HttpHeaders.ContentLength)?.toLongOrNull()
                    ?: throw FSException.BadRequest()
                val channel = ctx.context.request.receiveChannel()

                chunkedUploadService.receiveChunk(request, contentLength, channel)
                ok(Unit)
            }
        }

        implement(filesApi.createDownload) {
            ok(downloadService.createSessions(request))
        }

        implement(UCloudFileDownload(providerId).download) {
            withContext<HttpCall> {
                audit(Unit)
                downloadService.download(request.token, ctx)
                okContentAlreadyDelivered()
            }
        }
        return@with
    }
}
