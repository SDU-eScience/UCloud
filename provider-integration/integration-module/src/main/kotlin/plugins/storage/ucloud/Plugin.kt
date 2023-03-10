package dk.sdu.cloud.plugins.storage.ucloud

import dk.sdu.cloud.*
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
import dk.sdu.cloud.config.removeProvider
import dk.sdu.cloud.file.orchestrator.api.*
import dk.sdu.cloud.plugins.FileDownloadSession
import dk.sdu.cloud.plugins.FilePlugin
import dk.sdu.cloud.plugins.FileUploadSession
import dk.sdu.cloud.plugins.PluginContext
import dk.sdu.cloud.controllers.RequestContext
import dk.sdu.cloud.plugins.ConfiguredShare
import dk.sdu.cloud.plugins.FileCollectionPlugin
import dk.sdu.cloud.plugins.SharePlugin
import dk.sdu.cloud.plugins.UCloudFile
import dk.sdu.cloud.plugins.fileName
import dk.sdu.cloud.plugins.rpcClient
import dk.sdu.cloud.plugins.storage.ucloud.tasks.CopyTask
import dk.sdu.cloud.plugins.storage.ucloud.tasks.CreateFolderTask
import dk.sdu.cloud.plugins.storage.ucloud.tasks.DeleteTask
import dk.sdu.cloud.plugins.storage.ucloud.tasks.EmptyTrashRequestItem
import dk.sdu.cloud.plugins.storage.ucloud.tasks.EmptyTrashTask
import dk.sdu.cloud.plugins.storage.ucloud.tasks.MoveTask
import dk.sdu.cloud.plugins.storage.ucloud.tasks.TrashRequestItem
import dk.sdu.cloud.plugins.storage.ucloud.tasks.TrashTask
import dk.sdu.cloud.provider.api.ResourceOwner
import dk.sdu.cloud.utils.secureToken
import io.ktor.utils.io.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.serialization.json.JsonObject
import kotlin.coroutines.coroutineContext

class UCloudFilePlugin : FilePlugin {
    override val pluginTitle: String = "UCloud"
    override var pluginName: String = "Unknown"
    override var productAllocation: List<ProductReferenceWithoutProvider> = emptyList()
    override var productAllocationResolved: List<Product> = emptyList()

    private lateinit var pluginConfig: ConfigSchema.Plugins.Files.UCloud

    lateinit var fs: NativeFS
    lateinit var trash: TrashService
    lateinit var directoryStats: FastDirectoryStats
    lateinit var queries: FileQueries
    lateinit var limitChecker: LimitChecker
    lateinit var memberFiles: MemberFiles
    lateinit var tasks: TaskSystem
    lateinit var uploads: ChunkedUploadService
    lateinit var downloads: DownloadService
    lateinit var pathConverter: PathConverter
    lateinit var usageScan: UsageScan
    lateinit var driveLocator: DriveLocator

    override fun supportsRealUserMode(): Boolean = false
    override fun supportsServiceUserMode(): Boolean = true

    override fun configure(config: ConfigSchema.Plugins.Files) {
        this.pluginConfig = (config as ConfigSchema.Plugins.Files.UCloud).normalize()
    }

    override suspend fun PluginContext.initialize() {
        if (!config.shouldRunServerCode()) return

        driveLocator = DriveLocator(
            productAllocationResolved.filterIsInstance<Product.Storage>(),
            pluginConfig,
            rpcClient
        )
        pathConverter = PathConverter(rpcClient, driveLocator)
        fs = NativeFS(pathConverter)
        trash = TrashService(pathConverter)
        directoryStats = FastDirectoryStats(driveLocator, fs)
        queries = FileQueries(pathConverter, NonDistributedStateFactory(), fs, trash, directoryStats)
        downloads = DownloadService(pathConverter, fs)
        limitChecker = LimitChecker(dbConnection, rpcClient)
        memberFiles = MemberFiles(fs, pathConverter)
        tasks = TaskSystem(dbConnection, pathConverter, fs, Dispatchers.IO, rpcClient, debugSystem)
        uploads = ChunkedUploadService(pathConverter, fs)
        usageScan = UsageScan(pluginName, pathConverter, directoryStats, rpcClient, dbConnection)

        with (tasks) {
            install(CopyTask())
            install(CreateFolderTask())
            install(DeleteTask())
            install(EmptyTrashTask())
            install(MoveTask())
            install(TrashTask(memberFiles, trash))

            launchScheduler(ProcessingScope)
        }

        driveLocator.fillDriveDatabase()
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
        return request.items.map {
            FileDownloadSession(secureToken(32), it.id)
        }
    }

    override suspend fun RequestContext.handleDownload(ctx: HttpCall, session: String, pluginData: String) {
        downloads.download(UCloudFile.create(pluginData), ctx)
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
        return request.items.map {
            FileUploadSession(secureToken(32), it.id)
        }
    }

    override suspend fun RequestContext.handleUpload(
        session: String,
        pluginData: String,
        offset: Long,
        chunk: ByteReadChannel
    ) {
        uploads.receiveChunk(UCloudFile.create(pluginData), offset, chunk)
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
        val defaultFsSupport = FSSupport(
            ProductReference("", "", ""),
            FSProductStatsSupport(
                sizeInBytes = true,
                sizeIncludingChildrenInBytes = true,
                modifiedAt = true,
                createdAt = true,
                accessedAt = true,
                unixPermissions = true,
                unixOwner = true,
                unixGroup = true
            ),
            FSCollectionSupport(
                aclModifiable = true,
                usersCanCreate = true,
                usersCanDelete = true,
                usersCanRename = true
            ),
            FSFileSupport(
                aclModifiable = false,
                trashSupported = true,
                isReadOnly = false,
                streamingSearchSupported = true,
                sharesSupported = true
            )
        )

        return BulkResponse(
            knownProducts.map { product ->
                if (product.id == driveProjectHomeName || product.id == driveShareName) {
                    defaultFsSupport.copy(
                        product = product,
                        collection = FSCollectionSupport(
                            aclModifiable = false,
                            usersCanCreate = false,
                            usersCanDelete = true,
                            usersCanRename = false
                        )
                    )
                } else {
                    defaultFsSupport.copy(
                        product = product
                    )
                }
            }
        )
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

    override suspend fun RequestContext.streamingSearch(
        req: FilesProviderStreamingSearchRequest
    ): ReceiveChannel<FilesProviderStreamingSearchResult.Result> = queries.streamingSearch(req)

    override suspend fun PluginContext.runMonitoringLoopInServerMode() {
        while (coroutineContext.isActive) {
            try {
                usageScan.startScanIfNeeded()
            } catch (ex: Throwable) {
                debugSystem.logThrowable("Caught exception during monitoring loop", ex)
            }

            delay(60_000)
        }
    }
}

class UCloudFileCollectionPlugin : FileCollectionPlugin {
    override val pluginTitle: String = "UCloud"
    override var pluginName: String = "Unknown"
    override var productAllocation: List<ProductReferenceWithoutProvider> = emptyList()
    override var productAllocationResolved: List<Product> = emptyList()
    private lateinit var filePlugin: UCloudFilePlugin

    override fun supportsRealUserMode(): Boolean = false
    override fun supportsServiceUserMode(): Boolean = true

    override suspend fun PluginContext.initialize() {
        if (!config.shouldRunServerCode()) return
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

    override suspend fun RequestContext.initInUserMode(owner: ResourceOwner) {
        filePlugin.memberFiles.initializeMemberFiles(owner.createdBy, owner.project)
    }

    override suspend fun RequestContext.create(resource: FileCollection): FindByStringId? {
        val drive = filePlugin.driveLocator.register(
            "",
            UCloudDrive.Collection(resource.id.toLong()),
            initiatedByEndUser = true,
        ).drive

        filePlugin.fs.createDirectories(
            filePlugin.pathConverter.ucloudToInternal(
                UCloudFile.createFromPreNormalizedString("/${drive.ucloudId}")
            )
        )
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

class UCloudSharePlugin : SharePlugin() {
    override val pluginTitle = "UCloud"

    private lateinit var filePlugin: UCloudFilePlugin

    override suspend fun PluginContext.initialize() {
        filePlugin = config.plugins.files[pluginName] as? UCloudFilePlugin ?: run {
            error(
                "The UCloud file collection plugin ($pluginName) must be used together with a " +
                        "matching UCloud file plugin"
            )
        }
    }

    override suspend fun RequestContext.onCreate(resource: Share): ConfiguredShare {
        val sourcePath = resource.specification.sourceFilePath
        val file = filePlugin.pathConverter.ucloudToInternal(UCloudFile.create(sourcePath))
        try {
            val stat = filePlugin.fs.stat(file)
            if (stat.fileType != FileType.DIRECTORY) {
                throw RPCException("'${file.fileName()}' is not a directory", HttpStatusCode.BadRequest)
            }
        } catch (ex: FSException.NotFound) {
            throw RPCException("'${file.fileName()}' no longer exists", HttpStatusCode.BadRequest)
        }

        val title = sourcePath.fileName()
        val drive = filePlugin.driveLocator.register(
            title,
            UCloudDrive.Share(UCloudDrive.PLACEHOLDER_ID, resource.id),
            createdByUser = "_ucloud",
            ownedByProject = null
        ).drive

        return ConfiguredShare(
            title,
            filePlugin.driveLocator.driveToProduct(drive).removeProvider(),
            (drive as UCloudDrive.Share).toProviderId(),
            drive.ucloudId.toString()
        )
    }
}
