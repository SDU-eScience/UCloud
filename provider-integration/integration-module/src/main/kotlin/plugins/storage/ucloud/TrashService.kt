package dk.sdu.cloud.plugins.storage.ucloud

import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.plugins.InternalFile
import dk.sdu.cloud.plugins.UCloudFile
import dk.sdu.cloud.plugins.fileName

class TrashService(
    private val pathConverter: PathConverter,
) {
    suspend fun findTrashDirectory(username: String, targetPath: InternalFile): InternalFile {
        if (username.contains("..") || username.contains("/")) {
            throw RPCException("Bad username", HttpStatusCode.BadRequest)
        }

        val (targetDrive) = pathConverter.locator.resolveDriveByInternalFile(targetPath)
        val project = targetDrive.project

        val (homeDrive) = pathConverter.locator.resolveDriveByProviderId(
            if (project == null) {
                UCloudDrive.PersonalWorkspace(UCloudDrive.PLACEHOLDER_ID, username)
            } else {
                UCloudDrive.ProjectMemberFiles(UCloudDrive.PLACEHOLDER_ID, project, username)
            }
        )

        return pathConverter.ucloudToInternal(
            UCloudFile.createFromPreNormalizedString("/${homeDrive.ucloudId}/$TRASH_FOLDER")
        )
    }

    fun isTrashFolder(file: InternalFile): Boolean {
        return file.fileName() == TRASH_FOLDER
    }

    companion object {
        const val TRASH_FOLDER = "Trash"
    }
}
