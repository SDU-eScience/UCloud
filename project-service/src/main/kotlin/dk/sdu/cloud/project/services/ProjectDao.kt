package dk.sdu.cloud.project.services

import com.github.jasync.sql.db.RowData
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.project.api.Project
import dk.sdu.cloud.project.api.ProjectMember
import dk.sdu.cloud.project.api.ProjectRole
import dk.sdu.cloud.project.api.UserProjectSummary
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.db.async.*
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flow
import org.joda.time.DateTimeConstants
import org.joda.time.DateTimeZone
import org.joda.time.LocalDateTime
import org.joda.time.Period

data class ProjectForVerification(val projectId: String, val username: String, val role: ProjectRole)

class ProjectDao {
    suspend fun create(session: AsyncDBConnection, id: String, title: String, principalInvestigator: String) {
        session.insert(ProjectTable) {
            set(ProjectTable.id, id)
            set(ProjectTable.title, title)
            set(ProjectTable.createdAt, LocalDateTime.now())
            set(ProjectTable.modifiedAt, LocalDateTime.now())
        }

        session.insert(ProjectMemberTable) {
            set(ProjectMemberTable.username, principalInvestigator)
            set(ProjectMemberTable.role, ProjectRole.PI.name)
            set(ProjectMemberTable.project, id)
            set(ProjectMemberTable.createdAt, LocalDateTime.now())
            set(ProjectMemberTable.modifiedAt, LocalDateTime.now())
        }

        verifyMembership(session, id, "_project")
    }

    suspend fun delete(session: AsyncDBConnection, id: String) {
        session
            .sendPreparedStatement(
                {
                    setParameter("project", id)
                },
                """
                    delete from project_members
                    where project = ?project
                """
            )

        session
            .sendPreparedStatement(
                {
                    setParameter("project", id)
                },
                """
                    delete from projects  
                    where id = ?project
                """
            )
    }

    suspend fun findById(session: AsyncDBConnection, projectId: String): Project {
        val members = session
            .sendPreparedStatement(
                { setParameter("project", projectId) },
                "select * from project_members where project_id = ?project"
            )
            .rows
            .map { it.toProjectMember() }

        return session
            .sendPreparedStatement(
                { setParameter("project", projectId) },
                "select * from projects where id = ?project"
            )
            .rows
            .singleOrNull()
            ?.toProject(members)
            ?: throw ProjectException.NotFound()
    }

    suspend fun findByIdPrefix(session: AsyncDBConnection, prefix: String): List<String> {
        return session
            .sendPreparedStatement(
                { setParameter("project", "$prefix%") },
                "select id from projects where id like ?project"
            )
            .rows
            .map { it.getString(0)!! }
    }

    suspend fun addMember(session: AsyncDBConnection, projectId: String, member: ProjectMember) {
        session.insert(ProjectMemberTable) {
            set(ProjectMemberTable.username, member.username)
            set(ProjectMemberTable.role, member.role.name)
            set(ProjectMemberTable.project, projectId)
            set(ProjectMemberTable.createdAt, LocalDateTime.now())
            set(ProjectMemberTable.modifiedAt, LocalDateTime.now())
        }
    }

    suspend fun deleteMember(session: AsyncDBConnection, projectId: String, member: String) {
        session
            .sendPreparedStatement(
                {
                    setParameter("project", projectId)
                    setParameter("member", member)
                },
                """
                    delete from project_members
                    where project_id = ?project and username = ?member
                """
            )
    }

    suspend fun changeMemberRole(
        session: AsyncDBConnection,
        projectId: String,
        member: String,
        newRole: ProjectRole
    ) {
        session
            .sendPreparedStatement(
                {
                    setParameter("role", newRole.name)
                    setParameter("username", member)
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
    }

    suspend fun findRoleOfMember(session: AsyncDBConnection, projectId: String, member: String): ProjectRole? {
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

    suspend fun listProjectsForUser(
        session: AsyncDBConnection,
        pagination: NormalizedPaginationRequest?,
        user: String
    ): Page<UserProjectSummary> {
        val items = session
            .sendPreparedStatement(
                {
                    setParameter("username", user)
                    setParameter("offset", if (pagination == null) 0 else pagination.page * pagination.itemsPerPage)
                    setParameter("limit", pagination?.itemsPerPage ?: Int.MAX_VALUE)
                },
                """
                    select mem.role, p.id, p.title  
                    from 
                        project_members mem inner join projects p on mem.project_id = p.id
                    where mem.username = ?username
                    order by p.id
                    offset ?offset
                    limit ?limit
                """
            )
            .rows
            .map {
                val role = ProjectRole.valueOf(it.getString(0)!!)
                val id = it.getString(1)!!
                val title = it.getString(2)!!

                // TODO (Performance) Not ideal code
                val needsVerification = if (role.isAdmin()) {
                    shouldVerify(session, id)
                } else {
                    false
                }

                UserProjectSummary(id, title, ProjectMember(user, role), needsVerification)
            }

        val count = if (pagination == null) {
            items.size
        } else {
            session
                .sendPreparedStatement(
                    { setParameter("username", user) },
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

    suspend fun shouldVerify(session: AsyncDBConnection, project: String): Boolean {
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
                VERIFICATION_REQUIRED_EVERY_X_DAYS * DateTimeConstants.MILLIS_PER_DAY
    }

    suspend fun verifyMembership(session: AsyncDBConnection, project: String, verifiedBy: String) {
        if (!verifiedBy.startsWith("_")) {
            if (findRoleOfMember(session, project, verifiedBy)?.isAdmin() != true) {
                throw RPCException("Not found or permission denied", HttpStatusCode.Forbidden)
            }
        }

        session.insert(ProjectMembershipVerified) {
            set(ProjectMembershipVerified.projectId, project)
            set(ProjectMembershipVerified.verification, LocalDateTime.now())
            set(ProjectMembershipVerified.verifiedBy, verifiedBy)
        }
    }

    @UseExperimental(ExperimentalCoroutinesApi::class)
    suspend fun findProjectsInNeedOfVerification(session: AsyncDBConnection): Flow<ProjectForVerification> {
        return channelFlow {
            session.sendPreparedStatement(
                {
                    setParameter("days", VERIFICATION_REQUIRED_EVERY_X_DAYS)
                },
                """
                    declare c no scroll cursor for 
                    
                    select pm.project_id, pm.username, pm.role
                    from 
                         project_members pm,
                         (
                             select project_id
                             from project_membership_verification v
                             group by project_id
                             having max(verification) <= (now() - (?days || ' day')::interval)
                         ) as latest
                         
                    where 
                        pm.project_id = latest.project_id and 
                        (pm.role = 'PI' or pm.role = 'ADMIN');

                """
            )

            session.sendQuery("fetch forward 100 from c").rows.forEach {
                send(
                    ProjectForVerification(
                        it["project_id"] as String,
                        it["username"] as String,
                        ProjectRole.valueOf(it["role"] as String)
                    )
                )
            }
        }
    }

    private object ProjectMembershipVerified : SQLTable("project_membership_verification") {
        val projectId = text("project_id")
        val verification = timestamp("verification")
        val verifiedBy = text("verified_by")
    }

    private object ProjectTable : SQLTable("projects") {
        val id = text("id")
        val title = text("title")
        val createdAt = timestamp("created_at")
        val modifiedAt = timestamp("modified_at")
    }

    private fun RowData.toProject(members: List<ProjectMember>): Project = Project(
        getField(ProjectTable.id),
        getField(ProjectTable.title),
        members
    )

    private object ProjectMemberTable : SQLTable("project_members") {
        val username = text("username")
        val role = text("role")
        val project = text("project_id")
        val createdAt = timestamp("created_at")
        val modifiedAt = timestamp("modified_at")
    }

    private fun RowData.toProjectMember(): ProjectMember = ProjectMember(
        getField(ProjectMemberTable.username),
        ProjectRole.valueOf(getField(ProjectMemberTable.role))
    )

    companion object {
        const val VERIFICATION_REQUIRED_EVERY_X_DAYS = 30L
    }
}
