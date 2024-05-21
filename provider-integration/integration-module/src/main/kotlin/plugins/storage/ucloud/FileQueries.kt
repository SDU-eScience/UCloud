package dk.sdu.cloud.plugins.storage.ucloud

import dk.sdu.cloud.NormalizedPaginationRequestV2
import dk.sdu.cloud.PageV2
import dk.sdu.cloud.accounting.api.providers.SortDirection
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.debug.log
import dk.sdu.cloud.debug.normal
import dk.sdu.cloud.debugSystem
import dk.sdu.cloud.file.orchestrator.api.FileIconHint
import dk.sdu.cloud.file.orchestrator.api.FileType
import dk.sdu.cloud.file.orchestrator.api.FilesProviderStreamingSearchRequest
import dk.sdu.cloud.file.orchestrator.api.FilesProviderStreamingSearchResult
import dk.sdu.cloud.file.orchestrator.api.FilesSortBy
import dk.sdu.cloud.file.orchestrator.api.PartialUFile
import dk.sdu.cloud.file.orchestrator.api.UFileIncludeFlags
import dk.sdu.cloud.file.orchestrator.api.UFileStatus
import dk.sdu.cloud.file.orchestrator.api.fileName
import dk.sdu.cloud.file.orchestrator.api.joinPath
import dk.sdu.cloud.plugins.InternalFile
import dk.sdu.cloud.plugins.UCloudFile
import dk.sdu.cloud.plugins.fileName
import dk.sdu.cloud.plugins.parent
import dk.sdu.cloud.plugins.parents
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.Time
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import libc.NativeStat
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.collections.ArrayDeque
import kotlin.collections.ArrayList
import kotlin.time.ExperimentalTime
import kotlin.time.measureTimedValue

const val MAX_FILE_COUNT_FOR_SORTING = 25_000


// Copy & paste to make porting easier. Doesn't actually use any distributed state.
interface DistributedStateFactory {
    fun <T> create(reader: KSerializer<T>, name: String, expiry: Long? = null): DistributedState<T>
}

interface DistributedState<T> {
    val name: String
    val expiry: Long?

    suspend fun get(): T?
    suspend fun set(value: T)
    suspend fun delete()
}

class NonDistributedStateFactory : DistributedStateFactory {
    override fun <T> create(reader: KSerializer<T>, name: String, expiry: Long?): DistributedState<T> {
        return NonDistributedState(name, expiry)
    }
}

class NonDistributedState<T>(override val name: String, override val expiry: Long?) : DistributedState<T> {
    private var value: T? = null
    private val mutex = Mutex()

    override suspend fun get(): T? = mutex.withLock { value }

    override suspend fun set(value: T) {
        mutex.withLock {
            this.value = value
        }
    }

    override suspend fun delete() {
        mutex.withLock {
            this.value = null
        }
    }
}

class FileQueries(
    private val pathConverter: PathConverter,
    private val distributedStateFactory: DistributedStateFactory,
    private val nativeFs: NativeFS,
    private val fileTrashService: TrashService,
    private val directoryStats: FastDirectoryStats,
) {
    suspend fun retrieve(file: UCloudFile, flags: UFileIncludeFlags): PartialUFile {
        try {
            val internalFile = pathConverter.ucloudToInternal(file)
            val nativeStat = nativeFs.stat(internalFile)
            val inheritedSensitivity: String? = inheritedSensitivity(internalFile)
            val sensitivity = runCatching { nativeFs.getExtendedAttribute(internalFile, SENSITIVITY_XATTR) }
                .getOrNull()?.takeIf { it != "inherit" } ?: inheritedSensitivity
            val forcedPrefix = file.parent()
            return convertNativeStatToUFile(internalFile, nativeStat, forcedPrefix.path, sensitivity)
        } catch (ex: RPCException) {
            if (ex.errorCode == PathConverter.INVALID_FILE_ERROR_CODE) {
                throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
            }
            throw ex
        }
    }

    private fun findIcon(file: InternalFile): FileIconHint? {
        return if (fileTrashService.isTrashFolder(file)) {
            FileIconHint.DIRECTORY_TRASH
        } else if (file.fileName() == "Jobs") {
            FileIconHint.DIRECTORY_JOBS
        } else {
            null
        }
    }

    suspend fun fileExists(file: UCloudFile): Boolean {
        return try {
            val internalFile = pathConverter.ucloudToInternal(file)
            nativeFs.stat(internalFile)
            true
        } catch (ex: RPCException) {
            false
        }
    }

    suspend fun findAvailableNameOnRename(id: String): String {
        for (i in 1..10000) {
            val prepath = id.substringBeforeLast("/")
            val filename = id.substringAfterLast("/").substringBeforeLast(".")
            val extension = filename.substringAfterLast(".")
            val hasExtension = extension != filename
            val newFilename = "$filename($i)"
            val newId = if (hasExtension) "$prepath/$newFilename.$extension" else "$prepath/$newFilename"
            if (!fileExists(UCloudFile.create(newId))) {
                return newId
            }
        }
        throw RPCException("Not able to rename file: $id", HttpStatusCode.BadRequest)
    }

    private suspend fun convertNativeStatToUFile(
        file: InternalFile,
        nativeStat: NativeStat,
        forcedPrefix: String? = null,
        sensitivity: String? = null,
    ): PartialUFile {
        val realPath = pathConverter.internalToUCloud(file).path
        val pathToReturn = if (forcedPrefix != null) {
            forcedPrefix.removeSuffix("/") + "/" + realPath.fileName()
        } else {
            realPath
        }

        return PartialUFile(
            pathToReturn,
            UFileStatus(
                nativeStat.fileType,
                findIcon(file),
                sizeInBytes = nativeStat.size,
                sizeIncludingChildrenInBytes = runCatching { directoryStats.getRecursiveSize(file) }.getOrNull(),
                modifiedAt = nativeStat.modifiedAt,
                unixMode = nativeStat.mode,
                unixOwner = nativeStat.ownerUid,
                unixGroup = nativeStat.ownerGid,
            ),
            nativeStat.modifiedAt,
            legacySensitivity = sensitivity
        )
    }

    @OptIn(ExperimentalTime::class)
    suspend fun browseFiles(
        file: UCloudFile,
        flags: UFileIncludeFlags,
        pagination: NormalizedPaginationRequestV2,
        sortBy: FilesSortBy,
        sortOrder: SortDirection?,
    ): PageV2<PartialUFile> {
        // NOTE(Dan): The next token consists of two parts. These two parts are separated by a single underscore:
        //
        // 1. The first part contains the current offset in the list. This allows a user to restart the search.
        // 2. The second part contains a unique ID.
        //
        // This token is used for storing state in Redis which contains a complete list of files found in this
        // directory. This allows the user to search a consistent snapshot of the files. If any files are removed
        // between calls then this endpoint will simply skip it.

        val next = pagination.next
        var foundFiles: List<InternalFile>? = null

        if (next != null) {
            val initialState = distributedStateFactory.create(ListSerializer(String.serializer()), next)
            foundFiles = initialState.get()?.map { InternalFile(it) }
        }

        debugSystem.normal("Locating files")
        val internalFile = try {
            pathConverter.ucloudToInternal(file)
        } catch (ex: RPCException) {
            if (ex.errorCode == PathConverter.INVALID_FILE_ERROR_CODE) {
                throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
            }
            throw ex
        }
        if (foundFiles == null) {
            foundFiles = nativeFs.listFiles(internalFile)
                .mapNotNull {
                    val result = InternalFile(internalFile.path + "/" + it)
                    if (flags.filterHiddenFiles && it.startsWith(".")) {
                        return@mapNotNull null
                    } else {
                        result
                    }
                }
        }
        debugSystem.normal("Files located (file count = ${foundFiles.size}) - Sorting files...")

        val inheritedSensitivity: String? = inheritedSensitivity(internalFile)

        // NOTE(jonas): Only allow user-selected FilesSortBy if user requests files from folder containing less than 25k files.
        val allowedSortBy = if (foundFiles.size <= MAX_FILE_COUNT_FOR_SORTING) {
            sortBy
        } else {
            FilesSortBy.PATH
        }

        val foundFilesToStat = HashMap<String, NativeFS.StatAndXattr>()
        foundFiles = sortFiles(nativeFs, allowedSortBy, sortOrder, foundFiles, foundFilesToStat, SENSITIVITY_XATTR)

        debugSystem.normal("Files sorted - Retrieving file information...")

        val offset = pagination.next?.substringBefore('_')?.toIntOrNull() ?: 0
        if (offset < 0) throw RPCException("Bad next token supplied", HttpStatusCode.BadRequest)
        val items = ArrayList<PartialUFile>()
        var i = offset
        var didSkipFiles = false
        var timeInStatNanos = 0L
        var timeInConversionNanos = 0L

        while (i < foundFiles.size && items.size < pagination.itemsPerPage) {
            val nextInternalFile = foundFiles[i++]

            when (allowedSortBy) {
                FilesSortBy.PATH -> {
                    try {
                        val (statAndAttributes, time) = measureTimedValue {
                            nativeFs.statAndFetchAttributes(nextInternalFile, SENSITIVITY_XATTR)
                        }

                        val sensitivity = statAndAttributes.attributes[0]?.takeIf { it != "inherit" } ?: inheritedSensitivity

                        timeInStatNanos += time.inWholeNanoseconds

                        val (converted, conversionTime) = measureTimedValue {
                            convertNativeStatToUFile(
                                nextInternalFile,
                                statAndAttributes.stat,
                                file.path,
                                sensitivity,
                            )
                        }
                        timeInConversionNanos += conversionTime.inWholeNanoseconds
                        items.add(converted)
                    } catch (ex: FSException.NotFound) {
                        // NOTE(Dan): File might have gone away between these two calls
                        didSkipFiles = true
                    }
                }
                else -> {
                    val nextFile = foundFilesToStat[nextInternalFile.path]
                    if (nextFile == null) {
                        didSkipFiles = true
                        continue
                    }

                    val sensitivity = nextFile.attributes[0]?.takeIf { it != "inherit" } ?: inheritedSensitivity
                    val (converted, conversionTime) = measureTimedValue {
                        convertNativeStatToUFile(
                            nextInternalFile,
                            nextFile.stat,
                            file.path,
                            sensitivity,
                        )
                    }
                    timeInConversionNanos += conversionTime.inWholeNanoseconds
                    items.add(converted)
                }
            }
        }

        if (items.isEmpty() && didSkipFiles) {
            // NOTE(Dan): The directory might not even exist anymore. We perform a check if we are about to return no
            // results and expected some. The call below will throw if the file does not exist.
            val isDir = nativeFs.stat(pathConverter.ucloudToInternal(file)).fileType == FileType.DIRECTORY
            if (!isDir) throw FSException.IsDirectoryConflict()
        }

        val didWeCoverEverything = i == foundFiles.size
        val newNext = if (!didWeCoverEverything) {
            val nextId = sessionIdCounter.getAndIncrement()
            val newToken = "${i}_${cachedFilesPrefix}${nextId}"

            val state = distributedStateFactory.create(ListSerializer(String.serializer()), newToken, DIR_CACHE_EXPIRATION)
            state.set(foundFiles.map { it.path })

            newToken
        } else {
            null
        }

        debugSystem.normal("File information gathered (${timeInStatNanos} ns in stat, ${timeInConversionNanos} ns in conversion) - Responding")

        return PageV2(pagination.itemsPerPage, items, newNext)
    }

    private suspend fun inheritedSensitivity(internalFile: InternalFile): String? {
        val ancestors = pathConverter.internalToUCloud(internalFile).parents()
            .filter { f -> f.path.removeSuffix("/").count { it == '/' } > 1 }
            .map { pathConverter.ucloudToInternal(it) } + internalFile
        var inheritedSensitivity: String? = null
        for (ancestor in ancestors) {
            val value = runCatching { nativeFs.getExtendedAttribute(ancestor, SENSITIVITY_XATTR) }.getOrNull()
            if (value != null && !value.equals("inherit", ignoreCase = true)) {
                inheritedSensitivity = value
            }
        }
        return inheritedSensitivity
    }

    @OptIn(DelicateCoroutinesApi::class, ExperimentalCoroutinesApi::class)
    suspend fun streamingSearch(
        req: FilesProviderStreamingSearchRequest
    ): ReceiveChannel<FilesProviderStreamingSearchResult.Result> = GlobalScope.produce {
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
            val childNames = runCatching { nativeFs.listFiles(file) }.getOrNull() ?: continue
            val children = childNames.map { InternalFile(joinPath(file.path, it)) }

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

                val fileInfo = runCatching { nativeFs.stat(child) }.getOrNull() ?: continue
                if (fileInfo.fileType == FileType.DIRECTORY) stack.add(child)
                if (!matches) continue

                folderBatch.add(convertNativeStatToUFile(child, fileInfo))
            }

            if (folderBatch.isNotEmpty()) {
                send(FilesProviderStreamingSearchResult.Result(folderBatch))
            }
        }
    }

    companion object : Loggable {
        override val log = logger()

        // NOTE(Dan): This ID is used for avoiding caching conflicts across restarts of a single service. That way every
        // started service will generate unique caching keys. This allows us to use a more cheap way of generating new
        // IDs. The IDs don't need to be secret since we always perform a permission check.
        private val sessionIdForCaching = UUID.randomUUID().toString()
        private val cachedFilesPrefix = "file-ucloud-dir-cache-$sessionIdForCaching-"
        private val sessionIdCounter = AtomicInteger(0)
        private const val DIR_CACHE_EXPIRATION = 1000L * 60 * 5

        private const val SENSITIVITY_XATTR = "user.sensitivity"
    }
}

fun sortFiles(
    nativeFs: NativeFS,
    sortBy: FilesSortBy,
    sortOrder: SortDirection?,
    foundFiles: List<InternalFile>,
    foundFilesToStat: HashMap<String, NativeFS.StatAndXattr>,
    vararg attributes: String,
): List<InternalFile> {
    if (sortBy != FilesSortBy.PATH) {
        for (file in foundFiles) {
            // NOTE(Dan): Catch any errors, since the files could go away at any point during this process
            runCatching {
                foundFilesToStat[file.path] = nativeFs.statAndFetchAttributes(file, *attributes)
            }
        }
    }

    val pathComparator = compareBy(String.CASE_INSENSITIVE_ORDER, InternalFile::path)
    var comparator = when (sortBy) {
        FilesSortBy.PATH -> pathComparator

        FilesSortBy.SIZE -> kotlin.Comparator<InternalFile> { a, b ->
            val aSize = foundFilesToStat[a.path]?.stat?.size ?: 0L
            val bSize = foundFilesToStat[b.path]?.stat?.size ?: 0L
            when {
                aSize < bSize -> -1
                aSize > bSize ->  1
                else -> 0
            }
        }.thenComparing(pathComparator)

        FilesSortBy.MODIFIED_AT -> kotlin.Comparator<InternalFile> { a, b ->
            val aModifiedAt = foundFilesToStat[a.path]?.stat?.modifiedAt ?: 0L
            val bModifiedAt = foundFilesToStat[b.path]?.stat?.modifiedAt ?: 0L
            when {
                aModifiedAt < bModifiedAt -> -1
                aModifiedAt > bModifiedAt ->  1
                else -> 0
            }
        }.thenComparing(pathComparator)
    }

    if (sortOrder != SortDirection.ascending) comparator = comparator.reversed()
    return foundFiles.sortedWith(comparator)
}
