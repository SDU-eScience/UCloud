package dk.sdu.cloud.file.services

import dk.sdu.cloud.file.api.FileSortBy
import dk.sdu.cloud.file.api.SensitivityLevel
import dk.sdu.cloud.file.api.SortOrder
import dk.sdu.cloud.file.api.StorageFile
import dk.sdu.cloud.file.api.StorageFileAttribute
import dk.sdu.cloud.file.api.StorageFileImpl
import dk.sdu.cloud.file.api.components
import dk.sdu.cloud.file.api.fileName
import dk.sdu.cloud.file.api.normalize
import dk.sdu.cloud.file.api.parent
import dk.sdu.cloud.file.api.path
import dk.sdu.cloud.file.util.FSException
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.paginate

/**
 * A service for looking up files.
 *
 * This service expands on the functionality provided by the likes of [CoreFileSystemService.listDirectory].
 * This includes features such as:
 *
 * - Lookup file in directory
 * - Sorting of files
 * - Pagination
 * - Mapping to public API format (i.e. [StorageFile])
 *
 * All service methods in this class can throw exceptions of type [FSException].
 */
class FileLookupService<Ctx : FSUserContext>(
    private val coreFs: CoreFileSystemService<Ctx>
) {
    suspend fun listDirectory(
        ctx: Ctx,
        path: String,
        pagination: NormalizedPaginationRequest,
        sortBy: FileSortBy = FileSortBy.TYPE,
        order: SortOrder = SortOrder.ASCENDING,
        attributes: List<StorageFileAttribute> = StorageFileAttribute.values().toList()
    ): Page<StorageFile> {
        return listDirectorySorted(ctx, path, sortBy, order, attributes, pagination)
    }

    private suspend fun listDirectorySorted(
        ctx: Ctx,
        path: String,
        sortBy: FileSortBy,
        order: SortOrder,
        attributes: List<StorageFileAttribute>,
        pagination: NormalizedPaginationRequest? = null
    ): Page<StorageFile> {
        val nativeAttributes = translateToNativeAttributes(attributes, sortBy)

        val cache = HashMap<String, SensitivityLevel>()

        return coreFs.listDirectorySorted(ctx, path, nativeAttributes, sortBy, order, pagination).mapItemsNotNull {
            readStorageFile(ctx, it, cache, nativeAttributes.toList())
        }
    }

    private inline fun <T, R : Any> Page<T>.mapItemsNotNull(mapper: (T) -> R?): Page<R> {
        val newItems = items.mapNotNull(mapper)
        return Page(
            itemsInTotal,
            itemsPerPage,
            pageNumber,
            newItems
        )
    }

    private fun translateToNativeAttributes(
        attributes: List<StorageFileAttribute>,
        sortBy: FileSortBy? = null
    ): HashSet<FileAttribute> {
        val result = HashSet<FileAttribute>()
        for (attrib in attributes) {
            result.addAll(
                when (attrib) {
                    StorageFileAttribute.fileType -> listOf(FileAttribute.FILE_TYPE)
                    StorageFileAttribute.path -> listOf(FileAttribute.RAW_PATH)
                    StorageFileAttribute.createdAt -> listOf(FileAttribute.TIMESTAMPS)
                    StorageFileAttribute.modifiedAt -> listOf(FileAttribute.TIMESTAMPS)
                    StorageFileAttribute.ownerName -> listOf(FileAttribute.OWNER, FileAttribute.CREATOR)
                    StorageFileAttribute.size -> listOf(FileAttribute.SIZE)
                    StorageFileAttribute.acl -> listOf(FileAttribute.SHARES)
                    StorageFileAttribute.sensitivityLevel -> listOf(
                        FileAttribute.SENSITIVITY,
                        FileAttribute.IS_LINK,
                        FileAttribute.LINK_TARGET,
                        FileAttribute.PATH
                    )
                    StorageFileAttribute.ownSensitivityLevel -> listOf(FileAttribute.SENSITIVITY)
                    StorageFileAttribute.link -> listOf(FileAttribute.IS_LINK)
                    StorageFileAttribute.fileId -> listOf(FileAttribute.INODE)
                    StorageFileAttribute.creator -> listOf(FileAttribute.CREATOR)
                    StorageFileAttribute.canonicalPath -> listOf(FileAttribute.PATH)
                }
            )
        }

        result.addAll(
            when (sortBy) {
                FileSortBy.TYPE -> listOf(FileAttribute.FILE_TYPE, FileAttribute.RAW_PATH)
                FileSortBy.PATH -> listOf(FileAttribute.RAW_PATH)
                FileSortBy.CREATED_AT -> listOf(FileAttribute.TIMESTAMPS)
                FileSortBy.MODIFIED_AT -> listOf(FileAttribute.TIMESTAMPS)
                FileSortBy.SIZE -> listOf(FileAttribute.SIZE)
                FileSortBy.ACL -> listOf(FileAttribute.SHARES)
                FileSortBy.SENSITIVITY -> listOf(FileAttribute.SENSITIVITY, FileAttribute.PATH)
                null -> emptyList()
            }
        )

        return result
    }

    private suspend fun lookupInheritedSensitivity(
        ctx: Ctx,
        realPath: String,
        cache: MutableMap<String, SensitivityLevel>
    ): SensitivityLevel {
        val cached = cache[realPath]
        if (cached != null) return cached
        if (realPath.components().size < 2) return SensitivityLevel.PRIVATE

        val components = realPath.normalize().components()
        val sensitivity = if (components.size == 2 && components[0] == "home") {
            SensitivityLevel.PRIVATE
        } else {
            run {
                val stat = coreFs.stat(
                    ctx,
                    realPath,
                    setOf(
                        FileAttribute.PATH,
                        FileAttribute.SENSITIVITY,
                        FileAttribute.IS_LINK,
                        FileAttribute.LINK_TARGET
                    )
                )
                if (stat.isLink) {
                    log.info("linkTarget is ${stat.linkTarget}")
                    lookupInheritedSensitivity(ctx, stat.linkTarget, cache)
                } else {
                    stat.sensitivityLevel ?: lookupInheritedSensitivity(ctx, stat.path.parent(), cache)
                }
            }
        }

        cache[realPath] = sensitivity
        return sensitivity
    }

    private suspend fun readStorageFile(
        ctx: Ctx,
        row: FileRow,
        cache: MutableMap<String, SensitivityLevel>,
        attributes: List<FileAttribute>
    ): StorageFile? {
        val owner = row._owner?.takeIf { it.isNotBlank() } ?: row._creator
        val creator = row._creator

        if (FileAttribute.OWNER in attributes && owner == null) {
            return null
        }
        if (FileAttribute.CREATOR in attributes && creator == null) {
            return null
        }

        return StorageFileImpl(
            fileTypeOrNull = row._fileType,
            pathOrNull = row._rawPath,
            createdAtOrNull = row._timestamps?.created,
            modifiedAtOrNull = row._timestamps?.modified,
            ownerNameOrNull = owner,
            sizeOrNull = row._size,
            aclOrNull = row._shares,
            sensitivityLevelOrNull = run {
                if (FileAttribute.SENSITIVITY in attributes) {
                    if (row.isLink) {
                        try {
                            lookupInheritedSensitivity(ctx, row.path, cache)
                        } catch (ex: FSException.PermissionException) {
                            // This might fail since the link could point to a target we do not have permissions for.
                            // In this case we will simply return no sensitivity information about it.
                            row.sensitivityLevel ?: SensitivityLevel.PRIVATE
                        }
                    }
                    else row.sensitivityLevel ?: lookupInheritedSensitivity(ctx, row.path.parent(), cache)
                } else {
                    null
                }
            },
            ownSensitivityLevelOrNull = row._sensitivityLevel,
            linkOrNull = row._isLink,
            fileIdOrNull = row._inode,
            creatorOrNull = creator,
            canonicalPathOrNull = run {
                if (row.isLink) row._linkTarget
                else row._rawPath
            }
        )
    }

    suspend fun lookupFileInDirectory(
        ctx: Ctx,
        path: String,
        itemsPerPage: Int,
        sortBy: FileSortBy,
        order: SortOrder,
        attributes: List<StorageFileAttribute> = StorageFileAttribute.values().toList()
    ): Page<StorageFile> {
        val normalizedItemsPerPage = NormalizedPaginationRequest(itemsPerPage, 0).itemsPerPage

        val allFiles = listDirectorySorted(ctx, path.parent(), sortBy, order, attributes).items
        println(allFiles)
        // The file isn't found because we resolve the symlink in the returned path. We should just look for the file
        // name
        val index = allFiles.indexOfFirst { it.path.fileName() == path.fileName() }
        if (index == -1) throw FSException.NotFound()

        val page = index / normalizedItemsPerPage
        return allFiles.paginate(NormalizedPaginationRequest(normalizedItemsPerPage, page))
    }

    suspend fun stat(
        ctx: Ctx,
        path: String,
        attributes: List<StorageFileAttribute> = StorageFileAttribute.values().toList()
    ): StorageFile {
        val mode = translateToNativeAttributes(attributes)
        return readStorageFile(
            ctx,
            coreFs.stat(ctx, path, mode),
            HashMap(),
            mode.toList()
        ) ?: throw FSException.NotFound()
    }

    companion object : Loggable {
        override val log = logger()
    }
}
