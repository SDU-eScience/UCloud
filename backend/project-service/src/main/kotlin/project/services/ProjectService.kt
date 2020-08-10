package dk.sdu.cloud.project.services

import com.github.jasync.sql.db.postgresql.exceptions.GenericDatabaseException
import dk.sdu.cloud.Role
import dk.sdu.cloud.Roles
import dk.sdu.cloud.auth.api.LookupUsersRequest
import dk.sdu.cloud.auth.api.UserDescriptions
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orRethrowAs
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.contact.book.api.ContactBookDescriptions
import dk.sdu.cloud.contact.book.api.InsertRequest
import dk.sdu.cloud.contact.book.api.ServiceOrigin
import dk.sdu.cloud.events.EventProducer
import dk.sdu.cloud.mail.api.MailDescriptions
import dk.sdu.cloud.mail.api.SendBulkRequest
import dk.sdu.cloud.mail.api.SendRequest
import dk.sdu.cloud.notification.api.CreateNotification
import dk.sdu.cloud.notification.api.Notification
import dk.sdu.cloud.notification.api.NotificationDescriptions
import dk.sdu.cloud.notification.api.NotificationType
import dk.sdu.cloud.project.api.ProjectEvent
import dk.sdu.cloud.project.api.ProjectMember
import dk.sdu.cloud.project.api.ProjectRole
import dk.sdu.cloud.project.utils.*
import dk.sdu.cloud.service.*
import dk.sdu.cloud.service.db.async.*
import io.ktor.http.HttpStatusCode
import org.joda.time.LocalDateTime
import java.util.*

object ProjectMemberTable : SQLTable("project_members") {
    val username = text("username", notNull = true)
    val role = text("role", notNull = true)
    val project = text("project_id", notNull = true)
    val createdAt = timestamp("created_at", notNull = true)
    val modifiedAt = timestamp("modified_at", notNull = true)
}

object ProjectTable : SQLTable("projects") {
    val id = text("id", notNull = true)
    val title = text("title", notNull = true)
    val createdAt = timestamp("created_at", notNull = true)
    val modifiedAt = timestamp("modified_at", notNull = true)
    val archived = bool("archived", notNull = true)
    val parent = text("parent", notNull = false)
}

object ProjectMembershipVerified : SQLTable("project_membership_verification") {
    val projectId = text("project_id", notNull = true)
    val verification = timestamp("verification", notNull = true)
    val verifiedBy = text("verified_by", notNull = true)
}

object ProjectInvite : SQLTable("invites") {
    val projectId = text("project_id", notNull = true)
    val username = text("username", notNull = true)
    val invitedBy = text("invited_by", notNull = true)
    val createdAt = timestamp("created_at", notNull = true)
}

class ProjectService(
    private val serviceClient: AuthenticatedClient,
    private val eventProducer: EventProducer<ProjectEvent>
) {
    suspend fun create(
        ctx: DBContext,
        actor: Actor,
        title: String,
        parent: String?,
        principalInvestigatorOverride: String?
    ): String {
        if (actor !is Actor.System) {
            val role = if (actor is Actor.User) actor.principal.role else Role.USER
            if (role !in Roles.PRIVILEGED) {
                if (parent == null) throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)
                requireRole(ctx, actor.safeUsername(), parent, ProjectRole.ADMINS)
            }
        }

        val id = UUID.randomUUID().toString()

        try {
            ctx.withSession { session ->
                session.insert(ProjectTable) {
                    set(ProjectTable.id, id)
                    set(ProjectTable.title, title)
                    set(ProjectTable.createdAt, LocalDateTime(Time.now()))
                    set(ProjectTable.modifiedAt, LocalDateTime(Time.now()))
                    set(ProjectTable.parent, parent)
                }

                session.insert(ProjectMemberTable) {
                    set(ProjectMemberTable.username, principalInvestigatorOverride ?: actor.safeUsername())
                    set(ProjectMemberTable.role, ProjectRole.PI.name)
                    set(ProjectMemberTable.project, id)
                    set(ProjectMemberTable.createdAt, LocalDateTime(Time.now()))
                    set(ProjectMemberTable.modifiedAt, LocalDateTime(Time.now()))
                }

                verifyMembership(session, "_project", id)
            }
        } catch (ex: GenericDatabaseException) {
            if (ex.errorCode == PostgresErrorCodes.UNIQUE_VIOLATION) {
                throw RPCException.fromStatusCode(HttpStatusCode.Conflict)
            }
            throw ex
        }

        eventProducer.produce(ProjectEvent.Created(id))
        eventProducer.produce(
            ProjectEvent.MemberAdded(
                id,
                ProjectMember(principalInvestigatorOverride ?: actor.safeUsername(), ProjectRole.PI)
            )
        )
        return id
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
                        SELECT role
                        FROM project_members
                        WHERE 
                            username = :username AND 
                            project_id = :project
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
            sendInviteNotifications(ctx, projectId, inviteFrom, invitesTo)

        } catch (ex: GenericDatabaseException) {
            if (ex.errorCode == PostgresErrorCodes.UNIQUE_VIOLATION) {
                throw ProjectException.AlreadyMember()
            }

            log.warn(ex.stackTraceToString())
            throw RPCException("Internal Server Error", HttpStatusCode.InternalServerError)
        }
    }

    private suspend fun sendInviteNotifications(
        ctx: DBContext,
        projectId: String,
        invitedBy: String,
        invitesTo: Set<String>
    ) {
        invitesTo.forEach { member ->
            // We do not wish to fail if contact book is misbehaving
            ContactBookDescriptions.insert.call(
                InsertRequest(invitedBy, listOf(member), ServiceOrigin.PROJECT_SERVICE),
                serviceClient
            )

            notify(NotificationType.PROJECT_INVITE, member, "$invitedBy has invited you to collaborate")
        }

        ctx.withSession { session ->
            val projectTitle = getProjectTitle(ctx, projectId)
            val messages = invitesTo.map { invitee ->
                SendRequest(
                    invitee,
                    PROJECT_USER_INVITE,
                    userInvitedToInviteeTemplate(invitee, projectTitle)
                )
            }

            // We don't wish to fail if mails fail at sending
            MailDescriptions.sendBulk.call(
                SendBulkRequest(messages),
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
                            username = :username and
                            project_id = :project
                    """
                )
                .rowsAffected > 0

            if (!hasInvite) throw ProjectException.NotFound()

            session.insert(ProjectMemberTable) {
                set(ProjectMemberTable.role, ProjectRole.USER.name)
                set(ProjectMemberTable.project, projectId)
                set(ProjectMemberTable.username, invitedUser)
                set(ProjectMemberTable.createdAt, LocalDateTime(Time.now()))
                set(ProjectMemberTable.modifiedAt, LocalDateTime(Time.now()))
            }

            eventProducer.produce(ProjectEvent.MemberAdded(projectId, ProjectMember(invitedUser, ProjectRole.USER)))
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
                            username = :username and
                            project_id = :project
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
        lateinit var existingRole: ProjectRole
        ctx.withSession { session ->
            existingRole = findRoleOfMember(session, projectId, initiatedBy) ?: throw ProjectException.NotFound()
            if (existingRole == ProjectRole.PI) throw ProjectException.CantDeleteUserFromProject()

            session
                .sendPreparedStatement(
                    {
                        setParameter("project", projectId)
                        setParameter("username", initiatedBy)
                    },
                    """
                        delete from project_members
                        where project_id = :project and username = :username
                    """
                )
        }

        ctx.withSession { session ->
            eventProducer.produce(
                ProjectEvent.MemberDeleted(
                    projectId,
                    ProjectMember(initiatedBy, existingRole)
                )
            )

            val (pi, admins) = getPIAndAdminsOfProject(ctx, projectId)
            val allAdmins = (admins + pi)
            val projectTitle = getProjectTitle(ctx, projectId)

            val notificationMessage = "$initiatedBy has left project: $projectTitle"
            allAdmins.forEach { admin ->
                notify(NotificationType.PROJECT_USER_LEFT, admin, notificationMessage)
            }

            MailDescriptions.sendBulk.call(
                SendBulkRequest(allAdmins.map { admin ->
                    SendRequest(
                        admin,
                        USER_LEFT,
                        userLeftTemplate(pi, initiatedBy, projectTitle)
                    )
                }),
                serviceClient
            ).orThrow()
        }
    }

    private suspend fun notify(
        notificationType: NotificationType,
        receiver: String,
        message: String,
        meta: Map<String, Any?> = emptyMap()
    ) {
        // We don't wish to fail if notifications fail
        NotificationDescriptions.create.call(
            CreateNotification(
                receiver,
                Notification(
                    notificationType.name,
                    message,
                    meta = meta
                )
            ),
            serviceClient
        )
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
                        where project_id = :project and username = :username
                    """
                )

            eventProducer.produce(
                ProjectEvent.MemberDeleted(
                    projectId,
                    ProjectMember(userToDelete, userToDeleteRole)
                )
            )


            val (pi, admins) = getPIAndAdminsOfProject(ctx, projectId)
            val allAdmins = (admins + pi)
            val projectTitle = getProjectTitle(ctx, projectId)

            val notificationMessage = "$userToDelete has been removed from project: $projectTitle"
            allAdmins.forEach { admin ->
                notify(NotificationType.PROJECT_USER_REMOVED, admin, notificationMessage)
            }

            notify(
                NotificationType.PROJECT_USER_REMOVED,
                userToDelete,
                "You have been removed from project: $projectTitle"
            )

            val adminMessages = allAdmins
                .map {
                    SendRequest(
                        pi,
                        USER_LEFT,
                        userRemovedTemplate(pi, userToDelete, projectTitle)
                    )
                }

            val userMessage = SendRequest(
                userToDelete,
                USER_LEFT,
                userRemovedToPersonRemovedTemplate(userToDelete, projectTitle)
            )

            MailDescriptions.sendBulk.call(
                SendBulkRequest(adminMessages + userMessage),
                serviceClient
            ).orThrow()
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
                throw ProjectException.Forbidden()
            }
            if (oldRole == newRole) return@withSession
            if (oldRole == ProjectRole.PI && updatedByRole != ProjectRole.PI) {
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
                            role = :role
                        where
                            username = :username and
                            project_id = :project
                    """
                )

            eventProducer.produce(
                ProjectEvent.MemberRoleUpdated(
                    projectId,
                    ProjectMember(memberToUpdate, oldRole),
                    ProjectMember(memberToUpdate, newRole)
                )
            )

            val (pi, admins) = getPIAndAdminsOfProject(ctx, projectId)
            val projectTitle = getProjectTitle(ctx, projectId)
            val allAdmins = admins + pi
            val notificationMessage = "$memberToUpdate has changed role to $newRole in project: $projectTitle"

            allAdmins.forEach { admin ->
                notify(
                    NotificationType.PROJECT_ROLE_CHANGE,
                    admin,
                    notificationMessage,
                    mapOf("projectId" to projectId)
                )
            }

            notify(NotificationType.PROJECT_ROLE_CHANGE, pi, notificationMessage, mapOf("projectId" to projectId))

            MailDescriptions.sendBulk.call(
                SendBulkRequest(allAdmins.map {
                    SendRequest(
                        pi,
                        USER_ROLE_CHANGE,
                        userRoleChangeTemplate(pi, memberToUpdate, newRole, projectTitle)
                    )
                }),
                serviceClient
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
                set(ProjectMembershipVerified.verification, LocalDateTime(Time.now()))
                set(ProjectMembershipVerified.verifiedBy, verifiedBy)
            }
        }
    }

    suspend fun getPIOfProject(db: DBContext, projectId: String): String {
        return db.withSession { session ->
            session
                .sendPreparedStatement(
                    {
                        setParameter("projectId", projectId)
                        setParameter("role", ProjectRole.PI.name)
                    },
                    """
                        SELECT * FROM project_members
                        WHERE project_id = :projectId AND "role" = :role
                    """
                )
                .rows
                .singleOrNull()
                ?.getField(ProjectMemberTable.username)
                ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound, "PI is not found")
        }
    }

    //Returns a Pair<PI, listOf<Admins>>
    suspend fun getPIAndAdminsOfProject(db: DBContext, projectId: String): Pair<String, List<String>> {
        return db.withSession { session ->
            val adminsAndPIs = session
                .sendPreparedStatement(
                    {
                        setParameter("projectId", projectId)
                        setParameter("piRole", ProjectRole.PI.name)
                        setParameter("adminRole", ProjectRole.ADMIN.name)
                    },
                    """
                        SELECT * FROM project_members
                        WHERE project_id = :projectId AND ("role" = :piRole OR "role" = :adminRole)
                    """
                )
                .rows
            var pi: String? = null
            val admins = mutableListOf<String>()
            adminsAndPIs.forEach { rowData ->
                if (rowData.getField(ProjectMemberTable.role) == ProjectRole.ADMIN.name) {
                    admins.add(rowData.getField(ProjectMemberTable.username))
                } else if (rowData.getField(ProjectMemberTable.role) == ProjectRole.PI.name) {
                    pi = rowData.getField(ProjectMemberTable.username)
                }
            }
            if (pi == null) {
                throw RPCException.fromStatusCode(HttpStatusCode.NotFound, "no PI found")
            }
            Pair(pi!!, admins)
        }
    }

    suspend fun getProjectTitle(db: DBContext, projectId: String): String {
        return db.withSession { session ->
            session
                .sendPreparedStatement(
                    {
                        setParameter("projectID", projectId)
                    },
                    """
                        SELECT * FROM projects
                        WHERE id = :projectID
                    """
                )
                .rows
                .singleOrNull()?.getField(ProjectTable.title)
                ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound, "Project not found")
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
                        set archived = :archiveStatus
                        where id = :project
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

    suspend fun renameProject(
        ctx: DBContext,
        actor: Actor,
        projectId: String,
        newTitle: String
    ) {
        val isAdmin = when (actor) {
            Actor.System -> true

            is Actor.User, is Actor.SystemOnBehalfOfUser -> {
                if (actor is Actor.User && actor.principal.role in Roles.PRIVILEGED) {
                    true
                } else {
                    findRoleOfMember(ctx, projectId, actor.username) in ProjectRole.ADMINS
                }
            }
        }

        if (!isAdmin) throw RPCException.fromStatusCode(HttpStatusCode.Unauthorized)

        ctx.withSession { session ->
            session.sendPreparedStatement(
                {
                    setParameter("project", projectId)
                    setParameter("newTitle", newTitle)
                },
                """update projects set title = :newTitle where id = :project"""
            )
        }
    }

    companion object : Loggable {
        override val log = logger()
        const val USER_ROLE_CHANGE = "Role change in project"
        const val USER_LEFT = "User left project"
        const val PROJECT_USER_INVITE = "User invited to project"
    }
}
