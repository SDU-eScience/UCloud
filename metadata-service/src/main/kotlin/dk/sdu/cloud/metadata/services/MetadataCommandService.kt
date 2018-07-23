package dk.sdu.cloud.metadata.services

import dk.sdu.cloud.metadata.api.ProjectMetadata
import dk.sdu.cloud.metadata.api.FileDescriptionForMetadata
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

    fun update(user: String, projectId: Long, metadata: UserEditableProjectMetadata)

    fun addFiles(projectId: Long, files: Set<FileDescriptionForMetadata>)

    /**
     * Removes files from a [ProjectMetadata]
     *
     * If the [files] contains the [ProjectMetadata.sduCloudRoot] then the [ProjectMetadata] should be deleted, see
     * [delete].
     */
    fun removeFilesById(projectId: Long, files: Set<String>)

    /**
     * Update the path of a single file
     *
     * If the path of the file corresponds to the project root, then the [ProjectMetadata.sduCloudRoot] field should
     * also be updated.
     */
    fun updatePathOfFile(projectId: Long, fileId: String, newPath: String)

    fun removeAllFiles(projectId: Long)

    fun canEdit(user: String, projectId: Long): Boolean

    fun delete(user: String, projectId: Long)
}