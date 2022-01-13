package dk.sdu.cloud.plugins.storage.posix

import dk.sdu.cloud.PageV2
import dk.sdu.cloud.ProcessingScope
import dk.sdu.cloud.ProductBasedConfiguration
import dk.sdu.cloud.accounting.api.ProductReference
import dk.sdu.cloud.accounting.api.providers.SortDirection
import dk.sdu.cloud.calls.BulkRequest
import dk.sdu.cloud.calls.BulkResponse
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.bulkRequestOf
import dk.sdu.cloud.file.orchestrator.api.*
import dk.sdu.cloud.http.ByteBuffer
import dk.sdu.cloud.http.Header
import dk.sdu.cloud.http.HttpContext
import dk.sdu.cloud.http.write
import dk.sdu.cloud.plugins.FileDownloadSession
import dk.sdu.cloud.plugins.FilePlugin
import dk.sdu.cloud.plugins.FileUploadSession
import dk.sdu.cloud.plugins.PluginContext
import dk.sdu.cloud.plugins.storage.InternalFile
import dk.sdu.cloud.plugins.storage.PathConverter
import dk.sdu.cloud.plugins.storage.UCloudFile
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.Logger
import dk.sdu.cloud.service.SimpleCache
import dk.sdu.cloud.service.Time
import dk.sdu.cloud.utils.*
import io.ktor.http.*
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import platform.posix.*
import kotlin.math.min

class PosixFilesPlugin : FilePlugin {
    private lateinit var pathConverter: PathConverter
    private lateinit var taskSystem: PosixTaskSystem

    override suspend fun PluginContext.initialize(pluginConfig: ProductBasedConfiguration) {
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
        isDirectory: Boolean,
        truncate: Boolean = true,
    ): Pair<String, Int> {
        val mode = if (isDirectory) DEFAULT_DIR_MODE else DEFAULT_FILE_MODE
        val fixedConflictPolicy = conflictPolicy

        fun createDirAndOpen(name: String): Pair<String, Int>? {
            // If it doesn't exist everything is good. Create the directory and return the name + fd.
            val status = mkdir(parent.path + "/" + name, DEFAULT_DIR_MODE)
            if (status >= 0) {
                val fd = open(parent.path + "/" + name, 0, DEFAULT_DIR_MODE)
                if (fd >= 0) return Pair(parent.path + "/" + name, fd)

                // Very unexpected, but technically possible. Fall through to the naming step.
            }

            // The name was taken before we could complete our operation. Fall through to naming step.
            return null
        }

        var oflags = 0
        if (!isDirectory) {
            oflags = oflags or O_CREAT or O_WRONLY
            if (truncate) oflags = oflags or O_TRUNC
            if (fixedConflictPolicy != WriteConflictPolicy.REPLACE) oflags = oflags or O_EXCL
        } else {
            oflags = oflags or O_DIRECTORY
        }

        val desiredFd = open(parent.path + "/" + desiredFileName, oflags, mode)
        if (!isDirectory) {
            if (desiredFd >= 0) return Pair(parent.path + "/" + desiredFileName, desiredFd)
        } else {
            // If it exists, and we allow overwrite then just return the open directory
            if (
                (fixedConflictPolicy == WriteConflictPolicy.REPLACE || fixedConflictPolicy == WriteConflictPolicy.MERGE_RENAME) &&
                desiredFd >= 0
            ) {
                return Pair(parent.path + "/" + desiredFileName, desiredFd)
            } else if (desiredFd < 0) {
                val result = createDirAndOpen(desiredFileName)
                if (result != null) return result
            } else {
                close(desiredFd) // We don't need this one
            }

            // We need to create a differently named directory (see below)
        }

        if (fixedConflictPolicy == WriteConflictPolicy.REJECT) throw RPCException.fromStatusCode(HttpStatusCode.Conflict)
        check(fixedConflictPolicy == WriteConflictPolicy.RENAME || fixedConflictPolicy == WriteConflictPolicy.MERGE_RENAME)

        for (attempt in 1 until 10_000) { // NOTE(Dan): We put an upper-limit to avoid looping 'forever'
            val filenameWithoutExtension = desiredFileName.substringBeforeLast('.')
            val extension = desiredFileName.substringAfterLast('.')
            val hasExtension = desiredFileName.length != filenameWithoutExtension.length

            val newName = buildString {
                append(filenameWithoutExtension)
                append("(")
                append(attempt)
                append(")")
                if (hasExtension) {
                    append('.')
                    append(extension)
                }
            }
            val attemptedFd = open(parent.path + "/" + newName, oflags, mode)
            if (!isDirectory) {
                if (attemptedFd >= 0) return Pair(parent.path + "/" + newName, attemptedFd)
            } else {
                val result = createDirAndOpen(newName)
                if (result != null) return result
            }
        }

        throw RPCException.fromStatusCode(
            HttpStatusCode.BadRequest,
            "Too many files with this name exist: '$desiredFileName'"
        )
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
        val reqItem = request
        val source = UCloudFile.create(reqItem.oldId)
        val destination = UCloudFile.create(reqItem.newId)
        val conflictPolicy = reqItem.conflictPolicy
        val needsToRecurse = move(
            pathConverter.ucloudToInternal(source),
            pathConverter.ucloudToInternal(destination),
            conflictPolicy
        ).needsToRecurse

        if (needsToRecurse) {
            val files = listFiles(pathConverter.ucloudToInternal(source))
            val requests = files.map { file ->
                FilesProviderMoveRequestItem(
                    reqItem.resolvedOldCollection,
                    reqItem.resolvedNewCollection,
                    source.path + "/" + file.path.fileName(),
                    destination.path + "/" + file.path.fileName(),
                    conflictPolicy
                )
            }
            for (nextRequest in requests) {
                PosixTask.Move(
                    title,
                    collectionId,
                    nextRequest,
                    id,
                    timestamp
                ).process()
            }
        }
    }

    data class MoveShouldContinue(val needsToRecurse: Boolean)

    private fun move(
        source: InternalFile,
        destination: InternalFile,
        conflictPolicy: WriteConflictPolicy
    ): MoveShouldContinue {
        val sourceStat = nativeStat(source)
        var shouldContinue = false

        val desiredFileName = destination.path.fileName()
        if (conflictPolicy == WriteConflictPolicy.MERGE_RENAME && sourceStat.status.type == FileType.DIRECTORY) {
            val destFd = open(desiredFileName, 0, 0)
            if (destFd >= 0) {
                shouldContinue = true
                close(destFd)
            }
        }

        val (destinationName, destinationFd) = createAccordingToPolicy(
            InternalFile(destination.path.parent()),
            desiredFileName,
            conflictPolicy,
            sourceStat.status.type == FileType.DIRECTORY,
        )
        close(destinationFd)

        if (conflictPolicy == WriteConflictPolicy.MERGE_RENAME && desiredFileName == destinationName &&
            sourceStat.status.type == FileType.DIRECTORY
        ) {
            // NOTE(Dan): Do nothing. The function above has potentially re-used an existing directory which
            // might not be empty. The `renameat` call will fail for non-empty directories which is not what we
            // want in this specific instance.
        } else {
            val err = rename(
                source.path,
                destinationName
            )

            if (err < 0) {
                throw RPCException.fromStatusCode(HttpStatusCode.InternalServerError, "failed with $errno")
            }
        }
        return MoveShouldContinue(shouldContinue)
    }

    override suspend fun PluginContext.copy(
        req: BulkRequest<FilesProviderCopyRequestItem>
    ): List<LongRunningTask?> {
        val result = req.items.map { reqItem ->
            processTask(
                PosixTask.Copy(
                    "Copy Files...",
                    reqItem.resolvedOldCollection.id,
                    reqItem
                )
            )
        }
        return result
    }

    private suspend fun PosixTask.Copy.process() {
        val reqItem = request
        val source = UCloudFile.create(reqItem.oldId)
        val destination = UCloudFile.create(reqItem.newId)
        val conflictPolicy = reqItem.conflictPolicy
        val result = copy(
            pathConverter.ucloudToInternal(source),
            pathConverter.ucloudToInternal(destination),
            conflictPolicy
        )
        if (result is CopyResult.CreatedDirectory) {
            val outputFile = result.outputFile
            val files = listFiles(
                pathConverter.ucloudToInternal(source)
            )

            val requests = files.map { file ->
                FilesProviderCopyRequestItem(
                    reqItem.resolvedOldCollection,
                    reqItem.resolvedNewCollection,
                    source.path + "/" + file.path.fileName(),
                    pathConverter.internalToUCloud(InternalFile(outputFile.path + "/" + file.path.fileName())).path,
                    conflictPolicy
                )
            }

            for (newRequest in requests) {
                PosixTask.Copy(title, collectionId, newRequest, id, timestamp).process()
            }
        }
    }

    sealed class CopyResult {
        object CreatedFile : CopyResult()
        class CreatedDirectory(val outputFile: InternalFile) : CopyResult()
        object NothingToCreate : CopyResult()
    }

    private fun copy(
        source: InternalFile,
        destination: InternalFile,
        conflictPolicy: WriteConflictPolicy,
    ): CopyResult {
        val sourceStat = nativeStat(source)
        val desiredFileName = destination.path.fileName()
        if (sourceStat.status.type == FileType.FILE) {
            val (destinationName, destinationFd) = createAccordingToPolicy(
                InternalFile(destination.path.parent()),
                desiredFileName,
                conflictPolicy,
                sourceStat.status.type == FileType.DIRECTORY,
            )

            val outs = NativeOutputStream(destinationFd)
            val ins = NativeInputStream(open(source.path, 0, 0))

            ins.copyTo(outs)
            fchmod(destinationFd, DEFAULT_FILE_MODE)
            close(destinationFd)
            return CopyResult.CreatedFile
        } else if (sourceStat.status.type == FileType.DIRECTORY) {
            val (destinationName, destinationFd) = createAccordingToPolicy(
                InternalFile(destination.path.parent()),
                desiredFileName,
                conflictPolicy,
                sourceStat.status.type == FileType.DIRECTORY,
            )
            return CopyResult.CreatedDirectory(
                destination
            )
        } else {
            return CopyResult.NothingToCreate
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
        val items = listFiles(pathConverter.ucloudToInternal(path)).filter {
            val fileName = it.path.fileName()
            (!request.browse.flags.filterHiddenFiles || !fileName.startsWith(".")) &&
                fileName != PosixTaskSystem.TASK_FOLDER
        }

        val shouldSort = items.size <= 10_000
        if (shouldSort) {
            val files = items.map { nativeStat(it) }
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
            val files = items.subList(offset, min(items.size, offset + itemsPerPage)).map { nativeStat(it) }
            return PageV2(
                itemsPerPage,
                files,
                if (items.size >= offset + itemsPerPage) "${offset + itemsPerPage}-${token}" else null
            )
        }
    }

    private fun nativeStat(file: InternalFile): PartialUFile {
        return memScoped {
            val st = alloc<stat>()
            val error = stat(file.path, st.ptr)
            if (error < 0) {
                // TODO actually remap the error code
                throw RPCException("Could not open file: ${file}", HttpStatusCode.NotFound)
            }

            val modifiedAt = (st.st_mtim.tv_sec * 1000) + (st.st_mtim.tv_nsec / 1_000_000)
            val internalToUCloud = pathConverter.internalToUCloud(file)
            val numberOfComponents = internalToUCloud.path.count { it == '/' }
            val isTrash = numberOfComponents == 2 && internalToUCloud.path.endsWith("/Trash")
            PartialUFile(
                internalToUCloud.path,
                UFileStatus(
                    if (st.st_mode and S_ISREG == 0U) FileType.DIRECTORY else FileType.FILE,
                    sizeInBytes = st.st_size,
                    modifiedAt = modifiedAt,
                    unixOwner = st.st_uid.toInt(),
                    unixGroup = st.st_gid.toInt(),
                    unixMode = st.st_mode.toInt(),
                    icon = if (isTrash) FileIconHint.DIRECTORY_TRASH else null
                ),
                modifiedAt
            )
        }
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
            mkdir(expectedTrashLocation.path, DEFAULT_DIR_MODE)
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
        val stack = ArrayDeque<InternalFile>()
        val discovered = HashSet<String>()
        stack.add(file)

        // Iterative bottom-up search of files
        while (stack.isNotEmpty()) {
            val nextItem = stack.removeFirst()
            if (nextItem.path !in discovered) {
                // If we have not seen the file before, check to see if it is a directory
                val stat = nativeStat(nextItem)
                if (stat.status.type == FileType.DIRECTORY) {
                    // If it is a directory, then we must process all of its children first. Add them to the front of
                    // the stack and revisit this folder again later. At this point, the folder will have been marked
                    // as discovered, and thus we won't recheck any of the files.
                    discovered.add(nextItem.path)
                    stack.addFirst(nextItem)
                    listFiles(nextItem).forEach {
                        stack.addFirst(it)
                    }
                } else {
                    // We can just delete normal files.
                    unlink(nextItem.path)
                }
            } else {
                if (keepRoot && nextItem == file) break
                // If we have already seen a file, them it must be a directory. Delete it with rmdir.
                rmdir(nextItem.path)
            }
        }
    }

    override suspend fun PluginContext.createFolder(
        req: BulkRequest<FilesProviderCreateFolderRequestItem>
    ): List<LongRunningTask?> {
        val result = req.items.map { reqItem ->
            val internalFile = pathConverter.ucloudToInternal(UCloudFile.create(reqItem.id))

            val err = mkdir(internalFile.path, DEFAULT_DIR_MODE)
            if (err < 0) {
                log.debug("Could not create directories at ${internalFile.path}")
                throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
            }
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
        chunk: ByteBuffer
    ) {
        val fileHandle = NativeFile.open(pluginData, readOnly = false, mode = DEFAULT_FILE_MODE.toInt())
        try {
            if (offset >= 0) {
                lseek(fileHandle.fd, offset, SEEK_SET)
            }
            fileHandle.write(chunk)
        } finally {
            fileHandle.close()
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

    override suspend fun PluginContext.handleDownload(ctx: HttpContext, session: String, pluginData: String) {
        val stat = nativeStat(InternalFile(pluginData))
        if (stat.status.type != FileType.FILE) {
            throw RPCException("Requested data is not a file", HttpStatusCode.NotFound)
        }

        ctx.session.sendHttpResponseWithFile(
            pluginData,
            listOf(Header("Content-Disposition", "attachment; filename=\"${pluginData.safeFileName()}\""))
        )
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

const val S_ISREG = 0x8000U
