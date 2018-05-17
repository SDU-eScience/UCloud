package dk.sdu.cloud.metadata.services

import dk.sdu.cloud.metadata.api.ProjectMetadata
import dk.sdu.cloud.metadata.api.FileDescriptionForMetadata
import dk.sdu.cloud.metadata.api.UserEditableProjectMetadata

sealed class MetadataException : RuntimeException() {
    class NotFound : MetadataException()
}

/**
 * Represents an external metadata service.
 *
 * This interface only describes the command side of the metadata service (i.e. the write operations)
 */
interface MetadataCommandService {
    fun create(metadata: ProjectMetadata)
    fun update(projectId: String, metadata: UserEditableProjectMetadata)

    fun addFiles(projectId: String, files: Set<FileDescriptionForMetadata>)

    fun removeFilesById(projectId: String, files: Set<String>)

    fun updatePathOfFile(projectId: String, fileId: String, newPath: String)

    fun removeAllFiles(projectId: String)
}