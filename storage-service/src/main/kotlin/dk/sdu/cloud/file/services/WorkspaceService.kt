package dk.sdu.cloud.file.services

import com.fasterxml.jackson.module.kotlin.readValue
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.file.api.WorkspaceMode
import dk.sdu.cloud.file.api.WorkspaceMount
import dk.sdu.cloud.file.api.Workspaces
import dk.sdu.cloud.file.services.linuxfs.translateAndCheckFile
import dk.sdu.cloud.service.DistributedLock
import dk.sdu.cloud.service.DistributedLockFactory
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.HibernateSession
import dk.sdu.cloud.service.db.criteria
import dk.sdu.cloud.service.db.get
import dk.sdu.cloud.service.db.withTransaction
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.delay
import org.hibernate.annotations.NaturalId
import java.io.File
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.Table
import kotlin.system.exitProcess

typealias CreatedWorkspace = Workspaces.Create.Response

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

@Entity
@Table(name = "workspace_jobs")
data class WorkspaceJobEntity(
    @get:Id
    @get:NaturalId
    var workspaceId: String,
    var fileGlobs: String,
    var destination: String,
    var replaceExisting: Boolean,
    var delete: Boolean,
    var status: Int
)

class WorkspaceJobService(
    private val db: DBSessionFactory<HibernateSession>,
    private val locks: DistributedLockFactory
) {
    suspend fun insertJob(
        workspaceId: String,
        fileGlobs: List<String>,
        destination: String,
        replaceExisting: Boolean,
        delete: Boolean
    ) {
        val entity =
            WorkspaceJobEntity(workspaceId, fileGlobs.joinToString("\n"), destination, replaceExisting, delete, 0)
        db.withTransaction { session ->
            session.save(entity)
        }
    }

    private fun lock(id: String): DistributedLock {
        return locks.create("workspace-${id}", 1000L * 60 * 3)
    }

    suspend fun selectJob(): Pair<WorkspaceJobEntity, DistributedLock> {
        while (true) {
            val jobs = db.withTransaction { session ->
                session
                    .criteria<WorkspaceJobEntity> {
                        entity[WorkspaceJobEntity::status] equal 0
                    }
                    .list()
            }

            for (job in jobs) {
                val lock = lock(job.workspaceId)
                if (lock.acquire()) {
                    return Pair(job, lock)
                }
            }

            delay(5000L)
        }
    }

    suspend fun markAsComplete(job: WorkspaceJobEntity, lock: DistributedLock) {
        db.withTransaction { session ->
            job.status = 1
            session.update(job)
        }

        lock.release()
    }
}

class WorkspaceService(
    fsRoot: File,
    private val creators: Map<WorkspaceMode, WorkspaceCreator>,
    private val queue: WorkspaceJobService
) {
    private val fsRoot = fsRoot.absoluteFile.normalize()

    suspend fun runWorkQueue() {
        while (true) {
            try {
                val (nextJob, lock) = queue.selectJob()
                log.info("Transferring workspace: ${nextJob.workspaceId}")
                transfer(
                    nextJob.workspaceId,
                    nextJob.fileGlobs.split("\n"),
                    nextJob.destination,
                    nextJob.replaceExisting,
                    lock
                )
                if (nextJob.delete) {
                    runCatching {
                        delete(nextJob.workspaceId)
                    }
                }
                queue.markAsComplete(nextJob, lock)
                log.info("Workspace transfer complete: ${nextJob.workspaceId}")
            } catch (ex: Throwable) {
                ex.printStackTrace()
                exitProcess(1)
            }
        }
    }

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

    suspend fun requestTransfer(
        workspaceId: String,
        fileGlobs: List<String>,
        destination: String,
        replaceExisting: Boolean,
        delete: Boolean
    ) {
        queue.insertJob(workspaceId, fileGlobs, destination, replaceExisting, delete)
    }

    private suspend fun transfer(
        workspaceId: String,
        fileGlobs: List<String>,
        destination: String,
        replaceExisting: Boolean,
        lock: DistributedLock
    ) {
        val workspace = workspaceFile(fsRoot, workspaceId)
        if (!Files.exists(workspace)) {
            log.warn("Workspace does not exist")
            return
        }

        val manifest = WorkspaceManifest.read(workspace)
        val mode = manifest.mode ?: WorkspaceMode.COPY_FILES

        val matchers = fileGlobs.map { FileSystems.getDefault().getPathMatcher("glob:$it") }
        val defaultDestinationDir = File(translateAndCheckFile(fsRoot, destination)).toPath()

        val creator = creators[mode] ?: return run {
            log.error("Found no workspace creator for $mode")
        }

        creator.transfer(
            workspaceId,
            manifest,
            replaceExisting,
            matchers,
            destination,
            defaultDestinationDir,
            lock
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
