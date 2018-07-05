package dk.sdu.cloud.storage.services

import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.paginate
import dk.sdu.cloud.storage.api.FileSortBy
import dk.sdu.cloud.storage.api.SortOrder
import dk.sdu.cloud.storage.api.StorageFile
import dk.sdu.cloud.storage.util.FSException
import dk.sdu.cloud.storage.util.fileName
import dk.sdu.cloud.storage.util.normalize
import dk.sdu.cloud.storage.util.parent
import org.slf4j.LoggerFactory

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
    private val coreFs: CoreFileSystemService<Ctx>,
    private val favoriteService: FavoriteService<Ctx>
) {
    fun listDirectory(
        ctx: Ctx,
        path: String,
        pagination: NormalizedPaginationRequest,
        sortBy: FileSortBy = FileSortBy.TYPE,
        order: SortOrder = SortOrder.ASCENDING
    ): Page<StorageFile> {
        return listDirectorySorted(ctx, path, sortBy, order).paginate(pagination)
    }

    private fun listDirectorySorted(
        ctx: Ctx,
        path: String,
        sortBy: FileSortBy,
        order: SortOrder
    ): List<StorageFile> {
        val favorites = favoriteService.retrieveFavoriteInodeSet(ctx)

        val allResults = coreFs.listDirectory(ctx, path, STORAGE_FILE_ATTRIBUTES).map {
            readStorageFile(it, favorites)
        }

        return allResults.let { results ->
            val naturalComparator: Comparator<StorageFile> = when (sortBy) {
                FileSortBy.ACL -> Comparator.comparingInt { it.acl.size }
                FileSortBy.ANNOTATION -> Comparator.comparing<StorageFile, String> {
                    it.annotations.sorted().joinToString("").toLowerCase()
                }
                FileSortBy.CREATED_AT -> Comparator.comparingLong { it.createdAt }
                FileSortBy.MODIFIED_AT -> Comparator.comparingLong { it.modifiedAt }

                FileSortBy.TYPE -> Comparator.comparing<StorageFile, String> {
                    it.type.name
                }.thenComparing(Comparator.comparing<StorageFile, String> {
                    it.path.fileName().toLowerCase()
                })

                FileSortBy.PATH -> Comparator.comparing<StorageFile, String> {
                    it.path.fileName().toLowerCase()
                }
                FileSortBy.SIZE -> Comparator.comparingLong { it.size }
                FileSortBy.FAVORITED -> Comparator.comparing<StorageFile, Boolean> { it.favorited }
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

    private fun readStorageFile(row: FileRow, favorites: Set<String>): StorageFile =
        StorageFile(
            type = row.fileType,
            path = row.path,
            createdAt = row.timestamps.created,
            modifiedAt = row.timestamps.modified,
            ownerName = row.owner,
            size = row.size,
            acl = row.shares,
            favorited = row.inode in favorites,
            sensitivityLevel = row.sensitivityLevel,
            link = row.isLink,
            annotations = row.annotations,
            inode = row.inode
        )

    fun lookupFileInDirectory(
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

    fun stat(
        ctx: Ctx,
        path: String
    ): StorageFile {
        val favorites = favoriteService.retrieveFavoriteInodeSet(ctx)
        return coreFs.stat(ctx, path, STORAGE_FILE_ATTRIBUTES).let { readStorageFile(it, favorites) }
    }

    companion object {
        private val log = LoggerFactory.getLogger(FileLookupService::class.java)

        private val STORAGE_FILE_ATTRIBUTES = setOf(
            FileAttribute.FILE_TYPE,
            FileAttribute.PATH,
            FileAttribute.TIMESTAMPS,
            FileAttribute.OWNER,
            FileAttribute.SIZE,
            FileAttribute.SHARES,
            FileAttribute.SENSITIVITY,
            FileAttribute.ANNOTATIONS,
            FileAttribute.INODE,
            FileAttribute.IS_LINK
        )
    }
}