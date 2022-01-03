package dk.sdu.cloud.accounting.services.projects

import com.github.jasync.sql.db.postgresql.exceptions.GenericDatabaseException
import dk.sdu.cloud.Actor
import dk.sdu.cloud.Roles
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.events.EventProducer
import dk.sdu.cloud.project.api.ProjectEvent
import dk.sdu.cloud.project.api.ProjectRole
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.db.async.*
import io.ktor.http.HttpStatusCode
import java.util.*

object GroupTable : SQLTable("project.groups") {
    val id = text("id")
    val project = text("project")
    val title = text("title")
}

object GroupMembershipTable : SQLTable("project.group_members") {
    val group = text("group_id")
    val username = text("username")
}

class ProjectGroupService(
    private val projects: ProjectService,
    private val eventProducer: EventProducer<ProjectEvent>
) {
    suspend fun createGroup(
        ctx: DBContext,
        createdBy: String,
        projectId: String,
        group: String
    ): String {
        val id = UUID.randomUUID().toString()
        try {
            ctx.withSession { session ->
                projects.requireRole(session, createdBy, projectId, ProjectRole.ADMINS)

                session.insert(GroupTable) {
                    set(GroupTable.project, projectId) // TODO This needs to be a foreign key
                    set(GroupTable.title, group)
                    set(GroupTable.id, id)
                }

                eventProducer.produce(ProjectEvent.GroupCreated(projectId, group))
            }
        } catch (ex: GenericDatabaseException) {
            if (ex.errorMessage.fields['C'] == PostgresErrorCodes.UNIQUE_VIOLATION) {
                throw RPCException("Group already exists", HttpStatusCode.Conflict)
            }

            log.warn(ex.stackTraceToString())
            throw RPCException.fromStatusCode(HttpStatusCode.InternalServerError)
        }
        return id
    }

    suspend fun deleteGroups(
        ctx: DBContext,
        deletedBy: String,
        projectId: String,
        groups: Set<String>
    ) {
        ctx.withSession { session ->
            projects.requireRole(session, deletedBy, projectId, ProjectRole.ADMINS)

            val block: EnhancedPreparedStatement.() -> Unit = {
                setParameter("project", projectId)
                setParameter("groups", groups.toList())
            }

            session
                .sendPreparedStatement(
                    block,
                    """
                        with allowed_groups as (
                            select id 
                            from project.groups g
                            where
                                g.id in (select * from unnest(:groups::text[])) and
                                g.project = :project
                        )
                        
                        delete from project.group_members
                        where group_id in (select * from allowed_groups)
                    """
                )

            session.sendPreparedStatement(
                block,

                """
                    delete from project.groups
                    where 
                        id in (select * from unnest(?groups::text[])) and
                        project = :project
                """
            )
        }

        eventProducer.produce(groups.map { groupId ->
            ProjectEvent.GroupDeleted(
                projectId,
                groupId
            )
        })
    }

    suspend fun addMember(
        ctx: DBContext,
        addedBy: String,
        projectId: String,
        groupId: String,
        newMember: String
    ) {
        try {
            ctx.withSession { session ->
                projects.requireRole(session, addedBy, projectId, ProjectRole.ADMINS)

                session.insert(GroupMembershipTable) {
                    set(GroupMembershipTable.group, groupId)
                    set(GroupMembershipTable.username, newMember)
                }

                eventProducer.produce(
                    ProjectEvent.MemberAddedToGroup(
                        projectId,
                        newMember,
                        groupId
                    )
                )
            }
        } catch (ex: GenericDatabaseException) {
            if (ex.errorMessage.fields['C'] == PostgresErrorCodes.UNIQUE_VIOLATION) {
                throw RPCException("Member is already in group", HttpStatusCode.Conflict)
            }

            log.warn(ex.stackTraceToString())
            throw RPCException.fromStatusCode(HttpStatusCode.InternalServerError)
        }
    }

    suspend fun removeMember(
        ctx: DBContext,
        removedBy: String,
        projectId: String,
        groupId: String,
        memberToRemove: String
    ) {
        ctx.withSession { session ->
            projects.requireRole(session, removedBy, projectId, ProjectRole.ADMINS)

            session
                .sendPreparedStatement(
                    {
                        setParameter("username", memberToRemove)
                        setParameter("group", groupId)
                    },
                    """
                        delete from project.group_members
                        where
                            username = :username and
                            group_id = :group
                    """
                )

            eventProducer.produce(
                ProjectEvent.MemberRemovedFromGroup(
                    projectId,
                    memberToRemove,
                    groupId
                )
            )
        }
    }


    suspend fun rename(
        ctx: DBContext,
        actor: Actor,
        groupId: String,
        newName: String
    ) {
        ctx.withSession { session ->

            val project = session.sendPreparedStatement(
                {
                    setParameter("group", groupId)
                },
                """select project from project.groups where id = :group"""
            ).rows

            val projectId = if (project.isNotEmpty()) {
                project[0]["project"].toString()
            } else {
                throw RPCException.fromStatusCode(HttpStatusCode.NotFound, "Project group not found")
            }

            val isAdmin = when (actor) {
                Actor.System -> true

                is Actor.User, is Actor.SystemOnBehalfOfUser -> {
                    if (actor is Actor.User && actor.principal.role in Roles.PRIVILEGED) {
                        true
                    } else {
                        projects.findRoleOfMember(ctx, projectId, actor.username) in ProjectRole.ADMINS
                    }
                }
                else -> false
            }

            if (!isAdmin) throw RPCException.fromStatusCode(HttpStatusCode.Unauthorized)

            session.sendPreparedStatement(
                {
                    setParameter("group", groupId)
                    setParameter("newTitle", newName)
                },
                """update project.groups set title = :newTitle where id = :group"""
            )
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}