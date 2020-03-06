package dk.sdu.cloud.file.migration

import com.fasterxml.jackson.module.kotlin.readValue
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.file.api.LINUX_FS_USER_UID
import dk.sdu.cloud.file.services.linuxfs.Chown
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.stackTraceToString
import java.io.File
import java.nio.file.Files

/*
// Old copy-backend
{
  "username": "TomAsmussen#6185",
  "mounts": [
    {
      "source": "/home/JohnBulava#9344/to_attach/system/",
      "destination": ".//system",
      "readOnly": false,
      "allowMergeDuringTransfer": true
    }
  ],
  "createSymbolicLinkAt": "/input",
  "createdAt": 1579097940546
}

// Data can be found in ./output/$DESTINATION (./output/system)
 */

class WorkspaceMigration(
    private val cephfsMount: File,
    private val dryRun: Boolean
) {
    private val workspaces = File(cephfsMount, "workspace")

    fun runMigration() {
        (workspaces.listFiles() ?: emptyArray()).forEach { file ->
            transferWorkspace(file)
        }
    }

    private fun transferWorkspace(workspace: File) {
        val workspaceJson = File(workspace, "workspace.json")
        if (!workspaceJson.exists()) {
            log.info("Workspace ${workspace.name} is invalid")
            return
        }

        val metadata = try {
            defaultMapper.readValue<Map<String, Any?>>(workspaceJson)
        } catch (ex: Throwable) {
            log.warn("Workspace caught invalid json ${workspace.name}\n${ex.stackTraceToString()}")
            return
        }

        if (metadata["mode"] == "COPY_ON_WRITE") {
            log.info("Workspace ${workspace.name} has data in RBD. This data is lost.")
            return
        }

        val username = metadata["username"] as? String? ?: run {
            log.info("Workspace ${workspace.name} has no associated owner")
            return
        }

        moveWorkspace(workspace, username)
    }

    private fun moveWorkspace(workspace: File, username: String) {
        log.info("Moving workspace from ${workspace.absolutePath} to $username")
        val workspaceOutput = File(workspace, "output")
        if (!workspaceOutput.exists()) {
            log.info("Could not find output folder for workspace ${workspace.name}")
            return
        }

        if ((workspaceOutput.listFiles() ?: emptyArray()).isEmpty()) {
            log.info("Workspace ${workspace.name} is empty")
            return
        }

        val finalDestination = File(cephfsMount, "/home/${username}/WorkspaceRecovery/${workspace.name}")
        log.debug("Workspace destination is $finalDestination")

        if (!dryRun) {
            val parentFile = finalDestination.parentFile
            if (!parentFile.exists()) {
                parentFile.mkdirs()
                Chown.setOwner(parentFile.toPath(), LINUX_FS_USER_UID, LINUX_FS_USER_UID)

                val readMe = File(parentFile, "README.txt")
                readMe.writeText(
                    """
                        TODO Explain why these files were moved
                    """.trimIndent()
                )

                Chown.setOwner(readMe.toPath(), LINUX_FS_USER_UID, LINUX_FS_USER_UID)
            }

            workspace.renameTo(finalDestination)

            Files.walk(finalDestination.toPath()).forEach { path ->
                Chown.setOwner(path, LINUX_FS_USER_UID, LINUX_FS_USER_UID)
            }
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}
