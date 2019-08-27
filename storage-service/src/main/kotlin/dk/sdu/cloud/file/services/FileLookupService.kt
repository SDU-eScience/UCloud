package dk.sdu.cloud.file.services

import dk.sdu.cloud.file.SERVICE_USER
import dk.sdu.cloud.file.api.*
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
    private val commandRunnerFactory: FSCommandRunnerFactory<Ctx>,
    private val coreFs: CoreFileSystemService<Ctx>
) {
    suspend fun listDirectory(
        ctx: Ctx,
        path: String,
        pagination: NormalizedPaginationRequest,
        sortBy: FileSortBy = FileSortBy.TYPE,
        order: SortOrder = SortOrder.ASCENDING,
        attributes: List<StorageFileAttribute> = StorageFileAttribute.values().toList(),
        type: FileType? = null
    ): Page<StorageFile> {
        return listDirectorySorted(ctx, path, sortBy, order, attributes, pagination, type)
    }

    private suspend fun listDirectorySorted(
        ctx: Ctx,
        path: String,
        sortBy: FileSortBy,
        order: SortOrder,
        attributes: List<StorageFileAttribute>,
        pagination: NormalizedPaginationRequest? = null,
        type: FileType? = null
    ): Page<StorageFile> {
        val nativeAttributes = translateToNativeAttributes(attributes, sortBy)

        val cache = HashMap<String, SensitivityLevel>()

        return coreFs.listDirectorySorted(
            ctx, path, nativeAttributes, sortBy, order, pagination, type
        ).mapItemsNotNull {
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
                    StorageFileAttribute.path -> listOf(FileAttribute.PATH)
                    StorageFileAttribute.createdAt -> listOf(FileAttribute.TIMESTAMPS)
                    StorageFileAttribute.modifiedAt -> listOf(FileAttribute.TIMESTAMPS)
                    StorageFileAttribute.ownerName -> listOf(FileAttribute.OWNER, FileAttribute.CREATOR)
                    StorageFileAttribute.size -> listOf(FileAttribute.SIZE)
                    StorageFileAttribute.acl -> listOf(FileAttribute.SHARES)
                    StorageFileAttribute.sensitivityLevel -> listOf(
                        FileAttribute.SENSITIVITY,
                        FileAttribute.PATH
                    )
                    StorageFileAttribute.ownSensitivityLevel -> listOf(FileAttribute.SENSITIVITY)
                    StorageFileAttribute.fileId -> listOf(FileAttribute.INODE)
                    StorageFileAttribute.creator -> listOf(FileAttribute.CREATOR)
                    StorageFileAttribute.canonicalPath -> listOf(FileAttribute.PATH)
                }
            )
        }

        result.addAll(
            when (sortBy) {
                FileSortBy.TYPE -> listOf(FileAttribute.FILE_TYPE, FileAttribute.PATH)
                FileSortBy.PATH -> listOf(FileAttribute.PATH)
                FileSortBy.CREATED_AT -> listOf(FileAttribute.TIMESTAMPS)
                FileSortBy.MODIFIED_AT -> listOf(FileAttribute.TIMESTAMPS)
                FileSortBy.SIZE -> listOf(FileAttribute.SIZE)
//                FileSortBy.ACL -> listOf(FileAttribute.SHARES)
                FileSortBy.SENSITIVITY -> listOf(FileAttribute.SENSITIVITY, FileAttribute.PATH)
                null -> emptyList()
            }
        )

        return result
    }

    // Note: We run this lookup as SERVICE_USER to avoid a lot of ACL checks. At this point we should already have
    // performed an access check. This will also ensure that we can read the correct sensitivity of files.
    private suspend fun lookupInheritedSensitivity(realPath: String): SensitivityLevel {
        val cache = HashMap<String, SensitivityLevel>()

        suspend fun lookupInheritedSensitivity(ctx: Ctx): SensitivityLevel {
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
                            FileAttribute.SENSITIVITY
                        )
                    )

                    stat.sensitivityLevel ?: lookupInheritedSensitivity(stat.path.parent())
                }
            }

            cache[realPath] = sensitivity
            return sensitivity
        }

        return commandRunnerFactory.withContext(SERVICE_USER) { ctx ->
            lookupInheritedSensitivity(ctx)
        }
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
            pathOrNull = row._path,
            createdAtOrNull = row._timestamps?.created,
            modifiedAtOrNull = row._timestamps?.modified,
            ownerNameOrNull = owner,
            sizeOrNull = row._size,
            aclOrNull = row._shares,
            sensitivityLevelOrNull = run {
                if (FileAttribute.SENSITIVITY in attributes) {
                    row.sensitivityLevel ?: lookupInheritedSensitivity(row.path.parent())
                } else {
                    null
                }
            },
            ownSensitivityLevelOrNull = row._sensitivityLevel,
            fileIdOrNull = row._inode,
            creatorOrNull = creator
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
