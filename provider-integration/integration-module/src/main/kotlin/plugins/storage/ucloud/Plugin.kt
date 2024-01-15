package dk.sdu.cloud.plugins.storage.ucloud

import dk.sdu.cloud.*
import dk.sdu.cloud.accounting.api.ProductReference
import dk.sdu.cloud.accounting.api.ProductV2
import dk.sdu.cloud.calls.BulkRequest
import dk.sdu.cloud.calls.BulkResponse
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.bulkRequestOf
import dk.sdu.cloud.calls.server.HttpCall
import dk.sdu.cloud.cli.CliHandler
import dk.sdu.cloud.cli.CommandLineInterface
import dk.sdu.cloud.cli.genericCommandLineHandler
import dk.sdu.cloud.cli.sendCommandLineUsage
import dk.sdu.cloud.config.ConfigSchema
import dk.sdu.cloud.config.ProductReferenceWithoutProvider
import dk.sdu.cloud.config.removeProvider
import dk.sdu.cloud.file.orchestrator.api.*
import dk.sdu.cloud.plugins.FileDownloadSession
import dk.sdu.cloud.plugins.FilePlugin
import dk.sdu.cloud.plugins.FileUploadSession
import dk.sdu.cloud.plugins.PluginContext
import dk.sdu.cloud.controllers.RequestContext
import dk.sdu.cloud.ipc.IpcContainer
import dk.sdu.cloud.ipc.handler
import dk.sdu.cloud.ipc.sendRequest
import dk.sdu.cloud.plugins.ConfiguredShare
import dk.sdu.cloud.plugins.FileCollectionPlugin
import dk.sdu.cloud.plugins.InternalFile
import dk.sdu.cloud.plugins.SharePlugin
import dk.sdu.cloud.plugins.UCloudFile
import dk.sdu.cloud.plugins.compute.ucloud.UCloudComputePlugin
import dk.sdu.cloud.plugins.fileName
import dk.sdu.cloud.plugins.ipcClient
import dk.sdu.cloud.plugins.ipcServer
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
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.utils.secureToken
import dk.sdu.cloud.utils.sendTerminalFrame
import dk.sdu.cloud.utils.sendTerminalMessage
import dk.sdu.cloud.utils.sendTerminalTable
import io.ktor.utils.io.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement

class UCloudFilePlugin : FilePlugin {
    override val pluginTitle: String = "UCloud"
    override var pluginName: String = "Unknown"
    override var productAllocation: List<ProductReferenceWithoutProvider> = emptyList()
    override var productAllocationResolved: List<ProductV2> = emptyList()

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
    var computePlugin: UCloudComputePlugin? = null

    override fun supportsRealUserMode(): Boolean = false
    override fun supportsServiceUserMode(): Boolean = true

    override fun configure(config: ConfigSchema.Plugins.Files) {
        this.pluginConfig = (config as ConfigSchema.Plugins.Files.UCloud).normalize()
    }

    override suspend fun PluginContext.initialize() {
        registerCli()

        if (!config.shouldRunServerCode()) return

        computePlugin = (config.plugins.jobs[pluginName] as? UCloudComputePlugin)

        driveLocator = DriveLocator(
            productAllocationResolved.filterIsInstance<ProductV2.Storage>(),
            pluginConfig,
            rpcClient
        )
        pathConverter = PathConverter(rpcClient, driveLocator)
        fs = NativeFS(pathConverter)
        trash = TrashService(pathConverter)
        directoryStats = FastDirectoryStats(driveLocator, fs)
        queries = FileQueries(pathConverter, NonDistributedStateFactory(), fs, trash, directoryStats)
        downloads = DownloadService(pathConverter, fs)
        limitChecker = LimitChecker(dbConnection, rpcClient, pathConverter)
        memberFiles = MemberFiles(fs, pathConverter)
        usageScan = UsageScan(pluginName, pathConverter, directoryStats)
        tasks = TaskSystem(pluginConfig, dbConnection, pathConverter, fs, Dispatchers.IO, rpcClient, debugSystem, usageScan)
        uploads = ChunkedUploadService(pathConverter, fs)

        val stagingFolderPath = pluginConfig.trash.stagingFolder?.takeIf { pluginConfig.trash.useStagingFolder }
        val stagingFolder = stagingFolderPath?.let { InternalFile(it) }?.takeIf {
            runCatching { fs.stat(it).fileType }.getOrNull() == FileType.DIRECTORY
        }

        with(tasks) {
            install(CopyTask())
            install(CreateFolderTask())
            install(DeleteTask())
            install(EmptyTrashTask(fs, stagingFolder))
            install(MoveTask())
            install(TrashTask(memberFiles, trash))

            launchScheduler(ProcessingScope)
        }

        driveLocator.fillDriveDatabase()
        registerIpcServer()
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
            val pluginData = defaultMapper.encodeToJsonElement(
                FileUploadSessionPluginData(it.id, it.conflictPolicy)
            ).toString()

            FileUploadSession(secureToken(32), pluginData)
        }
    }

    override suspend fun RequestContext.handleUpload(
        session: String,
        pluginData: String,
        offset: Long,
        finalChunk: Boolean,
        chunk: ByteReadChannel
    ) {
        val sessionData = defaultMapper.decodeFromString<FileUploadSessionPluginData>(pluginData)
        uploads.receiveChunk(UCloudFile.create(sessionData.id), offset, finalChunk, chunk, sessionData.conflictPolicy)
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
        usageScan.init()
    }

    private fun PluginContext.registerCli() {
        commandLineInterface?.addHandler(CliHandler("drives") { args ->
            fun sendHelp(): Nothing = sendCommandLineUsage("drives", "Manage drives") {
                subcommand(
                    "maintenance on",
                    "Turns maintenance on for a specific drive",
                    builder = {
                        arg("path", description = "The local path to a collection")
                        arg("description", description = "Description to display to end-users")
                    }
                )

                subcommand(
                    "maintenance off",
                    "Turns maintenance off for a specific drive",
                    builder = {
                        arg("path", description = "The local path to a collection")
                    }
                )

                subcommand(
                    "maintenance status",
                    "Checks the maintenance status of a drive",
                    builder = {
                        arg("id", description = "The local path to a collection")
                    }
                )

                subcommand(
                    "maintenance ls",
                    "Checks the list of drives which are in maintenance mode",
                )

                subcommand(
                    "locate-by-path",
                    "Locates a drive by a local path",
                    builder = {
                        arg("path", description = "The local path to a collection")
                    }
                )

                subcommand(
                    "used-by-jobs",
                    "Locates the set of jobs which should be killed when the drive is put into maintenance mode",
                    builder = {
                        arg("path", description = "The local path to a collection")
                    }
                )

                subcommand(
                    "update-system",
                    "Updates the system of a drive",
                    builder = {
                        arg("path", description = "The local path to a collection")
                        arg("system", description = "The name of the system to use")
                    }
                )

                subcommand(
                    "browse-by-system",
                    "Browses drives given a specific system",
                    builder = {
                        arg("system", description = "The name of the system to look for")
                    }
                )
            }

            val ipcClient = ipcClient

            genericCommandLineHandler {
                when (args.getOrNull(0)) {
                    "maintenance" -> {
                        when (args.getOrNull(1)) {
                            "on" -> {
                                val path = args.getOrNull(2) ?: sendHelp()
                                val description = args.getOrNull(3) ?: sendHelp()

                                ipcClient.sendRequest(
                                    CliIpc.enableMaintenanceMode,
                                    EnableMaintenanceMode(path, description)
                                )

                                sendTerminalMessage { line("OK") }
                            }

                            "off" -> {
                                val path = args.getOrNull(2) ?: sendHelp()

                                ipcClient.sendRequest(
                                    CliIpc.disableMaintenanceMode,
                                    DisableMaintenanceMode(path)
                                )

                                sendTerminalMessage { line("OK") }
                            }

                            "status" -> {
                                val path = args.getOrNull(2) ?: sendHelp()

                                val result = ipcClient.sendRequest(
                                    CliIpc.locateByPath,
                                    LocateByPath(path)
                                )

                                sendTerminalFrame("Drive") {
                                    wideTitle = true

                                    field("UCloud ID", result.driveId)
                                    field("System", result.system)
                                    field("Maintenance mode?", result.inMaintenanceMode)
                                    field("Local path", result.localPath)
                                }
                            }

                            "ls" -> {
                                val result = ipcClient.sendRequest(
                                    CliIpc.locateInMaintenance,
                                    Unit
                                )

                                sendDriveInfoTable(result)
                            }

                            else -> sendHelp()
                        }
                    }

                    "locate-by-path" -> {
                        val path = args.getOrNull(1) ?: sendHelp()
                        val result = ipcClient.sendRequest(
                            CliIpc.locateByPath,
                            LocateByPath(path)
                        )

                        sendTerminalFrame("Drive") {
                            wideTitle = true

                            field("UCloud ID", result.driveId)
                            field("System", result.system)
                            field("Maintenance mode?", result.inMaintenanceMode)
                            field("Local path", result.localPath)
                        }
                    }

                    "browse-by-system" -> {
                        val system = args.getOrNull(1) ?: sendHelp()
                        val result = ipcClient.sendRequest(
                            CliIpc.browseBySystem,
                            BrowseBySystem(system)
                        )

                        sendDriveInfoTable(result)
                    }

                    "used-by-jobs" -> {
                        val path = args.getOrNull(1) ?: sendHelp()
                        val result = ipcClient.sendRequest(
                            CliIpc.usedByJobs,
                            LocateByPath(path)
                        )

                        sendTerminalTable {
                            header("Job ID", 120)
                            for (row in result.jobIds) {
                                nextRow()
                                cell(row)
                            }
                        }
                    }

                    "update-system" -> {
                        val path = args.getOrNull(1) ?: sendHelp()
                        val system = args.getOrNull(2) ?: sendHelp()

                        ipcClient.sendRequest(
                            CliIpc.updateSystem,
                            UpdateSystem(path, system)
                        )

                        sendTerminalMessage { line("OK") }
                    }

                    else -> sendHelp()
                }
            }
        })
    }

    private fun CommandLineInterface.sendDriveInfoTable(result: DriveInfoItems) {
        sendTerminalTable {
            header("UCloud ID", 20)
            header("System", 20)
            header("Maintenance", 20)
            header("Local path", 60)

            for (item in result.items) {
                nextRow()
                cell(item.driveId)
                cell(item.system)
                cell(item.inMaintenanceMode)
                cell(item.localPath)
            }
        }
    }

    private fun PluginContext.registerIpcServer() {
        ipcServer.addHandler(CliIpc.enableMaintenanceMode.handler { user, request ->
            if (user.uid != 0) throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)
            driveLocator.enableMaintenanceModeByInternalPath(InternalFile(request.path), request.description)
        })

        ipcServer.addHandler(CliIpc.disableMaintenanceMode.handler { user, request ->
            if (user.uid != 0) throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)
            driveLocator.disableMaintenanceModeByInternalPath(InternalFile(request.path))
        })

        ipcServer.addHandler(CliIpc.locateByPath.handler { user, request ->
            if (user.uid != 0) throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)
            val driveAndSystem = driveLocator.resolveDriveByInternalFile(InternalFile(request.path))
            driveInfo(driveAndSystem)
        })

        ipcServer.addHandler(CliIpc.browseBySystem.handler { user, request ->
            if (user.uid != 0) throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)
            DriveInfoItems(
                driveLocator.enumerateDrives().items
                    .filter { it.system.name.equals(request.system, ignoreCase = true) }
                    .map { driveInfo(it) }
                    .take(50)
            )
        })

        ipcServer.addHandler(CliIpc.updateSystem.handler { user, request ->
            if (user.uid != 0) throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)
            val driveAndSystem = driveLocator.resolveDriveByInternalFile(InternalFile(request.path))
            val system = driveLocator.systemByName(request.system)
                ?: throw RPCException("Unknown system: ${request.system}", HttpStatusCode.BadRequest)
            driveLocator.updateSystem(driveAndSystem.drive.ucloudId, system)
        })

        ipcServer.addHandler(CliIpc.locateInMaintenance.handler { user, request ->
            if (user.uid != 0) throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)
            DriveInfoItems(
                driveLocator.enumerateDrives().items
                    .filter { it.inMaintenanceMode }
                    .map { driveInfo(it) }
            )
        })

        ipcServer.addHandler(CliIpc.usedByJobs.handler { user, request ->
            if (user.uid != 0) throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)

            val dryMaintenance = driveLocator.listGroupedDrives(
                driveLocator.resolveDriveByInternalFile(InternalFile(request.path))
            ).map { it.drive.ucloudId }

            UsedBySystems(
                computePlugin?.killJobsEnteringMaintenanceMode(dryRun = true, dryMaintenance)
                    ?: throw RPCException("No compute plugin registered", HttpStatusCode.BadRequest)
            )
        })
    }

    private fun driveInfo(driveAndSystem: DriveAndSystem) = DriveInfo(
        driveAndSystem.drive.ucloudId.toString(),
        driveAndSystem.system.name,
        driveAndSystem.driveRoot?.path ?: "(Share)",
        driveAndSystem.inMaintenanceMode
    )

    @Serializable
    private data class EnableMaintenanceMode(
        val path: String,
        val description: String
    )

    @Serializable
    private data class DisableMaintenanceMode(
        val path: String
    )

    @Serializable
    private data class LocateByPath(
        val path: String,
    )

    @Serializable
    private data class UpdateSystem(
        val path: String,
        val system: String,
    )

    @Serializable
    private data class BrowseBySystem(
        val system: String
    )

    @Serializable
    private data class DriveInfo(
        val driveId: String,
        val system: String,
        val localPath: String,
        val inMaintenanceMode: Boolean,
    )

    @Serializable
    private data class DriveInfoItems(
        val items: List<DriveInfo>
    )

    @Serializable
    private data class UsedBySystems(
        val jobIds: List<String>
    )

    @Serializable
    private data class FileUploadSessionPluginData(
        val id: String,
        val conflictPolicy: WriteConflictPolicy
    )

    private object CliIpc : IpcContainer("ucloud_storage_drives") {
        val enableMaintenanceMode =
            updateHandler("enableMaintenanceMode", EnableMaintenanceMode.serializer(), Unit.serializer())
        val disableMaintenanceMode =
            updateHandler("disableMaintenanceMode", DisableMaintenanceMode.serializer(), Unit.serializer())
        val locateByPath = updateHandler("locateByPath", LocateByPath.serializer(), DriveInfo.serializer())
        val updateSystem = updateHandler("updateSystem", UpdateSystem.serializer(), Unit.serializer())
        val browseBySystem = updateHandler("browseBySystem", BrowseBySystem.serializer(), DriveInfoItems.serializer())
        val locateInMaintenance = updateHandler("locateInMaintenance", Unit.serializer(), DriveInfoItems.serializer())
        val usedByJobs = updateHandler("usedByJobs", LocateByPath.serializer(), UsedBySystems.serializer())
    }

    companion object : Loggable {
        override val log = logger()
    }
}

class UCloudFileCollectionPlugin : FileCollectionPlugin {
    override val pluginTitle: String = "UCloud"
    override var pluginName: String = "Unknown"
    override var productAllocation: List<ProductReferenceWithoutProvider> = emptyList()
    override var productAllocationResolved: List<ProductV2> = emptyList()
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
