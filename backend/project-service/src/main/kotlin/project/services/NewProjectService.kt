package dk.sdu.cloud.project.services

import dk.sdu.cloud.Roles
import dk.sdu.cloud.SecurityPrincipal
import dk.sdu.cloud.auth.api.LookupUsersRequest
import dk.sdu.cloud.auth.api.UserDescriptions
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orRethrowAs
import dk.sdu.cloud.project.api.ProjectMember
import dk.sdu.cloud.project.api.ProjectRole
import dk.sdu.cloud.project.api.UserProjectSummary
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.db.async.*
import io.ktor.http.HttpStatusCode
import org.joda.time.DateTimeConstants
import org.joda.time.LocalDateTime

class NewProjectService(
    private val serviceClient: AuthenticatedClient
) {
    suspend fun create(
        ctx: DBContext,
        createdBy: SecurityPrincipal,
        title: String
    ) {
        val id = title

        ctx.withSession { session ->
            session.insert(ProjectTable) {
                set(ProjectTable.id, id)
                set(ProjectTable.title, title)
                set(ProjectTable.createdAt, LocalDateTime.now())
                set(ProjectTable.modifiedAt, LocalDateTime.now())
            }

            session.insert(ProjectMemberTable) {
                set(ProjectMemberTable.username, createdBy.username)
                set(ProjectMemberTable.role, ProjectRole.PI.name)
                set(ProjectMemberTable.project, id)
                set(ProjectMemberTable.createdAt, LocalDateTime.now())
                set(ProjectMemberTable.modifiedAt, LocalDateTime.now())
            }

            verifyMembership(session, id, "_project")
        }
    }

    suspend fun listProjects(
        ctx: DBContext,
        username: String,
        pagination: NormalizedPaginationRequest?
    ): Page<UserProjectSummary> {
        ctx.withSession { session ->
            val items = session
                .sendPreparedStatement(
                    {
                        setParameter("username", username)
                        setParameter("offset", if (pagination == null) 0 else pagination.page * pagination.itemsPerPage)
                        setParameter("limit", pagination?.itemsPerPage ?: Int.MAX_VALUE)
                    },
                    """
                        select 
                            mem.role, 
                            p.id, 
                            p.title, 
                            is_favorite(mem.username, p.id) as is_fav
                        from 
                            project_members mem inner join projects p on mem.project_id = p.id
                        where 
                            mem.username = ?username
                        order by is_fav desc, p.id
                        offset ?offset
                        limit ?limit
                    """
                )
                .rows
                .map {
                    val role = ProjectRole.valueOf(it.getString(0)!!)
                    val id = it.getString(1)!!
                    val title = it.getString(2)!!
                    val isFavorite = it.getBoolean(3)!!

                    // TODO (Performance) Not ideal code
                    val needsVerification = if (role.isAdmin()) {
                        shouldVerify(session, id)
                    } else {
                        false
                    }

                    UserProjectSummary(id, title, ProjectMember(username, role), needsVerification, isFavorite)
                }

            val count = if (pagination == null) {
                items.size
            } else {
                session
                    .sendPreparedStatement(
                        { setParameter("username", username) },
                        """
                            select count(*)
                            from project_members
                            where username = ?username
                        """
                    )
                    .rows
                    .map { it.getLong(0)!!.toInt() }
                    .singleOrNull() ?: items.size
            }

            return Page(count, pagination?.itemsPerPage ?: count, pagination?.page ?: 0, items)
        }
    }

    suspend fun findRoleOfMember(ctx: DBContext, projectId: String, member: String): ProjectRole? {
        ctx.withSession { session ->
            return session
                .sendPreparedStatement(
                    {
                        setParameter("username", member)
                        setParameter("project", projectId)
                    },
                    """
                        select role
                        from project_members
                        where 
                            username = ?username and
                            project_id = ?project
                    """
                )
                .rows
                .map { ProjectRole.valueOf(it.getString(0)!!) }
                .singleOrNull()
        }
    }

    private suspend fun requireRole(
        ctx: DBContext,
        projectId: String,
        username: String,
        allowedRoles: Set<ProjectRole>
    ) {
        val role = findRoleOfMember(ctx, projectId, username)
        if (role == null || role !in allowedRoles) {
            throw ProjectException.Forbidden()
        }
    }

    suspend fun inviteMember(
        ctx: DBContext,
        projectId: String,
        inviteFrom: String,
        inviteTo: String
    ) {
        confirmUserExists(inviteTo)
        ctx.withSession { session ->
            requireRole(session, projectId, inviteFrom, ProjectRole.ADMINS)
            // TODO Create invitiation
        }
        sendInviteNotifications()
    }

    private suspend fun sendInviteNotifications() {
        TODO()
    }

    suspend fun acceptInvite() {

    }

    suspend fun verifyMembership(ctx: DBContext, project: String, verifiedBy: SecurityPrincipal) {
        verifyMembership(ctx, project, verifiedBy.username)
    }

    private suspend fun verifyMembership(ctx: DBContext, project: String, verifiedBy: String) {
        ctx.withSession { session ->
            if (!verifiedBy.startsWith("_")) {
                requireRole(ctx, project, verifiedBy, ProjectRole.ADMINS)
            }

            session.insert(ProjectMembershipVerified) {
                set(ProjectMembershipVerified.projectId, project)
                set(ProjectMembershipVerified.verification, LocalDateTime.now())
                set(ProjectMembershipVerified.verifiedBy, verifiedBy)
            }
        }
    }

    suspend fun shouldVerify(ctx: DBContext, project: String): Boolean {
        ctx.withSession { session ->
            val latestVerification = session
                .sendPreparedStatement(
                    {
                        setParameter("project", project)
                    },
                    """
                        select * 
                        from project_membership_verification 
                        where project_id = ?project  
                        order by verification desc
                        limit 1
                    """
                )
                .rows
                .map { it.getField(ProjectMembershipVerified.verification) }
                .singleOrNull()

            if (latestVerification == null) {
                verifyMembership(session, project, "_project")
                return false
            }

            return (System.currentTimeMillis() - latestVerification.toTimestamp()) >
                ProjectDao.VERIFICATION_REQUIRED_EVERY_X_DAYS * DateTimeConstants.MILLIS_PER_DAY
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
        if (user.role !in Roles.END_USER) throw ProjectException.CantAddUserToProject()
    }

    companion object : Loggable {
        override val log = logger()
    }
}
