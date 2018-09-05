package dk.sdu.cloud.metadata.services

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.metadata.util.normalize
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.RESTHandler
import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.withTransaction
import dk.sdu.cloud.service.stackTraceToString
import io.ktor.http.HttpStatusCode
import org.slf4j.LoggerFactory

data class Project(
    val id: Long?,
    val fsRoot: String,
    val fsRootId: String,
    val owner: String,
    val description: String
)

sealed class ProjectException : RuntimeException() {
    class Duplicate : ProjectException()
    class NotFound : ProjectException()
}

suspend inline fun RESTHandler<*, *, CommonErrorMessage, *>.tryWithProject(closure: () -> Unit) {
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

interface ProjectDAO<Session> {
    fun findByFSRoot(session: Session, path: String): Project
    fun findById(session: Session, id: Long): Project?
    fun createProject(session: Session, project: Project): Long
    fun findBestMatchingProjectByPath(session: Session, path: String): Project
    fun updateProjectRoot(session: Session, id: Long, newRoot: String)
    fun deleteProjectByRoot(session: Session, root: String)
    fun deleteProjectById(session: Session, id: Long)
}

class ProjectService<Session>(
    private val db: DBSessionFactory<Session>,
    private val dao: ProjectDAO<Session>
) {
    fun findByFSRoot(path: String): Project = db.withTransaction { dao.findByFSRoot(it, path.normalize()) }

    fun findById(id: Long): Project? = db.withTransaction { dao.findById(it, id) }

    fun createProject(project: Project): Long =
        db.withTransaction { dao.createProject(it, project.copy(fsRoot = project.fsRoot.normalize())) }

    fun findBestMatchingProjectByPath(path: String): Project =
        db.withTransaction { dao.findBestMatchingProjectByPath(it, path.normalize()) }

    fun updateProjectRoot(id: Long, newRoot: String) {
        db.withTransaction { dao.updateProjectRoot(it, id, newRoot.normalize()) }
    }

    fun deleteProjectByRoot(root: String) {
        db.withTransaction {
            dao.deleteProjectByRoot(it, root.normalize())
        }
    }

    fun deleteProjectById(id: Long) {
        db.withTransaction { dao.deleteProjectById(it, id) }
    }

    companion object : Loggable {
        override val log = logger()
    }
}