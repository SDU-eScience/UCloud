package dk.sdu.cloud.project.services

import dk.sdu.cloud.Role
import dk.sdu.cloud.auth.api.LookupUsersRequest
import dk.sdu.cloud.auth.api.UserDescriptions
import dk.sdu.cloud.client.AuthenticatedCloud
import dk.sdu.cloud.project.api.Project
import dk.sdu.cloud.project.api.ProjectEvent
import dk.sdu.cloud.project.api.ProjectMember
import dk.sdu.cloud.project.api.ProjectRole
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.MappedEventProducer
import dk.sdu.cloud.service.RPCException
import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.withTransaction
import dk.sdu.cloud.service.orRethrowAs
import io.ktor.http.HttpStatusCode
import java.util.*

class ProjectService<DBSession>(
    private val db: DBSessionFactory<DBSession>,
    private val dao: ProjectDao<DBSession>,
    private val eventProducer: MappedEventProducer<*, ProjectEvent>,
    private val serviceCloud: AuthenticatedCloud
) {
    suspend fun create(title: String, principalInvestigator: String): Project {
        confirmUserExists(principalInvestigator)

        return db.withTransaction { session ->
            val id = generateIdFromTitle(session, title)
            dao.create(session, id, title, principalInvestigator)
            val piMember = ProjectMember(principalInvestigator, ProjectRole.PI)

            val project = Project(id, title, listOf(piMember))
            eventProducer.emit(ProjectEvent.Created(project))
            project
        }
    }

    suspend fun delete(projectId: String) {
        db.withTransaction { session ->
            val project = dao.findById(session, projectId)
            dao.delete(session, projectId)
            eventProducer.emit(ProjectEvent.Deleted(project))
        }
    }

    fun view(user: String, projectId: String): Project {
        return db.withTransaction { session ->
            dao.findRoleOfMember(session, projectId, user) ?: throw ProjectException.NotFound()
            dao.findById(session, projectId)
        }
    }

    fun viewMemberInProject(user: String, projectId: String): ProjectMember {
        return db.withTransaction { session ->
            dao.findRoleOfMember(session, projectId, user)?.let { ProjectMember(user, it) }
                ?: throw ProjectException.NotFound()
        }
    }

    suspend fun addMember(user: String, projectId: String, member: ProjectMember) {
        confirmUserExists(member.username)

        db.withTransaction { session ->
            val project = findProjectAndRequireRole(session, user, projectId, setOf(ProjectRole.ADMIN, ProjectRole.PI))
            if (project.members.any { it.username == member.username }) throw ProjectException.AlreadyMember()

            dao.addMember(session, projectId, member)

            val projectWithNewMember = project.copy(members = project.members + member)
            eventProducer.emit(ProjectEvent.MemberAdded(projectWithNewMember, member))
        }
    }

    suspend fun deleteMember(user: String, projectId: String, member: String) {
        db.withTransaction { session ->
            val project = findProjectAndRequireRole(session, user, projectId, setOf(ProjectRole.ADMIN, ProjectRole.PI))
            val removedMember = project.members.find { it.username == member } ?: throw ProjectException.NotFound()

            dao.deleteMember(session, projectId, member)

            val newProject = project.copy(members = project.members.filter { it.username == member })
            eventProducer.emit(ProjectEvent.MemberDeleted(newProject, removedMember))
        }
    }

    private suspend fun confirmUserExists(username: String) {
        val lookup = UserDescriptions.lookupUsers.call(
            LookupUsersRequest(listOf(username)),
            serviceCloud
        ).orRethrowAs {
            log.warn("Caught the following error while looking up user: ${it.error} ${it.status}")
            throw ProjectException.InternalError()
        }

        val user = lookup.results[username] ?: throw ProjectException.UserDoesNotExist()
        log.debug("$username resolved to $user")
        if (user.role !in ALLOWED_ROLES) throw ProjectException.CantAddUserToProject()
    }

    suspend fun changeMemberRole(user: String, projectId: String, member: String, newRole: ProjectRole) {
        db.withTransaction { session ->
            val project = findProjectAndRequireRole(session, user, projectId, setOf(ProjectRole.ADMIN, ProjectRole.PI))
            val oldRole = project.members.find { it.username == member } ?: throw ProjectException.NotFound()

            dao.changeMemberRole(session, projectId, member, newRole)

            val newMember = ProjectMember(member, newRole)
            val projectWithNewMember =
                project.copy(members = project.members.filter { it.username == member } + newMember)
            eventProducer.emit(ProjectEvent.MemberRoleUpdated(projectWithNewMember, oldRole, newMember))
        }
    }

    private fun findProjectAndRequireRole(
        session: DBSession,
        user: String,
        projectId: String,
        requiredRole: Set<ProjectRole>
    ): Project {
        val project = dao.findById(session, projectId)
        val projectMember = project.members.find { it.username == user } ?: throw ProjectException.Unauthorized()
        if (projectMember.role !in requiredRole) {
            throw ProjectException.Unauthorized()
        }

        return project
    }

    private fun generateIdFromTitle(session: DBSession, title: String): String {
        return UUID.randomUUID().toString()
    }

    companion object : Loggable {
        override val log = logger()

        private val ALLOWED_ROLES = setOf(Role.USER, Role.ADMIN)
    }
}

sealed class ProjectException(why: String, statusCode: HttpStatusCode) : RPCException(why, statusCode) {
    class NotFound : ProjectException("Not found", HttpStatusCode.NotFound)
    class Unauthorized : ProjectException("Unauthorized", HttpStatusCode.Unauthorized)
    class UserDoesNotExist : ProjectException("User does not exist", HttpStatusCode.BadRequest)
    class CantAddUserToProject : ProjectException("This user cannot be added to this project", HttpStatusCode.BadRequest)
    class AlreadyMember : ProjectException("User is already a member of this project", HttpStatusCode.Conflict)
    class InternalError : ProjectException("Internal Server Error", HttpStatusCode.InternalServerError)
}
