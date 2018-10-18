package dk.sdu.cloud.indexing.services

/**
 * Provides reverse lookups of files (fileId -> canonical file path)
 */
interface ReverseLookupService {
    /**
     * Looks up a single file based on ID
     */
    fun reverseLookup(fileId: String): String

    /**
     * Looks up multiple files based on ID.
     *
     * The returned list will contain the same amount of elements as [fileIds] and will be in the same order. If an
     * element could not be found `null` is returned.
     */
    fun reverseLookupBatch(fileIds: List<String>): List<String?> {
        return fileIds.map { reverseLookup(it) }
    }
}
