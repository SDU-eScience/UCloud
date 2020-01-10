package dk.sdu.cloud.file.services

import com.fasterxml.jackson.module.kotlin.readValue
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.file.api.WorkspaceMode
import dk.sdu.cloud.file.api.WorkspaceMount
import dk.sdu.cloud.file.services.linuxfs.translateAndCheckFile
import dk.sdu.cloud.service.Loggable
import io.ktor.http.HttpStatusCode
import java.io.File
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path

data class CreatedWorkspace(val workspaceId: String, val failures: List<WorkspaceMount>)

data class WorkspaceManifest(
    val username: String,
    val mounts: List<WorkspaceMount>,
    val createSymbolicLinkAt: String,
    val createdAt: Long = System.currentTimeMillis(),
    val mode: WorkspaceMode? = null
) {
    fun write(workspace: Path) {
        val file = workspace.resolve(WorkspaceService.WORKSPACE_MANIFEST_NAME)
        defaultMapper.writeValue(file.toFile(), this)
    }

    companion object {
        fun read(workspace: Path): WorkspaceManifest {
            val file = workspace.resolve(WorkspaceService.WORKSPACE_MANIFEST_NAME)
            if (!Files.exists(file)) throw RPCException("Invalid workspace", HttpStatusCode.BadRequest)
            return defaultMapper.readValue(file.toFile())
        }
    }
}

fun workspaceFile(fsRoot: File, id: String) =
    File(File(fsRoot, WorkspaceService.WORKSPACE_PATH), id).toPath().normalize().toAbsolutePath()

class WorkspaceService(
    fsRoot: File,
    private val creators: Map<WorkspaceMode, WorkspaceCreator>
) {
    private val fsRoot = fsRoot.absoluteFile.normalize()

    suspend fun create(
        user: String,
        mounts: List<WorkspaceMount>,
        allowFailures: Boolean,
        createSymbolicLinkAt: String,
        mode: WorkspaceMode
    ): CreatedWorkspace {
        val creator = creators[mode]
            ?: throw RPCException("Found no workspace creator for $mode", HttpStatusCode.InternalServerError)
        return creator.create(user, mounts, allowFailures, createSymbolicLinkAt)
    }

    suspend fun transfer(
        workspaceId: String,
        fileGlobs: List<String>,
        destination: String,
        replaceExisting: Boolean
    ): List<String> {
        val workspace = workspaceFile(fsRoot, workspaceId)
        if (!Files.exists(workspace)) throw RPCException.fromStatusCode(HttpStatusCode.NotFound)

        val manifest = WorkspaceManifest.read(workspace)
        val mode = manifest.mode ?: WorkspaceMode.COPY_FILES

        val matchers = fileGlobs.map { FileSystems.getDefault().getPathMatcher("glob:$it") }
        val defaultDestinationDir = File(translateAndCheckFile(fsRoot, destination)).toPath()

        val creator = creators[mode]
            ?: throw RPCException("Found no workspace creator for $mode", HttpStatusCode.InternalServerError)

        return creator.transfer(
            workspaceId,
            manifest,
            workspace,
            replaceExisting,
            matchers,
            destination,
            defaultDestinationDir
        )
    }

    suspend fun delete(workspaceId: String) {
        val workspace = workspaceFile(fsRoot, workspaceId)
        if (!Files.exists(workspace)) throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
        val manifest = WorkspaceManifest.read(workspace)
        val mode = manifest.mode ?: WorkspaceMode.COPY_FILES
        val creator = creators[mode]
            ?: throw RPCException("Found no workspace creator for $mode", HttpStatusCode.InternalServerError)

        return creator.delete(workspaceId, manifest)
    }

    companion object : Loggable {
        override val log = logger()

        const val WORKSPACE_PATH = "workspace"
        const val WORKSPACE_MANIFEST_NAME = "workspace.json"
    }
}
