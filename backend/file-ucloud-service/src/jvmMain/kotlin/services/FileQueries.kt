package dk.sdu.cloud.file.ucloud.services

import dk.sdu.cloud.Actor
import dk.sdu.cloud.PageV2
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.file.orchestrator.api.*
import dk.sdu.cloud.file.ucloud.services.acl.AclService
import dk.sdu.cloud.file.ucloud.services.acl.PERSONAL_REPOSITORY
import dk.sdu.cloud.service.DistributedStateFactory
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.NormalizedPaginationRequestV2
import dk.sdu.cloud.service.create
import io.ktor.http.*
import java.util.*
import java.util.concurrent.atomic.AtomicInteger

class FileQueries(
    private val aclService: AclService,
    private val pathConverter: PathConverter,
    private val distributedStateFactory: DistributedStateFactory,
    private val nativeFs: NativeFS,
    private val fileTrashService: TrashService,
) {
    suspend fun retrieve(actor: Actor, file: UCloudFile, flags: FilesIncludeFlags): UFile {
        val myself = aclService.fetchMyPermissions(actor, file)
        if (!myself.contains(FilePermission.READ)) throw FSException.PermissionException()

        val internalFile = pathConverter.ucloudToInternal(file)
        val nativeStat = nativeFs.stat(internalFile)
        return convertNativeStatToUFile(internalFile, nativeStat, myself)
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
        myself: Set<FilePermission>,
    ): UFile {
        return UFile(
            pathConverter.internalToUCloud(file).path,
            nativeStat.fileType,
            findIcon(file),
            UFile.Stats(
                nativeStat.size,
                null, // TODO
                nativeStat.modifiedAt,
                null, // TODO Not supported
                nativeStat.modifiedAt,
                nativeStat.mode,
                nativeStat.ownerUid,
                nativeStat.ownerGid
            ),
            UFile.Permissions(
                myself.toList(),
                aclService.fetchOtherPermissions(pathConverter.internalToUCloud(file))
            ),
            null
        )
    }

    suspend fun browseFiles(
        actor: Actor,
        file: UCloudFile,
        flags: FilesIncludeFlags,
        pagination: NormalizedPaginationRequestV2,
        sortBy: FilesSortBy?,
        sortOrder: SortOrder?,
    ): PageV2<UFile> {
        // NOTE(Dan): The next token consists of two parts. These two parts are separated by a single underscore:
        //
        // 1. The first part contains the current offset in the list. This allows a user to restart the search.
        // 2. The second part contains a unique ID.
        //
        // This token is used for storing state in Redis which contains a complete list of files found in this
        // directory. This allows the user to search a consistent snapshot of the files. If any files are removed
        // between calls then this endpoint will simply skip it.

        val myself = aclService.fetchMyPermissions(actor, file)
        if (!myself.contains(FilePermission.READ)) throw FSException.PermissionException()

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

        // STUPID NAÏVE APPROACH
        when (sortBy) {
            FilesSortBy.CREATED_AT, FilesSortBy.MODIFIED_AT, FilesSortBy.SIZE -> {
                foundFiles = if (sortOrder == SortOrder.ASCENDING) {
                    foundFiles.sortedWith { a, b ->
                        val statA = nativeFs.stat(a)
                        val statB = nativeFs.stat(b)
                        when (sortBy) {
                            FilesSortBy.SIZE -> (statA.size - statB.size).toInt()
                            FilesSortBy.CREATED_AT -> TODO()
                            FilesSortBy.MODIFIED_AT -> (statA.modifiedAt - statB.modifiedAt).toInt()
                            else -> 0
                        }
                    }
                } else {
                    foundFiles.sortedWith { b, a ->
                        val statA = nativeFs.stat(a)
                        val statB = nativeFs.stat(b)
                        when (sortBy) {
                            FilesSortBy.SIZE -> (statA.size - statB.size).toInt()
                            FilesSortBy.CREATED_AT -> 0 // TODO(jonas)
                            FilesSortBy.MODIFIED_AT -> (statA.modifiedAt - statB.modifiedAt).toInt()
                            else -> 0
                        }
                    }
                }
            }
            FilesSortBy.PATH ->
                foundFiles = if (sortOrder == SortOrder.ASCENDING) {
                    foundFiles.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER, InternalFile::path))
                } else {
                    foundFiles.sortedWith(compareByDescending(String.CASE_INSENSITIVE_ORDER, InternalFile::path))
                }
        }

        val offset = pagination.next?.substringBefore('_')?.toIntOrNull() ?: 0
        if (offset < 0) throw RPCException("Bad next token supplied", HttpStatusCode.BadRequest)
        val items = ArrayList<UFile>()
        var i = offset
        var didSkipFiles = false
        while (i < foundFiles.size && items.size < pagination.itemsPerPage) {
            try {
                val nextInternalFile = foundFiles[i++]
                items.add(
                    convertNativeStatToUFile(
                        nextInternalFile,
                        nativeFs.stat(nextInternalFile),
                        myself // NOTE(Dan): This is always true for all parts of the UCloud/Storage system
                    )
                )
            } catch (ex: FSException.NotFound) {
                // NOTE(Dan): File might have gone away between these two calls
                didSkipFiles = true
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
