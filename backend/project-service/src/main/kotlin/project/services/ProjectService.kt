package dk.sdu.cloud.project.services

import com.github.jasync.sql.db.postgresql.exceptions.GenericDatabaseException
import dk.sdu.cloud.Role
import dk.sdu.cloud.auth.api.LookupUsersRequest
import dk.sdu.cloud.auth.api.UserDescriptions
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orRethrowAs
import dk.sdu.cloud.contact.book.api.ContactBookDescriptions
import dk.sdu.cloud.contact.book.api.InsertRequest
import dk.sdu.cloud.contact.book.api.ServiceOrigin
import dk.sdu.cloud.events.EventProducer
import dk.sdu.cloud.notification.api.CreateNotification
import dk.sdu.cloud.notification.api.Notification
import dk.sdu.cloud.notification.api.NotificationDescriptions
import dk.sdu.cloud.project.api.Project
import dk.sdu.cloud.project.api.ProjectEvent
import dk.sdu.cloud.project.api.ProjectMember
import dk.sdu.cloud.project.api.ProjectRole
import dk.sdu.cloud.project.api.UserProjectSummary
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.async.AsyncDBConnection
import dk.sdu.cloud.service.db.async.PostgresErrorCodes
import dk.sdu.cloud.service.db.async.errorCode
import dk.sdu.cloud.service.db.withTransaction
import dk.sdu.cloud.service.stackTraceToString
import io.ktor.http.HttpStatusCode

class ProjectService(
    private val db: DBSessionFactory<AsyncDBConnection>,
    private val dao: ProjectDao,
    private val groupDao: GroupDao,
    private val eventProducer: EventProducer<ProjectEvent>,
    private val serviceClient: AuthenticatedClient
) {
    suspend fun create(title: String, principalInvestigator: String): Project {
        confirmUserExists(principalInvestigator)
        return db.withTransaction { session ->
            try {
                dao.create(session, title, title, principalInvestigator)
                val piMember = ProjectMember(principalInvestigator, ProjectRole.PI)

                val project = Project(title, title)
                eventProducer.produce(ProjectEvent.Created(project))
                project
            } catch (ex: GenericDatabaseException) {
                if (ex.errorCode == PostgresErrorCodes.UNIQUE_VIOLATION) {
                    throw RPCException("Project already exists", HttpStatusCode.Conflict)
                }

                throw ex
            }
        }
    }

    suspend fun delete(projectId: String) {
        db.withTransaction { session ->
            val project = dao.findById(session, projectId)
            dao.delete(session, projectId)
            eventProducer.produce(ProjectEvent.Deleted(project))
        }
    }

    suspend fun view(user: String, projectId: String): Project {
        return db.withTransaction { session ->
            dao.findRoleOfMember(session, projectId, user) ?: throw ProjectException.NotFound()
            dao.findById(session, projectId)
        }
    }

    suspend fun viewMemberInProject(user: String, projectId: String): ProjectMember {
        return db.withTransaction { session ->
            dao.findRoleOfMember(session, projectId, user)?.let { ProjectMember(user, it) }
                ?: throw ProjectException.NotFound()
        }
    }

    suspend fun listProjects(user: String, pagination: NormalizedPaginationRequest?): Page<UserProjectSummary> {
        return db.withTransaction { session ->
            dao.listProjectsForUser(session, pagination, user)
        }
    }

    suspend fun addMember(user: String, projectId: String, member: ProjectMember) {
        confirmUserExists(member.username)

        try {
            db.withTransaction { session ->
                val project =
                    findProjectAndRequireRole(session, user, projectId, setOf(ProjectRole.ADMIN, ProjectRole.PI))

                dao.addMember(session, projectId, member)

                eventProducer.produce(ProjectEvent.MemberAdded(project, member))

                ContactBookDescriptions.insert.call(
                    InsertRequest(user, listOf(member.username), ServiceOrigin.PROJECT_SERVICE),
                    serviceClient
                )

                NotificationDescriptions.create.call(
                    CreateNotification(
                        member.username,
                        Notification(
                            "PROJECT_INVITE",
                            "$user has invited you to $projectId",
                            meta = mapOf(
                                "invitedBy" to user,
                                "projectId" to projectId
                            )
                        )
                    ),
                    serviceClient
                )
            }
        } catch (ex: GenericDatabaseException) {
            if (ex.errorMessage.fields['C'] == ALREADY_EXISTS_PSQL) {
                throw RPCException("Member is already in group", HttpStatusCode.Conflict)
            }

            log.warn(ex.stackTraceToString())
            throw RPCException("Internal Server Error", HttpStatusCode.InternalServerError)
        }
    }

    suspend fun deleteMember(user: String, projectId: String, member: String) {
        db.withTransaction { session ->
            val project = findProjectAndRequireRole(session, user, projectId, setOf(ProjectRole.ADMIN, ProjectRole.PI))
            val removedMemberRole = dao.findRoleOfMember(session, projectId, member)
                ?: throw ProjectException.NotFound()

            if (removedMemberRole == ProjectRole.PI) throw ProjectException.CantDeleteUserFromProject()

            dao.deleteMember(session, projectId, member)
            groupDao.removeMember(session, projectId, member)

            eventProducer.produce(ProjectEvent.MemberDeleted(project, ProjectMember(member, removedMemberRole)))
        }
    }

    private suspend fun confirmUserExists(username: String) {
        val lookup = UserDescriptions.lookupUsers.call(
            LookupUsersRequest(listOf(username)),
            serviceClient
        ).orRethrowAs {
            log.warn("Caught the following error while looking up user: ${it.error} ${it.statusCode}")
            throw ProjectException.InternalError()
        }

        val user = lookup.results[username] ?: throw ProjectException.UserDoesNotExist()
        log.debug("$username resolved to $user")
        if (user.role !in ALLOWED_ROLES) throw ProjectException.CantAddUserToProject()
    }

    suspend fun changeMemberRole(user: String, projectId: String, member: String, newRole: ProjectRole) {
        db.withTransaction { session ->
            val project = findProjectAndRequireRole(session, user, projectId, setOf(ProjectRole.ADMIN, ProjectRole.PI))
            val oldRole = dao.findRoleOfMember(session, projectId, member) ?: throw ProjectException.NotFound()

            if (oldRole == ProjectRole.PI) throw ProjectException.CantChangeRole()

            dao.changeMemberRole(session, projectId, member, newRole)

            eventProducer.produce(
                ProjectEvent.MemberRoleUpdated(
                    project,
                    ProjectMember(member, oldRole),
                    ProjectMember(member, newRole)
                )
            )
        }
    }

    suspend fun shouldVerify(user: String, projectId: String): Boolean {
        return db.withTransaction { session ->
            findProjectAndRequireRole(session, user, projectId, setOf(ProjectRole.ADMIN, ProjectRole.PI))
            dao.shouldVerify(session, projectId)
        }
    }

    suspend fun verifyMembership(user: String, projectId: String) {
        db.withTransaction { session ->
            dao.verifyMembership(session, projectId, user)
        }
    }

    private suspend fun findProjectAndRequireRole(
        session: AsyncDBConnection,
        user: String,
        projectId: String,
        requiredRole: Set<ProjectRole>
    ): Project {
        val project = dao.findById(session, projectId)
        val projectMemberRole = dao.findRoleOfMember(session, projectId, user)
        if (projectMemberRole !in requiredRole) {
            throw ProjectException.Forbidden()
        }

        return project
    }

    companion object : Loggable {
        override val log = logger()

        private val ALLOWED_ROLES = setOf(Role.USER, Role.ADMIN)
    }
}

