package dk.sdu.cloud.plugins

import dk.sdu.cloud.FindByStringId
import dk.sdu.cloud.PageV2
import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.accounting.api.ProductReference
import dk.sdu.cloud.accounting.api.ProductV2
import dk.sdu.cloud.calls.BulkRequest
import dk.sdu.cloud.calls.BulkResponse
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.server.HttpCall
import dk.sdu.cloud.config.*
import dk.sdu.cloud.controllers.FileListingEntry
import dk.sdu.cloud.controllers.RequestContext
import dk.sdu.cloud.file.orchestrator.api.*
import dk.sdu.cloud.service.SimpleCache
import dk.sdu.cloud.task.api.BackgroundTask
import dk.sdu.cloud.task.api.PauseOrCancelRequest
import dk.sdu.cloud.task.api.PostStatusRequest
import io.ktor.utils.io.*
import io.ktor.websocket.*
import kotlinx.coroutines.channels.ReceiveChannel

interface PathLike<T> {
    val path: String
    fun withNewPath(path: String): T
}

@JvmInline
value class InternalFile(override val path: String) : PathLike<InternalFile> {
    override fun withNewPath(path: String): InternalFile = InternalFile(path)
}

@JvmInline
value class UCloudFile private constructor(override val path: String) : PathLike<UCloudFile> {
    override fun withNewPath(path: String): UCloudFile = UCloudFile(path)

    companion object {
        fun create(path: String) = UCloudFile(path.normalize())
        fun createFromPreNormalizedString(path: String) = UCloudFile(path)
    }
}

fun <T : PathLike<T>> T.parent(): T = withNewPath(path.parent())
fun <T : PathLike<T>> T.parents(): List<T> = path.parents().map { withNewPath(it) }
fun <T : PathLike<T>> T.normalize(): T = withNewPath(path.normalize())
fun <T : PathLike<T>> T.components(): List<String> = path.components()
fun <T : PathLike<T>> T.fileName(): String = path.fileName()
fun <T : PathLike<T>> T.child(fileName: String): T = withNewPath("${path.removeSuffix("/")}/$fileName".removeSuffix("/"))

data class FileDownloadSession(val session: String, val pluginData: String)
data class FileUploadSession(val session: String, val pluginData: String)

interface FilePlugin : ResourcePlugin<Product.Storage, FSSupport, UFile, ConfigSchema.Plugins.Files> {
    var supportedUploadProtocols: List<UploadProtocol>

    suspend fun RequestContext.browse(path: UCloudFile, request: FilesProviderBrowseRequest): PageV2<PartialUFile>
    suspend fun RequestContext.retrieve(request: FilesProviderRetrieveRequest): PartialUFile
    suspend fun RequestContext.createDownload(request: BulkRequest<FilesProviderCreateDownloadRequestItem>): List<FileDownloadSession>
    suspend fun RequestContext.handleDownload(ctx: HttpCall, session: String, pluginData: String)
    suspend fun RequestContext.createFolder(req: BulkRequest<FilesProviderCreateFolderRequestItem>): List<BackgroundTask?>
    suspend fun RequestContext.createUpload(request: BulkRequest<FilesProviderCreateUploadRequestItem>): List<FileUploadSession>
    suspend fun RequestContext.handleUpload(session: String, pluginData: String, offset: Long, chunk: ByteReadChannel, lastChunk: Boolean)
    suspend fun RequestContext.handleFolderUpload(session: String, pluginData: String, fileCollections: SimpleCache<String, FileCollection>, fileEntry: FileListingEntry, chunk: ByteReadChannel, lastChunk: Boolean)
    suspend fun RequestContext.handleUploadWs(session: String, pluginData: String, fileCollections: SimpleCache<String, FileCollection>, websocket: WebSocketSession)
    suspend fun RequestContext.handleFolderUploadWs(session: String, pluginData: String, fileCollections: SimpleCache<String, FileCollection>, websocket: WebSocketSession)
    suspend fun RequestContext.moveToTrash(request: BulkRequest<FilesProviderTrashRequestItem>): List<BackgroundTask?>
    suspend fun RequestContext.emptyTrash(request: BulkRequest<FilesProviderEmptyTrashRequestItem>): List<BackgroundTask?>
    suspend fun RequestContext.move(req: BulkRequest<FilesProviderMoveRequestItem>): List<BackgroundTask?>
    suspend fun RequestContext.copy(req: BulkRequest<FilesProviderCopyRequestItem>): List<BackgroundTask?>
    suspend fun RequestContext.streamingSearch(
        req: FilesProviderStreamingSearchRequest
    ): ReceiveChannel<FilesProviderStreamingSearchResult.Result> {
        throw RPCException("Streaming search is not supported by this provider", HttpStatusCode.BadRequest)
    }
    suspend fun RequestContext.modifyTask(request: BackgroundTask) {
        println("MODIFYING TASK: $request")
    }

    override suspend fun RequestContext.create(resource: UFile): FindByStringId? {
        error("Not supported by this plugin")
    }
}

abstract class EmptyFilePlugin : FilePlugin {
    override var pluginName = "Unknown"
    override var productAllocation: List<ProductReferenceWithoutProvider> = emptyList()
    override var productAllocationResolved: List<ProductV2> = emptyList()
    override var supportedUploadProtocols: List<UploadProtocol> = emptyList()

    override suspend fun RequestContext.browse(path: UCloudFile, request: FilesProviderBrowseRequest): PageV2<PartialUFile> = throw RPCException("Not supported", HttpStatusCode.BadRequest)
    override suspend fun RequestContext.retrieve(request: FilesProviderRetrieveRequest): PartialUFile = throw RPCException("Not supported", HttpStatusCode.BadRequest)
    override suspend fun RequestContext.createDownload(request: BulkRequest<FilesProviderCreateDownloadRequestItem>): List<FileDownloadSession> = throw RPCException("Not supported", HttpStatusCode.BadRequest)
    override suspend fun RequestContext.handleDownload(ctx: HttpCall, session: String, pluginData: String) = throw RPCException("Not supported", HttpStatusCode.BadRequest)
    override suspend fun RequestContext.createFolder(req: BulkRequest<FilesProviderCreateFolderRequestItem>): List<BackgroundTask?> = throw RPCException("Not supported", HttpStatusCode.BadRequest)
    override suspend fun RequestContext.createUpload(request: BulkRequest<FilesProviderCreateUploadRequestItem>): List<FileUploadSession> = throw RPCException("Not supported", HttpStatusCode.BadRequest)
    override suspend fun RequestContext.handleUpload(session: String, pluginData: String, offset: Long, chunk: ByteReadChannel, lastChunk: Boolean) = throw RPCException("Not supported", HttpStatusCode.BadRequest)
    override suspend fun RequestContext.handleFolderUpload(session: String, pluginData: String, fileCollections: SimpleCache<String, FileCollection>, fileEntry: FileListingEntry, chunk: ByteReadChannel, lastChunk: Boolean) = throw RPCException("Not supported", HttpStatusCode.BadRequest)
    override suspend fun RequestContext.handleUploadWs(session: String, pluginData: String, fileCollections: SimpleCache<String, FileCollection>, websocket: WebSocketSession) = throw RPCException("Not supported", HttpStatusCode.BadRequest)
    override suspend fun RequestContext.handleFolderUploadWs(session: String, pluginData: String, collection: SimpleCache<String, FileCollection>, websocket: WebSocketSession) = throw RPCException("Not supported", HttpStatusCode.BadRequest)
    override suspend fun RequestContext.moveToTrash(request: BulkRequest<FilesProviderTrashRequestItem>): List<BackgroundTask?> = throw RPCException("Not supported", HttpStatusCode.BadRequest)
    override suspend fun RequestContext.emptyTrash(request: BulkRequest<FilesProviderEmptyTrashRequestItem>): List<BackgroundTask?> = throw RPCException("Not supported", HttpStatusCode.BadRequest)
    override suspend fun RequestContext.move(req: BulkRequest<FilesProviderMoveRequestItem>): List<BackgroundTask?> = throw RPCException("Not supported", HttpStatusCode.BadRequest)
    override suspend fun RequestContext.copy(req: BulkRequest<FilesProviderCopyRequestItem>): List<BackgroundTask?> = throw RPCException("Not supported", HttpStatusCode.BadRequest)
    override suspend fun RequestContext.delete(resource: UFile) = throw RPCException("Not supported", HttpStatusCode.BadRequest)

    override suspend fun RequestContext.retrieveProducts(knownProducts: List<ProductReference>): BulkResponse<FSSupport> {
        return BulkResponse(knownProducts.map { FSSupport(it) })
    }
}
