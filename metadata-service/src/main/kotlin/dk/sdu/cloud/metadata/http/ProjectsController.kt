package dk.sdu.cloud.metadata.http

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.client.RESTResponse
import dk.sdu.cloud.metadata.api.*
import dk.sdu.cloud.metadata.services.Project
import dk.sdu.cloud.metadata.services.ProjectException
import dk.sdu.cloud.metadata.services.ProjectService
import dk.sdu.cloud.metadata.services.tryWithProject
import dk.sdu.cloud.service.cloudClient
import dk.sdu.cloud.service.implement
import dk.sdu.cloud.service.logEntry
import dk.sdu.cloud.storage.api.FileDescriptions
import dk.sdu.cloud.storage.api.FileType
import dk.sdu.cloud.storage.api.SyncFileListRequest
import io.ktor.http.HttpStatusCode
import io.ktor.routing.Route
import org.slf4j.LoggerFactory
import java.util.stream.Collectors

class ProjectsController(
    private val projectEventProducer: ProjectEventProducer,
    private val projectService: ProjectService
) {
    fun configure(routing: Route) = with(routing) {
        implement(ProjectDescriptions.create) {
            logEntry(log, it)

            val result = FileDescriptions.syncFileList.call(SyncFileListRequest(it.fsRoot, 0), call.cloudClient)

            if (result is RESTResponse.Err) {
                if (result.status == HttpStatusCode.Forbidden.value) {
                    error(CommonErrorMessage("Not allowed"), HttpStatusCode.Forbidden)
                } else {
                    log.warn(result.response.responseBody)
                    error(CommonErrorMessage("Internal Server Error"), HttpStatusCode.InternalServerError)
                }
                return@implement
            }

            val initialFiles = result.response.responseBodyAsStream
                .bufferedReader()
                .lines()
                .map { parseSyncItem(it) }
                .map { FileDescriptionForMetadata(it.uniqueId, it.fileType, it.path) }
                .collect(Collectors.toList())

            tryWithProject {
                val project = Project("", it.fsRoot, "")
                val id = projectService.createProject(project)
                val projectWithId = project.copy(id = id)
                projectEventProducer.emit(ProjectEvent.Created(projectWithId, initialFiles))
                ok(CreateProjectResponse(id))
            }
        }

        implement(ProjectDescriptions.findProjectByPath) {
            logEntry(log, it)

            tryWithProject {
                ok(projectService.findByFSRoot(it.path) ?: throw ProjectException.NotFound())
            }
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(ProjectsController::class.java)
    }
}

private data class SyncItem(
    val fileType: FileType,
    val uniqueId: String,
    val user: String,
    val modifiedAt: Long,
    val checksum: String?,
    val checksumType: String?,
    val path: String
)

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

