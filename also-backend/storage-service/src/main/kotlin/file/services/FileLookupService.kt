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
        pagination: NormalizedPaginationRequest?,
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
        val nativeAttributes = addSortingAttributes(attributes, sortBy)

        return coreFs
            .listDirectorySorted(
                ctx,
                path,
                nativeAttributes,
                sortBy,
                order,
                pagination,
                type
            ).mapItemsNotNull { readStorageFile(it, nativeAttributes.toList()) }
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

    private fun addSortingAttributes(
        attributes: List<StorageFileAttribute>,
        sortBy: FileSortBy? = null
    ): HashSet<StorageFileAttribute> {
        val result = HashSet<StorageFileAttribute>()
        result.addAll(attributes)

        result.addAll(
            when (sortBy) {
                FileSortBy.TYPE -> listOf(StorageFileAttribute.fileType, StorageFileAttribute.path)
                FileSortBy.PATH -> listOf(StorageFileAttribute.path)
                FileSortBy.CREATED_AT -> listOf(StorageFileAttribute.createdAt)
                FileSortBy.MODIFIED_AT -> listOf(StorageFileAttribute.modifiedAt)
                FileSortBy.SIZE -> listOf(StorageFileAttribute.size)
                FileSortBy.SENSITIVITY -> listOf(StorageFileAttribute.sensitivityLevel, StorageFileAttribute.path)
                null -> emptyList()
            }
        )

        return result
    }

    // Note: We run this lookup as SERVICE_USER to avoid a lot of ACL checks. At this point we should already have
    // performed an access check. This will also ensure that we can read the correct sensitivity of files.
    private suspend fun lookupInheritedSensitivity(realPath: String): SensitivityLevel {
        val cache = HashMap<String, SensitivityLevel>()
        return commandRunnerFactory.withContext(SERVICE_USER) { ctx ->
            lookupInheritedSensitivity(ctx, realPath, cache)
        }
    }

    suspend fun lookupInheritedSensitivity(
        ctx: Ctx,
        cloudPath: String,
        cache: MutableMap<String, SensitivityLevel>
    ): SensitivityLevel {
        val cached = cache[cloudPath]
        if (cached != null) return cached
        if (cloudPath.components().size < 2) return SensitivityLevel.PRIVATE

        val components = cloudPath.normalize().components()
        val sensitivity = if (components.size == 2 && components[0] == "home") {
            SensitivityLevel.PRIVATE
        } else {
            run {
                val stat = coreFs.stat(
                    ctx,
                    cloudPath,
                    setOf(
                        StorageFileAttribute.path,
                        StorageFileAttribute.sensitivityLevel
                    )
                )

                stat.ownSensitivityLevelOrNull ?: lookupInheritedSensitivity(stat.path.parent())
            }
        }

        cache[cloudPath] = sensitivity
        return sensitivity
    }


    private suspend fun readStorageFile(
        row: StorageFile,
        attributes: List<StorageFileAttribute>
    ): StorageFile? {
        val owner = row.ownerNameOrNull?.takeIf { it.isNotBlank() }

        if (StorageFileAttribute.ownerName in attributes && owner == null) {
            return null
        }

        return StorageFileImpl(
            fileTypeOrNull = row.fileTypeOrNull,
            pathOrNull = row.pathOrNull,
            createdAtOrNull = row.createdAtOrNull,
            modifiedAtOrNull = row.modifiedAtOrNull,
            ownerNameOrNull = owner,
            sizeOrNull = row.sizeOrNull,
            aclOrNull = row.aclOrNull,
            sensitivityLevelOrNull = run {
                if (StorageFileAttribute.sensitivityLevel in attributes) {
                    row.ownSensitivityLevelOrNull ?: lookupInheritedSensitivity(row.path.parent())
                } else {
                    null
                }
            },
            ownSensitivityLevelOrNull = row.ownSensitivityLevelOrNull,
            permissionAlert = row.permissionAlert
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
        val mode = addSortingAttributes(attributes)
        return readStorageFile(
            coreFs.stat(ctx, path, mode),
            mode.toList()
        ) ?: throw FSException.NotFound()
    }

    companion object : Loggable {
        override val log = logger()
    }
}
