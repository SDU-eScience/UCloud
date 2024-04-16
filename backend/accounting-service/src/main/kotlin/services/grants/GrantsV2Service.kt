package dk.sdu.cloud.accounting.services.grants

import dk.sdu.cloud.*
import dk.sdu.cloud.accounting.api.ProductCategoryIdV2
import dk.sdu.cloud.accounting.api.WalletOwner
import dk.sdu.cloud.accounting.services.accounting.AccountingRequest
import dk.sdu.cloud.accounting.services.accounting.AccountingSystem
import dk.sdu.cloud.accounting.services.accounting.didLoadUnsynchronizedGrants
import dk.sdu.cloud.accounting.services.projects.v2.ProjectService
import dk.sdu.cloud.accounting.util.IdCard
import dk.sdu.cloud.accounting.util.IdCardService
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.bulkRequestOf
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.grant.api.*
import dk.sdu.cloud.mail.api.Mail
import dk.sdu.cloud.mail.api.MailDescriptions
import dk.sdu.cloud.mail.api.SendRequestItem
import dk.sdu.cloud.notification.api.CreateNotification
import dk.sdu.cloud.notification.api.Notification
import dk.sdu.cloud.notification.api.NotificationDescriptions
import dk.sdu.cloud.provider.api.checkDeicReferenceFormat
import dk.sdu.cloud.service.Time
import dk.sdu.cloud.service.db.async.AsyncDBConnection
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.sendPreparedStatement
import dk.sdu.cloud.service.db.async.withSession
import io.ktor.utils.io.*
import io.ktor.utils.io.jvm.javaio.*
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.ByteArrayOutputStream
import kotlin.random.Random

class GrantsV2Service(
    private val db: DBContext,
    private val idCardService: IdCardService,
    private val accountingService: AccountingSystem,
    private val serviceClient: AuthenticatedClient,
    private val defaultTemplate: String,
    private val projects: ProjectService,
) {
    // Introduction
    // =================================================================================================================
    // The grant service is responsible for handling the entirety of the "grant application" feature of UCloud. Its
    // primary purpose is to allow end-users to apply for resources from one or more grant givers. A grant giver is an
    // ordinary project who wishes to sub-allocate their own resources. Typical real world examples include:
    // a national allocator, a university department or a research center. The applicant starts by writing a
    // `GrantApplication` to the `GrantGiver`s. The application `Form` is derived from each `GrantGiver`'s
    // `GrantRequestSettings`. Once submitted, the applicant and grant givers can enter a dialog using the comment
    // system. Ultimately, each `GrantGiver` will have to either approve or reject the application. If all `GrantGiver`s
    // approve an application, then the applicant will receive their resources. If the application has indicated that
    // this is for a new project, then a new project will be created and the applicant will become the PI of the newly
    // created project.
    //
    // This is not a system will receive an enormous amount of traffic, as a result, it is not written with the aim
    // of achieving good performance. The architecture of this is centered around `Command`s and `Action`s.
    //
    // A `Command` describes a wish of the end-user. It describes something which should happen to a specific
    // `GrantApplication`. For example, a user may command the system to insert a new revision of the application by
    // using `Command.InsertRevision`.
    private sealed class Command {
        data class InsertRevision(val comment: String, val doc: GrantApplication.Document) : Command()
        data class UpdateApprovalState(val projectId: String, val newState: GrantApplication.State) : Command()
        data class Transfer(val comment: String, val sourceProjectId: String, val targetProjectId: String) : Command()
        data object Withdraw : Command()
        data class PostComment(val comment: String) : Command()
        data class DeleteComment(val commentId: String) : Command()
        data object Synchronize : Command()
    }

    // On the other hand, an `Action` describes a side effect. Side effects are created as a result of a `Command`. Each
    // `Command` will trigger 0 or more `Action`s. Each `Action` will interact with external systems to get the desired
    // effect. This normally includes one of the following systems: the database (to persistently store the change),
    // the accounting system (to grant resources) or the notification system (to tell you that something happened).
    private sealed class Action {
        data object Initialize : Action()
        data class NewRevision(val revision: GrantApplication.Revision) : Action()
        data class NewComment(val comment: GrantApplication.Comment) : Action()
        data class DeleteComment(val id: String) : Action()
        data class UpdateApprovalState(val approver: String, val state: GrantApplication.State) : Action()
        data class UpdateOverallState(val overallState: GrantApplication.State) : Action()
        data object GrantResources : Action()
    }

    // Initialization
    // =================================================================================================================
    // Initialization is triggered by the server component once the AccountingSystem is operational. This code will
    // ensure that any grants which were not successfully synchronized are synchronized now.
    suspend fun init() {
        val notYetSynchronized = db.withSession { session ->
            session.sendPreparedStatement(
                {},
                """
                    select id, pm.username, pm.project_id
                    from
                        "grant".applications
                        join "grant".grant_giver_approvals gga on applications.id = gga.application_id
                        join project.project_members pm on
                            gga.project_id = pm.project_id
                            and pm.role = 'PI'
                    where
                        synchronized = false
                        and overall_state = 'APPROVED'
                """
            ).rows.associate {
                // NOTE(Dan): This will implicitly keep a single PI of a grant giver. This is only needed to find
                // any actor which will pass the permission check for the retrieve call.

                val id = it.getLong(0)!!
                val username = it.getString(1)!!
                val projectId = it.getString(2)!!
                id to ActorAndProject(Actor.SystemOnBehalfOfUser(username), projectId)
            }
        }

        for ((id, actorAndProject) in notYetSynchronized) {
            modify(actorAndProject, id.toString()) {
                runCommand(Command.Synchronize)
            }
        }

        didLoadUnsynchronizedGrants.set(true)
    }


    // Modification API
    // =================================================================================================================
    // The modification API, triggered via `modify`, allows the program to send a stream of `Command`s to be applied to
    // a single `GrantApplication`. Any end-user call which requires modification of the data model will have to go
    // through this call. In a later section, we will cover the implementation of the `ModificationContext` which
    // actually processes `Command`s and produces `Action`s.
    //
    // Notable functions:
    // - `modify`        : Triggers modification of an existing or a new `GrantApplication`
    // - `submitRevision`: Uploads a new revision of a `GrantApplication` (potentially creating a new application)
    // - `updateState`   : Updates the state of a `GrantApplication`
    // - `transfer`      : Transfers a request from one `GrantGiver` to another `GrantGiver`
    // - `postComment`   : Posts a new comment to an application
    // - `deleteComment` : Deletes a comment from an application

    private suspend fun modify(
        actorAndProject: ActorAndProject,
        id: String?,
        initialDocument: GrantApplication.Document? = null,
        block: suspend ModificationContext.() -> Unit,
    ): ModificationResult {
        require((id != null && initialDocument == null) || (id == null && initialDocument != null))

        return db.withSession { session ->
            val application = if (id == null) {
                val doc = initialDocument!!
                val username = actorAndProject.actor.safeUsername()
                val currentRevision = GrantApplication.Revision(Time.now(), username, 0, doc)

                var projectTitle: String? = null
                var projectPi = username
                when (val r = doc.recipient) {
                    is GrantApplication.Recipient.ExistingProject -> {
                        val (title, pi) = session.sendPreparedStatement(
                            {
                                setParameter("username", username)
                                setParameter("project_id", r.id)
                            },
                            """
                                select distinct p.title, pi.username
                                from
                                    project.projects p,
                                    project.project_members pm,
                                    project.project_members pi
                                where
                                    p.id = :project_id
                                    
                                    and pm.username = :username
                                    and pm.project_id = p.id
                                    and (pm.role = 'ADMIN' or pm.role = 'PI')
                                    
                                    and pi.project_id = p.id
                                    and pi.role = 'PI'
                            """
                        ).rows.singleOrNull()?.let { Pair(it.getString(0)!!, it.getString(1)!!) }
                            ?: throw RPCException("Unknown recipient project", HttpStatusCode.BadRequest)

                        projectTitle = title
                        projectPi = pi
                    }

                    is GrantApplication.Recipient.NewProject -> {
                        projectTitle = r.title
                    }

                    is GrantApplication.Recipient.PersonalWorkspace -> {
                        projectTitle = null
                    }
                }

                GrantApplication(
                    "",
                    username,
                    Time.now(),
                    Time.now(),
                    currentRevision,
                    GrantApplication.Status(
                        GrantApplication.State.IN_PROGRESS,
                        doc.allocationRequests.map { it.grantGiver }.toSet().map {
                            GrantApplication.GrantGiverApprovalState(it, "", GrantApplication.State.IN_PROGRESS)
                        },
                        emptyList(),
                        listOf(currentRevision),
                        projectTitle,
                        projectPi
                    )
                )
            } else {
                retrieve(actorAndProject, id, session)
            }

            ModificationContext(
                this,
                actorAndProject,
                idCardService.fetchIdCard(actorAndProject),
                session,
                retrieveGrantGivers(actorAndProject, existingApplicationId = id, ctx = session),
                application,
            ).also { it.block() }.commit()
        }
    }

    suspend fun submitRevision(
        actorAndProject: ActorAndProject,
        request: GrantsV2.SubmitRevision.Request,
    ): FindByStringId {
        if (request.revision.allocationRequests.none { (it.balanceRequested ?: 0L) > 0 }) {
            throw RPCException("No resources requested", HttpStatusCode.BadRequest)
        }

        val applicationId = request.applicationId
        return modify(
            actorAndProject = actorAndProject,
            id = applicationId,
            initialDocument = request.revision.takeIf { applicationId == null },
            block = {
                runCommand(Command.InsertRevision(request.comment, request.revision))
            }
        ).applicationId.let(::FindByStringId)
    }

    suspend fun updateState(
        actorAndProject: ActorAndProject,
        request: GrantsV2.UpdateState.Request,
    ) {
        modify(actorAndProject, request.applicationId) {
            if (request.newState == GrantApplication.State.CLOSED) {
                runCommand(Command.Withdraw)
            } else {
                val project =
                    actorAndProject.project ?: throw RPCException("Unknown project", HttpStatusCode.BadRequest)
                runCommand(Command.UpdateApprovalState(project, request.newState))
            }
        }
    }

    suspend fun transfer(
        actorAndProject: ActorAndProject,
        request: GrantsV2.Transfer.Request,
    ) {
        val project = actorAndProject.project ?: throw RPCException("Unknown project", HttpStatusCode.BadRequest)
        modify(actorAndProject, request.applicationId) {
            runCommand(Command.Transfer(request.comment, project, request.target))
        }
    }

    suspend fun postComment(
        actorAndProject: ActorAndProject,
        request: GrantsV2.PostComment.Request,
    ): FindByStringId {
        return modify(actorAndProject, request.applicationId) {
            runCommand(Command.PostComment(request.comment))
        }.commentsCreated.single().let(::FindByStringId)
    }

    suspend fun deleteComment(
        actorAndProject: ActorAndProject,
        request: GrantsV2.DeleteComment.Request,
    ) {
        modify(actorAndProject, request.applicationId) {
            runCommand(Command.DeleteComment(request.commentId))
        }
    }

    // Read API
    // =================================================================================================================
    // The read API allows end-users and the service to read `GrantApplication`s stored in the database. In order to
    // be allowed to read an application, one of the following must be true:
    //
    // - You are the system (`ActorAndProject.System`)
    // - You are an administrator of the workspace who is the recipient of the application
    // - You are an administrator of one of the grant givers (this includes historical grant givers)
    //
    // Notable functions:
    // - `retrieve`            : Retrieve a single `GrantApplication`
    // - `browse`              : Fetch a page of `GrantApplication`s sorted by creation date (descending)
    // - `retrieveGrantGivers` : Fetches a list of potential `GrantGiver`s for an end-user

    suspend fun retrieve(
        actorAndProject: ActorAndProject,
        id: String,
        ctx: DBContext = db,
    ): GrantApplication {
        return browse(
            actorAndProject,
            GrantsV2.Browse.Request(
                includeIngoingApplications = true,
                includeOutgoingApplications = true,
                filter = GrantApplicationFilter.SHOW_ALL
            ),
            filterId = id,
            useProjectFilter = false,
            ctx = ctx
        ).items.singleOrNull() ?: throw RPCException("Could not find grant application", HttpStatusCode.NotFound)
    }

    suspend fun browse(
        actorAndProject: ActorAndProject,
        request: GrantsV2.Browse.Request,
        filterId: String? = null,
        useProjectFilter: Boolean = true,
        ctx: DBContext = db,
    ): PageV2<GrantApplication> {
        val pageSize = request.normalize().itemsPerPage

        return ctx.withSession { session ->
            // NOTE(Dan): This uses a slightly more simplified approach to read permissions now. If you have ever had
            // the ability to read it as a grant giver, then you may always read it. Otherwise, if you are an active
            // admin of the recipient workspace, then you can read it.

            val items = session.sendPreparedStatement(
                {
                    setParameter("filter_id", if (filterId != null) filterId.toLongOrNull() ?: -1 else null)
                    setParameter("show_active", request.filter != GrantApplicationFilter.INACTIVE)
                    setParameter("show_inactive", request.filter != GrantApplicationFilter.ACTIVE)
                    setParameter("include_ingoing", request.includeIngoingApplications)
                    setParameter("include_outgoing", request.includeOutgoingApplications)
                    setParameter("project_id", actorAndProject.project)
                    setParameter("use_project_filter", useProjectFilter)
                    setParameter("next", request.next?.toLongOrNull())
                    setParameter("username", actorAndProject.actor.safeUsername())
                },
                """
                    with
                        accessible_as_grant_giver as (
                            select rr.application_id as id
                            from
                                "grant".requested_resources rr
                                join project.project_members pm on rr.grant_giver = pm.project_id
                            where
                                (pm.role = 'ADMIN' or pm.role = 'PI')
                                and pm.username = :username
                                and (:filter_id::bigint is null or rr.application_id = :filter_id)
                                and :include_ingoing
                                and (not :use_project_filter or :project_id::text is not distinct from rr.grant_giver)
                        ),
                        accessible_as_sender as (
                            select app.id as id
                            from
                                "grant".applications app,
                                "grant".forms f
                            where 
                                f.application_id = app.id
                                and app.requested_by = :username
                                and (:filter_id::bigint is null or app.id = :filter_id)
                                and :include_outgoing
                                and (
                                    not :use_project_filter 
                                    or (
                                        f.recipient_type != 'existing_project'
                                        and :project_id::text is null
                                    )
                                    or (
                                        f.recipient_type = 'existing_project'
                                        and :project_id::text is not distinct from f.recipient
                                    )
                                )
                        ),
                        accessible_as_sender_admin as (
                            select f.application_id as id
                            from
                                "grant".forms f
                                join project.projects p on f.recipient = p.id
                                left join project.project_members pm on p.id = pm.project_id
                            where
                                f.recipient_type = 'existing_project'
                                and pm.username = :username
                                and (pm.role = 'ADMIN' or pm.role = 'PI')
                                and (:filter_id::bigint is null or f.application_id = :filter_id)
                                and :include_outgoing
                                and (
                                    not :use_project_filter 
                                    or :project_id::text is not distinct from f.recipient
                                )
                        ),
                        all_ids as (
                            select distinct id
                            from (
                                select id from accessible_as_grant_giver union
                                select id from accessible_as_sender union
                                select id from accessible_as_sender_admin
                            ) t
                            where
                                :next::bigint is null
                                or t.id < :next
                            order by id desc
                        ),
                        applications as (
                            select app.id
                            from
                                all_ids aid join
                                "grant".applications app on aid.id = app.id
                            where
                                (:show_active or app.overall_state != 'IN_PROGRESS')
                                and (:show_inactive or app.overall_state = 'IN_PROGRESS')
                            limit $pageSize
                        )
                    select "grant".application_to_json(a.id)
                    from applications a
                """
            ).rows.mapNotNull {
                runCatching {
                    defaultMapper.decodeFromString(GrantApplication.serializer(), it.getString(0)!!)
                }.getOrNull()
            }

            PageV2(
                pageSize,
                items,
                if (items.size < pageSize) null
                else items.lastOrNull()?.id
            )
        }
    }

    suspend fun retrieveGrantGivers(
        actorAndProject: ActorAndProject,
        existingProject: String? = null,
        existingApplicationId: String? = null,
        ctx: DBContext = db,
    ): List<GrantGiver> {
        // Normalize the code such that we only need to look at the actorAndProject
        if (existingApplicationId != null) {
            val result = retrieve(actorAndProject, existingApplicationId)
            when (val recipient = result.currentRevision.document.recipient) {
                is GrantApplication.Recipient.ExistingProject -> {
                    return retrieveGrantGivers(
                        ActorAndProject(Actor.SystemOnBehalfOfUser(result.createdBy), recipient.id)
                    )
                }

                is GrantApplication.Recipient.NewProject -> {
                    return retrieveGrantGivers(
                        ActorAndProject(Actor.SystemOnBehalfOfUser(result.createdBy), null)
                    )
                }

                is GrantApplication.Recipient.PersonalWorkspace -> {
                    return retrieveGrantGivers(
                        ActorAndProject(Actor.SystemOnBehalfOfUser(result.createdBy), null)
                    )
                }
            }
        }

        if (existingProject != null) {
            return retrieveGrantGivers(
                ActorAndProject(actorAndProject.actor, existingProject)
            )
        }

        @Suppress("SENSELESS_COMPARISON") // Dear IDE, why do you think I am asserting this?
        check(existingProject == null && existingApplicationId == null)

        val (actor, project) = actorAndProject
        val allocators = ctx.withSession { session ->
            session.sendPreparedStatement(
                {
                    setParameter("project_id", project)
                    setParameter("username", actor.safeUsername())
                },
                """
                    with
                        parent_project_id as (
                            select parent as project
                            from project.projects
                            where id = :project_id::text
                        ),
                        preliminary_list as (
                            select
                                allow_entry.project_id,
                                requesting_user.id,
                                requesting_user.email,
                                requesting_user.org_id
                            from
                                auth.principals requesting_user
                                join "grant".allow_applications_from allow_entry on
                                    allow_entry.type = 'anyone' 
                                    or (
                                        allow_entry.type = 'wayf'
                                        and allow_entry.applicant_id = requesting_user.org_id
                                    ) 
                                    or (
                                        allow_entry.type = 'email' 
                                        and requesting_user.email like '%@' || allow_entry.applicant_id
                                    )
                            where
                                requesting_user.id = :username
                        ),
                        after_exclusion as (
                            select
                                requesting_user.project_id as project
                            from
                                preliminary_list requesting_user
                                left join "grant".exclude_applications_from exclude_entry on 
                                    requesting_user.email like '%@' || exclude_entry.email_suffix
                                    and exclude_entry.project_id = requesting_user.project_id
                            group by
                                requesting_user.project_id
                            having
                                count(email_suffix) = 0
                        ),
                        project_ids as (
                            select distinct project
                            from (
                                select project from parent_project_id union
                                select project from after_exclusion
                            ) t
                        )
                    select
                        p.id,
                        p.title,
                        d.description,
                        t.personal_project,
                        t.new_project,
                        t.existing_project
                    from
                        project_ids pid
                        join project.projects p on pid.project = p.id
                        left join "grant".descriptions d on p.id = d.project_id
                        left join "grant".templates t on p.id = t.project_id
                    order by p.title
                """
            ).rows.map { row ->
                GrantGiver(
                    row.getString(0)!!,
                    row.getString(1)!!,
                    row.getString(2) ?: "No description",
                    Templates.PlainText(
                        row.getString(3) ?: defaultTemplate,
                        row.getString(4) ?: defaultTemplate,
                        row.getString(5) ?: defaultTemplate
                    ),
                    emptyList()
                )
            }
        }

        return allocators.map { allocator ->
            val targetPid = idCardService.lookupPidFromProjectId(allocator.id)
                ?: throw RPCException("Unknown target project? ${allocator.id}", HttpStatusCode.Forbidden)

            val wallets = accountingService.sendRequest(
                AccountingRequest.BrowseWallets(
                    IdCard.User(0, IntArray(0), IntArray(0), targetPid)
                )
            )

            val categories = wallets.mapNotNull { w ->
                if (w.quota == 0L) return@mapNotNull null
                if (w.paysFor.freeToUse) return@mapNotNull null
                w.paysFor
            }

            allocator.copy(categories = categories)
        }
    }

    // Grant settings
    // =================================================================================================================
    // Grant settings control how a `GrantGiver` project appears when a user wants to apply for resources from them.
    // This includes determining which logo to display, what the description of the `GrantGiver` is and which form the
    // applications must fill out.
    //
    // Additionally, these settings control which users can apply to a specific `GrantGiver`. By default, only
    // subprojects can apply to a project. However, if a UCloud administrator mark a `GrantGiver` as being enabled, then
    // the project can define complex lists which can allow many more users to apply for resources.
    //
    // Any workspace user is allowed to read the request settings of a project, but only workspace administrators may
    // update them. In addition, only UCloud administrators are allowed to set the `enabled` flag which allows the
    // allow and exclude lists to be updated.
    //
    // Notable calls:
    // - `retrieveRequestSettings` : Retrieves the request settings of a project
    // - `updateRequestSettings`   : Updates the request settings of a project
    // - `retrieveLogo`            : Retrieves the logo of a project (publicly available)
    // - `updateLogo`              : Updates the logo of a project
    suspend fun retrieveRequestSettings(
        actorAndProject: ActorAndProject,
        ctx: DBContext = db,
    ): GrantRequestSettings {
        val (actor, project) = actorAndProject
        if (project == null) throw RPCException("Unknown project", HttpStatusCode.NotFound)

        return ctx.withSession { session ->
            session.sendPreparedStatement(
                {
                    setParameter("username", actor.safeUsername())
                    setParameter("project_id", project)
                },
                """
                    with
                        project as (
                            select id
                            from
                                project.project_members pm
                                join project.projects p on pm.project_id = p.id
                            where
                                p.id = :project_id
                                and pm.username = :username
                        ),
                        is_enabled as (
                            select exists(
                                select 1
                                from
                                    "grant".is_enabled,
                                    project
                                where project_id = project.id
                            ) enabled
                        ),
                        description as (
                            select description
                            from "grant".descriptions, project
                            where project_id = project.id
                        ),
                        templates as (
                            select t.personal_project, t.new_project, t.existing_project
                            from "grant".templates t, project
                            where project_id = project.id
                        ),
                        allow_from as (
                            select jsonb_agg(jsonb_build_object(
                                'type', r.type,
                                case r.type
                                    when 'anyone' then 'any'
                                    when 'email' then 'domain'
                                    when 'wayf' then 'org'
                                end,
                                r.applicant_id
                            )) allow_list
                            from "grant".allow_applications_from r, project
                            where r.project_id = project.id
                        ),
                        deny_from as (
                            select jsonb_agg(jsonb_build_object(
                                'type', 'email',
                                'domain', r.email_suffix
                            )) deny_list
                            from "grant".exclude_applications_from r, project
                            where r.project_id = project.id
                        )
                    select enabled, description, t.personal_project, t.new_project, t.existing_project, allow_list, deny_list
                    from
                        is_enabled
                        left join description on true
                        left join templates t on true
                        left join allow_from on true
                        left join deny_from on true;
                """
            ).rows.map { row ->
                GrantRequestSettings(
                    row.getBoolean(0) ?: false,
                    row.getString(1) ?: "No description",
                    row.getString(5)?.let {
                        defaultMapper.decodeFromString(ListSerializer(UserCriteria.serializer()), it)
                    } ?: emptyList(),
                    row.getString(6)?.let {
                        defaultMapper.decodeFromString(ListSerializer(UserCriteria.serializer()), it)
                    } ?: emptyList(),
                    Templates.PlainText(
                        row.getString(2) ?: defaultTemplate,
                        row.getString(3) ?: defaultTemplate,
                        row.getString(4) ?: defaultTemplate,
                    )
                )
            }.singleOrNull()
        } ?: throw RPCException("Not able to read project settings for this project", HttpStatusCode.Forbidden)
    }

    suspend fun updateRequestSettings(
        actorAndProject: ActorAndProject,
        newSettings: GrantRequestSettings,
        ctx: DBContext = db,
    ) {
        fun forbidden(): Nothing =
            throw RPCException("Unable to update settings for this workspace", HttpStatusCode.Forbidden)

        val (actor, project) = actorAndProject
        if (project == null) throw RPCException(
            "Your current workspace does not have settings",
            HttpStatusCode.BadRequest
        )

        val isUCloudAdmin = (actor as? Actor.User)?.principal?.role in Roles.PRIVILEGED

        if (!isUCloudAdmin) {
            val idCard = idCardService.fetchIdCard(actorAndProject) as? IdCard.User ?: forbidden()
            val pid = idCardService.lookupPidFromProjectId(project) ?: forbidden()
            if (!idCard.adminOf.contains(pid)) forbidden()
        }

        ctx.withSession { session ->
            val existingSettings = retrieveRequestSettings(actorAndProject, session)
            val allowList =
                if (isUCloudAdmin || existingSettings.enabled) newSettings.allowRequestsFrom
                else emptyList()

            if (isUCloudAdmin) {
                if (newSettings.enabled) {
                    session.sendPreparedStatement(
                        { setParameter("project_id", project) },
                        "insert into \"grant\".is_enabled (project_id) values (:project_id) on conflict do nothing"
                    )
                } else {
                    session.sendPreparedStatement(
                        { setParameter("project_id", project) },
                        "delete from \"grant\".is_enabled where project_id = :project_id"
                    )
                }
            }

            session.sendPreparedStatement(
                { setParameter("project_id", project) },
                "delete from \"grant\".allow_applications_from where project_id = :project_id"
            )

            session.sendPreparedStatement(
                { setParameter("project_id", project) },
                "delete from \"grant\".exclude_applications_from where project_id = :project_id"
            )

            if (allowList.isNotEmpty()) {
                session.sendPreparedStatement(
                    {
                        setParameter("project_id", project)
                        allowList.split {
                            into("types") { it.type }
                            into("ids") { it.id }
                        }
                    },
                    """
                        insert into "grant".allow_applications_from (project_id, type, applicant_id) 
                        select :project_id, unnest(:types::text[]), unnest(:ids::text[])
                    """
                )
            }

            if (newSettings.excludeRequestsFrom.isNotEmpty()) {
                session.sendPreparedStatement(
                    {
                        setParameter("project_id", project)
                        newSettings.excludeRequestsFrom.filterIsInstance<UserCriteria.EmailDomain>().split {
                            into("domains") { it.domain }
                        }
                    },
                    """
                        insert into "grant".exclude_applications_from (project_id, email_suffix) 
                        select :project_id, unnest(:domains::text[])
                    """
                )
            }

            session.sendPreparedStatement(
                {
                    setParameter("project_id", project)
                    setParameter("description", newSettings.description)
                },
                """
                    insert into "grant".descriptions (project_id, description) 
                    values (:project_id, :description)
                    on conflict (project_id) do update set description = excluded.description
                """
            )

            session.sendPreparedStatement(
                {
                    val templates = newSettings.templates as Templates.PlainText
                    setParameter("project_id", project)
                    setParameter("personal", templates.personalProject)
                    setParameter("existing", templates.existingProject)
                    setParameter("new", templates.newProject)
                },
                """
                    insert into "grant".templates (project_id, personal_project, existing_project, new_project) 
                    values (:project_id, :personal, :existing, :new)
                    on conflict (project_id) do update set
                        personal_project = excluded.personal_project,
                        existing_project = excluded.existing_project,
                        new_project = excluded.new_project
                """
            )
        }
    }

    suspend fun retrieveLogo(projectId: String): ByteArray? {
        return db.withSession(remapExceptions = true) { session ->
            session
                .sendPreparedStatement(
                    { setParameter("projectId", projectId) },
                    "select data from \"grant\".logos where project_id = :projectId"
                ).rows.singleOrNull()?.getAs<ByteArray>(0)
        }
    }

    suspend fun uploadLogo(
        actorAndProject: ActorAndProject,
        streamLength: Long?,
        channel: ByteReadChannel,
    ) {
        val project = actorAndProject.project ?: throw RPCException("No project supplied!", HttpStatusCode.BadRequest)
        if (streamLength == null || streamLength > LOGO_MAX_SIZE) {
            throw RPCException("Logo is too large", HttpStatusCode.BadRequest)
        }

        val imageBytes = ByteArrayOutputStream(streamLength.toInt()).let { stream ->
            channel.copyTo(stream)
            stream.toByteArray()
        }

        db.withSession(remapExceptions = true) { session ->
            val success = session.sendPreparedStatement(
                {
                    setParameter("username", actorAndProject.actor.safeUsername())
                    setParameter("project_id", project)
                    setParameter("data", imageBytes)
                },
                """
                    insert into "grant".logos (project_id, data) 
                    select :project_id , :data
                    from
                        project.project_members pm
                    where
                        pm.username = :username and
                        (pm.role = 'PI' or pm.role = 'ADMIN') and
                        pm.project_id = :project_id
                    on conflict (project_id) do update set
                        data = excluded.data
                """
            ).rowsAffected > 0

            if (!success) {
                throw RPCException("Unable to upload logo", HttpStatusCode.NotFound)
            }
        }
    }

    // Command and action processing
    // =================================================================================================================
    // Finally, we arrive at the business logic itself. Here we are processing the `Command`s which are fed to us from
    // the other methods. Each time we process and command, we will authorize the command and execute it on the
    // in-memory representation of the `GrantApplication`. Every command will then output the desired side effects by
    // putting them in the `actionQueue`. Once all `Command`s have been processed, the `commit` function is invoked.
    // This function will process all the `Action`s which were generated.
    //
    // Notable functions:
    // - `modify` : See above for how to invoke this class

    private data class ModificationResult(
        val applicationId: String,
        val commentsCreated: List<String>,
    )

    private class ModificationContext(
        private val ctx: GrantsV2Service,
        private val actorAndProject: ActorAndProject,
        private var idCard: IdCard,
        private val session: AsyncDBConnection,

        private val grantGivers: List<GrantGiver>,
        initialApplication: GrantApplication
    ) {
        var application: GrantApplication = initialApplication
            private set

        private val actionQueue = ArrayList<Action>()

        init {
            if (initialApplication.id == "") actionQueue.add(Action.Initialize)
        }

        // Useful utility/authorization functions
        // -------------------------------------------------------------------------------------------------------------
        private suspend fun grantGiverProjects(): Set<Int> {
            return application.currentRevision.document.allocationRequests
                .mapNotNull { ctx.idCardService.lookupPidFromProjectId(it.grantGiver) }
                .toSet()
        }

        private suspend fun isGrantGiver(approverId: String? = null, withCache: Boolean = true): Boolean {
            return when (val idCard = idCard) {
                is IdCard.Provider -> false
                IdCard.System -> true
                is IdCard.User -> {
                    val projects =
                        if (approverId != null) setOf(ctx.idCardService.lookupPidFromProjectIdOrFail(approverId))
                        else grantGiverProjects()

                    val success = idCard.adminOf.any { it in projects }
                    if (withCache && !success) {
                        this.idCard = ctx.idCardService.fetchIdCard(actorAndProject, allowCached = false)
                        isGrantGiver(approverId, withCache = false)
                    } else {
                        success
                    }
                }
            }
        }

        private suspend fun requireGrantGiver(approverId: String? = null) {
            if (!isGrantGiver(approverId)) {
                throw RPCException(
                    "Only a grant giver can perform this action!",
                    HttpStatusCode.Forbidden
                )
            }
        }

        private suspend fun isOwner(): Boolean {
            when (val idCard = idCard) {
                is IdCard.Provider -> return false
                IdCard.System -> return true
                is IdCard.User -> {
                    val sender = application.createdBy
                    val doc = application.currentRevision.document
                    val existingProject = (doc.recipient as? GrantApplication.Recipient.ExistingProject)?.id

                    if (ctx.idCardService.lookupUidFromUsername(sender) == idCard.uid) {
                        return true
                    }

                    if (existingProject != null) {
                        val pid = ctx.idCardService.lookupPidFromProjectId(existingProject)
                        return idCard.adminOf.any { it == pid }
                    }

                    return false
                }
            }
        }

        private suspend fun requireOwner() {
            if (!isOwner()) {
                throw RPCException(
                    "Only the grant owner can perform this action!",
                    HttpStatusCode.Forbidden
                )
            }
        }

        private fun genericForbidden(): Nothing =
            throw RPCException("You are not allowed to perform this action", HttpStatusCode.Forbidden)

        private fun requireOpen() {
            if (application.status.overallState != GrantApplication.State.IN_PROGRESS) {
                throw RPCException("This application is no longer open!", HttpStatusCode.BadRequest)
            }
        }

        // Command processing
        // -------------------------------------------------------------------------------------------------------------
        // When new functionality is required, then you should add it to the big when statement in this function.
        suspend fun runCommand(command: Command) {
            when (command) {
                Command.Synchronize -> {
                    actionQueue.add(Action.GrantResources)
                }

                is Command.PostComment -> {
                    val newComment = GrantApplication.Comment(
                        "unknown",
                        actorAndProject.actor.safeUsername(),
                        Time.now(),
                        command.comment
                    )

                    application = application.copy(
                        status = application.status.copy(
                            comments = application.status.comments + newComment
                        )
                    )

                    actionQueue.add(Action.NewComment(newComment))
                }

                is Command.DeleteComment -> {
                    val comment = application.status.comments.find { it.id == command.commentId }
                    if (comment != null && comment.username != actorAndProject.actor.safeUsername()) {
                        throw RPCException("You cannot delete a comment you did not write", HttpStatusCode.Forbidden)
                    }

                    if (comment == null) {
                        throw RPCException("Unknown comment. Try reloading the page!", HttpStatusCode.NotFound)
                    }

                    application = application.copy(
                        status = application.status.copy(
                            comments = application.status.comments.filter { it.id != command.commentId }
                        )
                    )

                    actionQueue.add(Action.DeleteComment(comment.id))
                }

                is Command.InsertRevision -> {
                    if (command.doc.form is GrantApplication.Form.GrantGiverInitiated) {
                        if (application.id != "") genericForbidden()
                        val grantGivers = command.doc.allocationRequests.map { it.grantGiver }.toSet()
                        if (grantGivers.size != 1) genericForbidden()
                        val grantGiver = grantGivers.single()
                        if (!isGrantGiver(grantGiver)) genericForbidden()

                        insertRevision(
                            command.comment,
                            command.doc.copy(parentProjectId = grantGiver)
                        )

                        application = application.copy(
                            status = application.status.copy(
                                overallState = GrantApplication.State.APPROVED,
                                stateBreakdown = listOf(
                                    GrantApplication.GrantGiverApprovalState(
                                        grantGiver,
                                        "",
                                        GrantApplication.State.APPROVED
                                    )
                                )
                            )
                        )

                        actionQueue.add(Action.UpdateApprovalState(grantGiver, GrantApplication.State.APPROVED))
                        actionQueue.add(Action.UpdateOverallState(GrantApplication.State.APPROVED))
                        actionQueue.add(Action.GrantResources)
                    } else {
                        val newRefIds = if (isGrantGiver()) command.doc.referenceIds else null
                        if (newRefIds != null) {
                            for (id in newRefIds) checkDeicReferenceFormat(id)
                        }

                        if (application.status.overallState != GrantApplication.State.IN_PROGRESS) {
                            // We only allow editing of the reference ID for closed applications.
                            insertRevision(
                                command.comment,
                                application.currentRevision.document.copy(referenceIds = newRefIds)
                            )
                        } else {
                            when (val idCard = idCard) {
                                is IdCard.Provider -> genericForbidden()

                                IdCard.System -> {
                                    // Do nothing
                                }

                                is IdCard.User -> {
                                    if (!isGrantGiver()) {
                                        when (val recipient = command.doc.recipient) {
                                            is GrantApplication.Recipient.ExistingProject -> {
                                                val pid = ctx.idCardService.lookupPidFromProjectId(recipient.id)
                                                if (idCard.adminOf.none { it == pid }) genericForbidden()
                                            }

                                            is GrantApplication.Recipient.NewProject -> {
                                                // OK
                                            }

                                            is GrantApplication.Recipient.PersonalWorkspace -> {
                                                if (recipient.username != actorAndProject.actor.safeUsername()) genericForbidden()
                                            }
                                        }
                                    }
                                }
                            }

                            if (command.doc.parentProjectId != null) {
                                if (grantGivers.none { it.id == command.doc.parentProjectId }) {
                                    throw RPCException("Invalid primary affiliation supplied", HttpStatusCode.Forbidden)
                                }
                            }

                            for (req in command.doc.allocationRequests) {
                                if (!grantGivers.any { it.id == req.grantGiver }) {
                                    cannotBeRequested(req.category)
                                }
                            }

                            insertRevision(
                                command.comment,
                                command.doc.copy(referenceIds = newRefIds)
                            )
                        }
                    }
                }

                is Command.Transfer -> {
                    requireGrantGiver(command.sourceProjectId)
                    requireOpen()

                    val targetPid = ctx.idCardService.lookupPidFromProjectId(command.targetProjectId)
                        ?: throw RPCException("Unknown target project? ${command.targetProjectId}", HttpStatusCode.Forbidden)

                    val targetWallets = ctx.accountingService.sendRequest(
                        AccountingRequest.BrowseWallets(
                            IdCard.User(0, IntArray(0), IntArray(0), targetPid),
                        )
                    )

                    val originalRequests = application.currentRevision.document.allocationRequests
                    val requestsWhichAreMoving = originalRequests.filter { it.grantGiver == command.sourceProjectId }
                    val requestsWhichAreNotMoving = originalRequests.filter { it.grantGiver != command.sourceProjectId }
                    val requestsAfterMove = requestsWhichAreMoving.mapNotNull { req ->
                        targetWallets.find {
                            it.paysFor.provider == req.provider && it.paysFor.name == req.category
                        } ?: return@mapNotNull null

                        req.copy(grantGiver = command.targetProjectId)
                    }

                    insertRevision(
                        command.comment,
                        application.currentRevision.document.copy(
                            allocationRequests = requestsWhichAreNotMoving + requestsAfterMove,
                        )
                    )
                }

                is Command.UpdateApprovalState -> {
                    requireGrantGiver(command.projectId)
                    requireOpen()

                    if (command.newState == GrantApplication.State.CLOSED) {
                        throw RPCException("Grant givers cannot withdraw an application", HttpStatusCode.BadRequest)
                    }

                    val approvalState = application.status.stateBreakdown.map { s ->
                        if (s.projectId == command.projectId) {
                            s.copy(state = command.newState)
                        } else {
                            s
                        }
                    }

                    val newOverallState = when {
                        approvalState.any { it.state == GrantApplication.State.REJECTED } -> GrantApplication.State.REJECTED
                        approvalState.all { it.state == GrantApplication.State.APPROVED } -> GrantApplication.State.APPROVED
                        else -> GrantApplication.State.IN_PROGRESS
                    }

                    application = application.copy(
                        status = application.status.copy(
                            overallState = newOverallState,
                            stateBreakdown = approvalState
                        )
                    )

                    actionQueue.add(Action.UpdateApprovalState(command.projectId, command.newState))
                    actionQueue.add(Action.UpdateOverallState(newOverallState))
                    if (newOverallState == GrantApplication.State.APPROVED) {
                        actionQueue.add(Action.GrantResources)
                    }
                }

                Command.Withdraw -> {
                    requireOwner()
                    requireOpen()

                    application = application.copy(
                        status = application.status.copy(
                            overallState = GrantApplication.State.CLOSED,
                        )
                    )

                    actionQueue.add(Action.UpdateOverallState(GrantApplication.State.CLOSED))
                }
            }
        }

        private fun cannotBeRequested(category: String): Nothing = throw RPCException(
            "One or more of your requested resources can no longer be requested. " +
                    "Try to remove your request for '${category}' or reload the page.",
            HttpStatusCode.Forbidden
        )

        private suspend fun insertRevision(comment: String, newDoc: GrantApplication.Document) {
            val rev = GrantApplication.Revision(
                Time.now(),
                actorAndProject.actor.safeUsername(),
                application.currentRevision.revisionNumber + 1,
                newDoc.copy(revisionComment = comment)
            )

            application = application.copy(
                currentRevision = rev,
                status = application.status.copy(
                    revisions = application.status.revisions + rev
                )
            )

            actionQueue.add(Action.NewRevision(rev))
        }

        private fun GrantApplication.Recipient.reference(): String = when (this) {
            is GrantApplication.Recipient.ExistingProject -> id
            is GrantApplication.Recipient.NewProject -> title
            is GrantApplication.Recipient.PersonalWorkspace -> username
        }

        private fun GrantApplication.Recipient.type(): String = when (this) {
            is GrantApplication.Recipient.ExistingProject -> "existing_project"
            is GrantApplication.Recipient.NewProject -> "new_project"
            is GrantApplication.Recipient.PersonalWorkspace -> "personal"
        }

        // Action processing
        // -------------------------------------------------------------------------------------------------------------
        suspend fun commit(): ModificationResult {
            val commentsCreated = ArrayList<String>()

            for (action in actionQueue) {
                when (action) {
                    Action.Initialize -> {
                        val id = session.sendPreparedStatement(
                            {
                                setParameter("init_state", GrantApplication.State.IN_PROGRESS.name)
                                setParameter("requested_by", actorAndProject.actor.safeUsername())
                            },
                            """
                                insert into "grant".applications (overall_state, requested_by) 
                                values (:init_state, :requested_by)
                                returning id
                            """
                        ).rows.single().getLong(0)

                        application = application.copy(id = id.toString())

                        session.sendPreparedStatement(
                            {
                                setParameter("app_id", id)
                                setParameter(
                                    "grant_givers",
                                    application.currentRevision.document.allocationRequests.map { it.grantGiver }
                                        .toSet().toList()
                                )
                            },
                            """
                                with data as (
                                    select unnest(:grant_givers::text[]) as grant_giver
                                )
                                insert into "grant".grant_giver_approvals (application_id, project_id, project_title, state, updated_by, last_update) 
                                select :app_id, d.grant_giver, p.title, 'IN_PROGRESS', '_ucloud', now()
                                from data d join project.projects p on d.grant_giver = p.id
                            """
                        )
                    }

                    is Action.NewRevision -> {
                        val revisionNumber = application.currentRevision.revisionNumber + 1
                        session.sendPreparedStatement(
                            {
                                setParameter("app_id", application.id.toLongOrNull())
                                setParameter("created_by", action.revision.updatedBy)
                                setParameter("comment", action.revision.document.revisionComment)
                                setParameter("rev_id", revisionNumber)
                            },
                            """
                                insert into "grant".revisions (application_id, revision_number, created_at, updated_by, revision_comment) 
                                values (:app_id, :rev_id, now(), :created_by, :comment)
                            """
                        )

                        session.sendPreparedStatement(
                            {
                                setParameter("app_id", application.id.toLongOrNull())
                                setParameter("rev", revisionNumber)
                                setParameter("parent", action.revision.document.parentProjectId)
                                setParameter("recipient", action.revision.document.recipient.reference())
                                setParameter("recipient_type", action.revision.document.recipient.type())
                                setParameter(
                                    "form",
                                    (action.revision.document.form as GrantApplication.Form.WithText).text
                                )
                                setParameter("ref_id", action.revision.document.referenceIds)
                            },
                            """
                                insert into "grant".forms (application_id, revision_number, parent_project_id, recipient, recipient_type, form, reference_ids)
                                values (:app_id, :rev, :parent, :recipient, :recipient_type, :form, :ref_id)
                            """
                        )

                        session.sendPreparedStatement(
                            {
                                setParameter("app_id", application.id.toLongOrNull())
                                action.revision.document.allocationRequests.split {
                                    into("grant_givers") { it.grantGiver }
                                    into("balance") { it.balanceRequested ?: 0L }
                                    into("categories") { it.category }
                                    into("providers") { it.provider }
                                    into("start_dates") { it.period.start ?: Time.now() }
                                    into("end_dates") { it.period.end ?: (Time.now() + (1000L * 60 * 60 * 24 * 365)) }
                                    setParameter("rev_id", revisionNumber)
                                }
                            },
                            """
                                with
                                    data as (
                                        select
                                            unnest(:balance::int8[]) as balance,
                                            unnest(:categories::text[]) as category,
                                            unnest(:providers::text[]) as provider,
                                            to_timestamp(unnest(:start_dates::int8[]) / 1000) as start_date,
                                            to_timestamp(unnest(:end_dates::int8[]) / 1000) as end_date,
                                            unnest(:grant_givers::text[]) as grant_giver
                                    ),
                                    normalized_data as (
                                        select
                                            d.balance,
                                            pc.id as category,
                                            d.start_date,
                                            d.end_date,
                                            d.grant_giver
                                        from
                                            data d 
                                            join accounting.product_categories pc on 
                                                d.category = pc.category 
                                                and d.provider = pc.provider
                                    )
                                insert into "grant".requested_resources 
                                    (application_id, credits_requested, quota_requested_bytes, product_category, 
                                    start_date, end_date, grant_giver, revision_number) 
                                select
                                    :app_id, d.balance, null, d.category, d.start_date, d.end_date, d.grant_giver,
                                    :rev_id
                                from normalized_data d
                            """
                        )
                    }

                    is Action.UpdateApprovalState -> {
                        session.sendPreparedStatement(
                            {
                                setParameter("app_id", application.id.toLongOrNull())
                                setParameter("project_id", action.approver)
                                setParameter("state", action.state.name)
                                setParameter("updated_by", actorAndProject.actor.safeUsername())
                            },
                            """
                                insert into "grant".grant_giver_approvals(application_id, project_id, project_title, state, updated_by, last_update) 
                                select :app_id, :project_id, p.title, :state, :updated_by, now()
                                from project.projects p
                                where id = :project_id
                                on conflict (application_id, project_id) do update set
                                    last_update = excluded.last_update,
                                    state = excluded.state
                            """
                        )
                    }

                    is Action.UpdateOverallState -> {
                        session.sendPreparedStatement(
                            {
                                setParameter("id", application.id.toLongOrNull())
                                setParameter("new_state", action.overallState.name)
                            },
                            """
                                update "grant".applications
                                set overall_state = :new_state
                                where id = :id
                            """
                        )
                    }

                    is Action.NewComment -> {
                        val id = session.sendPreparedStatement(
                            {
                                setParameter("app_id", application.id.toLongOrNull())
                                setParameter("comment", action.comment.comment)
                                setParameter("posted_by", action.comment.username)
                            },
                            """
                                insert into "grant".comments(application_id, comment, posted_by) 
                                values (:app_id, :comment, :posted_by)
                                returning id
                            """
                        ).rows.singleOrNull()?.getLong(0)

                        commentsCreated.add(id.toString())
                    }

                    is Action.DeleteComment -> {
                        session.sendPreparedStatement(
                            {
                                setParameter("id", action.id.toLongOrNull())
                                setParameter("app_id", application.id.toLongOrNull())
                            },
                            """
                                delete from "grant".comments
                                where id = :id and application_id = :app_id
                            """
                        )
                    }

                    Action.GrantResources -> {
                        val doc = application.currentRevision.document
                        val isSubAllocator = (doc.form as? GrantApplication.Form.GrantGiverInitiated)?.subAllocator == true
                        val walletOwner = when (val recipient = doc.recipient) {
                            is GrantApplication.Recipient.PersonalWorkspace -> WalletOwner.User(recipient.username)
                            is GrantApplication.Recipient.ExistingProject -> WalletOwner.Project(recipient.id)
                            is GrantApplication.Recipient.NewProject -> {
                                // do this in a loop in case it goes wrong
                                var attempts = 0
                                var createdProject: String? = null
                                while (attempts < 1000) {
                                    try {
                                        createdProject = session.sendPreparedStatement(
                                            {
                                                setParameter("parent_id", doc.parentProjectId)
                                                setParameter("pi", application.createdBy)
                                                setParameter("is_sub_allocator", isSubAllocator)
                                                setParameter(
                                                    "title",
                                                    buildString {
                                                        append(recipient.title)
                                                        if (attempts > 0) {
                                                            append("-")
                                                            append(Random.nextInt(0, 10000).toString().padStart(4, '0'))
                                                        }
                                                    }
                                                )
                                            },
                                            """ 
                                                -- noinspection SqlUnusedCte
                                                with created_project as (
                                                    insert into project.projects (id, created_at, modified_at, title, archived, parent, dmp, subprojects_renameable, can_consume_resources)
                                                    select uuid_generate_v4()::text, now(), now(), :title, false, :parent_id, null, false, not :is_sub_allocator
                                                    on conflict do nothing
                                                    returning id
                                                ),
                                                created_user as (
                                                    insert into project.project_members (created_at, modified_at, role, username, project_id)
                                                    select now(), now(), 'PI', :pi, id
                                                    from created_project
                                                )
                                                select * from created_project
                                            """
                                        ).rows.singleOrNull()?.getString(0) ?: throw RPCException.fromStatusCode(
                                            HttpStatusCode.InternalServerError,
                                            "Error in creating project and PI"
                                        )
                                        break
                                    } catch (ignored: Throwable) {
                                        // Try again...
                                    }

                                    attempts++
                                }

                                WalletOwner.Project(
                                    createdProject
                                        ?: throw RPCException.fromStatusCode(HttpStatusCode.InternalServerError)
                                )
                            }
                        }

                        session.sendQuery("commit") // commit state changes immediately in case we crash
                        session.sendQuery("begin")

                        doc.allocationRequests.forEach { req ->
                            ctx.accountingService.sendRequest(
                                AccountingRequest.SubAllocate(
                                    IdCard.System,
                                    ProductCategoryIdV2(req.category, req.provider),
                                    walletOwner.reference(),
                                    req.balanceRequested!!,
                                    req.period.start ?: Time.now(),
                                    req.period.end ?: Time.now(),
                                    ownerOverride = req.grantGiver,
                                    grantedIn = application.id.toLongOrNull(),
                                )
                            )
                        }

                        if (doc.recipient is GrantApplication.Recipient.NewProject && walletOwner is WalletOwner.Project) {
                            ctx.projects.notifyChanges(session, listOf(walletOwner.projectId))
                        }
                    }
                }
            }

            // Notifications
            // ---------------------------------------------------------------------------------------------------------
            // We process notifications at the end of the function as opposed to during the function. We do this to
            // deduplicate some of the notifications we might otherwise send to users.
            //
            // TODO(Dan): This piece of code seriously makes me believe that we need to redo the entire mail API.
            //  This code is significantly longer than anything else in the grant system and it doesn't even contain
            //  the messages that we are sending to the end-users.

            data class NotificationBundle(
                val notification: Notification?,
                val email: Mail? = null,
            ) {
                init {
                    require(notification != null || email != null)
                }
            }

            val notificationsForGrantGivers = ArrayList<NotificationBundle>()
            val notificationForSenders = ArrayList<NotificationBundle>()
            val applicationTitle = application.status.projectTitle ?: when(val r = application.currentRevision.document.recipient) {
                is GrantApplication.Recipient.ExistingProject -> "existing project" // should not happen
                is GrantApplication.Recipient.NewProject -> r.title // should not happen
                is GrantApplication.Recipient.PersonalWorkspace -> "Personal workspace: ${r.username}" // will happen
            }
            val grantGiverPlaceholder = "grant giver"

            for (action in actionQueue) {
                when (action) {
                    Action.Initialize -> {
                        notificationsForGrantGivers.add(
                            NotificationBundle(
                                notification = Notification(
                                    "NEW_GRANT_APPLICATION",
                                    "New grant application from '${applicationTitle}'",
                                    meta = JsonObject(
                                        mapOf(
                                            "appId" to JsonPrimitive(application.id),
                                        )
                                    )
                                ),
                                email = Mail.NewGrantApplicationMail(
                                    application.createdBy,
                                    grantGiverPlaceholder,
                                    "New grant application from '${applicationTitle}'",
                                ),
                            )
                        )
                    }

                    is Action.NewComment -> {
                        val b = NotificationBundle(
                            notification = Notification(
                                "NEW_GRANT_COMMENT",
                                "New comment in application '${applicationTitle}'",
                                meta = JsonObject(
                                    mapOf(
                                        "appId" to JsonPrimitive(application.id),
                                    )
                                )
                            )
                        )

                        notificationsForGrantGivers.add(b)
                        notificationForSenders.add(b)
                    }

                    is Action.NewRevision -> {
                        val b = NotificationBundle(
                            notification = Notification(
                                "UPDATED_GRANT_APPLICATION",
                                "New update in application '${applicationTitle}'",
                                meta = JsonObject(
                                    mapOf(
                                        "appId" to JsonPrimitive(application.id),
                                    )
                                )
                            )
                        )

                        notificationsForGrantGivers.add(b)
                        notificationForSenders.add(b)
                    }

                    is Action.UpdateOverallState -> {
                        val notification = Notification(
                            "GRANT_APPLICATION_RESPONSE",
                            when (action.overallState) {
                                GrantApplication.State.APPROVED -> "Application from '$applicationTitle' has been approved"
                                GrantApplication.State.REJECTED -> "Application from '$applicationTitle' has been rejected"
                                GrantApplication.State.CLOSED -> "Application from '$applicationTitle' has been withdrawn"
                                GrantApplication.State.IN_PROGRESS -> "Application from '$applicationTitle' has been resumed"
                            },
                            meta = JsonObject(
                                mapOf(
                                    "appId" to JsonPrimitive(application.id),
                                )
                            )
                        )

                        notificationsForGrantGivers.add(
                            NotificationBundle(
                                notification = notification,
                                email = Mail.GrantApplicationStatusChangedToAdmin(
                                    action.overallState.name,
                                    grantGiverPlaceholder,
                                    application.createdBy,
                                    applicationTitle,
                                    notification.message
                                )
                            )
                        )

                        notificationForSenders.add(
                            NotificationBundle(
                                notification,
                                email = when (action.overallState) {
                                    GrantApplication.State.APPROVED -> Mail.GrantApplicationApproveMail(
                                        applicationTitle,
                                        notification.message
                                    )

                                    GrantApplication.State.REJECTED -> Mail.GrantApplicationRejectedMail(
                                        applicationTitle,
                                        notification.message
                                    )

                                    GrantApplication.State.CLOSED -> Mail.GrantApplicationWithdrawnMail(
                                        applicationTitle,
                                        actorAndProject.actor.safeUsername(),
                                        notification.message
                                    )

                                    GrantApplication.State.IN_PROGRESS -> null
                                }
                            )
                        )
                    }

                    Action.GrantResources,
                    is Action.UpdateApprovalState,
                    is Action.DeleteComment -> {
                        // Do nothing
                    }
                }
            }

            val allEmailsToSend = ArrayList<SendRequestItem>()

            if (notificationsForGrantGivers.isNotEmpty()) {
                // NOTE(Dan): This, purposefully, throws away any but the first grant giver we find for a single user.
                // We do not want to send multiple emails to a single user.
                val usernamesAndGrantGiverTitles = session.sendPreparedStatement(
                    { setParameter("pids", grantGiverProjects().toList()) },
                    """
                        select
                            pm.username, p.title
                        from
                            project.projects p
                            join project.project_members pm on p.id = pm.project_id
                        where 
                            (pm.role = 'ADMIN' or pm.role = 'PI')
                            and p.pid = some(:pids)
                    """
                ).rows.associate { Pair(it.getString(0)!!, it.getString(1)!!) }

                for ((username, grantGiver) in usernamesAndGrantGiverTitles) {
                    if (actorAndProject.actor.safeUsername() == username) continue
                    val bundle = notificationsForGrantGivers.lastOrNull() ?: continue

                    if (bundle.notification != null) {
                        NotificationDescriptions.create.call(
                            CreateNotification(username, bundle.notification),
                            ctx.serviceClient
                        )
                    }

                    if (bundle.email != null) {
                        // This is annoying...
                        val fixedEmail = when (bundle.email) {
                            is Mail.NewGrantApplicationMail -> bundle.email.copy(projectTitle = grantGiver)
                            is Mail.GrantApplicationStatusChangedToAdmin -> bundle.email.copy(projectTitle = grantGiver)
                            else -> bundle.email
                        }

                        allEmailsToSend.add(SendRequestItem(username, fixedEmail))
                    }
                }
            }

            if (notificationForSenders.isNotEmpty()) {
                val recipients = when (val r = application.currentRevision.document.recipient) {
                    is GrantApplication.Recipient.ExistingProject -> {
                        session.sendPreparedStatement(
                            { setParameter("pid", r.id) },
                            """
                                select
                                    pm.username
                                from
                                    project.projects p
                                    join project.project_members pm on p.id = pm.project_id
                                where 
                                    (pm.role = 'ADMIN' or pm.role = 'PI')
                                    and p.id = :pid
                            """
                        ).rows.map { it.getString(0)!! }
                    }

                    is GrantApplication.Recipient.NewProject -> listOf(application.createdBy)
                    is GrantApplication.Recipient.PersonalWorkspace -> listOf(application.createdBy)
                }

                for (username in recipients) {
                    if (actorAndProject.actor.safeUsername() == username) continue
                    val bundle = notificationForSenders.lastOrNull() ?: continue
                    if (bundle.notification != null) {
                        NotificationDescriptions.create.call(
                            CreateNotification(username, bundle.notification),
                            ctx.serviceClient
                        )
                    }

                    if (bundle.email != null) {
                        allEmailsToSend.add(SendRequestItem(username, bundle.email))
                    }
                }
            }

            if (allEmailsToSend.isNotEmpty()) {
                MailDescriptions.sendToUser.call(
                    bulkRequestOf(allEmailsToSend),
                    ctx.serviceClient
                )
            }

            return ModificationResult(application.id, commentsCreated)
        }
    }

    companion object {
        const val LOGO_MAX_SIZE = 1024 * 512
    }
}
