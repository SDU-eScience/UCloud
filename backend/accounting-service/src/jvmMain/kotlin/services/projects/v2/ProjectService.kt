package dk.sdu.cloud.accounting.services.projects.v2

import dk.sdu.cloud.*
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
import dk.sdu.cloud.service.db.async.AsyncDBConnection
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.sendPreparedStatement
import dk.sdu.cloud.service.db.async.withSession
import kotlinx.serialization.decodeFromString
import java.util.*

class ProjectService(
    private val db: DBContext,
    private val serviceClient: AuthenticatedClient,
    private val projectCache: ProjectCache,
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

        return if (providerId != null) {
            PartialQuery(
                {
                    setParameter("provider_id", providerId)
                    setParameter("id", id)
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
            // NOTE(Dan): If a user is making the request, then we must find the projects for which they are a
            // member. We will optionally fetch favorite data in case the sorting requires this.

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
                        when (sortBy) {
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

                        when (sortDirection) {
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
        // NOTE(Dan): `loadProjects` fetches additional relevant information about a project, based on the
        // `ProjectFlags`. It is also responsible for JSON-ifying the rows before the response is sent to the backend.
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
        return ctx.withSession(remapExceptions = true) { session ->
            // NOTE(Dan): First we check if the actor is allowed to create _all_ of the requested projects. The system
            // actor is, as always, allowed to create any project. Otherwise, you can create a project if you are an
            // administrator of the parent project. If the requested project is root-level, then you must be a UCloud
            // administrator.
            val (actor) = actorAndProject
            if (actor != Actor.System) {
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

            val ids = request.items.map { UUID.randomUUID().toString() }
            session.sendPreparedStatement(
                {
                    setParameter("ids", ids)
                    request.items.split {
                        into("titles") { it.title }
                        into("parents") { it.parent }
                    }
                },
                """
                    insert into project.projects (id, created_at, modified_at, title, parent, dmp) 
                    select unnest(:ids::text[]), now(), now(), unnest(:titles::text[]), unnest(:parents::text[]), null
                """
            )

            session.sendPreparedStatement(
                {
                    setParameter("ids", ids)
                    setParameter("username", actor.safeUsername())
                },
                """
                    insert into project.project_members (created_at, modified_at, role, username, project_id) 
                    select now(), now(), 'PI', :username, unnest(:ids::text[])
                """
            )

            projectCache.invalidate(actor.safeUsername())
            ids
        }.let { ids -> BulkResponse(ids.map { FindByStringId(it) }) }
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
        val (actor, project) = actorAndProject
        if (project == null) {
            throw RPCException(
                "You cannot update settings of your personal workspace. Try selecting a different project.",
                HttpStatusCode.BadRequest
            )
        }

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
                },
                buildString {
                    appendLine(
                        """
                            select jsonb_build_object(
                                'createdAt', timestamp_to_unix(i.created_at),
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
                                    i.created_at > to_timestamp(:next::bigint)
                                )
                            """
                        )
                        when (request.filterType) {
                            ProjectInviteType.INGOING -> {
                                appendLine("and i.username = :username")
                            }
                            ProjectInviteType.OUTGOING -> {
                                appendLine("and i.invited_by = :username")
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
        val (actor, project) = actorAndProject
        if (project == null) {
            throw RPCException(
                "You cannot invite someone to your personal workspace. Try selecting a different project!",
                HttpStatusCode.BadRequest
            )
        }

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
                            pm.username is null and
                            i.username is null
                    )
                    insert into project.invites (project_id, username, invited_by) 
                    select :project, invited_user, :username
                    from relevant_invites
                    returning invited_user
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
        ctx.withSession { session ->
            val success = session.sendPreparedStatement(
                {
                    setParameter("username", actorAndProject.actor.safeUsername())
                    setParameter("projects", request.items.map { it.project })
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
                """
            ).rowsAffected > 0

            if (!success) {
                throw RPCException(
                    "Could not accept your project invite. Maybe it is no longer valid? Try refreshing your browser.",
                    HttpStatusCode.BadRequest
                )
            }
        }

        projectCache.invalidate(actorAndProject.actor.safeUsername())
    }

    suspend fun deleteInvite(
        actorAndProject: ActorAndProject,
        request: ProjectsDeleteInviteRequest,
        ctx: DBContext = db,
    ) {
        // NOTE(Dan): This endpoint is used both by the recipient and the sender.
        ctx.withSession { session ->
            val success = session.sendPreparedStatement(
                {
                    setParameter("username", actorAndProject.actor.safeUsername())
                    setParameter("projects", request.items.map { it.project })
                },
                """
                    delete from project.invites
                    where
                        project_id = some(:projects::text[]) and
                        (
                            username = :username or
                            invited_by = :username
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
            val (actor, project) = actorAndProject
            val isLeaving = request.items.size == 1 && request.items.single().username == actor.safeUsername()

            // Personal workspaces are not actual projects. Make sure we didn't get this request without a project.
            if (project == null) {
                if (isLeaving) {
                    throw RPCException("You cannot leave your personal workspace.", HttpStatusCode.BadRequest)
                } else {
                    throw RPCException(
                        "You cannot remove members from your personal workspace.",
                        HttpStatusCode.BadRequest
                    )
                }
            }

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
        }

        for (reqItem in request.items) {
            projectCache.invalidate(reqItem.username)
        }
    }
}
