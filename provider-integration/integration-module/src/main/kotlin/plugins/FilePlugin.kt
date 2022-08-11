package dk.sdu.cloud.plugins

import dk.sdu.cloud.FindByStringId
import dk.sdu.cloud.PageV2
import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.accounting.api.ProductReference
import dk.sdu.cloud.calls.BulkRequest
import dk.sdu.cloud.calls.BulkResponse
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.server.HttpCall
import dk.sdu.cloud.config.*
import dk.sdu.cloud.file.orchestrator.api.*
import io.ktor.utils.io.*
import java.nio.ByteBuffer

@JvmInline
value class UCloudFile private constructor(val path: String) {
    companion object {
        fun create(path: String) = UCloudFile(path.normalize())
        fun createFromPreNormalizedString(path: String) = UCloudFile(path)
    }
}

data class FileDownloadSession(val session: String, val pluginData: String)
data class FileUploadSession(val session: String, val pluginData: String)

interface FilePlugin : ResourcePlugin<Product.Storage, FSSupport, UFile, ConfigSchema.Plugins.Files> {
    suspend fun PluginContext.browse(path: UCloudFile, request: FilesProviderBrowseRequest): PageV2<PartialUFile>
    suspend fun PluginContext.retrieve(request: FilesProviderRetrieveRequest): PartialUFile
    suspend fun PluginContext.createDownload(request: BulkRequest<FilesProviderCreateDownloadRequestItem>): List<FileDownloadSession>
    suspend fun PluginContext.handleDownload(ctx: HttpCall, session: String, pluginData: String)
    suspend fun PluginContext.createFolder(req: BulkRequest<FilesProviderCreateFolderRequestItem>): List<LongRunningTask?>
    suspend fun PluginContext.createUpload(request: BulkRequest<FilesProviderCreateUploadRequestItem>): List<FileUploadSession>
    suspend fun PluginContext.handleUpload(session: String, pluginData: String, offset: Long, chunk: ByteReadChannel)
    suspend fun PluginContext.moveToTrash(request: BulkRequest<FilesProviderTrashRequestItem>): List<LongRunningTask?>
    suspend fun PluginContext.emptyTrash(request: BulkRequest<FilesProviderEmptyTrashRequestItem>): List<LongRunningTask?>
    suspend fun PluginContext.move(req: BulkRequest<FilesProviderMoveRequestItem>): List<LongRunningTask?>
    suspend fun PluginContext.copy(req: BulkRequest<FilesProviderCopyRequestItem>): List<LongRunningTask?>

    override suspend fun PluginContext.create(resource: UFile): FindByStringId? {
        error("Not supported by this plugin")
    }

    override suspend fun PluginContext.runMonitoringLoop() {}
}

abstract class EmptyFilePlugin : FilePlugin {
    override var pluginName = "Unknown"
    override var productAllocation: List<ProductReferenceWithoutProvider> = emptyList()
    override var productAllocationResolved: List<Product> = emptyList()

    override suspend fun PluginContext.browse(path: UCloudFile, request: FilesProviderBrowseRequest): PageV2<PartialUFile> = throw RPCException("Not supported", HttpStatusCode.BadRequest)
    override suspend fun PluginContext.retrieve(request: FilesProviderRetrieveRequest): PartialUFile = throw RPCException("Not supported", HttpStatusCode.BadRequest)
    override suspend fun PluginContext.createDownload(request: BulkRequest<FilesProviderCreateDownloadRequestItem>): List<FileDownloadSession> = throw RPCException("Not supported", HttpStatusCode.BadRequest)
    override suspend fun PluginContext.handleDownload(ctx: HttpCall, session: String, pluginData: String) = throw RPCException("Not supported", HttpStatusCode.BadRequest)
    override suspend fun PluginContext.createFolder(req: BulkRequest<FilesProviderCreateFolderRequestItem>): List<LongRunningTask?> = throw RPCException("Not supported", HttpStatusCode.BadRequest)
    override suspend fun PluginContext.createUpload(request: BulkRequest<FilesProviderCreateUploadRequestItem>): List<FileUploadSession> = throw RPCException("Not supported", HttpStatusCode.BadRequest)
    override suspend fun PluginContext.handleUpload(session: String, pluginData: String, offset: Long, chunk: ByteReadChannel) = throw RPCException("Not supported", HttpStatusCode.BadRequest)
    override suspend fun PluginContext.moveToTrash(request: BulkRequest<FilesProviderTrashRequestItem>): List<LongRunningTask?> = throw RPCException("Not supported", HttpStatusCode.BadRequest)
    override suspend fun PluginContext.emptyTrash(request: BulkRequest<FilesProviderEmptyTrashRequestItem>): List<LongRunningTask?> = throw RPCException("Not supported", HttpStatusCode.BadRequest)
    override suspend fun PluginContext.move(req: BulkRequest<FilesProviderMoveRequestItem>): List<LongRunningTask?> = throw RPCException("Not supported", HttpStatusCode.BadRequest)
    override suspend fun PluginContext.copy(req: BulkRequest<FilesProviderCopyRequestItem>): List<LongRunningTask?> = throw RPCException("Not supported", HttpStatusCode.BadRequest)
    override suspend fun PluginContext.delete(resource: UFile) = throw RPCException("Not supported", HttpStatusCode.BadRequest)

    override suspend fun PluginContext.retrieveProducts(knownProducts: List<ProductReference>): BulkResponse<FSSupport> {
        return BulkResponse(knownProducts.map { FSSupport(it) })
    }
}
