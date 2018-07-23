package dk.sdu.cloud.metadata.http

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.auth.api.validatedPrincipal
import dk.sdu.cloud.client.RESTResponse
import dk.sdu.cloud.client.jwtAuth
import dk.sdu.cloud.metadata.api.*
import dk.sdu.cloud.metadata.services.Project
import dk.sdu.cloud.metadata.services.ProjectService
import dk.sdu.cloud.metadata.services.tryWithProject
import dk.sdu.cloud.metadata.util.normalize
import dk.sdu.cloud.service.*
import dk.sdu.cloud.storage.api.AnnotateFileRequest
import dk.sdu.cloud.storage.api.FileDescriptions
import dk.sdu.cloud.storage.api.FileType
import dk.sdu.cloud.storage.api.SyncFileListRequest
import io.ktor.http.HttpStatusCode
import io.ktor.routing.Route
import kotlinx.coroutines.experimental.io.jvm.javaio.toInputStream
import org.slf4j.LoggerFactory
import java.util.stream.Collectors

class ProjectsController(
    private val projectEventProducer: ProjectEventProducer,
    private val projectService: ProjectService<*>
) : Controller {
    override val baseContext = ProjectDescriptions.baseContext

    override fun configure(routing: Route) = with(routing) {
        implement(ProjectDescriptions.create) { request ->
            logEntry(log, request)

            tryWithProject {
                // TODO Move this to service
                // TODO Move this to service
                // TODO Move this to service
                val cloudCtx = call.cloudClient.parent
                val cloud = cloudCtx.jwtAuth(call.request.validatedPrincipal.token).withCausedBy(call.request.jobId)
                val result = FileDescriptions.syncFileList.call(SyncFileListRequest(request.fsRoot, 0), cloud)

                if (result is RESTResponse.Err) {
                    if (result.status == HttpStatusCode.Forbidden.value) {
                        error(CommonErrorMessage("Not allowed"), HttpStatusCode.Forbidden)
                    } else {
                        log.warn(result.rawResponseBody)
                        error(CommonErrorMessage("Internal Server Error"), HttpStatusCode.InternalServerError)
                    }
                    return@implement
                }

                val initialFiles = result.response.content.toInputStream()
                    .bufferedReader()
                    .lines()
                    .map { parseSyncItem(it) }
                    .collect(Collectors.toList())

                val metadataFiles = initialFiles.map { FileDescriptionForMetadata(it.uniqueId, it.fileType, it.path.normalize()) }
                println(metadataFiles)
                val rootFile = initialFiles.find { it.path.normalize() == request.fsRoot.normalize() } ?: return@implement run {
                    log.info("Expected to find information about root file")
                    error(CommonErrorMessage("Not allowed"), HttpStatusCode.Forbidden)
                }

                val currentUser = call.request.validatedPrincipal.subject
                if (rootFile.user != currentUser) {
                    log.debug("User is not owner of folder")
                    error(CommonErrorMessage("Not allowed"), HttpStatusCode.Forbidden)
                    return@implement
                }

                val annotateResult = FileDescriptions.annotate.call(
                    AnnotateFileRequest(request.fsRoot, PROJECT_ANNOTATION, currentUser),
                    call.cloudClient // Must be performed as the service
                )

                if (annotateResult is RESTResponse.Err) {
                    log.warn("Unable to annotate file! Status = ${annotateResult.status}")
                    log.warn(annotateResult.rawResponseBody)
                    error(CommonErrorMessage("Internal Server Error"), HttpStatusCode.InternalServerError)
                    return@implement
                }

                // TODO Failure in last part must remove the annotation!
                log.debug("Creating a project! $currentUser")
                val project = Project(null, request.fsRoot, currentUser, "")
                val id = projectService.createProject(project)
                val projectWithId = project.copy(id = id)
                projectEventProducer.emit(ProjectEvent.Created(projectWithId, metadataFiles))
                ok(CreateProjectResponse(id))
            }
        }

        implement(ProjectDescriptions.findProjectByPath) {
            logEntry(log, it)

            tryWithProject {
                ok(projectService.findByFSRoot(it.path))
            }
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(ProjectsController::class.java)
        const val PROJECT_ANNOTATION = "P"
    }
}

data class SyncItem(
    val fileType: FileType,
    val uniqueId: String,
    val user: String,
    val modifiedAt: Long,
    val checksum: String?,
    val checksumType: String?,
    val path: String
) {
    override fun toString(): String =
        StringBuilder().apply {
            val type = when (fileType) {
                FileType.FILE -> "F"
                FileType.DIRECTORY -> "D"
                else -> throw IllegalArgumentException()
            }

            append(type)
            append(',')

            append(uniqueId)
            append(',')

            append(user)
            append(',')

            append(modifiedAt)
            append(',')

            val hasChecksum = checksum != null
            append(if (hasChecksum) '1' else '0')
            append(',')

            if (hasChecksum) {
                append(checksum)
                append(',')

                append(checksumType)
                append(',')
            }

            append(path)
        }.toString()

}

// TODO Should probably be distributed with storage-api
private fun parseSyncItem(syncLine: String): SyncItem {
    var cursor = 0
    val chars = syncLine.toCharArray()
    fun readToken(): String {
        val builder = StringBuilder()
        while (cursor < chars.size) {
            val c = chars[cursor++]
            if (c == ',') break
            builder.append(c)
        }
        return builder.toString()
    }

    val fileType = readToken()
    val uniqueId = readToken()
    val user = readToken()
    val modifiedAt = readToken().toLong()
    val hasChecksum = when (readToken()) {
        "0" -> false
        "1" -> true
        else -> throw IllegalStateException("Bad server response")
    }

    val checksum = if (hasChecksum) readToken() else null
    val checksumType = if (hasChecksum) readToken() else null

    val path = syncLine.substring(cursor)

    return SyncItem(
        when (fileType) {
            "F" -> FileType.FILE
            "D" -> FileType.DIRECTORY
            else -> throw IllegalArgumentException("Unknown file type $fileType")
        },
        uniqueId,
        user,
        modifiedAt,
        checksum,
        checksumType,
        path
    )
}

