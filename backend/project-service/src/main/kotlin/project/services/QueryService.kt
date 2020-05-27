package dk.sdu.cloud.project.services

import dk.sdu.cloud.project.api.*
import dk.sdu.cloud.project.services.*
import dk.sdu.cloud.service.*
import dk.sdu.cloud.service.db.async.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import org.joda.time.DateTimeConstants

data class ProjectForVerification(val projectId: String, val username: String, val role: ProjectRole)

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
                                    unnest(?projects::text[]),
                                    unnest(?groups::text[]),
                                    unnest(?usernames::text[])
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
                            the_group = ?group and
                            project = ?project
                    """
                )
                .rows
                .size > 0
        }
    }

    suspend fun listGroupMembers(
        ctx: DBContext,
        requestedBy: String,
        projectId: String,
        groupId: String,
        pagination: NormalizedPaginationRequest
    ): Page<UserGroupSummary> {
        return ctx.withSession { session ->
            val requestedByRole = projects.requireRole(session, requestedBy, projectId, ProjectRole.ALL)

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
                            project = ?project and
                            (?group = '' or the_group = ?group) and
                            check_group_acl(?username, ?userIsAdmin, ?group)
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
                        setParameter("offset", pagination.offset)
                    },
                    """
                        select *
                        from group_members
                        where
                            project = ?project and 
                            (?group = '' or the_group = ?group) and
                            check_group_acl(?username, ?userIsAdmin, ?group)
                        order by
                            project, the_group, username
                        limit ?limit
                        offset ?offset
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

            Page(count, pagination.itemsPerPage, pagination.page, items)
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
                            g.project = ?project and
                            check_group_acl(?username, ?userIsAdmin, g.the_group)
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
                              g.project = ?project and
                              check_group_acl(?username, ?userIsAdmin, g.the_group)
                        group by g.the_group
                        order by g.the_group
                        offset ?offset
                        limit ?limit
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
                          pm.project_id = ?project and
                          pm.username ilike ?usernameQuery
                """
            } else {
                """
                    from project_members pm
                    where
                    pm.project_id = ?project and
                    pm.username ilike ?usernameQuery and
                    pm.username not in (
                        select pm.username
                            from group_members gm
                            where
                            gm.project = pm.project_id and
                            gm.the_group = ?notInGroup and
                            gm.username = pm.username
                    )
                """
            }

            val itemsInTotal = session.sendPreparedStatement(
                parameters,
                "select count(*) $baseQuery"
            ).rows.singleOrNull()?.getLong(0) ?: 0

            val items = session.sendPreparedStatement(
                {
                    parameters()
                    setParameter("limit", pagination.itemsPerPage)
                    setParameter("offset", pagination.itemsPerPage * pagination.page)
                },
                """
                    select * $baseQuery 
                    order by role, created_at
                    limit ?limit offset ?offset
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
                        select *
                        from project_members
                        where username = ?username
                    """
                )
                .rows
                .map {
                    UserStatusInProject(
                        it.getField(ProjectMemberTable.project),
                        ProjectMember(
                            username,
                            it.getField(ProjectMemberTable.role).let { ProjectRole.valueOf(it) }
                        )
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
                        where username = ?username
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
                            p.archived
                        from 
                            project_members mem inner join projects p on mem.project_id = p.id
                        where 
                            mem.username = ?username and
                            (?showArchived or p.archived = false) and
                            (is_favorite(mem.username, p.id))
                        order by is_fav desc, p.id
                        offset ?offset
                        limit ?limit
                    """.trimIndent()
                )
                .rows
                .map {
                    val role = ProjectRole.valueOf(it.getString(0)!!)
                    val id = it.getString(1)!!
                    val title = it.getString(2)!!
                    val isFavorite = it.getBoolean(3)!!
                    val isArchived = it.getBoolean(4)!!

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
                        isArchived
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
                            p.archived
                        from 
                            project_members mem inner join projects p on mem.project_id = p.id
                        where 
                            mem.username = ?username and
                            (?showArchived or p.archived = false) and
                            (?projectId::text is null or p.id = ?projectId) and
                            (not ?noFavorites or not is_favorite(mem.username, p.id))
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
                    val isArchived = it.getBoolean(4)!!

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
                        isArchived
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
                                username = ?username and
                                (?showArchived or p.archived = false) and
                                (?projectId::text is null or p.id = ?projectId) and
                                (not ?noFavorites or not is_favorite(mem.username, p.id))
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
                        where project_id = ?project  
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
                        where project_id = ?project
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
                        where username = ?username
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

    companion object : Loggable {
        override val log = logger()
        const val VERIFICATION_REQUIRED_EVERY_X_DAYS = 30L
    }
}