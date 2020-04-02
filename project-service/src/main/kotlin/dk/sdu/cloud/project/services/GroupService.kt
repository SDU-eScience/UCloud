package dk.sdu.cloud.project.services

import com.github.jasync.sql.db.postgresql.exceptions.GenericDatabaseException
import dk.sdu.cloud.SecurityPrincipal
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.events.EventProducer
import dk.sdu.cloud.project.api.*
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.async.AsyncDBConnection
import dk.sdu.cloud.service.db.withTransaction
import dk.sdu.cloud.service.stackTraceToString
import io.ktor.http.HttpStatusCode

class GroupService(
    private val db: DBSessionFactory<AsyncDBConnection>,
    private val groups: GroupDao,
    private val projects: ProjectDao,
    private val eventProducer: EventProducer<ProjectEvent>,
    private val serviceClient: AuthenticatedClient
) {
    suspend fun createGroup(principal: SecurityPrincipal, projectId: String, group: String) {
        try {
            val project = db.withTransaction { session ->
                val role =
                    projects.findRoleOfMember(session, projectId, principal.username) ?: return@withTransaction null
                if (!role.isAdmin()) return@withTransaction null
                val project = projects.findById(session, projectId)
                groups.createGroup(session, projectId, group)
                project
            } ?: throw ProjectException.CantAddGroup()

            eventProducer.produce(ProjectEvent.GroupCreated(project, group))
        } catch (ex: GenericDatabaseException) {
            if (ex.errorMessage.fields['C'] == ALREADY_EXISTS_PSQL) {
                throw RPCException("Group already exists", HttpStatusCode.Conflict)
            }

            log.warn(ex.stackTraceToString())
            throw RPCException.fromStatusCode(HttpStatusCode.InternalServerError)
        }
    }

    suspend fun deleteGroups(principal: SecurityPrincipal, projectId: String, groupNames: Set<String>) {
        val project = db.withTransaction { session ->
            val role = projects.findRoleOfMember(session, projectId, principal.username) ?: return@withTransaction null
            if (!role.isAdmin()) return@withTransaction null
            val project = projects.findById(session, projectId)
            groups.deleteGroups(session, projectId, groupNames)
            project
        } ?: throw ProjectException.CantDeleteGroup()

        eventProducer.produce(groupNames.map { groupName -> ProjectEvent.GroupDeleted(project, groupName) })
    }

    suspend fun listGroups(
        principal: SecurityPrincipal,
        project: String
    ): List<String> {
        return db.withTransaction { session ->
            projects.findRoleOfMember(session, project, principal.username) ?: return@withTransaction null
            groups.listGroups(session, project)
        } ?: throw RPCException("Not found", HttpStatusCode.NotFound)
    }

    suspend fun listGroupsWithSummary(
        principal: SecurityPrincipal,
        project: String,
        pagination: NormalizedPaginationRequest
    ): Page<GroupWithSummary> {
        return db.withTransaction { session ->
            projects.findRoleOfMember(session, project, principal.username) ?: return@withTransaction null
            groups.listGroupsWithSummary(session, project, pagination)
        } ?: throw RPCException("Not found", HttpStatusCode.NotFound)
    }

    suspend fun addMember(principal: SecurityPrincipal, projectId: String, group: String, member: String) {
        try {
            val project: Project = db.withTransaction { session ->
                val role =
                    projects.findRoleOfMember(session, projectId, principal.username) ?: return@withTransaction null
                if (!role.isAdmin()) return@withTransaction null
                val project = projects.findById(session, projectId)
                groups.addMemberToGroup(session, projectId, member, group)
                project
            } ?: throw ProjectException.CantChangeGroupMember()

            eventProducer.produce(ProjectEvent.MemberAddedToGroup(project, member, group))
        } catch (ex: GenericDatabaseException) {
            if (ex.errorMessage.fields['C'] == ALREADY_EXISTS_PSQL) {
                throw RPCException("Member is already in group", HttpStatusCode.Conflict)
            }

            log.warn(ex.stackTraceToString())
            throw RPCException.fromStatusCode(HttpStatusCode.InternalServerError)
        }
    }

    suspend fun removeMember(principal: SecurityPrincipal, projectId: String, group: String, member: String) {
        val project: Project = db.withTransaction { session ->
            val role = projects.findRoleOfMember(session, projectId, principal.username) ?: return@withTransaction null
            if (!role.isAdmin()) return@withTransaction null
            val project = projects.findById(session, projectId)
            groups.removeMemberFromGroup(session, projectId, member, group)
            project
        } ?: throw ProjectException.CantChangeGroupMember()

        eventProducer.produce(ProjectEvent.MemberRemovedFromGroup(project, member, group))
    }

    suspend fun listGroupMembers(
        principal: SecurityPrincipal,
        projectId: String,
        group: String,
        pagination: NormalizedPaginationRequest
    ): Page<UserGroupSummary> {
        db.withTransaction { session ->
            val isAdmin = projects.findRoleOfMember(session, projectId, principal.username)?.isAdmin() == true
            if (!isAdmin) throw ProjectException.Unauthorized()
            return groups.listGroupMembers(session, pagination, projectId, group)
        }
    }

    suspend fun updateGroupName(
        principal: SecurityPrincipal,
        projectId: String,
        oldGroupName: String,
        newGroupName: String
    ) {
        val project: Project = db.withTransaction { session ->
            val role = projects.findRoleOfMember(session, projectId, principal.username) ?: return@withTransaction null
            if (!role.isAdmin()) return@withTransaction null
            val project = projects.findById(session, projectId)
            groups.renameGroup(session, projectId, oldGroupName, newGroupName)
            project
        } ?: throw ProjectException.CantChangeGroupMember()

        eventProducer.produce(ProjectEvent.GroupRenamed(project, oldGroupName, newGroupName))
    }

    suspend fun isMemberQuery(
        queries: List<IsMemberQuery>
    ): List<Boolean> {
        return db.withTransaction { session ->
            groups.isMemberQuery(session, queries)
        }
    }

    suspend fun exists(
        project: String,
        group: String
    ): Boolean {
        return db.withTransaction { session ->
            groups.exists(session, project, group)
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}

