package dk.sdu.cloud.file.ucloud.services

import dk.sdu.cloud.PageV2
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.file.orchestrator.api.*
import dk.sdu.cloud.service.DistributedStateFactory
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.NormalizedPaginationRequestV2
import dk.sdu.cloud.service.create
import io.ktor.http.*
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.collections.ArrayList

const val PERSONAL_REPOSITORY = "Members' Files"

class FileQueries(
    private val pathConverter: PathConverter,
    private val distributedStateFactory: DistributedStateFactory,
    private val nativeFs: NativeFS,
    private val fileTrashService: TrashService,
    private val cephStats: CephFsFastDirectoryStats,
) {
    suspend fun retrieve(file: UCloudFile, flags: UFileIncludeFlags): PartialUFile {
        val internalFile = pathConverter.ucloudToInternal(file)
        val nativeStat = nativeFs.stat(internalFile)
        return convertNativeStatToUFile(internalFile, nativeStat)
    }

    private fun findIcon(file: InternalFile): FileIconHint? {
        val components = pathConverter.internalToRelative(file).components()
        return if (fileTrashService.isTrashFolder(null, file)) {
            FileIconHint.DIRECTORY_TRASH
        } else if (
            (components.size == 3 && components[0] == "home" && components[2] == "Jobs") ||
            (components.size == 5 && components[0] == "projects" &&
                components[2] == PERSONAL_REPOSITORY && components[4] == "Jobs")
        ) {
            FileIconHint.DIRECTORY_JOBS
        } else {
            null
        }
    }

    private suspend fun convertNativeStatToUFile(
        file: InternalFile,
        nativeStat: NativeStat,
    ): PartialUFile {
        return PartialUFile(
            pathConverter.internalToUCloud(file).path,
            UFileStatus(
                nativeStat.fileType,
                findIcon(file),
                sizeInBytes = nativeStat.size,
                sizeIncludingChildrenInBytes = runCatching { cephStats.getRecursiveSize(file) }.getOrNull(),
                modifiedAt = nativeStat.modifiedAt,
                unixMode = nativeStat.mode,
                unixOwner = nativeStat.ownerUid,
                unixGroup = nativeStat.ownerGid,
            ),
            nativeStat.modifiedAt,
        )
    }

    suspend fun browseFiles(
        file: UCloudFile,
        flags: UFileIncludeFlags,
        pagination: NormalizedPaginationRequestV2,
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
            val initialState = distributedStateFactory.create<List<String>>(next)
            foundFiles = initialState.get()?.map { InternalFile(it) }
        }

        if (foundFiles == null) {
            val internalFile = pathConverter.ucloudToInternal(file)
            foundFiles = nativeFs.listFiles(internalFile).map {
                InternalFile(internalFile.path + "/" + it)
            }
        }

        val offset = pagination.next?.substringBefore('_')?.toIntOrNull() ?: 0
        if (offset < 0) throw RPCException("Bad next token supplied", HttpStatusCode.BadRequest)
        val items = ArrayList<PartialUFile>()
        var i = offset
        var didSkipFiles = false
        while (i < foundFiles.size && items.size < pagination.itemsPerPage) {
            try {
                val nextInternalFile = foundFiles[i++]
                items.add(
                    convertNativeStatToUFile(
                        nextInternalFile,
                        nativeFs.stat(nextInternalFile),
                    )
                )
            } catch (ex: FSException.NotFound) {
                // NOTE(Dan): File might have gone away between these two calls
                didSkipFiles = true
            } catch (ex: Throwable) {
                ex.printStackTrace()
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

            val state = distributedStateFactory.create<List<String>>(newToken, DIR_CACHE_EXPIRATION)
            state.set(foundFiles.map { it.path })

            newToken
        } else {
            null
        }

        return PageV2(pagination.itemsPerPage, items, newNext)
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
    }
}
