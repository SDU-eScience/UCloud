package dk.sdu.cloud.project.services

import com.github.jasync.sql.db.postgresql.exceptions.GenericDatabaseException
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.events.EventProducer
import dk.sdu.cloud.project.api.ProjectEvent
import dk.sdu.cloud.project.api.ProjectRole
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.db.async.*
import dk.sdu.cloud.service.stackTraceToString
import io.ktor.http.HttpStatusCode

object GroupTable : SQLTable("groups") {
    val project = text("project")
    val group = text("title")
}

object GroupMembershipTable : SQLTable("group_members") {
    val group = text("group_id")
    val username = text("username")
}

class GroupService(
    private val projects: ProjectService,
    private val eventProducer: EventProducer<ProjectEvent>
) {
    suspend fun createGroup(
        ctx: DBContext,
        createdBy: String,
        projectId: String,
        group: String
    ) {
        try {
            ctx.withSession { session ->
                projects.requireRole(session, createdBy, projectId, ProjectRole.ADMINS)

                session.insert(GroupTable) {
                    set(GroupTable.project, projectId) // TODO This needs to be a foreign key
                    set(GroupTable.group, group)
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
    }

    suspend fun deleteGroups(
        ctx: DBContext,
        deletedBy: String,
        projectId: String,
        groups: Set<String>
    ) {
        ctx.withSession { session ->
            projects.requireRole(session, deletedBy, projectId, ProjectRole.ADMINS)

            session.sendPreparedStatement(
                {
                    setParameter("project", projectId)
                    setParameter("groups", groups.toList())
                },

                """
                    delete from groups
                    where 
                        id in (select * from unnest(?groups::text[]))
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
                        delete from group_members
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

    companion object : Loggable {
        override val log = logger()
    }
}