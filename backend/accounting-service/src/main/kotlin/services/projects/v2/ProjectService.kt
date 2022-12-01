package dk.sdu.cloud.accounting.services.projects.v2

import dk.sdu.cloud.*
import dk.sdu.cloud.calls.*
import dk.sdu.cloud.accounting.api.providers.SortDirection
import dk.sdu.cloud.accounting.util.PartialQuery
import dk.sdu.cloud.accounting.util.ProjectCache
import dk.sdu.cloud.auth.api.AuthProviders
import dk.sdu.cloud.calls.BulkRequest
import dk.sdu.cloud.calls.BulkResponse
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.mail.api.Mail
import dk.sdu.cloud.mail.api.MailDescriptions
import dk.sdu.cloud.mail.api.SendRequestItem
import dk.sdu.cloud.notification.api.CreateNotification
import dk.sdu.cloud.notification.api.Notification
import dk.sdu.cloud.notification.api.NotificationDescriptions
import dk.sdu.cloud.notification.api.NotificationType
import dk.sdu.cloud.project.api.v2.*
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.db.async.AsyncDBConnection
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.sendPreparedStatement
import dk.sdu.cloud.service.db.async.withSession
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.util.*
import kotlin.collections.ArrayList

private typealias OnProjectUpdatedHandler = suspend (projects: Collection<String>) -> Unit

class ProjectService(
    private val db: DBContext,
    private val serviceClient: AuthenticatedClient,
    private val projectCache: ProjectCache,
    private val developmentMode: Boolean,
) {
    private val updateHandlers = ArrayList<OnProjectUpdatedHandler>()

    fun addUpdateHandler(handler: OnProjectUpdatedHandler) {
        updateHandlers.add(handler)
    }

    suspend fun locateOrCreateAllUsersGroup(
        projectId: String,
        ctx: DBContext = db,
    ): String {
        return ctx.withSession { session ->
            val groupId = session
                .sendPreparedStatement(
                    {
                        setParameter("project_id", projectId)
                        setParameter("all_users", ALL_USERS_GROUP_TITLE)
                    },
                    """
                        select g.id
                        from project.groups g
                        where
                            g.project = :project_id and
                            g.title = :all_users
                    """
                )
                .rows
                .singleOrNull()
                ?.getString(0)

            if (groupId != null) return@withSession groupId

            val createdGroup = createGroup(
                ActorAndProject(Actor.System, projectId),
                bulkRequestOf(Group.Specification(projectId, ALL_USERS_GROUP_TITLE)),
                ctx = session
            ).responses.single().id

            val projectWithMembers = retrieve(
                ActorAndProject(Actor.System, projectId),
                ProjectsRetrieveRequest(projectId, includeMembers = true),
                ctx = session
            )

            val members = (projectWithMembers.status.members ?: emptyList())
            createGroupMember(
                ActorAndProject(Actor.System, null),
                BulkRequest(members.map { GroupMember(it.username, createdGroup) }),
                ctx = session,
                dispatchUpdate = false
            )

            createdGroup
        }
    }

    suspend fun retrieve(
        actorAndProject: ActorAndProject,
        request: ProjectsRetrieveRequest,
        ctx: DBContext = db,
    ): Project {
        val username = actorAndProject.actor.safeUsername()
        val result = ctx.withSession { session ->
            loadProjects(
                username,
                session,
                request,
                relevantProjects(actorAndProject, request.id, includeArchived = true)
            )
        }.firstOrNull() ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
        return result.postProcess(username.startsWith(AuthProviders.PROVIDER_PREFIX))
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
                    actorAndProject,
                    includeArchived = request.includeArchived == true,
                    sortBy = request.sortBy ?: ProjectsSortBy.title,
                    sortDirection = request.sortDirection ?: SortDirection.ascending,
                    offset = offset,
                    limit = limit,
                ),
                sortBy = request.sortBy ?: ProjectsSortBy.title,
                sortDirection = request.sortDirection ?: SortDirection.ascending,
            )
        }.postProcess(username.startsWith(AuthProviders.PROVIDER_PREFIX))

        val nextToken = if (items.size < limit) null else (offset + limit).toString()
        return PageV2(limit.toInt(), items, nextToken)
    }

    private fun relevantProjects(
        actorAndProject: ActorAndProject,
        id: String? = null,
        includeArchived: Boolean = false,
        sortBy: ProjectsSortBy? = null,
        sortDirection: SortDirection = SortDirection.ascending,
        offset: Long? = null,
        limit: Long? = null,
    ): PartialQuery {
        val (actor) = actorAndProject
        val username = actor.safeUsername()

        // NOTE(Dan): This function returns a partial query, which fetches a (optionally paginated) list of relevant
        // projects, without any additional data. The output of this function is then typically passed to
        // `loadProjects` which fetches additional relevant information about the projects. A project is relevant to
        // an actor in one of two cases:
        //
        // 1. They are a member of the project
        // 2. They are a provider and the project has received an allocation for one of the products the provider
        // manages
        //
        // The two cases requires drastically different code, thus we simply start by checking if we are a provider or
        // not.
        val providerId = if (username.startsWith(AuthProviders.PROVIDER_PREFIX)) {
            username.removePrefix(AuthProviders.PROVIDER_PREFIX)
        } else {
            null
        }

        return if (actor == Actor.System) {
            PartialQuery(
                {
                    setParameter("id", id)
                    setParameter("include_archived", includeArchived)
                },
                buildString {
                    // NOTE(Dan): Ordering is ignored for Actor.System

                    append(
                        """
                            with the_project as (
                                select p as project
                                from project.projects p
                                where
                                    :id::text is null or
                                    p.id = :id::text
                            )
                            select distinct p.project, null::text as role, (p.project).title
                            from the_project p
                            where
                                (:include_archived or (p.project).archived = false)
                            order by p.project
                        """
                    )

                    if (offset != null && limit != null) {
                        appendLine("offset $offset")
                        appendLine("limit $limit")
                    }
                }
            )
        } else if (providerId != null) {
            PartialQuery(
                {
                    setParameter("provider_id", providerId)
                    setParameter("id", id)
                    setParameter("include_archived", includeArchived)
                },
                buildString {
                    // NOTE(Dan): In this case we need to look for allocations to a product the provider manages. We
                    // then hook this up with the relevant projects.

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
                            select distinct p.project, null::text as role, (p.project).title
                            from
                                accounting.wallet_owner wo join
                                the_project p on wo.project_id = (p.project).id join
                                accounting.wallets w on wo.id = w.owned_by join
                                accounting.product_categories pc on w.category = pc.id join
                                accounting.wallet_allocations wa on w.id = wa.associated_wallet
                            where
                                pc.provider = :provider_id and
                                (:include_archived or (p.project).archived = false)
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
            // NOTE(Dan): If a user is making the request, then we must find the projects for which they are a
            // member. We will optionally fetch favorite data in case the sorting requires this.

            PartialQuery(
                {
                    setParameter("username", username)
                    setParameter("id", id)
                    setParameter("include_archived", includeArchived)
                },
                buildString {
                    run {
                        appendLine("select")
                        appendLine("p as project")
                        appendLine(", pm.role")
                        if (sortBy == ProjectsSortBy.favorite) {
                            appendLine(", pf.username is not null as is_favorite")
                        }
                        appendLine(", p.title")
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
                        appendLine("and (:include_archived or p.archived = false)")

                        appendLine("and (")
                        appendLine("  :id::text is null")
                        appendLine("  or p.id = :id")
                        appendLine(")")
                    }

                    writeSortSql(this, sortBy, sortDirection)

                    if (offset != null && limit != null) {
                        appendLine("offset $offset")
                        appendLine("limit $limit")
                    }
                }
            )
        }
    }

    private fun writeSortSql(builder: StringBuilder, sortBy: ProjectsSortBy?, sortDirection: SortDirection) {
        val sortDir = when (sortDirection) {
            SortDirection.ascending -> "asc"
            SortDirection.descending -> "desc"
        }

        with(builder) {
            if (sortBy != null) {
                when (sortBy) {
                    ProjectsSortBy.title -> {
                        appendLine("order by title $sortDir")
                    }

                    ProjectsSortBy.parent -> {
                        appendLine("order by title $sortDir")
                    }

                    ProjectsSortBy.favorite -> {
                        appendLine("order by is_favorite $sortDir, title asc")
                    }
                }
            }
        }
    }

    internal suspend fun loadProjects(
        username: String,
        session: AsyncDBConnection,
        flags: ProjectFlags,
        relevantProjects: PartialQuery,
        sortBy: ProjectsSortBy? = null,
        sortDirection: SortDirection = SortDirection.ascending
    ): List<Project> {
        // NOTE(Dan): `loadProjects` fetches additional relevant information about a project, based on the
        // `ProjectFlags`. It is also responsible for JSON-ifying the rows before the response is sent to the backend.
        val projects: List<Project> = session.sendPreparedStatement(
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
                            appendLine(", coalesce(array_remove(array_agg(distinct g), null), array[]::project.groups[]) all_groups")
                            appendLine(", coalesce(array_remove(array_agg(distinct gm), null), array[]::project.group_members[]) all_group_members")
                        } else {
                            appendLine(", null::project.groups[] all_groups")
                            appendLine(", null::project.group_members[] all_group_members")
                        }

                        if (flags.includeMembers == true) {
                            appendLine(", coalesce(array_remove(array_agg(distinct pm), null), array[]::project.project_members[]) all_members")
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
                        appendLine(", p.title")

                        if (flags.includeFavorite == true) {
                            appendLine(", pf.project_id")
                        }

                        if (sortBy == ProjectsSortBy.favorite) {
                            appendLine(", p.is_favorite")
                        }
                    }

                    writeSortSql(this, sortBy, sortDirection)

                    appendLine(")")
                }

                appendLine("select project.project_to_json(project, all_groups, all_group_members, all_members, favorite, role)")
                appendLine("from relevant_data")
            }
        ).rows.map { row ->
            defaultMapper.decodeFromString(row.getString(0)!!)
        }

        if (flags.includePath == true) {
            // NOTE(Dan): We don't have an efficient way of looking up the project hierarchies. So in this code-path
            // we are trying to make the best out of a bad situation. The algorithm works by resolving a full layer
            // of projects in a single iteration. This will in most cases limit us to max 5 queries to resolve all
            // projects.

            // NOTE(Dan): We keep a map between project IDs and their titles. We initialize this with the data we
            // already have, this can in some cases lead us to fewer queries needed.
            data class ProjectTitleAndParent(val title: String, val parent: String?)

            val projectToTitle = HashMap<String, ProjectTitleAndParent>()
            for (project in projects) {
                projectToTitle[project.id] = ProjectTitleAndParent(
                    project.specification.title,
                    project.specification.parent
                )
            }

            // NOTE(Dan): We then find all the project parents we don't currently know about. We will keep resolving
            // parents until we know all of them (or they are null).
            var unknownProjects: List<String> = projects.mapNotNull { project ->
                val parent = project.specification.parent
                if (parent != null && parent !in projectToTitle) {
                    parent
                } else {
                    null
                }
            }

            var failSafe = 100 // NOTE(Dan): Limit ourselves to 100 queries to avoid looping forever.
            while (unknownProjects.isNotEmpty() && failSafe > 0) {
                failSafe--
                val newUnknowns = ArrayList<String>()

                session.sendPreparedStatement(
                    {
                        setParameter("projects", unknownProjects)
                    },
                    """
                        select p.id, p.parent, p.title
                        from project.projects p
                        where p.id = some(:projects::text[])
                    """
                ).rows.forEach { row ->
                    val id = row.getString(0)!!
                    val parent = row.getString(1)
                    val title = row.getString(2)!!

                    projectToTitle[id] = ProjectTitleAndParent(title, parent)
                    if (parent != null && parent !in projectToTitle) {
                        newUnknowns.add(parent)
                    }
                }

                unknownProjects = newUnknowns
            }

            if (failSafe == 0) {
                log.warn("Fail-safe triggered while resolving project hierarchy: ${projects.map { it.id }}")
                throw RPCException.fromStatusCode(HttpStatusCode.InternalServerError)
            }

            // NOTE(Dan): Build the string based on the information we have resolved
            for (project in projects) {
                val components = ArrayList<String>()

                var current = project.specification.parent
                while (current != null) {
                    val c = projectToTitle[current] ?: break
                    components.add(c.title)
                    current = c.parent
                }

                project.status.path = buildString {
                    var first = true
                    components.reversed().forEach {
                        if (first) first = false
                        else append('/')
                        append(it)
                    }
                }
            }
        }

        // NOTE(Dan): Backwards compatibility which initializes an "All users" group the first time someone attempts
        // to load information about their project. This code does not require any additional database code, unless,
        // the group needs to be created. Hopefully, this means that performance won't be affected too much by this
        // path.
        var didNeedToInitAllUsersGroup = false
        if (flags.includeGroups == true) {
            for (project in projects) {
                val allGroups = project.status.groups ?: emptyList()
                if (allGroups.none { it.specification.title == ALL_USERS_GROUP_TITLE }) {
                    locateOrCreateAllUsersGroup(project.id, session)
                    didNeedToInitAllUsersGroup = true
                }
            }
        }

        if (didNeedToInitAllUsersGroup) {
            return loadProjects(username, session, flags, relevantProjects, sortBy, sortDirection)
        }

        return projects
    }

    private suspend fun ActorAndProject.requireProject(): String {
        val p = project
        if (p == null) {
            throw RPCException(
                "You cannot perform this operation in your personal workspace. Try selecting a different project!",
                HttpStatusCode.BadRequest
            )
        }
        return p
    }

    private suspend fun requireMembership(actor: Actor, projects: Collection<String>, session: AsyncDBConnection) {
        requireRole(actor, projects.toSet(), session, setOf(ProjectRole.PI, ProjectRole.ADMIN, ProjectRole.USER))
    }

    private suspend fun requireAdmin(actor: Actor, projects: Collection<String>, session: AsyncDBConnection) {
        requireRole(actor, projects.toSet(), session, setOf(ProjectRole.PI, ProjectRole.ADMIN))
    }

    private suspend fun requireRole(
        actor: Actor,
        projects: Set<String>,
        session: AsyncDBConnection,
        roleOneOf: Set<ProjectRole>,
    ) {
        if (actor == Actor.System) return

        val username = actor.safeUsername()

        val isAllowed = session.sendPreparedStatement(
            {
                setParameter("username", username)
                setParameter("projects", projects.toList())
                setParameter("roles", roleOneOf.map { it.name })
            },
            """
                select distinct pm.project_id
                from project.project_members pm
                where
                    pm.username = :username and
                    pm.project_id = some(:projects::text[]) and
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
        piOverride: String? = null,
        addSelfWithPiOverride: Boolean = false,
    ): BulkResponse<FindByStringId> {
        return ctx.withSession(remapExceptions = true) { session ->
            // NOTE(Dan): First we check if the actor is allowed to create _all_ of the requested projects. The system
            // actor is, as always, allowed to create any project. Otherwise, you can create a project if you are an
            // administrator of the parent project. If the requested project is root-level, then you must be a UCloud
            // administrator. Providers are themselves allowed to create a project as long as the project is a
            // direct child of the project which owns the provider.

            val (actor) = actorAndProject
            val username = actor.safeUsername()
            val providerId = if (username.startsWith(AuthProviders.PROVIDER_PREFIX)) {
                username.removePrefix(AuthProviders.PROVIDER_PREFIX)
            } else {
                null
            }

            var piUsername = username

            when {
                piOverride != null -> {
                    piUsername = piOverride
                }

                providerId != null -> {
                    // Providers can only create the project if it is a direct child of the owning project.
                    val parentProject = retrieveProviderProject(session, providerId)

                    for (reqItem in request.items) {
                        if (reqItem.parent != parentProject) throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)
                    }

                    // NOTE(Dan): The provider is not a real user in the system, as a result, they cannot be listed as
                    // the PI. Instead, we set the PI to be the PI of the provider project.
                    piUsername = session.sendPreparedStatement(
                        {
                            setParameter("project_id", parentProject)
                        },
                        """
                            select pm.username
                            from project.project_members pm
                            where
                                pm.project_id = :project_id and
                                pm.role = 'PI'
                        """
                    ).rows.singleOrNull()?.getString(0) ?: throw RPCException(
                        "Could not find appropriate PI",
                        HttpStatusCode.InternalServerError
                    )
                }

                actor == Actor.System -> {
                    // All good. The system is always allowed to create a project.
                }

                else -> {
                    // Check admin status of parent project
                    val parents = request.items.mapNotNull { it.parent }
                    requireAdmin(actor, parents, session)

                    // If we have a root-level project as part of the request, then we must verify that they are a UCloud
                    // administrator.
                    if (parents.size != request.items.size) {
                        val role = if (actor is Actor.User) actor.principal.role else Role.USER
                        if (role !in Roles.PRIVILEGED) {
                            throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)
                        }
                    }
                }
            }

            val ids = request.items.map { UUID.randomUUID().toString() }
            session.sendPreparedStatement(
                {
                    setParameter("ids", ids)
                    request.items.split {
                        into("titles") { it.title }
                        into("parents") { it.parent }
                        into("can_consume_resources") { it.canConsumeResources }
                    }
                },
                """
                    insert into project.projects (id, created_at, modified_at, title, parent, dmp, can_consume_resources) 
                    select unnest(:ids::text[]), now(), now(), unnest(:titles::text[]), unnest(:parents::text[]), null, unnest(:can_consume_resources::bool[])
                """
            )

            session.sendPreparedStatement(
                {
                    setParameter("ids", ids)
                    setParameter("username", piUsername)
                },
                """
                    insert into project.project_members (created_at, modified_at, role, username, project_id) 
                    select now(), now(), 'PI', :username, unnest(:ids::text[])
                """
            )

            if (piOverride != null && addSelfWithPiOverride) {
                session.sendPreparedStatement(
                    {
                        setParameter("ids", ids)
                        setParameter("username", actor.safeUsername())
                    },
                    """
                        insert into project.project_members (created_at, modified_at, role, username, project_id) 
                        select now(), now(), 'ADMIN', :username, unnest(:ids::text[])
                    """
                )
            }

            projectCache.invalidate(actor.safeUsername())

            ids.forEach { locateOrCreateAllUsersGroup(it, session) }

            // NOTE(Dan): We don't trigger an update handler here, since we expect further notifications to be
            // dispatched soon (e.g. grant notifications)

            ids
        }.let { ids -> BulkResponse(ids.map { FindByStringId(it) }) }
    }

    suspend fun retrieveProviderProject(
        actorAndProject: ActorAndProject,
        ctx: DBContext = db,
    ): Project {
        val (actor) = actorAndProject
        val username = actor.safeUsername()
        val providerId = if (username.startsWith(AuthProviders.PROVIDER_PREFIX)) {
            username.removePrefix(AuthProviders.PROVIDER_PREFIX)
        } else {
            null
        } ?: throw RPCException("Unable to find provider project", HttpStatusCode.InternalServerError)

        return ctx.withSession { session ->
            val id = retrieveProviderProject(session, providerId)
                ?: throw RPCException("Unable to find provider project", HttpStatusCode.InternalServerError)

            retrieve(
                ActorAndProject(Actor.System, null),
                ProjectsRetrieveRequest(
                    id,
                    includeMembers = true,
                    includeGroups = true,
                    includeFavorite = false,
                    includeArchived = true,
                    includeSettings = true,
                    includePath = true
                )
            )
        }
    }

    private suspend fun retrieveProviderProject(
        session: AsyncDBConnection,
        providerId: String
    ): String? {
        return session.sendPreparedStatement(
            {
                setParameter("provider_id", providerId)
            },
            """
                select r.project
                from
                    provider.providers p join
                    provider.resource r on
                        p.resource = r.id
                where
                    p.unique_name = :provider_id
            """
        ).rows.singleOrNull()?.getString(0)
    }

    suspend fun archive(
        actorAndProject: ActorAndProject,
        request: BulkRequest<FindByStringId>,
        ctx: DBContext = db,
    ) {
        ctx.withSession { session ->
            val projects = request.items.map { it.id }
            requireAdmin(actorAndProject.actor, projects, session)
            session.sendPreparedStatement(
                { setParameter("ids", projects) },
                """
                    update project.projects
                    set archived = true
                    where id = some(:ids::text[])
                """
            )

            updateHandlers.forEach { it(projects) }
        }
    }

    suspend fun renameProject(
        actorAndProject: ActorAndProject,
        request: BulkRequest<RenameProjectRequest>,
        ctx: DBContext = db,
    ) {
        request.items.forEach {
            if (it.newTitle.trim().length != it.newTitle.length) {
                throw RPCException("Project names cannot start or end with whitespace.", HttpStatusCode.BadRequest)
            }
        }

        ctx.withSession(remapExceptions = true) { session ->
            requireAdmin(actorAndProject.actor, request.items.map { it.id }, session)
            request.items.forEach {
                session.sendPreparedStatement(
                    {
                        setParameter("projectId", it.id)
                        setParameter("newTitle", it.newTitle)
                    },
                    """update project.projects set title = :newTitle where id = :projectId"""
                )
            }
        }
    }

    suspend fun unarchive(
        actorAndProject: ActorAndProject,
        request: BulkRequest<FindByStringId>,
        ctx: DBContext = db,
    ) {
        ctx.withSession { session ->
            val projects = request.items.map { it.id }
            requireAdmin(actorAndProject.actor, projects, session)
            session.sendPreparedStatement(
                { setParameter("ids", projects) },
                """
                    update project.projects
                    set archived = false
                    where id = some(:ids::text[])
                """
            )

            updateHandlers.forEach { it(projects) }
        }
    }

    suspend fun toggleFavorite(
        actorAndProject: ActorAndProject,
        request: BulkRequest<FindByStringId>,
        ctx: DBContext = db,
    ) {
        ctx.withSession { session ->
            val projects = request.items.map { it.id }.toSet()
            requireMembership(actorAndProject.actor, projects, session)

            val itemsDeleted = session.sendPreparedStatement(
                {
                    setParameter("ids", projects.toList())
                    setParameter("username", actorAndProject.actor.safeUsername())
                },
                """
                    delete from project.project_favorite
                    where
                        username = :username and
                        project_id = some(:ids::text[])
                    returning project_id
                """
            ).rows.map { it.getString(0)!! }

            val itemsToAdd = request.items.asSequence().filter { it.id !in itemsDeleted }.map { it.id }.toList()
            if (itemsToAdd.isNotEmpty()) {
                session.sendPreparedStatement(
                    {
                        setParameter("ids", itemsToAdd)
                        setParameter("username", actorAndProject.actor.safeUsername())
                    },
                    """
                        insert into project.project_favorite (username, project_id)
                        select :username, unnest(:ids::text[])
                    """
                )
            }
        }
    }

    suspend fun updateSettings(
        actorAndProject: ActorAndProject,
        settings: Project.Settings,
        ctx: DBContext = db,
    ) {
        val (actor) = actorAndProject
        val project = actorAndProject.requireProject()

        // NOTE(Dan): Check if we have anything to do. If not, just return.
        // NOTE(Dan): Right now we only have subproject settings.
        if (settings.subprojects == null) return

        ctx.withSession { session ->
            requireAdmin(actor, listOf(project), session)

            session.sendPreparedStatement(
                {
                    setParameter("project", project)
                    setParameter("allow_renaming", settings.subprojects?.allowRenaming == true)
                },
                """
                    update project.projects
                    set subprojects_renameable = :allow_renaming
                    where id = :project
                """
            )

            updateHandlers.forEach { it(listOf(project)) }
        }
    }

    suspend fun verifyMembership(
        actorAndProject: ActorAndProject,
        request: BulkRequest<FindByStringId>,
        ctx: DBContext = db,
    ) {
        ctx.withSession { session ->
            val projects = request.items.map { it.id }.toSet()
            requireAdmin(actorAndProject.actor, projects, session)
            session.sendPreparedStatement(
                {
                    setParameter("ids", projects.toList())
                    setParameter("username", actorAndProject.actor.safeUsername())
                },
                """
                    insert into project.project_membership_verification (project_id, verification, verified_by) 
                    select unnest(:ids::text[]), now(), :username
                """
            )
        }
    }

    suspend fun browseInvites(
        actorAndProject: ActorAndProject,
        request: ProjectsBrowseInvitesRequest,
        ctx: DBContext = db,
    ): PageV2<ProjectInvite> {
        val pagination = request.normalize()
        return ctx.withSession { session ->
            val items = session.sendPreparedStatement(
                {
                    setParameter("next", pagination.next?.toLongOrNull()?.let { it / 1000 })
                    setParameter("username", actorAndProject.actor.safeUsername())
                    setParameter(
                        "project_filter", if (request.filterType == ProjectInviteType.OUTGOING) {
                            actorAndProject.project
                        } else {
                            null
                        }
                    )
                },
                buildString {
                    appendLine(
                        """
                            select jsonb_build_object(
                                'createdAt', provider.timestamp_to_unix(i.created_at),
                                'invitedBy', i.invited_by,
                                'invitedTo', i.project_id,
                                'recipient', i.username,
                                'projectTitle', p.title
                            )    
                        """
                    )
                    appendLine(
                        """
                            from
                                project.invites i join
                                project.projects p on i.project_id = p.id
                        """
                    )
                    run {
                        appendLine("where")
                        appendLine(
                            """
                                (
                                    :next::bigint is null or
                                    i.created_at < to_timestamp(:next::bigint)
                                )
                            """
                        )
                        when (request.filterType) {
                            ProjectInviteType.INGOING -> {
                                appendLine("and i.username = :username")
                            }

                            ProjectInviteType.OUTGOING -> {
                                appendLine("and i.invited_by = :username")
                                appendLine("and i.project_id = :project_filter")
                            }

                            null -> {
                                appendLine("and (i.username = :username or i.invited_by = :username)")
                            }
                        }
                    }
                    appendLine("order by i.created_at desc")
                    appendLine("limit ${pagination.itemsPerPage}")
                }
            ).rows.map { defaultMapper.decodeFromString<ProjectInvite>(it.getString(0)!!) }

            val next: String? = if (items.size < pagination.itemsPerPage) {
                null
            } else {
                items.last().createdAt.toString()
            }

            PageV2(pagination.itemsPerPage, items, next)
        }
    }

    suspend fun createInvite(
        actorAndProject: ActorAndProject,
        request: BulkRequest<ProjectsCreateInviteRequestItem>,
        ctx: DBContext = db,
    ) {
        // NOTE(Dan): This function will invite users, and notify the users via both email and the internal notification
        // system. The vast majority of the code in this function is related to error handling and checking that the
        // input is actually valid.

        // We start by checking that a project was actually supplied
        val (actor) = actorAndProject
        val project = actorAndProject.requireProject()

        ctx.withSession { session ->
            // Only administrators of a project can invite members. Check that we are an administrator.
            requireAdmin(actor, listOf(project), session)

            // NOTE(Dan): If we are successful, then we need to know the title of the project. We choose to resolve
            // the project early, just in case it fails. It is extremely unlikely (impossible?) that the project does
            // not resolve, since we have just verified admin privileges in the project.
            val resolvedProject = retrieve(actorAndProject, ProjectsRetrieveRequest(project), session)

            // NOTE(Dan): Inviting a user is just a simple insertion into the `project.invites` table. Note that we
            // verify that the user exists and are not already a member/invited to the project. Invalid requests are
            // at this point simply filtered out.
            //
            // We return the users that are successfully invited into the system. We need to know which users were
            // added such that we can notify them about the invitation.
            val usersInvited = session.sendPreparedStatement(
                {
                    setParameter("username", actor.safeUsername())
                    setParameter("recipients", request.items.map { it.recipient })
                    setParameter("project", project)
                },
                """
                    with relevant_invites as (
                        select p.id invited_user
                        from
                            auth.principals p left join
                            project.project_members pm on 
                                p.id = pm.username and
                                pm.project_id = :project left join
                            project.invites i on 
                                p.id = i.invited_by and
                                i.project_id = :project
                        where
                            p.id = some(:recipients::text[]) and
                            p.role != 'SERVICE' and
                            pm.username is null and
                            i.username is null
                    )
                    insert into project.invites (project_id, username, invited_by) 
                    select :project, invited_user, :username
                    from relevant_invites
                    on conflict do nothing
                    returning username
                """
            ).rows.map { it.getString(0)!! }

            // NOTE(Dan): We consider the request successful if at least one user was invited. This makes it a bit
            // easier to copy&paste a large collection of users into the UI and not caring too much about which are
            // already in the project.
            val success = usersInvited.isNotEmpty()
            if (success) {
                // NOTE(Dan): We succeeded! We notify the users via email and internal notification system. Also note
                // that we are not calling `.orThrow()` on the RPCs. This is on purpose, we do not wish to cause a
                // failure just because one of the notification systems are down.
                val notifications = usersInvited.map { user ->
                    CreateNotification(
                        user,
                        Notification(
                            NotificationType.PROJECT_INVITE.name,
                            "${actor.safeUsername()} has invited you to collaborate"
                        )
                    )
                }

                val emails = usersInvited.map { user ->
                    SendRequestItem(user, Mail.ProjectInviteMail(resolvedProject.specification.title))
                }

                NotificationDescriptions.createBulk.call(
                    BulkRequest(notifications),
                    serviceClient
                )

                MailDescriptions.sendToUser.call(
                    BulkRequest(emails),
                    serviceClient
                )
            } else {
                // NOTE(Dan): This entire branch indicates that no new users were invited to the project. The rest
                // of the code is simply here to provider a meaningful error message to the client.

                if (request.items.size == 1) {
                    // If we have just a single recipient, try to find the mistake.
                    val status = session.sendPreparedStatement(
                        {
                            setParameter("project", project)
                            setParameter("recipients", request.items.map { it.recipient })
                        },
                        """
                            select
                                pm.username is not null is_member, 
                                i.username is not null has_been_invited
                            from
                                auth.principals p left join
                                project.project_members pm on 
                                    p.id = pm.username and
                                    pm.project_id = :project left join
                                project.invites i on 
                                    p.id = i.invited_by and
                                    i.project_id = :project
                            where
                                p.id = some(:recipients::text[])
                        """
                    ).rows.map { Pair(it.getBoolean(0)!!, it.getBoolean(1)!!) }.singleOrNull()

                    if (status == null) {
                        throw RPCException(
                            "Unknown user, try checking if the username is valid.",
                            HttpStatusCode.BadRequest
                        )
                    }

                    val (isMember, hasBeenInvited) = status
                    if (isMember) {
                        throw RPCException(
                            "The user you are trying to invite is already a member of the project!",
                            HttpStatusCode.BadRequest
                        )
                    }

                    if (hasBeenInvited) {
                        // This is technically OK
                        return@withSession
                    }

                    throw RPCException(
                        "An error has occurred, could not invite the user to the project. Try contacting support if " +
                            "the problem persists.",
                        HttpStatusCode.BadGateway
                    )
                } else {
                    // Otherwise, just ask them to check the input. Something clearly went wrong.
                    throw RPCException(
                        "None of the names supplied could be invited to the project. Try checking if the " +
                            "usernames are correct.",
                        HttpStatusCode.BadRequest
                    )
                }
            }
        }
    }

    suspend fun acceptInvite(
        actorAndProject: ActorAndProject,
        request: ProjectsAcceptInviteRequest,
        ctx: DBContext = db,
    ) {
        val projects = request.items.map { it.project }
        ctx.withSession { session ->
            val userInProjects = session
                .sendPreparedStatement(
                    {
                        setParameter("username", actorAndProject.actor.safeUsername())
                        setParameter("projects", projects)
                    },
                    """
                        with accepted_invites as (
                            delete from project.invites
                            where
                                username = :username and
                                project_id = some(:projects::text[])
                            returning project_id
                        )
                        insert into project.project_members (created_at, modified_at, role, username, project_id) 
                        select now(), now(), 'USER', :username, project_id
                        from accepted_invites
                        on conflict (username, project_id) do nothing
                        returning username, project_id
                    """
                )
                .rows
                .map { it.getString(0)!! to it.getString(1)!! }

            if (userInProjects.isEmpty()) {
                throw RPCException(
                    "Could not accept your project invite. Maybe it is no longer valid? Try refreshing your browser.",
                    HttpStatusCode.BadRequest
                )
            }

            for ((project, usersAndProjects) in userInProjects.groupBy { it.second }) {
                val group = locateOrCreateAllUsersGroup(project, session)
                createGroupMember(
                    ActorAndProject(Actor.System, null),
                    BulkRequest(usersAndProjects.map { GroupMember(it.first, group) }),
                    ctx = session,
                    dispatchUpdate = false,
                    allowNoChange = true
                )
            }

            updateHandlers.forEach { it(projects) }
        }

        projectCache.invalidate(actorAndProject.actor.safeUsername())
    }

    suspend fun deleteInvite(
        actorAndProject: ActorAndProject,
        request: ProjectsDeleteInviteRequest,
        ctx: DBContext = db,
    ) {
        // NOTE(Dan): This endpoint is used both by the recipient and the sender.
        val (actor) = actorAndProject
        val myUsername = actor.safeUsername()
        val isDeletingAsRecipient = request.items.all { it.username == myUsername }

        ctx.withSession { session ->
            val success = session.sendPreparedStatement(
                {
                    setParameter("user_is_recipient", isDeletingAsRecipient)
                    setParameter("username", myUsername)
                    setParameter("projects", request.items.map { it.project })
                },
                """
                    delete from project.invites
                    where
                        project_id = some(:projects::text[]) and
                        (
                            (:user_is_recipient =  true and username = :username) or
                            (:user_is_recipient = false and invited_by = :username)
                        )
                """
            ).rowsAffected > 0

            if (!success) {
                throw RPCException(
                    "Could not delete your project invite. Maybe it is no longer valid? Try refreshing your browser.",
                    HttpStatusCode.BadRequest
                )
            }
        }
    }

    suspend fun deleteMember(
        actorAndProject: ActorAndProject,
        request: BulkRequest<ProjectsDeleteMemberRequestItem>,
        ctx: DBContext = db,
    ) {
        ctx.withSession { session ->
            // Verify that the request is valid and makes sense
            val (actor) = actorAndProject
            val isLeaving = request.items.size == 1 && request.items.single().username == actor.safeUsername()

            // Personal workspaces are not actual projects. Make sure we didn't get this request without a project.
            val project = actorAndProject.requireProject()

            // Make sure that we are not trying to kick ourselves along with other users. Not allowing this simplifies
            // our code quite a bit.
            if (!isLeaving) {
                val containsSelf = request.items.any { it.username == actor.safeUsername() }
                if (containsSelf) {
                    throw RPCException(
                        "Cannot remove members from a project while also leaving",
                        HttpStatusCode.BadRequest
                    )
                }
            }

            // Enforce admin permissions if we are not leaving the project
            if (!isLeaving) {
                requireAdmin(actor, listOf(project), session)
            }

            // Delete from all groups in the project
            session.sendPreparedStatement(
                {
                    setParameter("project", project)
                    setParameter("users", request.items.map { it.username })
                },
                """
                    delete from project.group_members gm
                    using project.groups g
                    where
                        g.id = gm.group_id and
                        g.project = :project and
                        gm.username = some(:users::text[])
                """
            )

            // Actually remove from the project
            val success = session.sendPreparedStatement(
                {
                    setParameter("project", project)
                    setParameter("users", request.items.map { it.username })
                },
                """
                    delete from project.project_members
                    where
                        project_id = :project and
                        username = some(:users::text[]) and
                        role != 'PI'
                """
            ).rowsAffected > 0

            if (!success) {
                // Find an appropriate error message if we did not succeed.
                if (isLeaving) {
                    val isPrincipalInvestigator = runCatching {
                        requireRole(actor, setOf(project), session, setOf(ProjectRole.PI))
                    }.isSuccess

                    if (isPrincipalInvestigator) {
                        throw RPCException(
                            "You cannot leave the project while you are a principal investigator. " +
                                "You must transfer this to someone else before leaving.",
                            HttpStatusCode.BadRequest
                        )
                    } else {
                        throw RPCException(
                            "Unable to leave project. Maybe you are no longer a member?",
                            HttpStatusCode.BadRequest
                        )
                    }
                } else {
                    throw RPCException(
                        "Unable to remove members from project. Maybe they are no longer in the project?",
                        HttpStatusCode.BadRequest
                    )
                }
            }

            updateHandlers.forEach { it(listOf(project)) }
        }

        for (reqItem in request.items) {
            projectCache.invalidate(reqItem.username)
        }
    }

    suspend fun changeRole(
        actorAndProject: ActorAndProject,
        request: BulkRequest<ProjectsChangeRoleRequestItem>,
        ctx: DBContext = db,
    ) {
        val (actor) = actorAndProject
        val project = actorAndProject.requireProject()

        // NOTE(Dan): Some edge-cases requires us to modify the changes. As a result, you should use `requestItems`
        // instead of `request.items` in the code below.
        val requestItems = request.items.toMutableList()

        run {
            // NOTE(Dan): To ensure predictable and easy-to-understand results, we don't allow for duplicates. 
            // We could technically implement consistent results, but it complicates the SQL code required and
            // I don't see a good use-case for this.
            val seen = HashSet<String>()
            for (item in requestItems) {
                if (item.username in seen) {
                    throw RPCException(
                        "Your request contains conflicting changes for ${item.username}. " +
                            "Try again or contact support for assistance.",
                        HttpStatusCode.BadRequest
                    )
                }

                seen.add(item.username)
            }
        }

        // NOTE(Dan): We don't allow someone to change their own role. I don't see an obvious use-case for this, and
        // it simplifies our code a bit (see PI transfer below).
        val requestContainsSelf = requestItems.any { it.username == actor.safeUsername() }
        if (requestContainsSelf) {
            throw RPCException("You cannot change your own role!", HttpStatusCode.BadRequest)
        }

        ctx.withSession { session ->
            // NOTE(Dan): Only the PI can promote someone to a PI. If they are promoting someone to a PI, then we must
            // demote them to an admin also.
            val hasPiTransfer = requestItems.any { it.role == ProjectRole.PI }
            if (hasPiTransfer) {
                requireRole(actor, setOf(project), session, setOf(ProjectRole.PI))

                // NOTE(Dan): This cannot conflict with an existing change, since we don't normally allow you to change
                // your own role.
                requestItems.add(ProjectsChangeRoleRequestItem(actor.safeUsername(), ProjectRole.ADMIN))
            } else {
                // If we are not trying to make someone the PI, then we only need to be an admin.
                requireAdmin(actor, setOf(project), session)
            }

            // NOTE(Dan): Performing the update itself is straightforward. Just change the role in the table. We return
            // the username of the successful changes, such that we can send the correct notifications.
            val updatedUsers = session.sendPreparedStatement(
                {
                    setParameter("project", project)
                    requestItems.split {
                        into("users") { it.username }
                        into("roles") { it.role.name }
                    }
                },
                """
                    with changes as (
                        select 
                            unnest(:users::text[]) user_to_update, 
                            unnest(:roles::text[]) new_role
                    )
                    update project.project_members
                    set role = new_role
                    from changes
                    where
                        username = user_to_update and
                        project_id = :project
                    returning username
                """
            ).rows.map { it.getString(0)!! }

            val success = updatedUsers.isNotEmpty()
            if (!success) {
                // NOTE(Dan): If none of them were successful, then fail with an error message. We choose to return OK
                // if some were successful, since the client might simply have slightly out-of-date data.
                throw RPCException(
                    "Unable to change role of ${if (requestItems.size > 1) "members" else "member"}. " +
                        "Maybe they are no longer a member? Try again or contact support for assistance.",
                    HttpStatusCode.BadRequest
                )
            }

            // NOTE(Dan): The change was successful! We notify PI and admins of the project about the change.
            val titleAndAdmins = session.sendPreparedStatement(
                {
                    setParameter("project", project)
                },
                """
                    select p.title, pm.username
                    from
                        project.projects p left join
                        project.project_members pm on pm.project_id = p.id
                    where
                        p.id = :project and
                        (pm.role = 'ADMIN' or pm.role = 'PI')
                """
            ).rows.map { Pair(it.getString(0)!!, it.getString(1)!!) }

            // NOTE(Dan): This is really strange, fail with a warning
            if (titleAndAdmins.isEmpty()) {
                log.warn("changeRole succeeded for '$project' but no admins were found!")
                return@withSession
            }

            val projectTitle = titleAndAdmins.first().first
            val notifications = ArrayList<CreateNotification>()
            val emails = ArrayList<SendRequestItem>()
            for (reqItem in requestItems) {
                if (reqItem.username !in updatedUsers) continue

                val notificationMessage =
                    "${reqItem.username} has changed role to ${reqItem.role} in project: $projectTitle"
                for ((_, admin) in titleAndAdmins) {
                    notifications.add(
                        CreateNotification(
                            admin,
                            Notification(
                                NotificationType.PROJECT_ROLE_CHANGE.name,
                                notificationMessage,
                                meta = JsonObject(mapOf("projectId" to JsonPrimitive(project))),
                            )
                        )
                    )

                    emails.add(
                        SendRequestItem(
                            admin,
                            Mail.UserRoleChangeMail(
                                reqItem.username,
                                reqItem.role.name,
                                projectTitle
                            )
                        )
                    )
                }
            }

            // NOTE(Dan): As always, we don't fail if one of the notification mechanisms fail.
            NotificationDescriptions.createBulk.call(
                BulkRequest(notifications),
                serviceClient
            )

            MailDescriptions.sendToUser.call(
                BulkRequest(emails),
                serviceClient
            )

            updateHandlers.forEach { it(listOf(project)) }
            for (reqItem in requestItems) {
                projectCache.invalidate(reqItem.username)
            }
        }
    }

    suspend fun createGroup(
        actorAndProject: ActorAndProject,
        request: BulkRequest<Group.Specification>,
        ctx: DBContext = db,
        dispatchUpdate: Boolean = true,
    ): BulkResponse<FindByStringId> {
        val (actor) = actorAndProject
        val projects = request.items.map { it.project }.toSet()
        return ctx.withSession { session ->
            requireAdmin(actor, projects, session)

            val ids = request.items.map { UUID.randomUUID().toString() }
            val createdGroups = session.sendPreparedStatement(
                {
                    setParameter("ids", ids)
                    request.items.split {
                        into("projects") { it.project }
                        into("titles") { it.title }
                    }
                },
                """
                    insert into project.groups(title, project, id)
                    select unnest(:titles::text[]), unnest(:projects::text[]), unnest(:ids::text[])
                    on conflict do nothing
                    returning id
                """
            ).rows.map { it.getString(0)!! }

            val success = createdGroups.isNotEmpty()
            if (!success) {
                throw RPCException(
                    "Unable to create ${if (request.items.size > 1) "groups" else "group"}. " +
                        "Maybe a group with this name already exists in your project? " +
                        "Try refreshing or contact support for assistance.",
                    HttpStatusCode.BadRequest
                )
            }

            if (dispatchUpdate) updateHandlers.forEach { it(projects) }
            BulkResponse(ids.map { FindByStringId(it) })
        }
    }

    private suspend fun requireAdminOfGroups(
        actor: Actor,
        groups: Collection<String>,
        session: AsyncDBConnection,
    ) {
        if (actor == Actor.System) return

        val withoutDuplicates = groups.toSet()
        val numberOfGroups = session.sendPreparedStatement(
            {
                setParameter("username", actor.safeUsername())
                setParameter("groups", withoutDuplicates.toList())
            },
            """
                select g.id
                from
                    project.project_members pm join
                    project.groups g on pm.project_id = g.project
                where
                    g.id = some(:groups::text[]) and
                    pm.username = :username and
                    (pm.role = 'PI' or pm.role = 'ADMIN')
            """
        ).rows.size

        if (numberOfGroups != withoutDuplicates.size) {
            throw RPCException(
                "You must be an admin of your project to perform this action! " +
                    "Try refreshing or contact support for assistance.",
                HttpStatusCode.BadRequest
            )
        }
    }

    suspend fun renameGroup(
        actorAndProject: ActorAndProject,
        request: BulkRequest<ProjectsRenameGroupRequestItem>,
        ctx: DBContext = db,
    ) {
        for (reqItem in request.items) {
            try {
                checkSingleLine(ProjectsRenameGroupRequestItem::newTitle, reqItem.newTitle, maximumSize = 128)
            } catch (ex: Throwable) {
                throw RPCException("The new title is too long! Try a shorter title.", HttpStatusCode.BadRequest)
            }

            if (reqItem.newTitle == ALL_USERS_GROUP_TITLE) {
                throw RPCException(
                    "The group '$ALL_USERS_GROUP_TITLE' is special. You cannot rename a group to have this name.",
                    HttpStatusCode.BadRequest
                )
            }
        }

        val (actor) = actorAndProject
        ctx.withSession { session ->
            val groups = request.items.map { it.group }
            requireAdminOfGroups(actor, groups, session)

            val affectedProjectsAndOldTitles = session.sendPreparedStatement(
                {
                    setParameter("groups", groups)
                    setParameter("titles", request.items.map { it.newTitle })
                },
                """
                    with
                        changes as (
                            select
                                unnest(:groups::text[]) group_id, 
                                unnest(:titles::text[]) new_title
                        ),
                        updated as (
                            update project.groups g
                            set title = new_title
                            from changes
                            where g.id = group_id    
                        )
                    select g.project, g.title
                    from 
                        changes c join 
                        project.groups g on c.group_id = g.id
                """
            ).rows.map { it.getString(0)!! to it.getString(1)!! }
            val success = affectedProjectsAndOldTitles.isNotEmpty()

            if (!success) {
                throw RPCException(
                    "Unable to rename groups. Maybe the group no longer exists? " +
                        "Try refreshing or contact support for assistance.",
                    HttpStatusCode.BadRequest
                )
            }

            if (affectedProjectsAndOldTitles.any { it.second == ALL_USERS_GROUP_TITLE }) {
                throw RPCException(
                    "The group '$ALL_USERS_GROUP_TITLE' is special. You cannot rename a group with this name.",
                    HttpStatusCode.BadRequest
                )
            }

            updateHandlers.forEach { it(affectedProjectsAndOldTitles.map { it.first }) }
        }
    }

    suspend fun deleteGroup(
        actorAndProject: ActorAndProject,
        request: BulkRequest<FindByStringId>,
        ctx: DBContext = db,
    ) {
        val (actor) = actorAndProject
        ctx.withSession { session ->
            val groups = request.items.map { it.id }
            requireAdminOfGroups(actor, groups, session)

            session.sendPreparedStatement(
                { setParameter("groups", groups) },
                """
                    delete from provider.resource_acl_entry e
                    where e.group_id = some(:groups::text[])
                """
            )

            session.sendPreparedStatement(
                { setParameter("groups", groups) },
                "delete from project.group_members where group_id = some(:groups::text[])"
            )

            val affectedProjectsAndTitles = session.sendPreparedStatement(
                { setParameter("groups", groups) },
                """
                    delete from project.groups g
                    where id = some(:groups::text[])
                    returning g.project, g.title
                """
            ).rows.map { it.getString(0)!! to it.getString(1)!! }

            val success = affectedProjectsAndTitles.isNotEmpty()
            if (!success) {
                throw RPCException(
                    "Unable to delete groups. Maybe the group no longer exists? " +
                        "Try refreshing or contact support for assistance.",
                    HttpStatusCode.BadRequest
                )
            }

            if (affectedProjectsAndTitles.any { it.second == ALL_USERS_GROUP_TITLE }) {
                throw RPCException(
                    "The group '$ALL_USERS_GROUP_TITLE' is special. You cannot delete a group with this name.",
                    HttpStatusCode.BadRequest
                )
            }

            updateHandlers.forEach { it(affectedProjectsAndTitles.map { it.first }) }
        }
    }

    suspend fun createGroupMember(
        actorAndProject: ActorAndProject,
        request: BulkRequest<GroupMember>,
        ctx: DBContext = db,
        dispatchUpdate: Boolean = true,
        allowNoChange: Boolean = false
    ) {
        val (actor) = actorAndProject
        ctx.withSession { session ->
            val groups = request.items.map { it.group }
            requireAdminOfGroups(actor, groups, session)

            val success = session.sendPreparedStatement(
                {
                    request.items.split {
                        into("members") { it.username }
                        into("groups") { it.group }
                    }
                },
                """
                    with
                        raw_changes as (
                            select unnest(:members::text[]) member, unnest(:groups::text[]) group_id
                        ),
                        changes as (
                            select c.member, c.group_id
                            from
                                raw_changes c join
                                project.groups g on c.group_id = g.id join
                                project.project_members pm on
                                    g.project = pm.project_id and
                                    c.member = pm.username
                        )
                    insert into project.group_members (username, group_id)
                    select c.member, c.group_id
                    from changes c
                    on conflict (group_id, username) do nothing;
                """
            ).rowsAffected > 0

            if (!success && !allowNoChange) {
                throw RPCException(
                    "Unable to add member to group. Maybe they are no longer in the project? " +
                        "Try refreshing or contact support for assistance.",
                    HttpStatusCode.BadRequest
                )
            }

            val affectedProjects = session.sendPreparedStatement(
                {
                    setParameter("groups", request.items.map { it.group })
                },
                """
                    select g.project
                    from project.groups g
                    where id = some(:groups)
                """
            ).rows.map { it.getString(0)!! }

            if (dispatchUpdate) updateHandlers.forEach { it(affectedProjects) }
        }
    }

    suspend fun deleteGroupMember(
        actorAndProject: ActorAndProject,
        request: BulkRequest<GroupMember>,
        ctx: DBContext = db,
    ) {
        val (actor) = actorAndProject
        ctx.withSession { session ->
            val groups = request.items.map { it.group }
            requireAdminOfGroups(actor, groups, session)

            val success = session.sendPreparedStatement(
                {
                    request.items.split {
                        into("members") { it.username }
                        into("groups") { it.group }
                    }
                },
                """
                    with changes as (
                        select unnest(:members::text[]) member, unnest(:groups::text[]) group_id
                    )
                    delete from project.group_members gm
                    using changes c
                    where
                        c.member = gm.username and
                        c.group_id = gm.group_id
                """
            ).rowsAffected > 0

            if (!success) {
                throw RPCException(
                    "Unable to remove member of group. Maybe they are no longer in the group? " +
                        "Try refreshing or contact support for assistance.",
                    HttpStatusCode.BadRequest
                )
            }

            val affectedProjectsAndGroupTitles = session.sendPreparedStatement(
                {
                    setParameter("groups", request.items.map { it.group })
                },
                """
                    select g.project, g.title
                    from project.groups g
                    where id = some(:groups)
                """
            ).rows.map { it.getString(0)!! to it.getString(1)!! }

            if (affectedProjectsAndGroupTitles.any { it.second == ALL_USERS_GROUP_TITLE }) {
                throw RPCException(
                    "The group '$ALL_USERS_GROUP_TITLE' is special. You cannot remove a member from this group.",
                    HttpStatusCode.BadRequest
                )
            }

            updateHandlers.forEach { it(affectedProjectsAndGroupTitles.map { it.first }) }
        }
    }

    private suspend fun Project.postProcess(isProvider: Boolean): Project {
        return listOf(this).postProcess(isProvider).single()
    }

    private suspend fun List<Project>.postProcess(isProvider: Boolean): List<Project> {
        // TODO(Dan): Check if we are allowed to do the following:
        if (!developmentMode) return this

        val list = this
        if (!isProvider) return list
        val usernames = list.flatMap { it.status.members?.map { it.username } ?: emptyList() }.toSet()
        if (usernames.isNotEmpty()) {
            val emails = db.withSession { session ->
                session.sendPreparedStatement(
                    {
                        setParameter("usernames", usernames.toList())
                    },
                    """
                        select id, email
                        from auth.principals
                        where
                            id = some(:usernames::text[]) and
                            (role = 'USER' or role = 'ADMIN')
                    """
                ).rows.mapNotNull {
                    val username = it.getString(0)!!
                    val mail = it.getString(1) ?: return@mapNotNull null
                    username to mail
                }.toMap()
            }

            for (project in list) {
                val members = project.status.members ?: continue
                for (member in members) {
                    member.email = emails[member.username]
                }
            }
        }
        return list
    }

    companion object : Loggable {
        override val log = logger()

        const val ALL_USERS_GROUP_TITLE = "All users"
    }
}
