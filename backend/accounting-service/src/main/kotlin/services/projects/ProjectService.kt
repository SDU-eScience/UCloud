package dk.sdu.cloud.accounting.services.projects

import dk.sdu.cloud.Actor
import dk.sdu.cloud.ActorAndProject
import dk.sdu.cloud.FindByStringId
import dk.sdu.cloud.Roles
import dk.sdu.cloud.accounting.util.ProjectCache
import dk.sdu.cloud.calls.BulkRequest
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.bulkRequestOf
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.project.api.*
import dk.sdu.cloud.project.api.v2.FindByProjectId
import dk.sdu.cloud.project.api.v2.ProjectsChangeRoleRequestItem
import dk.sdu.cloud.project.api.v2.ProjectsCreateInviteRequestItem
import dk.sdu.cloud.project.api.v2.ProjectsDeleteInviteRequestItem
import dk.sdu.cloud.project.api.v2.ProjectsDeleteMemberRequestItem
import dk.sdu.cloud.project.api.v2.ProjectsRetrieveRequest
import dk.sdu.cloud.safeUsername
import dk.sdu.cloud.service.*
import dk.sdu.cloud.service.db.async.*
import dk.sdu.cloud.project.api.v2.Project as P2
import dk.sdu.cloud.accounting.services.projects.v2.ProjectService as P2Service

object ProjectMemberTable : SQLTable("project.project_members") {
    val username = text("username", notNull = true)
    val role = text("role", notNull = true)
    val project = text("project_id", notNull = true)
    val createdAt = timestamp("created_at", notNull = true)
    val modifiedAt = timestamp("modified_at", notNull = true)
}

object ProjectTable : SQLTable("project.projects") {
    val id = text("id", notNull = true)
    val title = text("title", notNull = true)
    val createdAt = timestamp("created_at", notNull = true)
    val modifiedAt = timestamp("modified_at", notNull = true)
    val archived = bool("archived", notNull = true)
    val parent = text("parent", notNull = false)
    val allowsRenaming = bool("subprojects_renameable", notNull = false)
}

object ProjectMembershipVerified : SQLTable("project.project_membership_verification") {
    val projectId = text("project_id", notNull = true)
    val verification = timestamp("verification", notNull = true)
    val verifiedBy = text("verified_by", notNull = true)
}

object ProjectInvite : SQLTable("project.invites") {
    val projectId = text("project_id", notNull = true)
    val username = text("username", notNull = true)
    val invitedBy = text("invited_by", notNull = true)
    val createdAt = timestamp("created_at", notNull = true)
}

class ProjectService(
    private val serviceClient: AuthenticatedClient,
    private val projectCache: ProjectCache,
    private val p2: P2Service,
) {
    suspend fun create(
        ctx: DBContext,
        actor: Actor,
        title: String,
        parent: String?,
        principalInvestigatorOverride: String?
    ): String {
        val piUsername = principalInvestigatorOverride ?: actor.safeUsername()
        projectCache.invalidate(piUsername)

        return p2.create(
            ActorAndProject(actor, null),
            bulkRequestOf(P2.Specification(parent, title)),
            ctx = ctx,
            piOverride = principalInvestigatorOverride
        ).responses.first().id
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
                        FROM project.project_members
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
        p2.createInvite(
            ActorAndProject(
                Actor.SystemOnBehalfOfUser(inviteFrom),
                projectId
            ),
            BulkRequest(invitesTo.map { ProjectsCreateInviteRequestItem(it) }),
            ctx = ctx
        )
    }

    suspend fun acceptInvite(
        ctx: DBContext,
        invitedUser: String,
        projectId: String
    ) {
        p2.acceptInvite(
            ActorAndProject(Actor.SystemOnBehalfOfUser(invitedUser), null),
            bulkRequestOf(FindByProjectId(projectId)),
            ctx = ctx
        )
        projectCache.invalidate(invitedUser)
    }

    suspend fun rejectInvite(
        ctx: DBContext,
        rejectedBy: String,
        projectId: String,
        username: String
    ) {
        p2.deleteInvite(
            ActorAndProject(Actor.SystemOnBehalfOfUser(rejectedBy), null),
            bulkRequestOf(ProjectsDeleteInviteRequestItem(projectId, username)),
            ctx = ctx
        )
    }

    suspend fun leaveProject(
        ctx: DBContext,
        initiatedBy: String,
        projectId: String
    ) {
        p2.deleteMember(
            ActorAndProject(Actor.SystemOnBehalfOfUser(initiatedBy), projectId),
            bulkRequestOf(ProjectsDeleteMemberRequestItem(initiatedBy)),
            ctx = ctx
        )

        projectCache.invalidate(initiatedBy)
    }

    suspend fun deleteMember(
        ctx: DBContext,
        deletedBy: String,
        projectId: String,
        userToDelete: String
    ) {
        p2.deleteMember(
            ActorAndProject(Actor.SystemOnBehalfOfUser(deletedBy), projectId),
            bulkRequestOf(ProjectsDeleteMemberRequestItem(userToDelete)),
            ctx = ctx
        )
        projectCache.invalidate(userToDelete)
    }

    suspend fun changeRoleOfMember(
        ctx: DBContext,
        updatedBy: String,
        projectId: String,
        memberToUpdate: String,
        newRole: ProjectRole,
        allowPiChange: Boolean = false
    ) {
        p2.changeRole(
            ActorAndProject(Actor.SystemOnBehalfOfUser(updatedBy), projectId),
            bulkRequestOf(
                ProjectsChangeRoleRequestItem(memberToUpdate, newRole)
            ),
            ctx = ctx
        )

        projectCache.invalidate(memberToUpdate)
    }

    suspend fun transferPrincipalInvestigatorRole(
        ctx: DBContext,
        initiatedBy: String,
        projectId: String,
        newPrincipalInvestigator: String
    ) {
        changeRoleOfMember(ctx, initiatedBy, projectId, newPrincipalInvestigator, ProjectRole.PI)
    }

    suspend fun verifyMembership(
        ctx: DBContext,
        verifiedBy: String,
        project: String
    ) {
        val actor = if (verifiedBy.startsWith("_")) Actor.System else Actor.SystemOnBehalfOfUser(verifiedBy)
        p2.verifyMembership(ActorAndProject(actor, project), bulkRequestOf(FindByStringId(project)), ctx = ctx)
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
                        SELECT * FROM project.project_members
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

    suspend fun setArchiveStatus(
        ctx: DBContext,
        requestedBy: String,
        projectId: String,
        archiveStatus: Boolean
    ) {
        p2.archive(
            ActorAndProject(Actor.SystemOnBehalfOfUser(requestedBy), projectId),
            bulkRequestOf(FindByStringId(projectId)),
            ctx = ctx
        )
    }

    suspend fun allowsSubProjectsRenaming(
        ctx: DBContext,
        projectId: String,
        actor: Actor
    ): Boolean {
        val p = p2.retrieve(
            ActorAndProject(actor, projectId),
            ProjectsRetrieveRequest(
                projectId,
                includeSettings = true
            ),
            ctx = ctx
        )

        return p.status.settings!!.subprojects!!.allowRenaming
    }

    suspend fun allowedRenaming(
        ctx: DBContext,
        projectId: String,
        actor: Actor
    ): Boolean {
        val parent = p2.retrieve(
            ActorAndProject(actor, null),
            ProjectsRetrieveRequest(projectId),
            ctx = ctx
        ).specification.parent ?: return false

        return p2.retrieve(
            ActorAndProject(Actor.System, null),
            ProjectsRetrieveRequest(parent, includeSettings = true),
            ctx = ctx
        ).status.settings!!.subprojects!!.allowRenaming
    }

    suspend fun toggleRenaming(
        ctx: DBContext,
        projectId: String,
        actor: Actor
    ) {
        ctx.withSession { session ->
            val current = allowsSubProjectsRenaming(session, projectId, actor)
            p2.updateSettings(
                ActorAndProject(actor, projectId),
                P2.Settings(
                    subprojects = P2.Settings.SubProjects(
                        allowRenaming = !current
                    )
                ),
                ctx = session
            )
        }
    }

    suspend fun renameProject(
        ctx: DBContext,
        actor: Actor,
        projectId: String,
        newTitle: String
    ) {
        if (newTitle.trim().length != newTitle.length) {
            throw RPCException("Project names cannot start or end with whitespace.", HttpStatusCode.BadRequest)
        }
        ctx.withSession(remapExceptions = true) { session ->
            requireAdmin(session, projectId, actor)
            session.sendPreparedStatement(
                {
                    setParameter("project", projectId)
                    setParameter("newTitle", newTitle)
                },
                """update project.projects set title = :newTitle where id = :project """
            )
        }
    }

    suspend fun fetchDataManagementPlan(
        ctx: DBContext,
        actor: Actor,
        projectId: String
    ): String? {
        return null
    }

    suspend fun updateDataManagementPlan(
        ctx: DBContext,
        actor: Actor,
        projectId: String,
        dmp: String?
    ) {
        // Do nothing
    }

    private suspend fun requireAdmin(ctx: DBContext, projectId: String, actor: Actor) {
        val isAdmin = when (actor) {
            Actor.System -> true

            is Actor.User, is Actor.SystemOnBehalfOfUser -> {
                if (actor is Actor.User && actor.principal.role in Roles.PRIVILEGED) {
                    true
                } else {
                    findRoleOfMember(ctx, projectId, actor.username) in ProjectRole.ADMINS
                }
            }

            else -> false
        }

        if (!isAdmin) throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)

    }

    companion object : Loggable {
        override val log = logger()
    }
}
