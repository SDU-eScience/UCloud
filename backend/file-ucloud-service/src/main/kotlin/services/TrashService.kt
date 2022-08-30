package dk.sdu.cloud.file.ucloud.services

import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.file.orchestrator.api.joinPath

class TrashService(
    private val pathConverter: PathConverter,
) {
    suspend fun findTrashDirectory(username: String, targetPath: InternalFile): InternalFile {
        if (username.contains("..") || username.contains("/")) {
            throw RPCException("Bad username", HttpStatusCode.BadRequest)
        }

        val project = pathConverter.fetchProject(pathConverter.internalToUCloud(targetPath))
        val relativeFile = pathConverter.internalToRelative(targetPath)
        val components = relativeFile.components()
        return when {
            project != null -> {
                pathConverter.relativeToInternal(
                    RelativeInternalFile(
                        joinPath(
                            PathConverter.PROJECT_DIRECTORY,
                            project,
                            PERSONAL_REPOSITORY,
                            username,
                            TRASH_FOLDER
                        ).removeSuffix("/")
                    )
                )
            }

            components[0] == PathConverter.HOME_DIRECTORY -> {
                pathConverter.relativeToInternal(
                    RelativeInternalFile("/${PathConverter.HOME_DIRECTORY}/$username/$TRASH_FOLDER")
                )
            }

            components[0] == PathConverter.COLLECTION_DIRECTORY -> {
                if (components.size < 2) throw FSException.CriticalException("Unable to delete this file: $targetPath")

                pathConverter.relativeToInternal(
                    RelativeInternalFile("/${PathConverter.COLLECTION_DIRECTORY}/${components[1]}/${TRASH_FOLDER}")
                )
            }

            else -> {
                throw FSException.CriticalException("Unable to delete this file: $targetPath")
            }
        }
    }

    fun isTrashFolder(file: InternalFile): Boolean {
        val components = pathConverter.internalToRelative(file).components()
        if (components.size < 2) return false
        return when {
            components[0] == PathConverter.HOME_DIRECTORY -> components.size == 3 && components[2] == TRASH_FOLDER
            components[0] == PathConverter.PROJECT_DIRECTORY -> components.size == 5 && components[4] == TRASH_FOLDER
            components[0] == PathConverter.COLLECTION_DIRECTORY -> components.size == 3 && components[2] == TRASH_FOLDER
            else -> false
        }
    }

    companion object {
        const val TRASH_FOLDER = "Trash"
    }
}
