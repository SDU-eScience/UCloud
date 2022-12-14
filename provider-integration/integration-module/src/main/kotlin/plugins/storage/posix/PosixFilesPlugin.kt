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
import dk.sdu.cloud.plugins.InternalFile
import dk.sdu.cloud.plugins.PluginContext
import dk.sdu.cloud.controllers.RequestContext
import dk.sdu.cloud.debug.detailD
import dk.sdu.cloud.logThrowable
import dk.sdu.cloud.plugins.UCloudFile
import dk.sdu.cloud.plugins.components
import dk.sdu.cloud.plugins.fileName
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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.serializer
import libc.clib
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.PosixFileAttributes
import java.nio.file.attribute.PosixFilePermission
import kotlin.io.path.absolutePathString
import kotlin.math.min
import kotlin.system.exitProcess
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime
import java.nio.file.Files as NioFiles

class PosixFilesPlugin : FilePlugin {
    override val pluginTitle: String = "Posix"
    override var pluginName: String = "Unknown"
    override var productAllocation: List<ProductReferenceWithoutProvider> = emptyList()
    override var productAllocationResolved: List<Product> = emptyList()
    private lateinit var pathConverter: PathConverter
    private lateinit var taskSystem: PosixTaskSystem

    override suspend fun PluginContext.initialize() {
        pathConverter = PathConverter(this)
        taskSystem = PosixTaskSystem(this, pathConverter)

        // NOTE(Dan): Set the umask required for this plugin. This is done to make sure that projects have predictable
        // results regardless of how the underlying system is configured. Without this, it is entirely possible that
        // users can create directories which no other member can use.
        clib.umask("0007".toInt(8))
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
            val now = Time.now()
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
                    for (attempt in 0 until 10) {
                        val success = runCatching {
                            taskSystem.markTaskAsComplete(task.collectionId, task.id)
                        }.isSuccess

                        if (success) break

                        delay(1500)
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
                val filenameWithoutExtension = desiredFileName.substringBeforeLast('.')
                val extension = desiredFileName.substringAfterLast('.')
                val hasExtension = desiredFileName.length != filenameWithoutExtension.length

                append(filenameWithoutExtension)
                if (attempt != 0) {
                    append("(")
                    append(attempt)
                    append(")")
                }
                if (hasExtension) {
                    append('.')
                    append(extension)
                }
            })

            if (!NioFiles.exists(idealName)) return InternalFile(idealName.absolutePathString())
        }
        throw RPCException.fromStatusCode(HttpStatusCode.Conflict)
    }

    override suspend fun RequestContext.move(
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

        val desiredDestination = pathConverter.ucloudToInternal(UCloudFile.create(request.newId)).toNioPath()
        val destination = createAccordingToPolicy(
            desiredDestination.parent.toInternalFile(),
            desiredDestination.toInternalFile().path.fileName(),
            request.conflictPolicy
        ).toNioPath()

        NioFiles.move(
            pathConverter.ucloudToInternal(UCloudFile.create(request.oldId)).toNioPath(),
            destination,
            *copyOptions.toTypedArray()
        )
    }

    override suspend fun RequestContext.copy(
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
        val destination = run {
            val desiredDestination = pathConverter.ucloudToInternal(UCloudFile.create(request.newId)).toNioPath()
            createAccordingToPolicy(
                desiredDestination.parent.toInternalFile(),
                desiredDestination.toInternalFile().path.fileName(),
                request.conflictPolicy
            ).toNioPath()
        }

        if (destination.startsWith(source)) {
            return
        }

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

    override suspend fun RequestContext.retrieve(request: FilesProviderRetrieveRequest): PartialUFile {
        return nativeStat(pathConverter.ucloudToInternal(UCloudFile.create(request.retrieve.id)))
    }

    private val browseCache = SimpleCache<String, List<PartialUFile>>(lookup = { null })

    override suspend fun RequestContext.browse(
        path: UCloudFile,
        request: FilesProviderBrowseRequest
    ): PageV2<PartialUFile> {
        try {
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
                val files = items.mapNotNull { runCatching { nativeStat(InternalFile(it)) }.getOrNull() }
                val pathComparator = compareBy(String.CASE_INSENSITIVE_ORDER, PartialUFile::id)
                var comparator =
                    when (FilesSortBy.values().find { it.name == request.browse.sortBy } ?: FilesSortBy.PATH) {
                        FilesSortBy.PATH -> pathComparator
                        FilesSortBy.SIZE -> Comparator<PartialUFile> { a, b ->
                            val aSize = a.status.sizeInBytes ?: 0L
                            val bSize = b.status.sizeInBytes ?: 0L
                            when {
                                aSize < bSize -> -1
                                aSize > bSize -> 1
                                else -> 0
                            }
                        }.thenComparator { a, b -> pathComparator.compare(a, b) }

                        FilesSortBy.MODIFIED_AT -> Comparator<PartialUFile> { a, b ->
                            val aModifiedAt = a.status.modifiedAt ?: 0L
                            val bModifiedAt = b.status.modifiedAt ?: 0L
                            when {
                                aModifiedAt < bModifiedAt -> -1
                                aModifiedAt > bModifiedAt -> 1
                                else -> 0
                            }
                        }.thenComparator { a, b -> pathComparator.compare(a, b) }
                    }
                if (request.browse.sortDirection != SortDirection.ascending) comparator = comparator.reversed()
                val sortedFiles = files.sortedWith(comparator)
                browseCache.insert(token, sortedFiles)
                return paginateFiles(sortedFiles, offset, token)
            } else {
                val files = items.subList(offset, min(items.size, offset + itemsPerPage)).mapNotNull {
                    runCatching { nativeStat(InternalFile(it)) }.getOrNull()
                }
                return PageV2(
                    itemsPerPage,
                    files,
                    if (items.size >= offset + itemsPerPage) "${offset + itemsPerPage}-${token}" else null
                )
            }
        } catch (ex: Throwable) {
            if ((ex is RPCException && ex.httpStatusCode == HttpStatusCode.NotFound) || ex is AccessDeniedException) {
                // NOTE(Dan): Fail-safe which triggers only if we get a permission denied on a top-level drive. We
                // use this as an indication that the IM did not correctly restart after a group membership change.
                if (path.components().size == 1) {
                    Thread {
                        @Suppress("BlockingMethodInNonBlockingContext")
                        Thread.sleep(500)
                        log.warn("SHUTTING DOWN SERVICE. FAIL-SAFE FOR GROUP MEMBERSHIP PERMISSIONS HAS BEEN TRIGGERED.")
                        log.warn("IF THIS TRIGGERS IN A LOOP, THEN YOU HAVE A PERMISSION ERROR FOR $path")
                        exitProcess(0)
                    }.start()
                    throw RPCException("Unable to open folder. Please try again.", HttpStatusCode.Forbidden)
                }
            }

            throw ex
        }
    }

    private fun nativeStat(file: InternalFile): PartialUFile {
        val internalToUCloud = pathConverter.internalToUCloud(file)

        try {
            val posixAttributes = NioFiles.readAttributes(file.toNioPath(), PosixFileAttributes::class.java)

            val numberOfComponents = internalToUCloud.path.count { it == '/' }
            val isTopLevel = numberOfComponents == 2
            val fileName = internalToUCloud.path.fileName()

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
                    icon = when {
                        isTopLevel && fileName == "Trash" -> FileIconHint.DIRECTORY_TRASH
                        isTopLevel && fileName == "UCloud Jobs" -> FileIconHint.DIRECTORY_JOBS
                        else -> null
                    }
                ),
                posixAttributes.lastModifiedTime().toMillis(),
            )
        } catch (ex: AccessDeniedException) {
            return PartialUFile(
                internalToUCloud.path,
                UFileStatus(
                    FileType.FILE,
                    sizeInBytes = 0L,
                    modifiedAt = 0L,
                    accessedAt = 0L,
                    unixMode = 0,
                    unixOwner = 0,
                    unixGroup = 0
                ),
                0L
            )
        }
    }

    override suspend fun RequestContext.moveToTrash(
        request: BulkRequest<FilesProviderTrashRequestItem>
    ): List<LongRunningTask?> {
        val allItems = request.items
            .groupBy { it.resolvedCollection.id }
            .map { (collId, tasks) ->
                collId to processTask(
                    PosixTask.MoveToTrash(
                        "Moving files to trash...",
                        collId,
                        tasks[0].resolvedCollection,
                        tasks
                    )
                )
            }
            .toMap()
        val didSeeCollection = hashSetOf<String>()

        return request.items.map {
            if (it.resolvedCollection.id !in didSeeCollection) {
                didSeeCollection.add(it.resolvedCollection.id)
                allItems[it.resolvedCollection.id]
            } else {
                LongRunningTask.Complete()
            }
        }
    }

    @OptIn(ExperimentalTime::class)
    private suspend fun PosixTask.MoveToTrash.process() {
        val expectedTrashLocation = pathConverter.ucloudToInternal(
            UCloudFile.create("/${collectionId}/Trash")
        )

        if (!fileExists(expectedTrashLocation.path)) {
            NioFiles.createDirectories(expectedTrashLocation.toNioPath())
        }

        for (reqItem in request) {
            PosixTask.Move(
                title,
                collectionId,
                FilesProviderMoveRequestItem(
                    resolvedCollection,
                    resolvedCollection,
                    reqItem.id,
                    "/${resolvedCollection.id}/Trash/${reqItem.id.fileName()}",
                    WriteConflictPolicy.RENAME
                )
            ).process()
        }
    }

    override suspend fun RequestContext.emptyTrash(
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

    override suspend fun RequestContext.delete(resource: UFile) {
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

    override suspend fun RequestContext.createFolder(
        req: BulkRequest<FilesProviderCreateFolderRequestItem>
    ): List<LongRunningTask?> {
        val result = req.items.map { reqItem ->
            val internalFile = pathConverter.ucloudToInternal(UCloudFile.create(reqItem.id))
            try {
                NioFiles.createDirectories(internalFile.toNioPath())
            } catch (ex: FileAlreadyExistsException) {
                throw RPCException(
                    "Unable to create folder. A file with this name already exists!",
                    HttpStatusCode.Conflict
                )
            } catch (ex: AccessDeniedException) {
                throw RPCException(
                    "Unable to create folder. It looks like you do not have sufficient permissions!",
                    HttpStatusCode.Forbidden
                )
            }
            LongRunningTask.Complete()
        }
        return result
    }

    override suspend fun RequestContext.createUpload(
        request: BulkRequest<FilesProviderCreateUploadRequestItem>
    ): List<FileUploadSession> {
        return request.items.map {
            val file = pathConverter.ucloudToInternal(UCloudFile.create(it.id))

            // Confirm that we can open the file
            try {
                NativeFile.open(file.path, readOnly = false, mode = null).close()
            } catch (ex: FileNotFoundException) {
                throw RPCException(
                    "You lack the permissions to create a file here",
                    HttpStatusCode.BadRequest
                )
            } catch (ex: Throwable) {
                throw RPCException(
                    "Unknown error. Perhaps you are not allowed to create a file here?",
                    HttpStatusCode.BadRequest
                )
            }

            FileUploadSession(secureToken(64), file.path)
        }
    }

    override suspend fun RequestContext.handleUpload(
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
                if (read > 0) {
                    file.write(buffer, 0, read)
                }
            }
        }
    }

    override suspend fun RequestContext.createDownload(
        request: BulkRequest<FilesProviderCreateDownloadRequestItem>
    ): List<FileDownloadSession> {
        return request.items.map {
            val file = pathConverter.ucloudToInternal(UCloudFile.create(it.id))

            // Confirm that the file exists
            val stat = nativeStat(file)
            if (stat.status.type != FileType.FILE) {
                throw RPCException("Requested data is not a file", HttpStatusCode.NotFound)
            }

            try {
                NioFiles.newByteChannel(file.toNioPath()).close()
            } catch (ex: AccessDeniedException) {
                throw RPCException("You do not have permissions to download this file", HttpStatusCode.NotFound)
            }

            FileDownloadSession(secureToken(64), file.path)
        }
    }

    override suspend fun RequestContext.handleDownload(ctx: HttpCall, session: String, pluginData: String) {
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

    @OptIn(ExperimentalCoroutinesApi::class)
    override suspend fun RequestContext.streamingSearch(
        req: FilesProviderStreamingSearchRequest
    ): ReceiveChannel<FilesProviderStreamingSearchResult.Result> = ProcessingScope.produce {
        val currentFolder = req.currentFolder ?: return@produce
        val normalizedQuery = req.query.trim().split(" ").map { it.lowercase() }
        fun fileNameMatchesQuery(name: String): Boolean {
            val lowercased = name.lowercase()
            return normalizedQuery.any { lowercased.contains(it) }
        }

        val deadline = Time.now() + 60_000

        val stack = ArrayDeque<InternalFile>()
        stack.add(pathConverter.ucloudToInternal(UCloudFile.create(currentFolder)))
        while (isActive && Time.now() < deadline) {
            val file = stack.removeFirstOrNull() ?: break
            val childNames = runCatching { listFiles(file.path) }.getOrNull() ?: continue
            val children = childNames.map { InternalFile(it) }

            // NOTE(Dan): The folderBatch cannot be re-used between folders as the results are consumed asynchronously
            // and clearing it at the end would result in an empty result in the consumer.
            val folderBatch = ArrayList<PartialUFile>()
            for (child in children) {
                val fileName = child.fileName()
                val isProbablyFile = run {
                    // NOTE(Dan): This small snippet tries to determine if the file we are looking at is probably a
                    // file. We do this to avoid stat-ing files which do not match the query and are probably not
                    // directories. The end result is that we can search a lot more files in the time we have, since
                    // most file-systems probably contains mostly files which obviously do not match the query.
                    //
                    // We assume an entry is a file if its name has a dot within the last 5 characters of its name.
                    // Extensions are typically not longer than 4 characters.
                    val indexOfLastDot = fileName.lastIndexOf('.')
                    if (indexOfLastDot == -1) return@run false
                    fileName.length - indexOfLastDot <= 5
                }

                val matches = fileNameMatchesQuery(child.fileName())
                if (isProbablyFile && !matches) continue

                val fileInfo = runCatching { nativeStat(child) }.getOrNull() ?: continue
                if (fileInfo.status.type == FileType.DIRECTORY) {
                    if (!NioFiles.isSymbolicLink(child.toNioPath())) stack.add(child)
                }
                if (!matches) continue

                folderBatch.add(fileInfo)
            }

            if (folderBatch.isNotEmpty()) {
                send(FilesProviderStreamingSearchResult.Result(folderBatch))
            }
        }
    }

    override suspend fun RequestContext.retrieveProducts(
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
                    streamingSearchSupported = true
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
