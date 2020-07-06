package dk.sdu.cloud.project.services

import com.github.jasync.sql.db.RowData
import dk.sdu.cloud.Roles
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.project.api.GroupWithSummary
import dk.sdu.cloud.project.api.IngoingInvite
import dk.sdu.cloud.project.api.IsMemberQuery
import dk.sdu.cloud.project.api.LookupPrincipalInvestigatorResponse
import dk.sdu.cloud.project.api.OutgoingInvite
import dk.sdu.cloud.project.api.Project
import dk.sdu.cloud.project.api.ProjectMember
import dk.sdu.cloud.project.api.ProjectRole
import dk.sdu.cloud.project.api.UserGroupSummary
import dk.sdu.cloud.project.api.UserProjectSummary
import dk.sdu.cloud.project.api.UserStatusInProject
import dk.sdu.cloud.project.api.UserStatusResponse
import dk.sdu.cloud.service.Actor
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.EnhancedPreparedStatement
import dk.sdu.cloud.service.db.async.getField
import dk.sdu.cloud.service.db.async.getFieldNullable
import dk.sdu.cloud.service.db.async.paginatedQuery
import dk.sdu.cloud.service.db.async.sendPreparedStatement
import dk.sdu.cloud.service.db.async.withSession
import dk.sdu.cloud.service.mapItems
import dk.sdu.cloud.service.offset
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import org.joda.time.DateTimeConstants

data class ProjectForVerification(
    val projectId: String,
    val username: String,
    val role: ProjectRole,
    val title: String
)

class QueryService(
    private val projects: ProjectService
) {
    suspend fun isMemberOfGroup(
        ctx: DBContext,
        queries: List<IsMemberQuery>
    ): List<Boolean> {
        return ctx.withSession { session ->
            val response = BooleanArray(queries.size)

            session
                .sendPreparedStatement(
                    {
                        setParameter("projects", queries.map { it.project })
                        setParameter("groups", queries.map { it.group })
                        setParameter("usernames", queries.map { it.username })
                    },
                    """
                        select *
                        from group_members gm
                        where
                            (gm.project, gm.the_group, gm.username) in (
                                select
                                    unnest(:projects::text[]),
                                    unnest(:groups::text[]),
                                    unnest(:usernames::text[])
                            )
                    """
                )
                .rows
                .forEach { row ->
                    val summary = UserGroupSummary(
                        row.getField(GroupMembershipTable.project),
                        row.getField(GroupMembershipTable.group),
                        row.getField(GroupMembershipTable.username)
                    )

                    val idx = queries.indexOfFirst {
                        it.group == summary.group &&
                            it.project == summary.projectId &&
                            it.username == summary.username
                    }

                    if (idx == -1) {
                        log.warn("Could not find matching row in query. This probably shouldn't happen!")
                        log.warn("row = $row, queries = $queries")
                    } else {
                        response[idx] = true
                    }
                }

            response.toList()
        }
    }

    suspend fun groupExists(ctx: DBContext, projectId: String, groupId: String): Boolean {
        return ctx.withSession { session ->
            session
                .sendPreparedStatement(
                    {
                        setParameter("group", groupId)
                        setParameter("project", projectId)
                    },
                    """
                        select *
                        from groups
                        where
                            the_group = :group and
                            project = :project
                    """
                )
                .rows
                .size > 0
        }
    }

    suspend fun groupsCount(ctx: DBContext, projectId: String): Long {
        return ctx.withSession { session ->
            session.sendPreparedStatement(
                {
                    setParameter("project", projectId)
                },
                """
                    select count(*) from project.groups where project = :project
                """
            ).rows.singleOrNull()?.getLong(0) ?: 0
        }
    }

    suspend fun listGroupMembers(
        ctx: DBContext,
        requestedBy: String?,
        projectId: String,
        groupId: String,
        pagination: NormalizedPaginationRequest?
    ): Page<UserGroupSummary> {
        return ctx.withSession { session ->
            val requestedByRole =
                if (requestedBy == null) ProjectRole.ADMIN
                else projects.requireRole(session, requestedBy, projectId, ProjectRole.ALL)

            val params: EnhancedPreparedStatement.() -> Unit = {
                setParameter("project", projectId)
                setParameter("group", groupId)
                setParameter("userIsAdmin", requestedByRole.isAdmin())
                setParameter("username", requestedBy)
            }

            val count = session
                .sendPreparedStatement(
                    params,
                    """
                        select count(*) 
                        from group_members 
                        where 
                            project = :project and
                            (:group = '' or the_group = :group) and
                            (:username::text is null or check_group_acl(:username, :userIsAdmin, :group))
                    """
                )
                .rows
                .map { it.getLong(0)!!.toInt() }
                .singleOrNull() ?: 0

            val items = session
                .sendPreparedStatement(
                    {
                        params()
                        setParameter("limit", pagination?.itemsPerPage ?: Int.MAX_VALUE)
                        setParameter("offset", pagination?.offset ?: 0)
                    },
                    """
                        select *
                        from group_members
                        where
                            project = :project and 
                            (:group = '' or the_group = :group) and
                            (:username::text is null or check_group_acl(:username, :userIsAdmin, :group))
                        order by
                            project, the_group, username
                        limit :limit
                        offset :offset
                    """
                )
                .rows
                .map {
                    UserGroupSummary(
                        it.getField(GroupMembershipTable.project),
                        it.getField(GroupMembershipTable.group),
                        it.getField(GroupMembershipTable.username)
                    )
                }

            Page(count, pagination?.itemsPerPage ?: items.size, pagination?.page ?: 0, items)
        }
    }

    suspend fun listGroups(
        ctx: DBContext,
        requestedBy: String,
        projectId: String,
        pagination: NormalizedPaginationRequest
    ): Page<GroupWithSummary> {
        return ctx.withSession { session ->
            val requestedByRole = projects.requireRole(session, requestedBy, projectId, ProjectRole.ALL)

            val params: EnhancedPreparedStatement.() -> Unit = {
                setParameter("project", projectId)
                setParameter("userIsAdmin", requestedByRole.isAdmin())
                setParameter("username", requestedBy)
            }

            // Count how many groups there are. We will use this for pagination
            val groupCount = session
                .sendPreparedStatement(
                    params,
                    """
                        select count(g.the_group)
                        from groups g
                        where
                            g.project = :project and
                            check_group_acl(:username, :userIsAdmin, g.the_group)
                    """
                )
                .rows
                .map { it.getLong(0)!!.toInt() }
                .singleOrNull() ?: 0

            val items = session
                .sendPreparedStatement(
                    {
                        params()
                        setParameter("limit", pagination.itemsPerPage)
                        setParameter("offset", pagination.itemsPerPage * pagination.page)
                    },
                    """
                        select g.the_group, count(gm.username)
                        from groups g left join group_members gm on g.project = gm.project and g.the_group = gm.the_group
                        where
                              g.project = :project and
                              check_group_acl(:username, :userIsAdmin, g.the_group)
                        group by g.the_group
                        order by g.the_group
                        offset :offset
                        limit :limit
                    """
                )
                .rows
                .map { row ->
                    val groupName = row.getString(0)!!
                    val memberCount = row.getLong(1)?.toInt() ?: 0

                    GroupWithSummary(groupName, memberCount)
                }

            Page(groupCount, pagination.itemsPerPage, pagination.page, items)
        }
    }

    suspend fun membershipSearch(
        ctx: DBContext,
        requestedBy: String,
        projectId: String,
        query: String,
        pagination: NormalizedPaginationRequest,
        notInGroup: String? = null
    ): Page<ProjectMember> {
        return ctx.withSession { session ->
            projects.requireRole(session, requestedBy, projectId, ProjectRole.ALL)

            // TODO This gets quite ugly because we need to order by which paginated query doesn't currently support
            val parameters: EnhancedPreparedStatement.() -> Unit = {
                setParameter("project", projectId)
                setParameter("usernameQuery", "%${query}%")
                if (notInGroup != null) {
                    setParameter("notInGroup", notInGroup)
                }
            }

            val baseQuery = if (notInGroup == null) {
                """
                    from project_members pm
                    where
                          pm.project_id = :project and
                          pm.username ilike :usernameQuery
                """
            } else {
                """
                    from project_members pm
                    where
                    pm.project_id = :project and
                    pm.username ilike :usernameQuery and
                    pm.username not in (
                        select pm.username
                            from group_members gm
                            where
                            gm.project = pm.project_id and
                            gm.the_group = :notInGroup and
                            gm.username = pm.username
                    )
                """
            }

            val itemsInTotal = session.sendPreparedStatement(
                parameters,
                "select count(*) $baseQuery"
            ).rows.singleOrNull()?.getLong(0) ?: 0

            @Suppress("SqlResolve") val items = session.sendPreparedStatement(
                {
                    parameters()
                    setParameter("limit", pagination.itemsPerPage)
                    setParameter("offset", pagination.itemsPerPage * pagination.page)
                },
                //language=sql
                """
                    select * $baseQuery 
                    order by 
                        CASE role
                            WHEN 'PI' THEN 1
                            WHEN 'ADMIN' THEN 2
                            WHEN 'USER' THEN 3
                            ELSE 4
                        END, username, created_at
                    limit :limit offset :offset
                """
            )

            Page(
                itemsInTotal.toInt(),
                pagination.itemsPerPage,
                pagination.page,
                items.rows.map { row ->
                    val username = row.getString("username")!!
                    val role = row.getString("role")!!.let { ProjectRole.valueOf(it) }
                    ProjectMember(username, role)
                }
            )
        }
    }

    suspend fun membersCount(
        ctx: DBContext,
        requestedBy: String,
        projectId: String
    ): Long {
        return ctx.withSession { session ->
            projects.requireRole(session, requestedBy, projectId, ProjectRole.ADMINS)

            session.sendPreparedStatement(
                {
                    setParameter("projectId", projectId)
                },
                """
                    select count(*) from project.project_members where project_id = :projectId
                """.trimIndent()
            ).rows.singleOrNull()?.getLong(0) ?: 0
        }
    }

    suspend fun summarizeMembershipForUser(
        ctx: DBContext,
        username: String
    ): UserStatusResponse {
        return ctx.withSession { session ->
            val projectStatus = session
                .sendPreparedStatement(
                    {
                        setParameter("username", username)
                    },
                    """
                        select pm.*, p.title as title, p.parent as parent
                        from project_members pm, projects p
                        where 
                            pm.username = :username and
                            p.id = pm.project_id 
                    """
                )
                .rows
                .map {
                    UserStatusInProject(
                        it.getField(ProjectMemberTable.project),
                        it.getString("title")!!,
                        ProjectMember(
                            username,
                            it.getField(ProjectMemberTable.role).let { ProjectRole.valueOf(it) }
                        ),
                        it.getString("parent")
                    )
                }

            val groupStatus = session
                .sendPreparedStatement(
                    {
                        setParameter("username", username)
                    },
                    """
                        select *
                        from group_members
                        where username = :username
                    """
                )
                .rows
                .map {
                    UserGroupSummary(
                        it.getField(GroupMembershipTable.project),
                        it.getField(GroupMembershipTable.group),
                        username
                    )
                }

            UserStatusResponse(projectStatus, groupStatus)
        }
    }

    suspend fun listFavoriteProjects(
        ctx: DBContext,
        username: String,
        showArchived: Boolean,
        pagination: NormalizedPaginationRequest
    ): Page<UserProjectSummary> {
        return ctx.withSession { session ->
            val params: EnhancedPreparedStatement.() -> Unit = {
                setParameter("username", username)
                setParameter("showArchived", showArchived)
            }

            val items = session
                .sendPreparedStatement(
                    {
                        params()
                        setParameter("offset", pagination.page * pagination.itemsPerPage)
                        setParameter("limit", pagination.itemsPerPage)
                    },
                    """
                        select 
                            mem.role, 
                            p.id, 
                            p.title, 
                            is_favorite(mem.username, p.id) as is_fav,
                            p.archived,
                            p.parent
                        from 
                            project_members mem inner join projects p on mem.project_id = p.id
                        where 
                            mem.username = :username and
                            (:showArchived or p.archived = false) and
                            (is_favorite(mem.username, p.id))
                        order by is_fav desc, p.id
                        offset :offset
                        limit :limit
                    """
                )
                .rows
                .map {
                    val role = ProjectRole.valueOf(it.getString(0)!!)
                    val id = it.getString(1)!!
                    val title = it.getString(2)!!
                    val isFavorite = it.getBoolean(3)!!
                    val isArchived = it.getBoolean(4)!!
                    val parent = it.getString(5)

                    // TODO (Performance) Not ideal code
                    val needsVerification = if (role.isAdmin()) {
                        shouldVerify(session, id)
                    } else {
                        false
                    }

                    UserProjectSummary(
                        id,
                        title,
                        ProjectMember(username, role),
                        needsVerification,
                        isFavorite,
                        isArchived,
                        parent
                    )
                }

            Page(items.size, pagination.itemsPerPage, pagination.page, items)
        }
    }

    suspend fun listProjects(
        ctx: DBContext,
        username: String,
        showArchived: Boolean,
        pagination: NormalizedPaginationRequest?,
        projectId: String? = null,
        noFavorites: Boolean
    ): Page<UserProjectSummary> {
        return ctx.withSession { session ->
            val params: EnhancedPreparedStatement.() -> Unit = {
                setParameter("username", username)
                setParameter("showArchived", showArchived)
                setParameter("projectId", projectId)
                setParameter("noFavorites", noFavorites)
            }
            val items = session
                .sendPreparedStatement(
                    {
                        params()
                        setParameter("offset", if (pagination == null) 0 else pagination.page * pagination.itemsPerPage)
                        setParameter("limit", pagination?.itemsPerPage ?: Int.MAX_VALUE)
                    },
                    """
                        select 
                            mem.role, 
                            p.id, 
                            p.title, 
                            is_favorite(mem.username, p.id) as is_fav,
                            p.archived,
                            p.parent
                        from 
                            project_members mem inner join projects p on mem.project_id = p.id
                        where 
                            mem.username = :username and
                            (:showArchived or p.archived = false) and
                            (:projectId::text is null or p.id = :projectId) and
                            (not :noFavorites or not is_favorite(mem.username, p.id))
                        order by is_fav desc, p.id
                        offset :offset
                        limit :limit
                    """
                )
                .rows
                .map {
                    val role = ProjectRole.valueOf(it.getString(0)!!)
                    val id = it.getString(1)!!
                    val title = it.getString(2)!!
                    val isFavorite = it.getBoolean(3)!!
                    val isArchived = it.getBoolean(4)!!
                    val parent = it.getString(5)

                    // TODO (Performance) Not ideal code
                    val needsVerification = if (role.isAdmin()) {
                        shouldVerify(session, id)
                    } else {
                        false
                    }

                    UserProjectSummary(
                        id,
                        title,
                        ProjectMember(username, role),
                        needsVerification,
                        isFavorite,
                        isArchived,
                        parent
                    )
                }

            val count = if (pagination == null) {
                items.size
            } else {
                session
                    .sendPreparedStatement(
                        {
                            params()
                        },
                        """
                            select count(*)
                            from
                                project_members mem inner join projects p on mem.project_id = p.id
                            where 
                                username = :username and
                                (:showArchived or p.archived = false) and
                                (:projectId::text is null or p.id = :projectId) and
                                (not :noFavorites or not is_favorite(mem.username, p.id))
                        """
                    )
                    .rows
                    .map { it.getLong(0)!!.toInt() }
                    .singleOrNull() ?: items.size
            }

            Page(count, pagination?.itemsPerPage ?: count, pagination?.page ?: 0, items)
        }
    }

    suspend fun shouldVerify(ctx: DBContext, project: String): Boolean {
        return ctx.withSession { session ->
            val latestVerification = session
                .sendPreparedStatement(
                    {
                        setParameter("project", project)
                    },
                    """
                        select * 
                        from project_membership_verification 
                        where project_id = :project  
                        order by verification desc
                        limit 1
                    """
                )
                .rows
                .map { it.getField(ProjectMembershipVerified.verification) }
                .singleOrNull()

            if (latestVerification == null) {
                projects.verifyMembership(session, "_project", project)
                return@withSession false
            }

            return@withSession (System.currentTimeMillis() - latestVerification.toTimestamp()) >
                VERIFICATION_REQUIRED_EVERY_X_DAYS * DateTimeConstants.MILLIS_PER_DAY
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun findProjectsInNeedOfVerification(ctx: DBContext): Flow<ProjectForVerification> {
        return ctx.withSession { session ->
            channelFlow {
                session.sendPreparedStatement(
                    {
                        setParameter("days", VERIFICATION_REQUIRED_EVERY_X_DAYS)
                    },
                    """
                        declare c no scroll cursor for 
                        
                        select pm.project_id, pm.username, pm.role, p.title
                        from 
                             projects p,
                             project_members pm,
                             (
                                 select project_id
                                 from project_membership_verification v
                                 group by project_id
                                 having max(verification) <= (now() - (:days || ' day')::interval)
                             ) as latest
                             
                        where 
                            pm.project_id = p.id and
                            pm.project_id = latest.project_id and 
                            (pm.role = 'PI' or pm.role = 'ADMIN');

                    """
                )

                session.sendQuery("fetch forward 100 from c").rows.forEach {
                    send(
                        ProjectForVerification(
                            it["project_id"] as String,
                            it["username"] as String,
                            ProjectRole.valueOf(it["role"] as String),
                            it["title"] as String
                        )
                    )
                }
            }
        }
    }

    suspend fun listOutgoingInvites(
        ctx: DBContext,
        requestedBy: String,
        projectId: String,
        pagination: NormalizedPaginationRequest
    ): Page<OutgoingInvite> {
        return ctx.withSession { session ->
            projects.requireRole(ctx, requestedBy, projectId, ProjectRole.ADMINS)

            session
                .paginatedQuery(
                    pagination,
                    {
                        setParameter("project", projectId)
                    },
                    """
                        from invites 
                        where project_id = :project
                    """
                )
                .mapItems {
                    OutgoingInvite(
                        it.getField(ProjectInvite.username),
                        it.getField(ProjectInvite.invitedBy),
                        it.getField(ProjectInvite.createdAt).toTimestamp()
                    )
                }
        }
    }

    suspend fun listIngoingInvites(
        ctx: DBContext,
        requestedBy: String,
        pagination: NormalizedPaginationRequest
    ): Page<IngoingInvite> {
        return ctx.withSession { session ->
            session
                .paginatedQuery(
                    pagination,
                    {
                        setParameter("username", requestedBy)
                    },
                    """
                        from invites 
                        where username = :username
                    """
                )
                .mapItems {
                    IngoingInvite(
                        it.getField(ProjectInvite.projectId),
                        it.getField(ProjectInvite.invitedBy),
                        it.getField(ProjectInvite.createdAt).toTimestamp()
                    )
                }
        }
    }

    suspend fun exists(
        ctx: DBContext,
        projectId: String
    ): Boolean {
        return ctx.withSession { session ->
            session
                .sendPreparedStatement(
                    { setParameter("projectId", projectId) },
                    "select count(*) from projects where id = :projectId"
                )
                .rows
                .single()
                .let { it.getLong(0)!! } > 0L
        }
    }

    suspend fun findProject(
        ctx: DBContext,
        actor: Actor,
        id: String
    ): Project {
        return ctx.withSession { session ->
            when (actor) {
                Actor.System -> {
                    // Allowed
                }

                is Actor.User, is Actor.SystemOnBehalfOfUser -> {
                    if (actor is Actor.User && actor.principal.role in Roles.PRIVILEGED) {
                        // Allowed
                    } else {
                        projects.requireRole(ctx, actor.username, id, ProjectRole.ALL)
                    }
                }
            }

            session
                .sendPreparedStatement(
                    { setParameter("id", id) },
                    "select * from projects where id = :id"
                )
                .rows
                .singleOrNull()
                ?.toProject()
                ?: throw ProjectException.NotFound()
        }
    }

    suspend fun listSubProjects(
        ctx: DBContext,
        pagination: NormalizedPaginationRequest?,
        actor: Actor,
        id: String
    ): Page<Project> {
        return ctx.withSession { session ->
            val isAdmin = when (actor) {
                Actor.System -> true

                is Actor.User, is Actor.SystemOnBehalfOfUser -> {
                    if (actor is Actor.User && actor.principal.role in Roles.PRIVILEGED) {
                        true
                    } else {
                        projects.findRoleOfMember(ctx, id, actor.username) in ProjectRole.ADMINS
                    }
                }
            }

            if (isAdmin) {
                session
                    .paginatedQuery(
                        pagination,
                        { setParameter("id", id) },
                        "from projects where parent = :id"
                    )
                    .mapItems { it.toProject() }
            } else {
                val params: EnhancedPreparedStatement.() -> Unit = {
                    setParameter("id", id)
                    setParameter("username", actor.username)
                    setParameter("offset", pagination?.offset ?: 0)
                    setParameter("limit", pagination?.itemsPerPage ?: Int.MAX_VALUE)
                }

                val count =
                    if (pagination == null) null
                    else {
                        session
                            .sendPreparedStatement(
                                params,

                                """
                            select count(p.id)
                            from projects p, project_members pm
                            where
                                p.parent = :id and
                                pm.project_id = p.id and
                                pm.username = :username and
                                (pm.role = 'ADMIN' or pm.role = 'PI')
                        """
                            )
                            .rows
                            .single()
                            .getLong(0) ?: 0L
                    }

                val items = session
                    .sendPreparedStatement(
                        params,

                        """
                            select p.*
                            from projects p, project_members pm
                            where
                                p.parent = :id and
                                pm.project_id = p.id and
                                pm.username = :username and
                                (pm.role = 'ADMIN' or pm.role = 'PI')
                            offset :offset
                            limit :limit
                        """
                    )
                    .rows
                    .map { it.toProject() }

                val itemsInTotal = count?.toInt() ?: items.size
                Page.forRequest(pagination, itemsInTotal, items)
            }
        }
    }

    suspend fun subProjectsCount(
        ctx: DBContext,
        requestedBy: String,
        projectId: String
    ): Long {
        return ctx.withSession { session ->
            projects.requireRole(session, requestedBy, projectId, ProjectRole.ADMINS)

            session.sendPreparedStatement(
                {
                    setParameter("projectId", projectId)
                },
                """
                    select count(*) from project.projects where parent = :projectId
                """.trimIndent()
            ).rows.singleOrNull()?.getLong(0) ?: 0
        }
    }

    suspend fun viewAncestors(
        ctx: DBContext,
        actor: Actor,
        projectId: String
    ): List<Project> {
        val resultList = ArrayList<Project>()
        ctx.withSession { session ->
            var currentProject: Result<Project> = runCatching { findProject(session, actor, projectId) }
            while (currentProject.isSuccess) {
                val nextProject = currentProject.getOrThrow()
                resultList.add(nextProject)

                val parent = nextProject.parent ?: break
                currentProject = runCatching { findProject(session, actor, parent) }
            }

            val ex = currentProject.exceptionOrNull()
            if (ex != null) {
                if (ex is RPCException &&
                    ex.httpStatusCode in setOf(HttpStatusCode.NotFound, HttpStatusCode.Forbidden)
                ) {
                    // All good, we expected one of these
                } else {
                    // Not good, rethrow to caller
                    throw ex
                }
            }
        }
        return resultList.asReversed()
    }

    private fun RowData.toProject(): Project {
        return Project(
            getField(ProjectTable.id),
            getField(ProjectTable.title),
            getFieldNullable(ProjectTable.parent),
            getField(ProjectTable.archived)
        )
    }

    suspend fun lookupPrincipalInvestigator(
        ctx: DBContext,
        actor: Actor,
        projectId: String
    ): LookupPrincipalInvestigatorResponse {
        return ctx.withSession { session ->
            findProject(session, actor, projectId)

            val pi = session
                .sendPreparedStatement(
                    { setParameter("projectId", projectId) },
                    "select username from project_members where project_id = :projectId and role = 'PI' limit 1"
                )
                .rows.firstOrNull()?.getString(0) ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound)

            LookupPrincipalInvestigatorResponse(pi)
        }
    }

    suspend fun lookupAdmins(
        ctx: DBContext,
        actor: Actor,
        projectId: String
    ): List<ProjectMember> {
        if (actor !is Actor.System && !(actor is Actor.User && actor.principal.role in Roles.PRIVILEGED)) {
            throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)
        }

        return ctx.withSession { session ->
            val (pi, admins) = projects.getPIAndAdminsOfProject(session, projectId)
            admins.map { ProjectMember(it, ProjectRole.ADMIN) } + ProjectMember(pi, ProjectRole.PI)
        }
    }

    companion object : Loggable {
        override val log = logger()
        const val VERIFICATION_REQUIRED_EVERY_X_DAYS = 30L
    }
}
