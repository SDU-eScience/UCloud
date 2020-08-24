package dk.sdu.cloud.project.services

import com.github.jasync.sql.db.RowData
import dk.sdu.cloud.Roles
import dk.sdu.cloud.SecurityPrincipal
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.project.api.*
import dk.sdu.cloud.service.*
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.EnhancedPreparedStatement
import dk.sdu.cloud.service.db.async.getField
import dk.sdu.cloud.service.db.async.getFieldNullable
import dk.sdu.cloud.service.db.async.paginatedQuery
import dk.sdu.cloud.service.db.async.sendPreparedStatement
import dk.sdu.cloud.service.db.async.withSession
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
                        setParameter("groups", queries.map { it.group })
                        setParameter("usernames", queries.map { it.username })
                    },
                    """
                        select *
                        from group_members gm
                        inner join groups g on gm.group_id = g.id
                        where
                            (gm.group_id, gm.username) in (
                                select
                                    unnest(:groups::text[]),
                                    unnest(:usernames::text[])
                            )
                    """
                )
                .rows
                .forEach { row ->
                    val summary = UserGroupSummary(
                        row.getField(GroupTable.project),
                        row.getField(GroupMembershipTable.group),
                        row.getField(GroupMembershipTable.username)
                    )

                    val idx = queries.indexOfFirst {
                        it.group == summary.group &&
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
                            id = :group and
                            project = :project
                    """
                )
                .rows
                .size > 0
        }
    }

    suspend fun groupsCount(ctx: DBContext, user: SecurityPrincipal, projectId: String): Long {
        if(isAdminOrPIOfProject(ctx, user.username, projectId)) {
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
        } else throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)
    }

    suspend fun viewGroup(ctx: DBContext, requestedBy: String, projectId: String, groupId: String): GroupWithSummary {
        return ctx.withSession { session ->
            val requestedByRole =
                if (requestedBy == null) ProjectRole.ADMIN
                else projects.requireRole(session, requestedBy, projectId, ProjectRole.ALL)

            val group = session.sendPreparedStatement(
                {
                    setParameter("group", groupId)
                    setParameter("project", projectId)
                    setParameter("username", requestedBy)
                    setParameter("userIsAdmin", requestedByRole.isAdmin())
                },
                """
                    select g.id, g.title, count(gm.username)
                    from groups g left join group_members gm on g.id = gm.group_id
                    where
                        id = :group and 
                            check_group_acl(:username, :userIsAdmin, g.id)
                    group by g.id
                """
            ).rows.get(0)

            GroupWithSummary(
                group.getAs("id"),
                group.getAs("title"),
                group.getLong(2)?.toInt() ?: 0
            )
        }
    }

    suspend fun lookupGroupByTitle(ctx: DBContext, projectId: String, title: String): GroupWithSummary {
        return ctx.withSession { session ->
            val groups = session.sendPreparedStatement(
                {
                    setParameter("title", title)
                    setParameter("project", projectId)
                },
                """
                    select g.id, g.title, count(gm.username)
                    from groups g left join group_members gm on g.id = gm.group_id
                    where
                        title = :title and project = :project
                    group by g.id
                """
            ).rows

            if (groups.size <= 0) throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
            val group = groups.get(0)

            GroupWithSummary(
                group.getAs("id"),
                group.getAs("title"),
                group.getLong(2)?.toInt() ?: 0
            )
        }
    }

    suspend fun lookupProjectAndGroup(ctx: DBContext, projectId: String, groupId: String): ProjectAndGroup {
        return ctx.withSession { session ->
            val results = session.sendPreparedStatement(
                {
                    setParameter("project", projectId)
                    setParameter("group", groupId)
                },
                """
                    select p.title as projecttitle, p.archived as projectarchived, p.parent as projectparent, g.title as grouptitle
                    from projects p left join groups g on g.project = p.id
                    where
                        p.id = :project and g.id = :group
                """
            ).rows

            if (results.size <= 0) throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
            val result = results.get(0)

            ProjectAndGroup(
                Project(
                    projectId,
                    result.getAs("projecttitle"),
                    result.getAs("projectparent"),
                    result.getAs("projectarchived")
                ),
                ProjectGroup(
                    groupId,
                    result.getAs("grouptitle")
                )
            )
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
                            (:group = '' or group_id = :group) and
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
                        from group_members gm
                        inner join groups g on g.id = gm.group_id
                        where
                            (:group = '' or group_id = :group) and
                            (:username::text is null or check_group_acl(:username, :userIsAdmin, :group))
                        order by
                            group_id, username
                        limit :limit
                        offset :offset
                    """
                )
                .rows
                .map {
                    UserGroupSummary(
                        it.getField(GroupTable.project),
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
                        select count(g.id)
                        from groups g
                        where
                            g.project = :project and
                            check_group_acl(:username, :userIsAdmin, g.id)
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
                        select g.id, g.title, count(gm.username)
                        from groups g left join group_members gm on g.id = gm.group_id
                        where
                              g.project = :project and
                              check_group_acl(:username, :userIsAdmin, g.id)
                        group by g.id, g.title
                        order by g.title
                        offset :offset
                        limit :limit
                    """
                )
                .rows
                .map { row ->
                    val groupId = row.getString(0)!!
                    val groupTitle = row.getString(1)!!
                    val memberCount = row.getLong(2)?.toInt() ?: 0

                    GroupWithSummary(groupId, groupTitle, memberCount)
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
                            gm.group_id = :notInGroup and
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

            val membersOfAnyGroup = session
                .sendPreparedStatement(
                    {
                        setParameter("users", items.rows.map { it.getString("username")!! })
                        setParameter("projectId", projectId)
                    },
                    """
                        with members as (select unnest(:users::text[]) as username)
                        select m.username
                        from members m, group_members gm join groups g on (gm.group_id = g.id)
                        where
                            m.username = gm.username and
                            g.project = :projectId
                    """
                )
                .rows
                .map { it.getString(0)!! }
                .toSet()

            Page(
                itemsInTotal.toInt(),
                pagination.itemsPerPage,
                pagination.page,
                items.rows.map { row ->
                    val username = row.getString("username")!!
                    val role = row.getString("role")!!.let { ProjectRole.valueOf(it) }
                    ProjectMember(username, role, username in membersOfAnyGroup)
                }
            )
        }
    }

    suspend fun membersCount(
        ctx: DBContext,
        user: SecurityPrincipal,
        projectId: String
    ): Long {
        if(isAdminOrPIOfProject(ctx, user.username, projectId)) {
            return ctx.withSession { session ->
                session.sendPreparedStatement(
                    {
                        setParameter("projectId", projectId)
                    },
                    """
                    select count(*) from project.project_members where project_id = :projectId
                """
                ).rows.singleOrNull()?.getLong(0) ?: 0
            }
        } else throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)
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
                        from group_members gm
                        inner join groups g on g.id = gm.group_id 
                        where username = :username
                    """
                )
                .rows
                .map {
                    UserGroupSummary(
                        it.getField(GroupTable.project),
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

            return@withSession (Time.now() - latestVerification.toTimestamp()) >
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
            val count = session
                .sendPreparedStatement(
                    { setParameter("username", requestedBy) },
                    "select count(*) from invites where username = :username"
                )
                .rows
                .single()
                .getLong(0) ?: 0L
            val items = session
                .sendPreparedStatement(
                    {
                        setParameter("username", requestedBy)
                        setParameter("o", pagination.offset)
                        setParameter("l", pagination.itemsPerPage)
                    },
                    """
                        select invites.*, projects.title as title
                        from invites, projects
                        where
                            invites.project_id = projects.id and
                            username = :username
                        order by projects.title
                        offset :o
                        limit :l
                    """
                )
                .rows
                .map {
                    IngoingInvite(
                        it.getField(ProjectInvite.projectId),
                        it.getString("title")!!,
                        it.getField(ProjectInvite.invitedBy),
                        it.getField(ProjectInvite.createdAt).toTimestamp()
                    )
                }

            Page.forRequest(pagination, count.toInt(), items)
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

    suspend fun isAdminOrPIOfProject(ctx: DBContext, username: String, projectId: String): Boolean {
        val piAndAdmins = projects.getPIAndAdminsOfProject(ctx, projectId)
        when {
            piAndAdmins.first == username -> return true
            piAndAdmins.second.contains(username) -> return true
            else -> return false
        }
    }

    suspend fun subProjectsCount(
        ctx: DBContext,
        user: SecurityPrincipal,
        projectId: String
    ): Long {
        if(isAdminOrPIOfProject(ctx, user.username, projectId)) {
            return ctx.withSession { session ->
                session
                    .sendPreparedStatement(
                        {
                            setParameter("projectId", projectId)
                        },
                        """
                        select count(*) from project.projects where parent = :projectId
                    """
                    ).rows
                    .singleOrNull()
                    ?.getLong(0) ?: 0
            }
        } else {
            throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)
        }
    }

    suspend fun viewAncestors(
        ctx: DBContext,
        actor: Actor,
        projectId: String
    ): List<Project> {
        log.debug("viewAncestors($actor, $projectId)")
        val resultList = ArrayList<Project>()
        ctx.withSession { session ->
            var currentProject: Result<Project> = runCatching { findProject(session, actor, projectId) }
            currentProject.getOrThrow() // Throw immediately if the initial project is not found
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

    suspend fun lookupByTitle(
        ctx: DBContext,
        title: String
    ): Project? {
        return ctx.withSession { session ->
            session
                .sendPreparedStatement(
                    { setParameter("title", title) },
                    "select * from projects where title = :title"
                )
                .rows
                .singleOrNull()?.toProject() ?: null
        }
    }

    suspend fun lookupById(
        ctx: DBContext,
        title: String
    ): Project? {
        return ctx.withSession { session ->
            session
                .sendPreparedStatement(
                    { setParameter("id", title) },
                    "select * from projects where id = :id"
                )
                .rows
                .singleOrNull()?.toProject() ?: null
        }
    }

    suspend fun lookupByIdBulk(
        ctx: DBContext,
        titles: List<String>
    ): List<Project> {
        return ctx.withSession { session ->
            session
                .sendPreparedStatement(
                    { setParameter("ids", titles) },
                    "select * from projects where id IN (SELECT unnest(:ids::text[]))"
                )
                .rows
                .map { it.toProject() }
        }
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

    suspend fun lookupAdminsBulk(
        ctx: DBContext,
        actor: Actor,
        projectIds: List<String>
    ): List<Pair<String, List<ProjectMember>>> {
        if (actor !is Actor.System && !(actor is Actor.User && actor.principal.role in Roles.PRIVILEGED)) {
            throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)
        }

        val projectWithAdmins = mutableListOf<Pair<String,List<ProjectMember>>>()
        ctx.withSession { session ->
            projectIds.forEach { projectId ->
                val (pi, admins) = projects.getPIAndAdminsOfProject(session, projectId)
                projectWithAdmins.add(
                    Pair(
                        projectId,
                        admins.map { ProjectMember(it, ProjectRole.ADMIN) } + ProjectMember(pi, ProjectRole.PI)
                    )
                )
            }
        }

        return projectWithAdmins
    }

    companion object : Loggable {
        override val log = logger()
        const val VERIFICATION_REQUIRED_EVERY_X_DAYS = 30L
    }
}
