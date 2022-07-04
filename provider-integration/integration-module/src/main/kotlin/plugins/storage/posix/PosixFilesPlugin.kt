package dk.sdu.cloud.plugins.storage.posix

import dk.sdu.cloud.PageV2
import dk.sdu.cloud.ProcessingScope
import dk.sdu.cloud.accounting.api.*
import dk.sdu.cloud.accounting.api.providers.SortDirection
import dk.sdu.cloud.calls.BulkRequest
import dk.sdu.cloud.calls.BulkResponse
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.server.HttpCall
import dk.sdu.cloud.config.ProductReferenceWithoutProvider
import dk.sdu.cloud.file.orchestrator.api.*
import dk.sdu.cloud.plugins.FileDownloadSession
import dk.sdu.cloud.plugins.FilePlugin
import dk.sdu.cloud.plugins.FileUploadSession
import dk.sdu.cloud.plugins.PluginContext
import dk.sdu.cloud.plugins.UCloudFile
import dk.sdu.cloud.plugins.storage.InternalFile
import dk.sdu.cloud.plugins.storage.PathConverter
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.Logger
import dk.sdu.cloud.service.SimpleCache
import dk.sdu.cloud.service.Time
import dk.sdu.cloud.utils.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.utils.io.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import libc.clib
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.PosixFileAttributes
import java.nio.file.attribute.PosixFilePermission
import kotlin.io.path.absolute
import kotlin.io.path.absolutePathString
import kotlin.math.min
import java.nio.file.Files as NioFiles

class PosixFilesPlugin : FilePlugin {
    override var pluginName: String = "Unknown"
    override var productAllocation: List<ProductReferenceWithoutProvider> = emptyList()
    override var productAllocationResolved: List<Product> = emptyList()
    private lateinit var pathConverter: PathConverter
    private lateinit var taskSystem: PosixTaskSystem

    override suspend fun PluginContext.initialize() {
        pathConverter = PathConverter(this)
        taskSystem = PosixTaskSystem(this, pathConverter)
    }

    private val tasksInitializedMutex = Mutex()
    private val tasksInitialized = HashSet<String>()
    private suspend fun initializeTasksIfNeeded(collection: FileCollection) {
        var needsInit = false
        tasksInitializedMutex.withLock {
            if (collection.id !in tasksInitialized) {
                tasksInitialized.add(collection.id)
                needsInit = true
            }
        }

        if (needsInit) {
            taskSystem.retrieveCurrentTasks(collection.id).forEach { processTask(it, doCreate = false) }
        }
    }

    private suspend fun processTask(task: PosixTask, doCreate: Boolean = true): LongRunningTask {
        val shouldBackgroundProcess = if (!doCreate) {
            true
        } else {
            // TODO(Dan): Come up with some better criteria based on the task requirements
            true
        }

        val processor: suspend () -> Unit = {
            when (task) {
                is PosixTask.Move -> task.process()
                is PosixTask.Copy -> task.process()
                is PosixTask.MoveToTrash -> task.process()
                is PosixTask.EmptyTrash -> task.process()
            }
        }

        // Dispatch task related tasks and run the processor
        if (shouldBackgroundProcess && doCreate) {
            taskSystem.registerTask(task)
        }

        return if (shouldBackgroundProcess) {
            ProcessingScope.launch {
                try {
                    processor()
                } finally {
                    // NOTE(Dan): If the task takes less than a second to complete, then it is still possible that
                    // UCloud hasn't even processed our task creation request. Wait for a bit to make sure that it is
                    // ready to mark it as complete.
                    if (Time.now() - task.timestamp < 1000) {
                        delay(250)
                    }
                    runCatching {
                        taskSystem.markTaskAsComplete(task.collectionId, task.id)
                    }
                }
            }
            LongRunningTask.ContinuesInBackground(task.id)
        } else {
            processor()
            LongRunningTask.Complete()
        }
    }

    private fun createAccordingToPolicy(
        parent: InternalFile,
        desiredFileName: String,
        conflictPolicy: WriteConflictPolicy,
    ): InternalFile {
        for (attempt in 0 until 10_000) { // NOTE(Dan): We put an upper-limit to avoid looping 'forever'
            if (attempt > 0 && conflictPolicy == WriteConflictPolicy.REJECT) {
                throw RPCException.fromStatusCode(HttpStatusCode.Conflict)
            }

            val idealName = parent.toNioPath().resolve(buildString {
                append(desiredFileName)
                if (attempt != 0) {
                    append(" (")
                    append(attempt)
                    append(")")
                }
            })

            if (!NioFiles.exists(idealName)) return InternalFile(idealName.absolutePathString())
        }
        throw RPCException.fromStatusCode(HttpStatusCode.Conflict)
    }

    override suspend fun PluginContext.move(
        req: BulkRequest<FilesProviderMoveRequestItem>
    ): List<LongRunningTask?> {
        val result = req.items.map { reqItem ->
            processTask(PosixTask.Move("Moving files...", reqItem.resolvedOldCollection.id, reqItem))
        }

        return result
    }

    private suspend fun PosixTask.Move.process() {
        val copyOptions = buildList<CopyOption> {
            if (request.conflictPolicy == WriteConflictPolicy.REPLACE) {
                add(StandardCopyOption.REPLACE_EXISTING)
            }
        }

        NioFiles.move(
            pathConverter.ucloudToInternal(UCloudFile.create(request.oldId)).toNioPath(),
            pathConverter.ucloudToInternal(UCloudFile.create(request.newId)).toNioPath(),
            *copyOptions.toTypedArray()
        )
    }

    override suspend fun PluginContext.copy(
        req: BulkRequest<FilesProviderCopyRequestItem>
    ): List<LongRunningTask?> {
        val result = req.items.map { reqItem ->
            processTask(
                PosixTask.Copy(
                    "Copying Files...",
                    reqItem.resolvedOldCollection.id,
                    reqItem
                )
            )
        }
        return result
    }

    private suspend fun PosixTask.Copy.process() {
        val source = pathConverter.ucloudToInternal(UCloudFile.create(request.oldId)).toNioPath()

        val desiredDestination = pathConverter.ucloudToInternal(UCloudFile.create(request.newId)).toNioPath()
        val destination = createAccordingToPolicy(
            desiredDestination.parent.toInternalFile(),
            desiredDestination.toInternalFile().path.fileName(),
            request.conflictPolicy
        ).toNioPath()

        println("Ideal destination is $destination")

        val copyOptions = buildList<CopyOption> {
            if (request.conflictPolicy == WriteConflictPolicy.REPLACE) add(StandardCopyOption.REPLACE_EXISTING)
        }.toTypedArray()

        if (NioFiles.isDirectory(source)) {
            NioFiles.walkFileTree(source, object : SimpleFileVisitor<Path>() {
                override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                    NioFiles.createDirectories(destination.resolve(source.relativize(dir)))
                    return FileVisitResult.CONTINUE
                }

                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    NioFiles.copy(file, destination.resolve(source.relativize(file)), *copyOptions)
                    return FileVisitResult.CONTINUE
                }
            })
        } else {
            NioFiles.copy(source, destination, *copyOptions)
        }
    }

    override suspend fun PluginContext.retrieve(request: FilesProviderRetrieveRequest): PartialUFile {
        return nativeStat(pathConverter.ucloudToInternal(UCloudFile.create(request.retrieve.id)))
    }

    private val browseCache = SimpleCache<String, List<PartialUFile>>(lookup = { null })

    override suspend fun PluginContext.browse(
        path: UCloudFile,
        request: FilesProviderBrowseRequest
    ): PageV2<PartialUFile> {
        initializeTasksIfNeeded(request.resolvedCollection)

        val itemsPerPage = request.browse.itemsPerPage ?: 50
        fun paginateFiles(files: List<PartialUFile>, offset: Int, token: String): PageV2<PartialUFile> {
            try {
                return PageV2(
                    itemsPerPage,
                    files.subList(offset, min(files.size, offset + itemsPerPage)),
                    if (files.size >= offset + itemsPerPage) "${offset + itemsPerPage}-${token}" else null
                )
            } catch (ex: IndexOutOfBoundsException) {
                throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
            }
        }

        var offset = 0
        browseCache.cleanup()
        val next = request.browse.next
        if (next != null) {
            val offsetText = next.substringBefore('-')
            val token = next.substringAfter('-')
            val parsedOffset = offsetText.toIntOrNull()
            if (parsedOffset != null) {
                offset = parsedOffset
                val files = browseCache.get(token)
                if (files != null) {
                    return paginateFiles(files, offset, token)
                }
            }
        }

        val token = path.path
        val items = listFiles(pathConverter.ucloudToInternal(path).path).filter {
            val fileName = it.fileName()
            (!request.browse.flags.filterHiddenFiles || !fileName.startsWith(".")) &&
                fileName != PosixTaskSystem.TASK_FOLDER
        }

        val shouldSort = items.size <= 10_000
        if (shouldSort) {
            val files = items.map { nativeStat(InternalFile(it)) }
            val pathComparator = compareBy(String.CASE_INSENSITIVE_ORDER, PartialUFile::id)
            var comparator = when (FilesSortBy.values().find { it.name == request.browse.sortBy } ?: FilesSortBy.PATH) {
                FilesSortBy.PATH -> pathComparator
                FilesSortBy.SIZE -> Comparator<PartialUFile> { a, b ->
                    ((a.status.sizeInBytes ?: 0L) - (b.status.sizeInBytes ?: 0L)).toInt()
                }.thenComparator { a, b -> pathComparator.compare(a, b) }
                FilesSortBy.MODIFIED_AT -> Comparator<PartialUFile> { a, b ->
                    ((a.status.modifiedAt ?: 0L) - (b.status.modifiedAt ?: 0L)).toInt()
                }.thenComparator { a, b -> pathComparator.compare(a, b) }
            }
            if (request.browse.sortDirection != SortDirection.ascending) comparator = comparator.reversed()
            val sortedFiles = files.sortedWith(comparator)
            browseCache.insert(token, sortedFiles)
            return paginateFiles(sortedFiles, offset, token)
        } else {
            val files = items.subList(offset, min(items.size, offset + itemsPerPage)).map { nativeStat(InternalFile(it)) }
            return PageV2(
                itemsPerPage,
                files,
                if (items.size >= offset + itemsPerPage) "${offset + itemsPerPage}-${token}" else null
            )
        }
    }


    private fun nativeStat(file: InternalFile): PartialUFile {
        val posixAttributes = NioFiles.readAttributes(file.toNioPath(), PosixFileAttributes::class.java)

        val internalToUCloud = pathConverter.internalToUCloud(file)
        val numberOfComponents = internalToUCloud.path.count { it == '/' }
        val isTrash = numberOfComponents == 2 && internalToUCloud.path.endsWith("/Trash")

        return PartialUFile(
            internalToUCloud.path,
            UFileStatus(
                if (posixAttributes.isDirectory) FileType.DIRECTORY else FileType.FILE,
                sizeInBytes = posixAttributes.size(),
                modifiedAt = posixAttributes.lastModifiedTime().toMillis(),
                accessedAt = posixAttributes.lastAccessTime().toMillis(),
                unixMode = posixAttributes.permissions().toInt(),
                unixOwner = clib.retrieveUserIdFromName(posixAttributes.owner().name),
                unixGroup = clib.retrieveGroupIdFromName(posixAttributes.group().name),
                icon = if (isTrash) FileIconHint.DIRECTORY_TRASH else null,
            ),
            posixAttributes.lastModifiedTime().toMillis(),
        )
    }

    override suspend fun PluginContext.moveToTrash(
        request: BulkRequest<FilesProviderTrashRequestItem>
    ): List<LongRunningTask?> {
        return request.items.map { reqItem ->
            processTask(
                PosixTask.MoveToTrash(
                    "Moving files to trash...",
                    reqItem.resolvedCollection.id,
                    reqItem
                )
            )
        }
    }

    private suspend fun PosixTask.MoveToTrash.process() {
        val expectedTrashLocation = pathConverter.ucloudToInternal(
            UCloudFile.create("/${request.resolvedCollection.id}/Trash")
        )

        if (!fileExists(expectedTrashLocation.path)) {
            NioFiles.createDirectories(expectedTrashLocation.toNioPath())
        }

        PosixTask.Move(
            title,
            collectionId,
            FilesProviderMoveRequestItem(
                request.resolvedCollection,
                request.resolvedCollection,
                request.id,
                "/${request.resolvedCollection.id}/Trash/${request.id.fileName()}",
                WriteConflictPolicy.RENAME
            )
        ).process()
    }

    override suspend fun PluginContext.emptyTrash(
        request: BulkRequest<FilesProviderEmptyTrashRequestItem>
    ): List<LongRunningTask?> {
        return request.items.map { reqItem ->
            processTask(PosixTask.EmptyTrash("Emptying trash...", reqItem.resolvedCollection.id, reqItem))
        }
    }

    private suspend fun PosixTask.EmptyTrash.process() {
        val internalFile = pathConverter.ucloudToInternal(UCloudFile.create(request.id))
        val isTrash = request.id.count { it == '/' } == 2 && request.id.endsWith("/Trash")
        if (!isTrash) {
            // Nothing to do
        } else {
            delete(internalFile, keepRoot = true)
        }
    }

    override suspend fun PluginContext.delete(resource: UFile) {
        delete(pathConverter.ucloudToInternal(UCloudFile.create(resource.id)))
    }

    private fun delete(file: InternalFile, keepRoot: Boolean = false) {
        NioFiles.walkFileTree(file.toNioPath(), object : SimpleFileVisitor<Path>() {
            override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
                if (!keepRoot || file.toNioPath() != dir) {
                    NioFiles.delete(dir)
                }
                return FileVisitResult.CONTINUE
            }

            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                NioFiles.delete(file)
                return FileVisitResult.CONTINUE
            }
        })
    }

    override suspend fun PluginContext.createFolder(
        req: BulkRequest<FilesProviderCreateFolderRequestItem>
    ): List<LongRunningTask?> {
        val result = req.items.map { reqItem ->
            val internalFile = pathConverter.ucloudToInternal(UCloudFile.create(reqItem.id))
            NioFiles.createDirectories(internalFile.toNioPath())
            LongRunningTask.Complete()
        }
        return result
    }

    override suspend fun PluginContext.createUpload(
        request: BulkRequest<FilesProviderCreateUploadRequestItem>
    ): List<FileUploadSession> {
        return request.items.map {
            val file = pathConverter.ucloudToInternal(UCloudFile.create(it.id))

            // Confirm that we can open the file
            NativeFile.open(file.path, readOnly = false, mode = DEFAULT_FILE_MODE.toInt()).close()

            FileUploadSession(secureToken(64), file.path)
        }
    }

    override suspend fun PluginContext.handleUpload(
        session: String,
        pluginData: String,
        offset: Long,
        chunk: ByteReadChannel
    ) {
        val file = RandomAccessFile(pluginData, "rw")
        file.use { file ->
            file.seek(offset)
            val buffer = ByteArray(1024 * 16)
            while (!chunk.isClosedForRead) {
                val read = chunk.readAvailable(buffer)
                file.write(buffer, 0, read)
            }
        }
    }

    override suspend fun PluginContext.createDownload(
        request: BulkRequest<FilesProviderCreateDownloadRequestItem>
    ): List<FileDownloadSession> {
        return request.items.map {
            val file = pathConverter.ucloudToInternal(UCloudFile.create(it.id))

            // Confirm that the file exists
            val stat = nativeStat(file)
            if (stat.status.type != FileType.FILE) {
                throw RPCException("Requested data is not a file", HttpStatusCode.NotFound)
            }

            FileDownloadSession(secureToken(64), file.path)
        }
    }

    override suspend fun PluginContext.handleDownload(ctx: HttpCall, session: String, pluginData: String) {
        val file = InternalFile(pluginData).toNioPath()
        if (!NioFiles.isRegularFile(file)) {
            throw RPCException("Requested data is not a file", HttpStatusCode.NotFound)
        }

        val channel = NioFiles.newByteChannel(file, StandardOpenOption.READ)

        with (ctx.ktor.call) {
            response.header(
                HttpHeaders.ContentDisposition,
                "attachment; filename=\"${pluginData.safeFileName()}\""
            )

            val buffer = ByteBuffer.allocateDirect(1024 * 4)
            respondBytesWriter {
                while (true) {
                    buffer.clear()
                    if (channel.read(buffer) == -1) break
                    buffer.flip()
                    writeFully(buffer)
                }
            }
        }
    }

    override suspend fun PluginContext.retrieveProducts(
        knownProducts: List<ProductReference>
    ): BulkResponse<FSSupport> {
        return BulkResponse(knownProducts.map {
            FSSupport(
                it,
                FSProductStatsSupport(
                    sizeInBytes = true,
                    sizeIncludingChildrenInBytes = false,
                    modifiedAt = true,
                    createdAt = false,
                    accessedAt = false,
                    unixPermissions = true,
                    unixOwner = true,
                    unixGroup = true,
                ),
                FSCollectionSupport(
                    aclModifiable = false,
                    usersCanCreate = false,
                    usersCanDelete = false,
                    usersCanRename = false,
                ),
                FSFileSupport(
                    aclModifiable = false,
                    trashSupported = true,
                    isReadOnly = false,
                )
            )
        })
    }

    companion object : Loggable {
        override val log: Logger = logger()

        const val DEFAULT_DIR_MODE = 488U // 0750
        const val DEFAULT_FILE_MODE = 416U // 0640

        private val safeFileNameChars =
            "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ._-+,@£$€!½§~'=()[]{}0123456789".let {
                CharArray(it.length) { i -> it[i] }.toSet()
            }

        private fun String.safeFileName(): String {
            val normalName = fileName()
            return buildString(normalName.length) {
                normalName.forEach {
                    when (it) {
                        in safeFileNameChars -> append(it)
                        else -> append('_')
                    }
                }
            }
        }
    }
}

fun InternalFile.toNioPath(): Path {
    return File(path).toPath()
}

fun Path.toInternalFile(): InternalFile {
    return InternalFile(absolutePathString())
}

private val bitMapToPosixPermission = mapOf(
    PosixFilePermission.OWNER_READ to "400".toInt(8),
    PosixFilePermission.OWNER_WRITE to "200".toInt(8),
    PosixFilePermission.OWNER_EXECUTE to "100".toInt(8),

    PosixFilePermission.GROUP_READ to "40".toInt(8),
    PosixFilePermission.GROUP_WRITE to "20".toInt(8),
    PosixFilePermission.GROUP_EXECUTE to "10".toInt(8),

    PosixFilePermission.OTHERS_READ to "4".toInt(8),
    PosixFilePermission.OTHERS_WRITE to "2".toInt(8),
    PosixFilePermission.OTHERS_EXECUTE to "1".toInt(8),
)

fun Collection<PosixFilePermission>.toInt(): Int {
    var result = 0
    for (perm in this) {
        val bit = bitMapToPosixPermission.getValue(perm)
        result = result or bit
    }
    return result
}

fun posixFilePermissionsFromInt(mode: Int): Set<PosixFilePermission> {
    val result = HashSet<PosixFilePermission>()
    for ((perm, bit) in bitMapToPosixPermission) {
        if (mode and bit != 0) result.add(perm)
    }
    return result
}

const val S_ISREG = 0x8000U
