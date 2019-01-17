package dk.sdu.cloud.metadata.services

import dk.sdu.cloud.metadata.api.ProjectMetadata
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page

/**
 * Represents an external metadata service.
 *
 * This interface only describes the command side of the metadata service (i.e. the write operations)
 *
 * The interface only contains the most simple query operations, that all metadata services are expected
 * to implement.
 */
interface MetadataQueryService {
    fun getById(user: String, id: String): ProjectMetadata?
}

/**
 * Represents an external metadata service.
 *
 * This interface describes the query side (i.e.
 */
interface MetadataAdvancedQueryService {
    fun simpleQuery(user: String, query: String, paging: NormalizedPaginationRequest): Page<ProjectMetadata>
}
