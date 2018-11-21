package dk.sdu.cloud.indexing.services

import dk.sdu.cloud.file.api.EventMaterializedStorageFile
import dk.sdu.cloud.indexing.api.FileQuery
import dk.sdu.cloud.indexing.api.SortRequest
import dk.sdu.cloud.indexing.api.StatisticsRequest
import dk.sdu.cloud.indexing.api.StatisticsResponse
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page

/**
 * Provides query operations of the file index.
 */
interface IndexQueryService {
    fun findFileByIdOrNull(id: String): EventMaterializedStorageFile?

    fun query(
        query: FileQuery,
        paging: NormalizedPaginationRequest,
        sorting: SortRequest? = null
    ): Page<EventMaterializedStorageFile>

    fun statisticsQuery(statisticsRequest: StatisticsRequest): StatisticsResponse
}
