package dk.sdu.cloud.plugins

import dk.sdu.cloud.FindByStringId
import dk.sdu.cloud.PageV2
import dk.sdu.cloud.ProductBasedConfiguration
import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.calls.BulkRequest
import dk.sdu.cloud.calls.BulkResponse
import dk.sdu.cloud.file.orchestrator.api.*
import dk.sdu.cloud.http.HttpContext
import dk.sdu.cloud.plugins.storage.UCloudFile

data class FileDownloadSession(val session: String, val pluginData: String)

interface FilePlugin : ResourcePlugin<Product.Storage, FSSupport, UFile, ProductBasedConfiguration> {
    suspend fun PluginContext.browse(path: UCloudFile, request: FilesProviderBrowseRequest): PageV2<PartialUFile>
    suspend fun PluginContext.retrieve(request: FilesProviderRetrieveRequest): PartialUFile
    suspend fun PluginContext.createDownload(request: BulkRequest<FilesProviderCreateDownloadRequestItem>): List<FileDownloadSession>
    suspend fun PluginContext.handleDownload(ctx: HttpContext, session: String, pluginData: String)

    override suspend fun PluginContext.create(resource: UFile): FindByStringId? {
        error("Not supported by this plugin")
    }

    override suspend fun PluginContext.runMonitoringLoop() {}

    suspend fun PluginContext.createFolder(req: BulkRequest<FilesProviderCreateFolderRequestItem>): BulkResponse<LongRunningTask?>

}
