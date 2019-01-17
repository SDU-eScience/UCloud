package dk.sdu.cloud.metadata.services

import dk.sdu.cloud.metadata.api.ProjectMetadata
import dk.sdu.cloud.metadata.api.UserEditableProjectMetadata

sealed class MetadataException : RuntimeException() {
    class NotFound : MetadataException()
    class Duplicate : MetadataException()
    class NotAllowed : MetadataException()
}

/**
 * Represents an external metadata service.
 *
 * This interface only describes the command side of the metadata service (i.e. the write operations)
 */
interface MetadataCommandService {
    fun create(metadata: ProjectMetadata)

    fun update(user: String, projectId: String, metadata: UserEditableProjectMetadata)

    suspend fun canEdit(user: String, projectId: String): Boolean

    suspend fun delete(user: String, projectId: String)
}
