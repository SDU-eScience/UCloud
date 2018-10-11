package dk.sdu.cloud.indexing.services

interface ReverseLookupService {
    fun reverseLookup(fileId: String): String

    fun reverseLookupBatch(fileIds: List<String>): List<String?> {
        return fileIds.map { reverseLookup(it) }
    }
}
