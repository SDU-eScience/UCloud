package dk.sdu.cloud.file.stats.services

import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.file.api.FileDescriptions
import dk.sdu.cloud.file.api.FindHomeFolderRequest
import dk.sdu.cloud.file.stats.api.SearchResult
import dk.sdu.cloud.indexing.api.AllOf
import dk.sdu.cloud.indexing.api.FileQuery
import dk.sdu.cloud.indexing.api.QueryDescriptions
import dk.sdu.cloud.indexing.api.QueryRequest
import dk.sdu.cloud.indexing.api.SortDirection
import dk.sdu.cloud.indexing.api.SortRequest
import dk.sdu.cloud.indexing.api.SortableField

class RecentFilesService(
    private val serviceCloud: AuthenticatedClient
) {
    suspend fun queryRecentFiles(
        username: String,
        causedById: String? = null
    ): List<SearchResult> {
        val homeDir = FileDescriptions.findHomeFolder.call(
            FindHomeFolderRequest(username),
            serviceCloud
        ).orThrow().path
        val result = QueryDescriptions.query.call(
            QueryRequest(
                query = FileQuery(
                    roots = listOf(homeDir),
                    owner = AllOf.with(username)
                ),
                sortBy = SortRequest(
                    field = SortableField.MODIFIED_AT,
                    direction = SortDirection.DESCENDING
                ),
                itemsPerPage = 10
            ),
            serviceCloud//.optionallyCausedBy(causedById)
        ).orThrow()

        return result.items
    }
}
