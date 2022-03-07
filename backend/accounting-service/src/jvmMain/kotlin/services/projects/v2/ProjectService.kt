package dk.sdu.cloud.accounting.services.projects.v2

import com.github.jasync.sql.db.RowData
import com.github.jasync.sql.db.postgresql.exceptions.GenericDatabaseException
import dk.sdu.cloud.safeUsername
import dk.sdu.cloud.auth.api.AuthProviders
import dk.sdu.cloud.PageV2
import dk.sdu.cloud.*
import dk.sdu.cloud.service.*
import dk.sdu.cloud.service.db.async.*
import dk.sdu.cloud.calls.*
import dk.sdu.cloud.accounting.util.PartialQuery
import dk.sdu.cloud.project.api.v2.*
import dk.sdu.cloud.accounting.api.providers.SortDirection
import kotlinx.serialization.decodeFromString

class ProjectService(
    private val db: DBContext,
) {
    suspend fun retrieve(
        actorAndProject: ActorAndProject,
        request: ProjectsRetrieveRequest,
        ctx: DBContext = db,
    ): Project {
        val username = actorAndProject.actor.safeUsername()
        return ctx.withSession { session ->
            loadProjects(username, session, request, relevantProjects(username, request.id))
        }.firstOrNull() ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
    }

    suspend fun browse(
        actorAndProject: ActorAndProject,
        request: ProjectsBrowseRequest,
        ctx: DBContext = db,
    ): PageV2<Project> {
        val username = actorAndProject.actor.safeUsername()
        val pagination = request.normalize()
        val offset = run {
            val next = request.next
            if (next == null) 0L else next.toLongOrNull() ?: 0L 
        }
        val limit = pagination.itemsPerPage.toLong()

        val items = ctx.withSession { session ->
            loadProjects(
                username, 
                session, 
                request, 
                relevantProjects(
                    username, 
                    sortBy = request.sortBy, 
                    sortDirection = request.sortDirection,
                    offset = offset,
                    limit = limit,
                )
            )
        }

        val nextToken = if (items.size < limit) null else (offset + limit).toString()
        return PageV2(limit.toInt(), items, nextToken)
    }

    private fun relevantProjects(
        username: String, 
        id: String? = null, 
        sortBy: ProjectsSortBy? = null, 
        sortDirection: SortDirection = SortDirection.ascending,
        offset: Long? = null,
        limit: Long? = null,
    ): PartialQuery {
        val providerId = if (username.startsWith(AuthProviders.PROVIDER_PREFIX)) {
            username.removePrefix(AuthProviders.PROVIDER_PREFIX)
        } else {
            null
        }

        return if (providerId != null) {
            PartialQuery(
                {
                    setParameter("provider_id", providerId)
                    setParameter("id", id)
                },
                buildString {
                    // NOTE(Dan): Ordering is ignored for providers 

                    append(
                        """
                            with the_project as (
                                select p as project
                                from project.projects p
                                where
                                    :id::text is null or
                                    p.id = :id::text
                            )
                            select distinct p.project, null::text as role
                            from
                                accounting.wallet_owner wo join
                                the_project p on wo.project_id = (p.project).id join
                                accounting.wallets w on wo.id = w.owned_by join
                                accounting.product_categories pc on w.category = pc.id join
                                accounting.wallet_allocations wa on w.id = wa.associated_wallet
                            where
                                pc.provider = :provider
                            order by p.project
                        """
                    )

                    if (offset != null && limit != null) {
                        appendLine("offset $offset")
                        appendLine("limit $limit")
                    }
                }
            )
        } else {
            PartialQuery(
                {
                    setParameter("username", username)
                    setParameter("id", id)
                },
                buildString {
                    run {
                        appendLine("select")
                        appendLine("p as project")
                        appendLine(", pm.role")
                        if (sortBy == ProjectsSortBy.favorite) {
                            appendLine(", pf.username is not null as is_favorite")
                        }
                    }

                    run {
                        appendLine("from")
                        appendLine("project.projects p")
                        appendLine("join project.project_members pm on p.id = pm.project_id")
                        if (sortBy == ProjectsSortBy.favorite) {
                            appendLine("left join project.project_favorite pf on")
                            appendLine("  p.id = pf.project_id")
                            appendLine("  and pf.username = :username")
                        }
                    }

                    run {
                        appendLine("where")
                        appendLine("pm.username = :username")

                        appendLine("and (")
                        appendLine("  :id::text is null")
                        appendLine("  or p.id = :id")
                        appendLine(")")
                    }

                    if (sortBy != null) {
                        when(sortBy) {
                            ProjectsSortBy.title -> {
                                append("order by p.title")
                            }

                            ProjectsSortBy.parent -> {
                                append("order by p.title")
                            }

                            ProjectsSortBy.favorite -> {
                                append("order by is_favorite")
                            }
                        }

                        when(sortDirection) {
                            SortDirection.ascending -> appendLine(" asc")
                            SortDirection.descending -> appendLine(" desc")
                        }
                    }

                    if (offset != null && limit != null) {
                        appendLine("offset $offset")
                        appendLine("limit $limit")
                    }
                }
            )
        }
    }

    private suspend fun loadProjects(
        username: String,
        session: AsyncDBConnection,
        flags: ProjectFlags,
        relevantProjects: PartialQuery,
    ): List<Project> {
        return session.sendPreparedStatement(
            {
                with(relevantProjects) {
                    arguments()
                }
                setParameter("username", username)
            },
            buildString {
                appendLine("with")
                appendLine("relevant_projects as (${relevantProjects.query}),")
                run {
                    appendLine("relevant_data as (")
                    run {
                        appendLine("select")
                        appendLine("p.project")
                        appendLine(", p.role")

                        if (flags.includeGroups == true) {
                            appendLine(", coalesce(array_agg(distinct g), array[]::project.groups[]) all_groups")
                            appendLine(", coalesce(array_agg(distinct gm), array[]::project.group_members[]) all_group_members")
                        } else {
                            appendLine(", null::project.groups[] all_groups")
                            appendLine(", null::project.group_members[] all_group_members")
                        }

                        if (flags.includeMembers == true) {
                            appendLine(", coalesce(array_agg(distinct pm), array[]::project.project_members[]) all_members")
                        } else {
                            appendLine(", null::project.project_members[] all_members")
                        }

                        if (flags.includeFavorite == true) {
                            appendLine(", pf.project_id is not null as favorite")
                        } else {
                            appendLine(", null::boolean as favorite")
                        }
                    }

                    run {
                        appendLine("from")
                        appendLine("relevant_projects p")

                        if (flags.includeGroups == true) {
                            appendLine("left join project.groups g on (p.project).id = g.project")
                            appendLine("left join project.group_members gm on g.id = gm.group_id")
                        }

                        if (flags.includeMembers == true) {
                            appendLine("left join project.project_members pm on (p.project).id = pm.project_id")
                        }

                        if (flags.includeFavorite == true) {
                            appendLine("left join project.project_favorite pf on")
                            appendLine("  (p.project).id = pf.project_id")
                            appendLine("  and pf.username = :username")
                        }
                    }

                    run {
                        appendLine("group by")
                        appendLine("p.project")
                        appendLine(", p.role")

                        if (flags.includeFavorite == true) {
                            appendLine(", pf.project_id")
                        }
                    }
                    appendLine(")")
                }

                appendLine("select project.project_to_json(project, all_groups, all_group_members, all_members, favorite, role)")
                appendLine("from relevant_data")
            }
        ).rows.map { row ->
            defaultMapper.decodeFromString(row.getString(0)!!)
        }
    }

    private suspend fun requireMembership(actor: Actor, projects: List<String>, session: AsyncDBConnection) {
        requireRole(actor, projects, session, setOf(ProjectRole.PI, ProjectRole.ADMIN, ProjectRole.USER))
    }

    private suspend fun requireAdmin(actor: Actor, projects: List<String>, session: AsyncDBConnection) {
        requireRole(actor, projects, session, setOf(ProjectRole.PI, ProjectRole.ADMIN))
    }

    private suspend fun requireRole(
        actor: Actor, 
        projects: List<String>,
        session: AsyncDBConnection,
        roleOneOf: Set<ProjectRole>,
    ) {
        if (actor == Actor.System) return

        val username = actor.safeUsername()

        val isAllowed = session.sendPreparedStatement(
            {
                setParameter("username", username)
                setParameter("projects", projects)
                setParameter("roles", roleOneOf.map { it.name })
            },
            """
                select distinct pm.project_id
                from project.project_members pm
                where
                    pm.username = :username and
                    pm.project_id = some(:project::text[]) and
                    pm.role = some(:roles::text[])
                order by pm.project_id
            """
        ).rows.size == projects.size

        if (!isAllowed) throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)
    }

    suspend fun create(
        actorAndProject: ActorAndProject,
        request: BulkRequest<Project.Specification>,
        ctx: DBContext = db,
    ): BulkResponse<FindByStringId> {
        TODO("Not yet implemented")
    }

    suspend fun archive(
        actorAndProject: ActorAndProject,
        request: BulkRequest<FindByStringId>,
        ctx: DBContext = db,
    ) {
        TODO("Not yet implemented")
    }

    suspend fun toggleFavorite(
        actorAndProject: ActorAndProject,
        request: BulkRequest<FindByStringId>,
        ctx: DBContext = db,
    ) {
        TODO("Not yet implemented")
    }

    suspend fun updateSettings(
        actorAndProject: ActorAndProject,
        settings: Project.Settings,
        ctx: DBContext = db,
    ) {
        TODO("Not yet implemented")
    }

    suspend fun verifyMembership(
        actorAndProject: ActorAndProject,
        request: BulkRequest<FindByStringId>,
        ctx: DBContext = db,
    ) {
        TODO("Not yet implemented")
    }
}

