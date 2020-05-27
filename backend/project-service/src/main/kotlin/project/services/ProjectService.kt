package dk.sdu.cloud.project.services

import com.github.jasync.sql.db.postgresql.exceptions.GenericDatabaseException
import dk.sdu.cloud.Roles
import dk.sdu.cloud.SecurityPrincipal
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
import dk.sdu.cloud.project.api.*
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.db.async.*
import dk.sdu.cloud.service.stackTraceToString
import io.ktor.http.HttpStatusCode
import org.joda.time.LocalDateTime

object ProjectMemberTable : SQLTable("project_members") {
    val username = text("username")
    val role = text("role")
    val project = text("project_id")
    val createdAt = timestamp("created_at")
    val modifiedAt = timestamp("modified_at")
}

object ProjectTable : SQLTable("projects") {
    val id = text("id")
    val title = text("title")
    val createdAt = timestamp("created_at")
    val modifiedAt = timestamp("modified_at")
    val archived = bool("archived")
}

object ProjectMembershipVerified : SQLTable("project_membership_verification") {
    val projectId = text("project_id")
    val verification = timestamp("verification")
    val verifiedBy = text("verified_by")
}

object ProjectInvite : SQLTable("invites") {
    val projectId = text("project_id")
    val username = text("username")
    val invitedBy = text("invited_by")
    val createdAt = timestamp("created_at")
}

class ProjectService(
    private val serviceClient: AuthenticatedClient,
    private val eventProducer: EventProducer<ProjectEvent>
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

            verifyMembership(session, "_project", id)
        }

        eventProducer.produce(ProjectEvent.Created(id))
    }

    suspend fun findRoleOfMember(ctx: DBContext, projectId: String, member: String): ProjectRole? {
        return ctx.withSession { session ->
            session
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

    suspend fun requireRole(
        ctx: DBContext,
        username: String,
        projectId: String,
        allowedRoles: Set<ProjectRole>
    ): ProjectRole {
        val role = findRoleOfMember(ctx, projectId, username)
        if (role == null || role !in allowedRoles) {
            throw ProjectException.Forbidden()
        }
        return role
    }

    suspend fun inviteMember(
        ctx: DBContext,
        inviteFrom: String,
        projectId: String,
        invitesTo: Set<String>
    ) {
        try {
            confirmUsersExist(invitesTo)
            ctx.withSession { session ->
                requireRole(session, inviteFrom, projectId, ProjectRole.ADMINS)

                invitesTo.map { member ->
                    val existingRole = findRoleOfMember(session, projectId, member)
                    if (existingRole != null) {
                        throw ProjectException.AlreadyMember()
                    }

                    session.insert(ProjectInvite) {
                        set(ProjectInvite.invitedBy, inviteFrom)
                        set(ProjectInvite.projectId, projectId)
                        set(ProjectInvite.username, member)
                    }
                }
            }
            sendInviteNotifications(projectId, inviteFrom, invitesTo)
        } catch (ex: GenericDatabaseException) {
            if (ex.errorCode == PostgresErrorCodes.UNIQUE_VIOLATION) {
                throw ProjectException.AlreadyMember()
            }

            log.warn(ex.stackTraceToString())
            throw RPCException("Internal Server Error", HttpStatusCode.InternalServerError)
        }
    }

    private suspend fun sendInviteNotifications(
        projectId: String,
        invitedBy: String,
        invitesTo: Set<String>
    ) {
        invitesTo.forEach { member ->
            ContactBookDescriptions.insert.call(
                InsertRequest(invitedBy, listOf(member), ServiceOrigin.PROJECT_SERVICE),
                serviceClient
            )

            NotificationDescriptions.create.call(
                CreateNotification(
                    member,
                    Notification(
                        "PROJECT_INVITE",
                        "$invitedBy has invited you to $projectId",
                        meta = mapOf(
                            "invitedBy" to invitedBy,
                            "projectId" to projectId
                        )
                    )
                ),
                serviceClient
            )
        }
    }

    suspend fun acceptInvite(
        ctx: DBContext,
        invitedUser: String,
        projectId: String
    ) {
        ctx.withSession { session ->
            val hasInvite = session
                .sendPreparedStatement(
                    {
                        setParameter("username", invitedUser)
                        setParameter("project", projectId)
                    },
                    """
                        delete from invites
                        where
                            username = ?username and
                            project_id = ?project
                    """
                )
                .rowsAffected > 0

            if (!hasInvite) throw ProjectException.NotFound()

            session.insert(ProjectMemberTable) {
                set(ProjectMemberTable.role, ProjectRole.USER.name)
                set(ProjectMemberTable.project, projectId)
                set(ProjectMemberTable.username, invitedUser)
                set(ProjectMemberTable.createdAt, LocalDateTime.now())
                set(ProjectMemberTable.modifiedAt, LocalDateTime.now())
            }
        }
    }

    suspend fun rejectInvite(
        ctx: DBContext,
        rejectedBy: String,
        projectId: String,
        username: String
    ) {
        ctx.withSession { session ->
            if (rejectedBy != username) {
                requireRole(session, rejectedBy, projectId, ProjectRole.ADMINS)
            }

            val hasInvite = session
                .sendPreparedStatement(
                    {
                        setParameter("username", username)
                        setParameter("project", projectId)
                    },
                    """
                        delete from invites
                        where
                            username = ?username and
                            project_id = ?project
                    """
                )
                .rowsAffected > 0

            if (!hasInvite) throw ProjectException.NotFound()
        }
    }

    suspend fun leaveProject(
        ctx: DBContext,
        initiatedBy: String,
        projectId: String
    ) {
        ctx.withSession { session ->
            val existingRole = findRoleOfMember(session, projectId, initiatedBy) ?: throw ProjectException.NotFound()
            if (existingRole == ProjectRole.PI) throw ProjectException.CantDeleteUserFromProject()

            session
                .sendPreparedStatement(
                    {
                        setParameter("project", projectId)
                        setParameter("username", initiatedBy)
                    },
                    """
                        delete from project_members
                        where project_id = ?project and username = ?username
                    """
                )

            eventProducer.produce(
                ProjectEvent.MemberDeleted(
                    projectId,
                    ProjectMember(initiatedBy, existingRole)
                )
            )
        }
    }

    suspend fun deleteMember(
        ctx: DBContext,
        deletedBy: String,
        projectId: String,
        userToDelete: String
    ) {
        // TODO Performance: This method is running way more queries than is actually needed
        ctx.withSession { session ->
            requireRole(ctx, deletedBy, projectId, ProjectRole.ADMINS)

            val userToDeleteRole = findRoleOfMember(ctx, projectId, userToDelete)
            if (userToDeleteRole == ProjectRole.PI) {
                throw ProjectException.CantDeleteUserFromProject()
            } else if (userToDeleteRole == null) {
                throw ProjectException.NotFound()
            }

            session
                .sendPreparedStatement(
                    {
                        setParameter("project", projectId)
                        setParameter("username", userToDelete)
                    },
                    """
                        delete from project_members
                        where project_id = ?project and username = ?username
                    """
                )

            eventProducer.produce(
                ProjectEvent.MemberDeleted(
                    projectId,
                    ProjectMember(userToDelete, userToDeleteRole)
                )
            )
        }
    }

    suspend fun changeRoleOfMember(
        ctx: DBContext,
        updatedBy: String,
        projectId: String,
        memberToUpdate: String,
        newRole: ProjectRole,
        allowPiChange: Boolean = false
    ) {
        ctx.withSession { session ->
            val updatedByRole = requireRole(session, updatedBy, projectId, ProjectRole.ADMINS)
            val oldRole = if (updatedBy == memberToUpdate) {
                updatedByRole
            } else {
                findRoleOfMember(session, projectId, memberToUpdate) ?: throw ProjectException.NotFound()
            }

            if (!allowPiChange && oldRole == ProjectRole.PI) throw ProjectException.CantChangeRole()
            if (!allowPiChange && newRole == ProjectRole.PI) {
                log.info("1")
                throw ProjectException.Forbidden()
            }
            if (oldRole == newRole) return@withSession
            if (oldRole == ProjectRole.PI && updatedByRole != ProjectRole.PI) {
                log.info("2")
                throw ProjectException.Forbidden()
            }

            session
                .sendPreparedStatement(
                    {
                        setParameter("role", newRole.name)
                        setParameter("username", memberToUpdate)
                        setParameter("project", projectId)
                    },
                    """
                        update project_members  
                        set
                            modified_at = now(),
                            role = ?role
                        where
                            username = ?username and
                            project_id = ?project
                    """
                )

            eventProducer.produce(
                ProjectEvent.MemberRoleUpdated(
                    projectId,
                    ProjectMember(memberToUpdate, oldRole),
                    ProjectMember(memberToUpdate, newRole)
                )
            )
        }
    }

    suspend fun transferPrincipalInvestigatorRole(
        ctx: DBContext,
        initiatedBy: String,
        projectId: String,
        newPrincipalInvestigator: String
    ) {
        ctx.withSession { session ->
            changeRoleOfMember(session, initiatedBy, projectId, newPrincipalInvestigator, ProjectRole.PI, true)
            changeRoleOfMember(session, initiatedBy, projectId, initiatedBy, ProjectRole.ADMIN, true)
        }
    }

    suspend fun verifyMembership(
        ctx: DBContext,
        verifiedBy: String,
        project: String
    ) {
        ctx.withSession { session ->
            if (!verifiedBy.startsWith("_")) {
                requireRole(ctx, verifiedBy, project, ProjectRole.ADMINS)
            }

            session.insert(ProjectMembershipVerified) {
                set(ProjectMembershipVerified.projectId, project)
                set(ProjectMembershipVerified.verification, LocalDateTime.now())
                set(ProjectMembershipVerified.verifiedBy, verifiedBy)
            }
        }
    }

    suspend fun setArchiveStatus(
        ctx: DBContext,
        requestedBy: String,
        projectId: String,
        archiveStatus: Boolean
    ) {
        ctx.withSession { session ->
            requireRole(session, requestedBy, projectId, ProjectRole.ADMINS)

            session
                .sendPreparedStatement(
                    {
                        setParameter("project", projectId)
                        setParameter("archiveStatus", archiveStatus)
                    },
                    """
                        update projects 
                        set archived = ?archiveStatus
                        where id = ?project
                    """
                )
        }
    }

    private suspend fun confirmUsersExist(users: Set<String>) {
        val lookup = UserDescriptions.lookupUsers.call(
            LookupUsersRequest(users.toList()),
            serviceClient
        ).orRethrowAs {
            log.warn("Caught the following error while looking up user: ${it.error} ${it.statusCode}")
            throw ProjectException.InternalError()
        }

        users.forEach { username ->
            val user = lookup.results[username] ?: throw ProjectException.UserDoesNotExist()
            log.debug("$username resolved to $user")
            if (user.role !in Roles.END_USER) throw ProjectException.CantAddUserToProject()
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}
