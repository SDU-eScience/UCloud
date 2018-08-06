package dk.sdu.cloud.indexing.services

import dk.sdu.cloud.indexing.api.TimestampQuery
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.storage.api.EventMaterializedStorageFile
import dk.sdu.cloud.storage.api.FileType
import dk.sdu.cloud.storage.api.SensitivityLevel

interface IndexQueryService {
    fun simpleQuery(
        roots: List<String>,
        query: String,
        paging: NormalizedPaginationRequest
    ): Page<EventMaterializedStorageFile>

    fun advancedQuery(
        roots: List<String>,
        name: String?,
        owner: String?,
        extensions: List<String>?,
        fileTypes: List<FileType>?,
        createdAt: TimestampQuery?,
        modifiedAt: TimestampQuery?,
        sensitivity: List<SensitivityLevel>?,
        annotations: List<String>?,
        paging: NormalizedPaginationRequest
    ): Page<EventMaterializedStorageFile>

    fun findFileByIdOrNull(id: String): EventMaterializedStorageFile?
}