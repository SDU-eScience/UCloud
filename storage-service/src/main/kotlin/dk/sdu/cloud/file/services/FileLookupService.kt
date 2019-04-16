package dk.sdu.cloud.file.services

import dk.sdu.cloud.file.api.FileSortBy
import dk.sdu.cloud.file.api.SensitivityLevel
import dk.sdu.cloud.file.api.SortOrder
import dk.sdu.cloud.file.api.StorageFile
import dk.sdu.cloud.file.api.components
import dk.sdu.cloud.file.api.fileName
import dk.sdu.cloud.file.api.normalize
import dk.sdu.cloud.file.api.parent
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
        order: SortOrder = SortOrder.ASCENDING
    ): Page<StorageFile> {
        return listDirectorySorted(ctx, path, sortBy, order).paginate(pagination)
    }

    private suspend fun listDirectorySorted(
        ctx: Ctx,
        path: String,
        sortBy: FileSortBy,
        order: SortOrder
    ): List<StorageFile> {
        val cache = HashMap<String, SensitivityLevel>()
        val allResults = coreFs.listDirectory(
            ctx, path,
            STORAGE_FILE_ATTRIBUTES
        ).mapNotNull {
            readStorageFile(ctx, it, cache)
        }

        return allResults.let { results ->
            val naturalComparator: Comparator<StorageFile> = when (sortBy) {
                FileSortBy.ACL -> Comparator.comparingInt { it.acl?.size ?: 0 }
                FileSortBy.ANNOTATION -> Comparator.comparing<StorageFile, String> {
                    it.annotations.sorted().joinToString("").toLowerCase()
                }
                FileSortBy.CREATED_AT -> Comparator.comparingLong { it.createdAt }
                FileSortBy.MODIFIED_AT -> Comparator.comparingLong { it.modifiedAt }

                FileSortBy.TYPE -> Comparator.comparing<StorageFile, String> {
                    it.fileType.name
                }.thenComparing(Comparator.comparing<StorageFile, String> {
                    it.path.fileName().toLowerCase()
                })

                FileSortBy.PATH -> Comparator.comparing<StorageFile, String> {
                    it.path.fileName().toLowerCase()
                }
                FileSortBy.SIZE -> Comparator.comparingLong { it.size }
                FileSortBy.SENSITIVITY -> Comparator.comparing<StorageFile, String> {
                    it.sensitivityLevel.name.toLowerCase()
                }
            }

            val comparator = when (order) {
                SortOrder.ASCENDING -> naturalComparator
                SortOrder.DESCENDING -> naturalComparator.reversed()
            }

            results.sortedWith(comparator)
        }
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
        cache: MutableMap<String, SensitivityLevel> = HashMap()
    ): StorageFile? {
        val owner = row._xowner?.takeIf { it.isNotBlank() } ?: row._owner
        val creator = row._owner
        if (owner == null || creator == null) return null

        return StorageFile(
            fileType = row.fileType,
            path = row.rawPath,
            createdAt = row.timestamps.created,
            modifiedAt = row.timestamps.modified,
            ownerName = owner,
            size = row.size,
            acl = row.shares,
            sensitivityLevel = run {
                log.info("SENSITIVITY BASED ON THIS: " + row.toString())
                if (row.isLink) lookupInheritedSensitivity(ctx, row.path, cache)
                else row.sensitivityLevel ?: lookupInheritedSensitivity(ctx, row.path.parent(), cache)
            },
            ownSensitivityLevel = row.sensitivityLevel,
            link = row.isLink,
            fileId = row.inode,
            creator = creator
        )
    }

    suspend fun lookupFileInDirectory(
        ctx: Ctx,
        path: String,
        itemsPerPage: Int,
        sortBy: FileSortBy,
        order: SortOrder
    ): Page<StorageFile> {
        val normalizedItemsPerPage = NormalizedPaginationRequest(itemsPerPage, 0).itemsPerPage

        val allFiles = listDirectorySorted(ctx, path.parent(), sortBy, order)
        val index = allFiles.indexOfFirst { it.path.normalize() == path.normalize() }
        if (index == -1) throw FSException.NotFound()

        val page = index / normalizedItemsPerPage
        return allFiles.paginate(NormalizedPaginationRequest(normalizedItemsPerPage, page))
    }

    suspend fun stat(
        ctx: Ctx,
        path: String
    ): StorageFile {
        return readStorageFile(ctx, coreFs.stat(ctx, path, STORAGE_FILE_ATTRIBUTES)) ?: throw FSException.NotFound()
    }

    companion object : Loggable {
        override val log = logger()

        @Suppress("ObjectPropertyNaming")
        private val STORAGE_FILE_ATTRIBUTES = setOf(
            FileAttribute.FILE_TYPE,
            FileAttribute.RAW_PATH,
            FileAttribute.TIMESTAMPS,
            FileAttribute.OWNER,
            FileAttribute.SIZE,
            FileAttribute.SHARES,
            FileAttribute.SENSITIVITY,
            FileAttribute.INODE,
            FileAttribute.IS_LINK,
            FileAttribute.XOWNER
        )
    }
}
