package dk.sdu.cloud.plugins.storage.ucloud

import dk.sdu.cloud.plugins.InternalFile
import dk.sdu.cloud.plugins.UCloudFile
import dk.sdu.cloud.plugins.child

class MemberFiles(
    private val fs: NativeFS,
    private val paths: PathConverter
) {
    suspend fun initializeMemberFiles(username: String, project: String?): InternalFile {
        if (username.contains("..") || username.contains("/")) {
            throw IllegalArgumentException("Unexpected username: $username")
        }

        if (project != null) {
            if (project.contains("..") || project.contains("/")) {
                throw IllegalArgumentException("Unexpected project: $project")
            }

            val (drive) = paths.locator.register(
                "Member Files: ${username}",
                UCloudDrive.ProjectMemberFiles(UCloudDrive.PLACEHOLDER_ID, project, username),
                ownedByProject = project,
                createdByUser = username
            )

            val file = paths.ucloudToInternal(UCloudFile.createFromPreNormalizedString("/${drive.ucloudId}"))
            val exists = try {
                fs.stat(file)
                true
            } catch (ex: FSException.NotFound) {
                false
            }

            if (exists) return file

            fs.createDirectories(file)
            return file
        } else {
            val (drive) = paths.locator.register(
                "Home",
                UCloudDrive.PersonalWorkspace(UCloudDrive.PLACEHOLDER_ID, username),
                createdByUser = username
            )

            val file = paths.ucloudToInternal(UCloudFile.createFromPreNormalizedString("/${drive.ucloudId}"))

            val exists = try {
                fs.stat(file)
                true
            } catch (ex: FSException.NotFound) {
                false
            }

            if (exists) return file

            fs.createDirectories(file)
            fs.createDirectories(file.child("Jobs"))
            fs.createDirectories(file.child("Trash"))

            return file
        }
    }
}

