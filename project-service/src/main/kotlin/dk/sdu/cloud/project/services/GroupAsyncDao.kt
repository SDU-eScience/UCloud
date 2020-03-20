package dk.sdu.cloud.project.services

import com.github.jasync.sql.db.RowData
import dk.sdu.cloud.project.api.GroupWithSummary
import dk.sdu.cloud.project.api.UserGroupSummary
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.db.async.AsyncDBConnection
import dk.sdu.cloud.service.db.async.SQLTable
import dk.sdu.cloud.service.db.async.getField
import dk.sdu.cloud.service.db.async.insert
import dk.sdu.cloud.service.db.async.paginatedQuery
import dk.sdu.cloud.service.db.async.sendPreparedStatement
import dk.sdu.cloud.service.db.async.text
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

class GroupAsyncDao : GroupDao<AsyncDBConnection> {
    override suspend fun createGroup(session: AsyncDBConnection, project: String, group: String) {
        session.insert(GroupTable) {
            set(GroupTable.project, project)
            set(GroupTable.group, group)
        }
    }

    override suspend fun deleteGroups(session: AsyncDBConnection, project: String, groups: Set<String>) {
        session.sendPreparedStatement(
            {
                setParameter("project", project)
                setParameter("groups", groups.toList())
            },

            """
                delete from groups
                where 
                    project = ?project and   
                    the_group in ?groups 
            """
        )
    }

    override suspend fun listGroups(session: AsyncDBConnection, project: String): List<String> {
        return session
            .sendPreparedStatement(
                {
                    setParameter("project", project)
                },

                """
                    select the_group
                    from groups g
                    where g.project = ?project
                    order by g.the_group
                """
            )
            .rows
            .map { it.getString(0)!! }
    }

    override suspend fun addMemberToGroup(
        session: AsyncDBConnection,
        project: String,
        username: String,
        group: String
    ) {
        session.insert(GroupMembershipTable) {
            set(GroupMembershipTable.group, group)
            set(GroupMembershipTable.project, project)
            set(GroupMembershipTable.username, username)
        }
    }

    override suspend fun removeMemberFromGroup(
        session: AsyncDBConnection,
        project: String,
        username: String,
        group: String
    ) {
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
                        project = ?project
                        the_group = ?group
                """
            )
    }

    override suspend fun listGroupMembers(
        session: AsyncDBConnection,
        pagination: NormalizedPaginationRequest,
        project: String,
        groupFilter: String?
    ): Page<UserGroupSummary> {
        val count = session
            .sendPreparedStatement(
                {
                    setParameter("project", project)
                    setParameter("group", groupFilter ?: "")
                },
                """
                    select count(*) 
                    from group_members 
                    where 
                        project = ?project and
                        (?group = '' or the_group = ?group)
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
                },
                """
                    select *
                    from group_members
                    where
                        project = ?project and 
                        (?group = '' or the_group = ?group)
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

    override suspend fun listGroupsForUser(
        session: AsyncDBConnection,
        pagination: NormalizedPaginationRequest?,
        username: String,
        projectFilter: String?
    ): Page<UserGroupSummary> {
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

    override suspend fun listGroupsWithSummary(
        session: AsyncDBConnection,
        project: String,
        pagination: NormalizedPaginationRequest
    ): Page<GroupWithSummary> {
        // Count how many groups there are. We will use this for pagination
        val groupCount = session
            .sendPreparedStatement(
                {
                    setParameter("project", project)
                },
                """
                    select count(groups.the_group)
                    from groups
                    where groups.project = ?project
                """
            )
            .rows
            .map { it.getLong(0)!!.toInt() }
            .singleOrNull() ?: 0

        // We will collect results in this:
        val groupWithSummaryByGroup = HashMap<String, GroupWithSummary>()
        // Find all the relevant information:
        session
            .sendPreparedStatement(
                {
                    setParameter("limit", pagination.itemsPerPage)
                    setParameter("offset", pagination.itemsPerPage * pagination.page)
                    setParameter("project", project)
                },
                """
                    select 
                        group_filter.the_group,
                        rank_filter.username,
                        count(rank_filter.username)

                    from 
                        group_members,
                         
                        (
                            select groups.the_group
                            from groups
                            where project = ?project
                            limit ?limit
                            offset ?offset
                        ) group_filter left outer join 
                        (
                            select group_members.the_group,
                                   group_members.username,
                                   rank() over (partition by the_group order by username) as rank
                            from group_members
                            where group_members.project = ?project
                        ) rank_filter on (group_filter.the_group = rank_filter.the_group)

                    where 
                        (rank <= 5
                            and group_members.the_group = rank_filter.the_group
                            and group_filter.the_group = rank_filter.the_group)
                        or (rank is null)

                    group by group_filter.the_group, rank_filter.username
                    order by group_filter.the_group;
                """.trimIndent()
            )
            .rows
            .forEach { row ->
                val groupName = row.getString(0)!!
                val username = row.getString(1)
                val memberCount = row.getInt(2)

                val existingSummary = groupWithSummaryByGroup[groupName] ?: GroupWithSummary(groupName, 0, emptyList())

                val newMembers = if (username != null) listOf(username) else emptyList()
                groupWithSummaryByGroup[groupName] = existingSummary.copy(
                    numberOfMembers = memberCount ?: 0,
                    memberPreview = existingSummary.memberPreview + newMembers
                )
            }

        if (groupWithSummaryByGroup.isEmpty() && groupCount != 0) {
            // The above query doesn't work if _no_ group members exist (of any project)
            // This block deals with this edge case. TODO Just deal with it in the query.

            log.warn("Mismatch between number of groups")
            return listGroups(session, project).paginate(pagination).mapItems { GroupWithSummary(it, 0, emptyList()) }
        }

        return Page(
            groupCount,
            pagination.itemsPerPage,
            pagination.page,
            groupWithSummaryByGroup.values.toList()
        )
    }

    override suspend fun searchForMembers(
        session: AsyncDBConnection,
        project: String,
        query: String,
        pagination: NormalizedPaginationRequest
    ): Page<String> {
        return session
            .paginatedQuery(
                pagination,
                {
                    setParameter("project", project)
                    setParameter("usernameQuery", "%${query}%")
                },
                """
                    from group_members
                    where
                        project = ?project and
                        username like ?usernameQuery
                """
            )
            .mapItems { it.getField(GroupMembershipTable.username) }
    }

    override suspend fun renameGroup(
        session: AsyncDBConnection,
        projectId: String,
        oldGroupName: String,
        newGroupName: String
    ) {
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

        session
            .sendPreparedStatement(
                {
                    setParameter("oldGroup", oldGroupName)
                    setParameter("project", projectId)
                },
                """
                    delete from group_members  
                    where
                        project = ?project and
                        the_group = ?oldGroup
                """
            )
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
