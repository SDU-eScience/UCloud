package dk.sdu.cloud.file.stats.services

import dk.sdu.cloud.client.AuthenticatedCloud
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
import dk.sdu.cloud.service.optionallyCausedBy
import dk.sdu.cloud.service.orThrow

class RecentFilesService(
    private val serviceCloud: AuthenticatedCloud
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
                )
            ),
            serviceCloud.optionallyCausedBy(causedById)
        ).orThrow()

        return result.items
    }
}
