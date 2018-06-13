package dk.sdu.cloud.metadata.services

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.service.RESTHandler
import dk.sdu.cloud.service.stackTraceToString
import io.ktor.http.HttpStatusCode
import org.slf4j.LoggerFactory

data class Project(
    val id: String?,
    val fsRoot: String,
    val owner: String,
    val description: String
)

sealed class ProjectException : RuntimeException() {
    class Duplicate : ProjectException()
    class NotFound : ProjectException()
}

suspend inline fun RESTHandler<*, *, CommonErrorMessage>.tryWithProject(closure: () -> Unit) {
    try {
        closure()
    } catch (ex: ProjectException) {
        when (ex) {
            is ProjectException.Duplicate -> error(CommonErrorMessage("Already exists"), HttpStatusCode.Conflict)
            is ProjectException.NotFound -> error(CommonErrorMessage("Not Found"), HttpStatusCode.NotFound)
        }
    } catch (ex: Exception) {
        error(CommonErrorMessage("Internal Server Error"), HttpStatusCode.InternalServerError)
        ProjectService.log.warn(ex.stackTraceToString())
    }
}

interface ProjectDAO {
    fun findByFSRoot(path: String): Project
    fun findById(id: String): Project?
    fun createProject(project: Project): String
    fun findBestMatchingProjectByPath(path: String): Project
    fun updateProjectRoot(id: String, newRoot: String)
    fun deleteProjectByRoot(root: String)
    fun deleteProjectById(id: String)
}

// Maybe this would make sense?
class ProjectService(private val dao: ProjectDAO) : ProjectDAO by dao {
    companion object {
        val log = LoggerFactory.getLogger(ProjectService::class.java)
    }
}