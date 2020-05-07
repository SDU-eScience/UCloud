package dk.sdu.cloud.project.services

import com.github.jasync.sql.db.RowData
import dk.sdu.cloud.project.api.*
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.db.async.*
import dk.sdu.cloud.service.mapItems
import dk.sdu.cloud.service.offset
import dk.sdu.cloud.service.paginate
import kotlin.collections.HashMap
import kotlin.collections.List
import kotlin.collections.Set
import kotlin.collections.emptyList
import kotlin.collections.forEach
import kotlin.collections.listOf
import kotlin.collections.map
import kotlin.collections.plus
import kotlin.collections.set
import kotlin.collections.singleOrNull
import kotlin.collections.toList

object GroupTable : SQLTable("groups") {
    val project = text("project")
    val group = text("the_group")
}

object GroupMembershipTable : SQLTable("group_members") {
    val project = text("project")
    val group = text("the_group")
    val username = text("username")
}

class GroupDao {
    suspend fun createGroup(ctx: DBContext, project: String, group: String) {
        ctx.withSession { session ->
            session.insert(GroupTable) {
                set(GroupTable.project, project)
                set(GroupTable.group, group)
            }
        }
    }

    suspend fun deleteGroups(ctx: DBContext, project: String, groups: Set<String>) {
        ctx.withSession { session ->
            session.sendPreparedStatement(
                {
                    setParameter("project", project)
                    setParameter("groups", groups.toList())
                },
                """
                    delete from group_members
                    where
                        project = ?project and
                        the_group in (select * from unnest(?groups::text[]))
                """
            )

            session.sendPreparedStatement(
                {
                    setParameter("project", project)
                    setParameter("groups", groups.toList())
                },

                """
                    delete from groups
                    where 
                        project = ?project and   
                        the_group in (select * from unnest(?groups::text[]))
                """
            )
        }
    }

    suspend fun listGroups(
        ctx: DBContext,
        project: String,
        requestedBy: ProjectMember
    ): List<String> {
        ctx.withSession { session ->
            return session
                .sendPreparedStatement(
                    {
                        setParameter("project", project)
                        setParameter("userIsAdmin", requestedBy.role.isAdmin())
                        setParameter("username", requestedBy.username)
                    },

                    """
                        select the_group
                        from groups g
                        where 
                            g.project = ?project and
                            check_group_acl(?username, ?userIsAdmin, g.the_group)
                        order by g.the_group
                    """
                )
                .rows
                .map { it.getString(0)!! }
        }
    }

    suspend fun addMemberToGroup(
        ctx: DBContext,
        project: String,
        username: String,
        group: String
    ) {
        ctx.withSession { session ->
            session.insert(GroupMembershipTable) {
                set(GroupMembershipTable.group, group)
                set(GroupMembershipTable.project, project)
                set(GroupMembershipTable.username, username)
            }
        }
    }

    suspend fun removeMemberFromGroup(
        ctx: DBContext,
        project: String,
        username: String,
        group: String
    ) {
        ctx.withSession { session ->
            session
                .sendPreparedStatement(
                    {
                        setParameter("username", username)
                        setParameter("project", project)
                        setParameter("group", group)
                    },
                    """
                        delete from group_members
                        where
                            username = ?username and
                            project = ?project and
                            the_group = ?group
                    """
                )
        }
    }

    suspend fun listGroupMembers(
        ctx: DBContext,
        pagination: NormalizedPaginationRequest,
        project: String,
        groupFilter: String?,
        requestedBy: ProjectMember
    ): Page<UserGroupSummary> {
        ctx.withSession { session ->
            val count = session
                .sendPreparedStatement(
                    {
                        setParameter("project", project)
                        setParameter("group", groupFilter ?: "")
                        setParameter("userIsAdmin", requestedBy.role.isAdmin())
                        setParameter("username", requestedBy.username)
                    },
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
                        setParameter("project", project)
                        setParameter("group", groupFilter ?: "")
                        setParameter("limit", pagination.itemsPerPage)
                        setParameter("offset", pagination.offset)
                        setParameter("userIsAdmin", requestedBy.role.isAdmin())
                        setParameter("username", requestedBy.username)
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
                .map { it.toUserGroupSummary() }

            return Page(count, pagination.itemsPerPage, pagination.page, items)
        }
    }

    suspend fun listGroupsForUser(
        ctx: DBContext,
        pagination: NormalizedPaginationRequest?,
        username: String,
        projectFilter: String? = null
    ): Page<UserGroupSummary> {
        ctx.withSession { session ->
            val items = session
                .sendPreparedStatement(
                    {
                        setParameter("limit", pagination?.itemsPerPage ?: Int.MAX_VALUE)
                        setParameter("offset", if (pagination == null) 0 else pagination.itemsPerPage * pagination.page)
                        setParameter("project", projectFilter ?: "")
                        setParameter("username", username)
                    },
                    """
                        select *
                        from group_members
                        where
                            (?project = '' or project = ?project) and
                            username = ?username
                        order by
                            project, the_group, username
                        limit ?limit
                        offset ?offset
                    """
                )
                .rows
                .map { it.toUserGroupSummary() }

            val count = if (pagination == null) {
                items.size
            } else {
                session
                    .sendPreparedStatement(
                        {
                            setParameter("project", projectFilter ?: "")
                            setParameter("username", username)
                        },
                        """
                            select count(*)
                            from group_members
                            where
                                (?project = '' or project = ?project) and
                                username = ?username
                        """
                    )
                    .rows
                    .map { it.getLong(0)!!.toInt() }
                    .singleOrNull() ?: 0
            }

            return Page(count, pagination?.itemsPerPage ?: count, pagination?.page ?: 0, items)
        }
    }

    suspend fun listGroupsWithSummary(
        ctx: DBContext,
        project: String,
        pagination: NormalizedPaginationRequest,
        requestedBy: ProjectMember
    ): Page<GroupWithSummary> {
        ctx.withSession { session ->
            // Count how many groups there are. We will use this for pagination
            val groupCount = session
                .sendPreparedStatement(
                    {
                        setParameter("project", project)
                        setParameter("userIsAdmin", requestedBy.role.isAdmin())
                        setParameter("username", requestedBy.username)
                    },
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
                        setParameter("limit", pagination.itemsPerPage)
                        setParameter("offset", pagination.itemsPerPage * pagination.page)
                        setParameter("project", project)
                        setParameter("userIsAdmin", requestedBy.role.isAdmin())
                        setParameter("username", requestedBy.username)
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

            return Page(groupCount, pagination.itemsPerPage, pagination.page, items)
        }
    }

    suspend fun searchForMembers(
        ctx: DBContext,
        project: String,
        query: String,
        notInGroup: String?,
        pagination: NormalizedPaginationRequest
    ): Page<ProjectMember> {
        ctx.withSession { session ->
            // TODO This gets quite ugly because we need to order by which paginated query doesn't currently support
            val parameters: EnhancedPreparedStatement.() -> Unit = {
                setParameter("project", project)
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

            return Page(
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

    suspend fun renameGroup(
        ctx: DBContext,
        projectId: String,
        oldGroupName: String,
        newGroupName: String
    ) {
        ctx.withSession { session ->
            createGroup(session, projectId, newGroupName)

            session
                .sendPreparedStatement(
                    {
                        setParameter("newGroup", newGroupName)
                        setParameter("oldGroup", oldGroupName)
                        setParameter("project", projectId)
                    },
                    """
                        update group_members  
                        set the_group = ?newGroup
                        where
                            the_group = ?oldGroup and
                            project = ?project
                    """
                )

            deleteGroups(session, projectId, setOf(oldGroupName))
        }
    }

    suspend fun isMemberQuery(
        ctx: DBContext,
        queries: List<IsMemberQuery>
    ): List<Boolean> {
        ctx.withSession { session ->
            val response = ArrayList<Boolean>()
            repeat(queries.size) {
                response.add(false)
            }

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
                .map { it.toUserGroupSummary() }
                .forEach { row ->
                    val idx = queries.indexOfFirst {
                        it.group == row.group && it.project == row.projectId && it.username == row.username
                    }

                    if (idx == -1) {
                        log.warn("Could not find matching row in query. This probably shouldn't happen!")
                        log.warn("row = $row, queries = $queries")
                    } else {
                        response[idx] = true
                    }
                }

            return response
        }
    }

    suspend fun exists(ctx: DBContext, project: String, group: String): Boolean {
        ctx.withSession { session ->
            return session
                .sendPreparedStatement(
                    {
                        setParameter("group", group)
                        setParameter("project", project)
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

    suspend fun removeMember(ctx: DBContext, project: String, username: String) {
        ctx.withSession { session ->
            session
                .sendPreparedStatement(
                    {
                        setParameter("username", username)
                        setParameter("project", project)
                    },
                    """
                        delete from group_members
                        where
                            username = ?username and
                            project = ?project
                    """
                )
        }
    }

    private fun RowData.toUserGroupSummary(): UserGroupSummary =
        UserGroupSummary(
            getField(GroupMembershipTable.project),
            getField(GroupMembershipTable.group),
            getField(GroupMembershipTable.username)
        )

    companion object : Loggable {
        override val log = logger()
    }
}
