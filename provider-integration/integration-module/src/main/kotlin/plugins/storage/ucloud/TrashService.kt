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
        val project = pathConverter.findProjectOwner(targetDrive.ucloudId.toString())

        val systemAndDrive = try {
            pathConverter.locator.resolveDriveByProviderId(
                if (project == null) {
                    UCloudDrive.PersonalWorkspace(UCloudDrive.PLACEHOLDER_ID, username)
                } else {
                    UCloudDrive.ProjectMemberFiles(UCloudDrive.PLACEHOLDER_ID, project, username)
                }
            )
        } catch (ex: RPCException) {
            if (ex.httpStatusCode == HttpStatusCode.NotFound) {
                null
            } else {
                throw ex
            }
        }

        val homeDrive = systemAndDrive?.drive
        return if (homeDrive != null && !systemAndDrive.inMaintenanceMode) {
            pathConverter.ucloudToInternal(
                UCloudFile.createFromPreNormalizedString("/${homeDrive.ucloudId}/$TRASH_FOLDER")
            )
        } else {
            // Fallback to storing it in this drive (which presumably is not in maintenance mode)
            pathConverter.ucloudToInternal(
                UCloudFile.createFromPreNormalizedString("/${targetDrive.ucloudId}/$TRASH_FOLDER")
            )
        }
    }

    fun isTrashFolder(file: InternalFile): Boolean {
        return file.fileName() == TRASH_FOLDER
    }

    companion object {
        const val TRASH_FOLDER = "Trash"
    }
}
