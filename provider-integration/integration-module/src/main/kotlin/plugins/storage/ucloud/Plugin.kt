package dk.sdu.cloud.plugins.storage.ucloud

import dk.sdu.cloud.*
import dk.sdu.cloud.accounting.api.ProductReference
import dk.sdu.cloud.accounting.api.ProductV2
import dk.sdu.cloud.calls.BulkRequest
import dk.sdu.cloud.calls.BulkResponse
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.bulkRequestOf
import dk.sdu.cloud.calls.client.AtomicInteger
import dk.sdu.cloud.calls.server.HttpCall
import dk.sdu.cloud.cli.CliHandler
import dk.sdu.cloud.cli.CommandLineInterface
import dk.sdu.cloud.cli.genericCommandLineHandler
import dk.sdu.cloud.cli.sendCommandLineUsage
import dk.sdu.cloud.config.ConfigSchema
import dk.sdu.cloud.config.ProductCost
import dk.sdu.cloud.config.ProductReferenceWithoutProvider
import dk.sdu.cloud.config.removeProvider
import dk.sdu.cloud.controllers.FileListingEntry
import dk.sdu.cloud.controllers.FilesUploadIpc
import dk.sdu.cloud.file.orchestrator.api.*
import dk.sdu.cloud.controllers.RequestContext
import dk.sdu.cloud.ipc.IpcContainer
import dk.sdu.cloud.ipc.handler
import dk.sdu.cloud.ipc.sendRequest
import dk.sdu.cloud.plugins.*
import dk.sdu.cloud.plugins.compute.ucloud.UCloudComputePlugin
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
import dk.sdu.cloud.service.SimpleCache
import dk.sdu.cloud.sql.useAndInvoke
import dk.sdu.cloud.sql.withSession
import dk.sdu.cloud.utils.secureToken
import dk.sdu.cloud.utils.sendTerminalFrame
import dk.sdu.cloud.utils.sendTerminalMessage
import dk.sdu.cloud.utils.sendTerminalTable
import io.ktor.util.*
import io.ktor.utils.io.*
import io.ktor.utils.io.pool.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.selects.onTimeout
import kotlinx.coroutines.selects.select
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import libc.clib
import org.cliffc.high_scale_lib.NonBlockingHashSet
import java.nio.ByteBuffer
import kotlin.math.min

class UCloudFilePlugin : FilePlugin {
    override val pluginTitle: String = "UCloud"
    override var pluginName: String = "Unknown"
    override var productAllocation: List<ProductReferenceWithoutProvider> = emptyList()
    override var productAllocationResolved: List<ProductV2> = emptyList()
    override var supportedUploadProtocols = listOf(UploadProtocol.WEBSOCKET, UploadProtocol.CHUNKED)

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
    lateinit var uploadDescriptors: UploadDescriptors
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
        tasks =
            TaskSystem(pluginConfig, dbConnection, pathConverter, fs, Dispatchers.IO, rpcClient, debugSystem, usageScan)
        uploadDescriptors = UploadDescriptors(pathConverter, fs)
        uploads = ChunkedUploadService(uploadDescriptors)

        for (alloc in productAllocationResolved) {
            val p = config.products.storage.find { it.category.toId() == alloc.category.toId() } ?: continue
            when (val cost = p.cost) {
                ProductCost.Free -> {
                    // OK
                }

                is ProductCost.Money -> {
                    if (cost.interval != null) {
                        error("Unable to support products in UCloud storage with interval != null")
                    }
                }

                is ProductCost.Resource -> {
                    if (cost.accountingInterval != null) {
                        error("Unable to support products in UCloud storage with interval != null")
                    }
                }
            }
        }

        val stagingFolderPath = pluginConfig.trash.stagingFolder?.takeIf { pluginConfig.trash.useStagingFolder }
        val stagingFolder = stagingFolderPath?.let { InternalFile(it) }?.takeIf {
            runCatching { fs.stat(it).fileType }.getOrNull() == FileType.DIRECTORY
        }

        uploadDescriptors.startMonitoringLoop()

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

    override suspend fun PluginContext.onWalletSynchronized(notification: AllocationPlugin.Message) {
        usageScan.notifyAccounting(notification)
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

        val checkedItems = req.items.map { reqItem ->
            val fileExists = queries.fileExists(UCloudFile.create(reqItem.id))
            if (reqItem.conflictPolicy == WriteConflictPolicy.REJECT && fileExists) {
                throw RPCException("Folder already exists", HttpStatusCode.Conflict)
            }
            if (reqItem.conflictPolicy == WriteConflictPolicy.RENAME && fileExists) {
                val foundNewName = queries.findAvailableNameOnRename(reqItem.id)
                reqItem.copy(id = foundNewName)
            } else {
                reqItem
            }
        }

        return checkedItems.map { reqItem ->
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
            val ucloudFile = UCloudFile.create(it.id)
            val internalFile = pathConverter.ucloudToInternal(ucloudFile)
            limitChecker.checkLimit(driveLocator.resolveDriveByInternalFile(internalFile).drive.ucloudId.toString())
            val targetPath = it.id

            val pluginData = defaultMapper.encodeToJsonElement(
                FileUploadSessionPluginData(targetPath, it.type, it.conflictPolicy)
            ).toString()

            FileUploadSession(secureToken(32), pluginData)
        }
    }

    override suspend fun RequestContext.handleUpload(
        session: String,
        pluginData: String,
        offset: Long,
        chunk: ByteReadChannel,
        lastChunk: Boolean
    ) {
        val sessionData = defaultMapper.decodeFromString<FileUploadSessionPluginData>(pluginData)
        uploads.receiveChunk(
            UCloudFile.create(sessionData.target),
            offset,
            chunk,
            sessionData.conflictPolicy,
            lastChunk
        )

        if (lastChunk) {
            ipcClient.sendRequest(
                FilesUploadIpc.delete,
                FindByStringId(session)
            )
        }
    }

    private suspend fun RequestContext.doHandleFolderUpload(
        pluginData: String,
        fileCollections: SimpleCache<String, FileCollection>,
        fileEntry: FileListingEntry,
        chunk: ByteReadChannel,
        lastChunk: Boolean
    ): Boolean {
        val sessionData = defaultMapper.decodeFromString<FileUploadSessionPluginData>(pluginData)

        val collection = fileCollections.get(sessionData.target.split("/")[1])
            ?: throw RPCException("Unable to resolve file collection", HttpStatusCode.BadRequest)

        // Create folders
        val folders = fileEntry.path.split("/").dropLast(1)
        val allFolders = mutableListOf<String>()

        var i = 0
        while (i < folders.size) {
            // TODO This really needs to be improved
            val element = folders.subList(0, i + 1).joinToString("/")
            if (element.contains("../")) error("Bailing")
            if (element == "..") error("Bailing")

            allFolders.add(element)
            i++
        }

        allFolders.forEach { folder ->
            try {
                createFolder(
                    bulkRequestOf(
                        FilesProviderCreateFolderRequestItem(
                            collection,
                            sessionData.target + "/" + folder,
                            WriteConflictPolicy.REJECT
                        )
                    )
                )
            } catch (e: RPCException) {
                // Ignore: Folder already exists
            }
        }

        val targetPath = sessionData.target + "/" + fileEntry.path

        return uploads.receiveChunk(
            UCloudFile.create(targetPath),
            fileEntry.offset,
            chunk,
            WriteConflictPolicy.REPLACE,
            lastChunk,
            fileEntry.modifiedAt
        )
    }

    override suspend fun RequestContext.handleFolderUpload(
        session: String,
        pluginData: String,
        fileCollections: SimpleCache<String, FileCollection>,
        fileEntry: FileListingEntry,
        chunk: ByteReadChannel,
        lastChunk: Boolean
    ) {
        doHandleFolderUpload(pluginData, fileCollections, fileEntry, chunk, lastChunk)
    }

    override suspend fun RequestContext.handleUploadWs(
        session: String,
        pluginData: String,
        fileCollections: SimpleCache<String, FileCollection>,
        websocket: WebSocketSession
    ) {
        var fileOffset = 0L
        var totalSize = 0L

        for (frame in websocket.incoming) {
            if (frame.frameType == FrameType.TEXT) {
                val metadata = (frame as? Frame.Text)?.readText()?.split(' ')
                if (metadata != null) {
                    fileOffset = metadata[0].toLong()
                    totalSize = metadata[1].toLong()
                }
            } else {
                if (!frame.buffer.isDirect) {
                    DefaultDirectBufferPoolForFileIo.useInstance { nativeBuffer ->
                        try {
                            var offset = 0
                            while (offset < frame.data.size) {
                                if (nativeBuffer.remaining() == 0) nativeBuffer.flip()
                                val count = min(frame.data.size - offset, nativeBuffer.remaining())
                                nativeBuffer.put(frame.data, offset, count)
                                nativeBuffer.flip()
                                offset += count
                                val channel = ByteReadChannel(nativeBuffer)
                                handleUpload(
                                    session,
                                    pluginData,
                                    fileOffset,
                                    channel,
                                    fileOffset + count >= totalSize
                                )

                                fileOffset += count
                            }
                        } catch (ex: Throwable) {
                            ex.printStackTrace()
                            throw ex
                        }
                    }
                } else {
                    handleUpload(
                        session,
                        pluginData,
                        fileOffset,
                        ByteReadChannel(frame.data),
                        fileOffset + frame.data.size >= totalSize
                    )

                    fileOffset += frame.data.size
                }

                websocket.send("$fileOffset")
            }
        }
    }

    override suspend fun RequestContext.handleFolderUploadWs(
        session: String,
        pluginData: String,
        fileCollections: SimpleCache<String, FileCollection>,
        websocket: WebSocketSession
    ) {
        val sessionData = defaultMapper.decodeFromString<FileUploadSessionPluginData>(pluginData)
        val listing = HashMap<UInt, FileListingEntry>()
        val backlog = NonBlockingHashSet<Int>()

        val destinationFolder = pathConverter.ucloudToInternal(UCloudFile.create(sessionData.target))

        val filesCompleted = AtomicInteger(0)
        val responseBuffer = ByteBuffer.allocateDirect(1024 * 4)
        suspend fun flushResponses() {
            responseBuffer.flip()
            if (responseBuffer.hasRemaining()) {
                websocket.send(Frame.Binary(true, responseBuffer))
            }
            responseBuffer.clear()
        }

        ProcessingScope.launch {
            var last = 0
            val buf = ByteBuffer.allocateDirect(5)
            while (isActive && websocket.isActive) {
                /*
                val firstBacklog = backlog.firstOrNull()
                firstBacklog?.let { println("Backlog: ${listing[it.toUInt()]} ${backlog.size}") }
                 */

                val current = filesCompleted.get()
                if (last != current) {
                    buf.clear()
                    buf.put(FolderUploadMessageType.FILES_COMPLETED.ordinal.toByte())
                    buf.putInt(current)
                    buf.flip()
                    websocket.send(Frame.Binary(true, buf))
                    last = current
                }
                delay(100)
            }
        }

        coroutineScope {
            for (frame in websocket.incoming) {
                val buffer = frame.buffer
                val frameType = FolderUploadMessageType.entries.getOrNull(buffer.get().toInt())
                    ?: throw RPCException("Invalid frame type", HttpStatusCode.BadRequest)

                when (frameType) {
                    FolderUploadMessageType.LISTING -> {
                        data class ListingResponse(val message: FolderUploadMessageType, val entry: UInt)
                        val workChannel = Channel<List<FileListingEntry>>(Channel.BUFFERED)
                        val workResponses = Channel<List<ListingResponse>>(Channel.BUFFERED)
                        val workerCount = Runtime.getRuntime().availableProcessors()
                        val workersActive = AtomicInteger(workerCount)

                        coroutineScope {
                            repeat(workerCount) { id ->
                                ProcessingScope.launch {
                                    try {
                                        while (isActive) {
                                            val entries = workChannel.receiveCatching().getOrNull() ?: break

                                            val paths = Array(entries.size) { destinationFolder.path + "/" + entries[it].path }
                                            val sizes = LongArray(entries.size) { entries[it].size }
                                            val modifiedTimestamps = LongArray(entries.size) { entries[it].modifiedAt }
                                            val results = BooleanArray(entries.size)

                                            clib.scanFiles(paths, sizes, modifiedTimestamps, results)

                                            workResponses.send(
                                                results.mapIndexed { index, skip ->
                                                    val entry = entries[index]
                                                    if (skip) {
                                                        filesCompleted.incrementAndGet()
                                                        listing[entry.id]?.offset = entry.size
                                                    }

                                                    ListingResponse(
                                                        if (skip) FolderUploadMessageType.SKIP else FolderUploadMessageType.OK,
                                                        entry.id,
                                                    )
                                                }
                                            )
                                        }
                                    } catch (ex: Throwable) {
                                        ex.printStackTrace()
                                    } finally {
                                        if (workersActive.decrementAndGet() == 0) {
                                            workResponses.close()
                                        }
                                    }
                                }
                            }

                            ProcessingScope.launch {
                                try {
                                    var batch = ArrayList<FileListingEntry>()

                                    while (buffer.hasRemaining()) {
                                        val fileId = buffer.getInt().toUInt()
                                        val size = buffer.getLong()
                                        val modifiedAt = buffer.getLong()
                                        val pathSize = buffer.getInt()
                                        if (pathSize > 1024 * 64) error("Refusing allocate space for this file: $pathSize")
                                        val pathBytes = ByteArray(pathSize)
                                        buffer.get(pathBytes)
                                        val path = pathBytes.decodeToString()

                                        val fileListingEntry = FileListingEntry(fileId, path, size, modifiedAt, 0)
                                        listing[fileId] = fileListingEntry
                                        batch.add(fileListingEntry)
                                        if (batch.size > 100) {
                                            workChannel.send(batch)
                                            batch = ArrayList()
                                        }
                                    }

                                    if (batch.isNotEmpty()) workChannel.send(batch)
                                    workChannel.close()
                                } catch (ex: Throwable) {
                                    ex.printStackTrace()
                                }
                            }

                            try {
                                while (isActive && !workResponses.isClosedForReceive) {
                                    select<Unit> {
                                        workResponses.onReceive { batch ->
                                            for ((type, entry) in batch) {
                                                if (responseBuffer.remaining() < 64) flushResponses()
                                                if (type == FolderUploadMessageType.OK) {
                                                    backlog.add(entry.toInt())
                                                }
                                                responseBuffer.put(type.ordinal.toByte())
                                                responseBuffer.putInt(entry.toInt())
                                            }
                                        }

                                        onTimeout(100) {
                                            flushResponses()
                                        }
                                    }
                                }
                            } catch (ex: Throwable) {
                                ex.printStackTrace()
                            }
                        }

                        flushResponses()
                    }

                    FolderUploadMessageType.SKIP -> {
                        val fileId = buffer.getInt().toUInt()
                        filesCompleted.getAndIncrement()
                        backlog.remove(fileId.toInt())
                    }

                    FolderUploadMessageType.CHUNK -> {
                        val fileId = buffer.getInt().toUInt()
                        val fileEntry: FileListingEntry = listing[fileId]
                            ?: throw RPCException.fromStatusCode(HttpStatusCode.BadRequest)
                        ProcessingScope.launch {
                            if (!frame.buffer.isDirect) {
                                DefaultDirectBufferPoolForFileIo.useInstance { nativeBuffer ->
                                    val data = if (buffer.hasArray()) {
                                        buffer.array().sliceArray(5..<buffer.array().size)
                                    } else {
                                        buffer.moveToByteArray()
                                    }

                                    try {
                                        var offset = 0
                                        var first = true
                                        while (offset < data.size || first) {
                                            first = false
                                            if (nativeBuffer.remaining() == 0) nativeBuffer.flip()
                                            val count = min(data.size - offset, nativeBuffer.remaining())
                                            nativeBuffer.put(data, offset, count)
                                            nativeBuffer.flip()
                                            offset += count
                                            val channel = ByteReadChannel(nativeBuffer)

                                            val isDone = doHandleFolderUpload(
                                                pluginData,
                                                fileCollections,
                                                fileEntry,
                                                channel,
                                                fileEntry.offset + count >= fileEntry.size
                                            )

                                            fileEntry.offset += count
                                            if (isDone || fileEntry.offset >= fileEntry.size) {
                                                filesCompleted.getAndIncrement()
                                                backlog.remove(fileEntry.id.toInt())
                                            }
                                        }
                                    } catch (ex: Throwable) {
                                        ex.printStackTrace()
                                        throw ex
                                    }
                                }
                            } else {
                                val data = if (buffer.hasArray()) {
                                    buffer.array().sliceArray(5..<buffer.array().size)
                                } else {
                                    buffer.moveToByteArray()
                                }

                                val isDone = doHandleFolderUpload(
                                    pluginData,
                                    fileCollections,
                                    fileEntry,
                                    ByteReadChannel(data),
                                    fileEntry.offset + data.size >= fileEntry.size
                                )

                                fileEntry.offset += data.size
                                if (isDone || fileEntry.offset >= fileEntry.size) {
                                    filesCompleted.getAndIncrement()
                                    backlog.remove(fileEntry.id.toInt())
                                }
                            }
                        }
                    }

                    else -> {}
                }
            }
        }
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

        ipcServer.addHandler(CliIpc.locateInMaintenance.handler { user, _ ->
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
        val target: String,
        val type: UploadType,
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
        val drives = ArrayList<UCloudDrive>()
        dbConnection.withSession { session ->
            session.prepareStatement(
                """
                        select collection_id, local_reference, project, type
                        from ucloud_storage_drives
                        where collection_id = :id
                    """
            ).useAndInvoke(
                prepare = {
                    bindLong("id", resource.id.toLong())
                },
                readRow = { row ->
                    val driveId = row.getLong(0)!!
                    val localReference = row.getString(1)
                    val project = row.getString(2)
                    val type = UCloudDrive.Type.valueOf(row.getString(3)!!)

                    drives.add(
                        when (type) {
                            UCloudDrive.Type.PERSONAL_WORKSPACE -> {
                                UCloudDrive.PersonalWorkspace(driveId, localReference!!)
                            }

                            UCloudDrive.Type.PROJECT_REPOSITORY -> {
                                UCloudDrive.ProjectRepository(driveId, project!!, localReference!!)
                            }

                            UCloudDrive.Type.PROJECT_MEMBER_FILES -> {
                                UCloudDrive.ProjectMemberFiles(driveId, project!!, localReference!!)
                            }

                            UCloudDrive.Type.COLLECTION -> {
                                UCloudDrive.Collection(driveId)
                            }

                            UCloudDrive.Type.SHARE -> {
                                UCloudDrive.Share(driveId, localReference!!)
                            }
                        }
                    )
                }
            )
        }
        val drive = drives.singleOrNull() ?: throw RPCException("Cannot locate drive", HttpStatusCode.NotFound)
        filePlugin.driveLocator.remove(
            drive
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

enum class FolderUploadMessageType {
    OK,
    CHECKSUM,
    CHUNK,
    SKIP,
    LISTING,
    FILES_COMPLETED,
}
