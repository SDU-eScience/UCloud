package dk.sdu.cloud.plugins.storage.ucloud

import dk.sdu.cloud.FindByStringId
import dk.sdu.cloud.PageV2
import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.accounting.api.ProductReference
import dk.sdu.cloud.calls.BulkRequest
import dk.sdu.cloud.calls.BulkResponse
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.bulkRequestOf
import dk.sdu.cloud.calls.server.HttpCall
import dk.sdu.cloud.config.ConfigSchema
import dk.sdu.cloud.config.ProductReferenceWithoutProvider
import dk.sdu.cloud.dbConnection
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.file.orchestrator.api.*
import dk.sdu.cloud.plugins.FileDownloadSession
import dk.sdu.cloud.plugins.FilePlugin
import dk.sdu.cloud.plugins.FileUploadSession
import dk.sdu.cloud.plugins.InternalFile
import dk.sdu.cloud.plugins.PluginContext
import dk.sdu.cloud.controllers.RequestContext
import dk.sdu.cloud.plugins.FileCollectionPlugin
import dk.sdu.cloud.plugins.UCloudFile
import dk.sdu.cloud.plugins.rpcClient
import dk.sdu.cloud.plugins.storage.ucloud.tasks.EmptyTrashRequestItem
import dk.sdu.cloud.plugins.storage.ucloud.tasks.TrashRequestItem
import dk.sdu.cloud.provider.api.ResourceOwner
import dk.sdu.cloud.provider.api.UpdatedAclWithResource
import io.ktor.utils.io.*
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement

class UCloudFilePlugin : FilePlugin {
    override val pluginTitle: String = "UCloud"
    override var pluginName: String = "Unknown"
    override var productAllocation: List<ProductReferenceWithoutProvider> = emptyList()
    override var productAllocationResolved: List<Product> = emptyList()

    private lateinit var pluginConfig: ConfigSchema.Plugins.Files.UCloud

    lateinit var fs: NativeFS
    lateinit var trash: TrashService
    lateinit var cephStats: CephFsFastDirectoryStats
    lateinit var queries: FileQueries
    lateinit var limitChecker: LimitChecker
    lateinit var memberFiles: MemberFiles
    lateinit var tasks: TaskSystem
    lateinit var uploads: ChunkedUploadService
    lateinit var downloads: DownloadService
    lateinit var pathConverter: PathConverter

    override fun supportsRealUserMode(): Boolean = false
    override fun supportsServiceUserMode(): Boolean = true

    override fun configure(config: ConfigSchema.Plugins.Files) {
        this.pluginConfig = config as ConfigSchema.Plugins.Files.UCloud
    }

    override suspend fun PluginContext.initialize() {
        val storageProduct = productAllocationResolved.getOrNull(0) ?: return

        pathConverter = PathConverter(
            config.core.providerId,
            storageProduct.category.name,
            InternalFile(pluginConfig.mountLocation),
            rpcClient
        )
        fs = NativeFS(pathConverter)
        trash = TrashService(pathConverter)
        cephStats = CephFsFastDirectoryStats(fs)
        queries = FileQueries(pathConverter, NonDistributedStateFactory(), fs, trash, cephStats)
        downloads = DownloadService(config.core.providerId, dbConnection, pathConverter, fs)
        limitChecker = LimitChecker(dbConnection, pathConverter)
        memberFiles = MemberFiles(fs, pathConverter, rpcClient)
        tasks = TaskSystem(dbConnection, pathConverter, fs, Dispatchers.IO, rpcClient, debugSystem)
        uploads = ChunkedUploadService(dbConnection, pathConverter, fs)
    }

    override suspend fun RequestContext.browse(
        path: UCloudFile,
        request: FilesProviderBrowseRequest
    ): PageV2<PartialUFile> {
        return queries.browseFiles(
            path,
            request.browse.flags,
            request.browse.normalize(),
            request.browse.sortBy?.let { runCatching { FilesSortBy.valueOf(it) }.getOrNull() } ?: FilesSortBy.PATH,
            request.browse.sortDirection
        )
    }

    override suspend fun RequestContext.retrieve(request: FilesProviderRetrieveRequest): PartialUFile {
        return queries.retrieve(UCloudFile.create(request.retrieve.id), request.retrieve.flags)
    }

    override suspend fun RequestContext.createDownload(request: BulkRequest<FilesProviderCreateDownloadRequestItem>): List<FileDownloadSession> {
        // TODO Need to change to plugin format
        TODO("Not yet implemented")
    }

    override suspend fun RequestContext.handleDownload(ctx: HttpCall, session: String, pluginData: String) {
        TODO("Not yet implemented")
    }

    override suspend fun RequestContext.createFolder(req: BulkRequest<FilesProviderCreateFolderRequestItem>): List<LongRunningTask?> {
        for (reqItem in req.items) {
            limitChecker.checkLimit(reqItem.resolvedCollection)
        }

        for (reqItem in req.items) {
            if (reqItem.conflictPolicy == WriteConflictPolicy.REJECT &&
                queries.fileExists(UCloudFile.create(reqItem.id))
            ) {
                throw RPCException("Folder already exists", HttpStatusCode.Conflict)
            }
        }

        return req.items.map { reqItem ->
            tasks.submitTask(
                Files.createFolder.fullName,
                defaultMapper.encodeToJsonElement(
                    BulkRequest.serializer(FilesProviderCreateFolderRequestItem.serializer()),
                    bulkRequestOf(reqItem)
                ) as JsonObject
            )
        }
    }

    override suspend fun RequestContext.createUpload(request: BulkRequest<FilesProviderCreateUploadRequestItem>): List<FileUploadSession> {
        TODO("Not yet implemented")
    }

    override suspend fun RequestContext.handleUpload(
        session: String,
        pluginData: String,
        offset: Long,
        chunk: ByteReadChannel
    ) {
        TODO("Not yet implemented")
    }

    override suspend fun RequestContext.moveToTrash(request: BulkRequest<FilesProviderTrashRequestItem>): List<LongRunningTask?> {
        val username = ucloudUsername ?: throw RPCException("No username supplied", HttpStatusCode.BadRequest)
        return request.items.map { reqItem ->
            tasks.submitTask(
                Files.trash.fullName,
                defaultMapper.encodeToJsonElement(
                    BulkRequest.serializer(TrashRequestItem.serializer()),
                    bulkRequestOf(TrashRequestItem(username, reqItem.id))
                ) as JsonObject
            )
        }
    }

    override suspend fun RequestContext.emptyTrash(request: BulkRequest<FilesProviderEmptyTrashRequestItem>): List<LongRunningTask?> {
        val username = ucloudUsername ?: throw RPCException("No username supplied", HttpStatusCode.BadRequest)
        return request.items.map { requestItem ->
            tasks.submitTask(
                Files.emptyTrash.fullName,
                defaultMapper.encodeToJsonElement(
                    BulkRequest.serializer(EmptyTrashRequestItem.serializer()),
                    bulkRequestOf(EmptyTrashRequestItem(username, requestItem.id))
                ) as JsonObject
            )
        }
    }

    override suspend fun RequestContext.move(req: BulkRequest<FilesProviderMoveRequestItem>): List<LongRunningTask?> {
        for (reqItem in req.items) {
            limitChecker.checkLimit(reqItem.resolvedNewCollection)
        }

        for (reqItem in req.items) {
            if (reqItem.oldId.substringBeforeLast('/') == reqItem.newId.substringBeforeLast('/')) {
                if (reqItem.conflictPolicy == WriteConflictPolicy.REJECT &&
                    queries.fileExists(UCloudFile.create(reqItem.newId))
                ) {
                    throw RPCException("File or folder already exists", HttpStatusCode.Conflict)
                }
            }
        }

        return req.items.map { reqItem ->
            tasks.submitTask(
                Files.move.fullName,
                defaultMapper.encodeToJsonElement(
                    BulkRequest.serializer(FilesProviderMoveRequestItem.serializer()),
                    bulkRequestOf(reqItem)
                ) as JsonObject
            )
        }
    }

    override suspend fun RequestContext.copy(req: BulkRequest<FilesProviderCopyRequestItem>): List<LongRunningTask?> {
        req.items.forEach { reqItem ->
            limitChecker.checkLimit(reqItem.resolvedNewCollection)
        }

        return req.items.map { reqItem ->
            tasks.submitTask(
                Files.copy.fullName,
                defaultMapper.encodeToJsonElement(
                    BulkRequest.serializer(FilesProviderCopyRequestItem.serializer()),
                    bulkRequestOf(reqItem)
                ) as JsonObject
            )
        }
    }

    override suspend fun RequestContext.retrieveProducts(knownProducts: List<ProductReference>): BulkResponse<FSSupport> {
        TODO("Not yet implemented")
    }

    override suspend fun RequestContext.delete(resource: UFile) {
        tasks.submitTask(
            Files.delete.fullName,
            defaultMapper.encodeToJsonElement(
                BulkRequest.serializer(UFile.serializer()),
                bulkRequestOf(resource)
            ) as JsonObject
        )
    }
}

class UCloudFileCollectionPlugin : FileCollectionPlugin {
    override val pluginTitle: String = "UCloud"
    override var pluginName: String = "Unknown"
    override var productAllocation: List<ProductReferenceWithoutProvider> = emptyList()
    override var productAllocationResolved: List<Product> = emptyList()
    private lateinit var filePlugin: UCloudFilePlugin

    override suspend fun PluginContext.initialize() {
        filePlugin = config.plugins.files[pluginName] as? UCloudFilePlugin ?: run {
            error(
                "The UCloud file collection plugin ($pluginName) must be used together with a " +
                        "matching UCloud file plugin"
            )
        }
    }

    override suspend fun RequestContext.retrieveProducts(knownProducts: List<ProductReference>): BulkResponse<FSSupport> {
        return with(filePlugin) {
            retrieveProducts(knownProducts)
        }
    }

    override suspend fun RequestContext.init(owner: ResourceOwner) {
        filePlugin.memberFiles.initializeMemberFiles(owner.createdBy, owner.project)
    }

    override suspend fun RequestContext.create(resource: FileCollection): FindByStringId? {
        filePlugin.fs.createDirectories(filePlugin.pathConverter.collectionLocation(resource.id))
        return null
    }

    override suspend fun RequestContext.delete(resource: FileCollection) {
        filePlugin.tasks.submitTask(
            Files.delete.fullName,
            defaultMapper.encodeToJsonElement(
                BulkRequest.serializer(FindByPath.serializer()),
                bulkRequestOf(FindByPath("/${resource.id}"))
            ) as JsonObject
        )
    }
}
