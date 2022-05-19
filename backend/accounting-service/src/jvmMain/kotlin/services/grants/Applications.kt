package dk.sdu.cloud.accounting.services.grants

import dk.sdu.cloud.*
import dk.sdu.cloud.accounting.api.DepositNotificationsProvider
import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.accounting.api.grants.UpdateReferenceIdRequest
import dk.sdu.cloud.accounting.services.projects.v2.ProviderNotificationService
import dk.sdu.cloud.accounting.util.Providers
import dk.sdu.cloud.accounting.util.SimpleProviderCommunication
import dk.sdu.cloud.calls.BulkRequest
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.grant.api.*
import dk.sdu.cloud.mail.api.Mail
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.db.async.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement

class GrantApplicationService(
    private val db: DBContext,
    private val notifications: GrantNotificationService,
    private val providers: Providers<SimpleProviderCommunication>,
    private val projectNotifications: ProviderNotificationService,
) {
    suspend fun retrieveProducts(
        actorAndProject: ActorAndProject,
        request: GrantsBrowseProductsRequest,
    ): List<Product> {
        return db.withSession(remapExceptions = true) { session ->
            session.sendPreparedStatement(
                {
                    setParameter("source", request.projectId)
                    setParameter("username", actorAndProject.actor.safeUsername())
                    setParameter("grant_recipient", request.recipientId)
                    setParameter("grant_recipient_type", request.recipientType)
                },
                """
                    with
                        can_submit_application as (
                            select "grant".can_submit_application(:username, :source, :grant_recipient,
                                :grant_recipient_type) can_submit
                        ),
                        approver_permission_check as (
                            select true can_submit
                            from
                                project.project_members pm
                            where
                                pm.project_id = :source and
                                (pm.role = 'ADMIN' or pm.role = 'PI') and
                                pm.username = :username
                        ),
                        permission_check as (
                            select bool_or(can_submit) can_submit
                            from (
                                select can_submit
                                from can_submit_application
                                union
                                select can_submit
                                from approver_permission_check
                            ) t
                        )
                    select accounting.product_to_json(p, pc, null)
                    from
                        permission_check join
                        accounting.products p on can_submit join
                        accounting.product_categories pc on p.category = pc.id join
                        accounting.wallet_owner wo on wo.project_id = :source join
                        accounting.wallets w on
                            wo.id = w.owned_by and
                            pc.id = w.category join
                        accounting.wallet_allocations alloc on
                            alloc.associated_wallet = w.id and
                            now() >= alloc.start_date and
                            (alloc.end_date is null or now() <= alloc.end_date)
                    order by pc.provider, pc.category
                """
            )
        }.rows.map { defaultMapper.decodeFromString(it.getString(0)!!) }
    }

    suspend fun submit(
        actorAndProject: ActorAndProject,
        request: BulkRequest<CreateApplication>
    ): List<FindByLongId> {
        request.items.forEach { createRequest ->
            val recipient = createRequest.document.recipient
            if (recipient is GrantApplication.Recipient.PersonalWorkspace && recipient.username != actorAndProject.actor.safeUsername()) {
                throw RPCException("Cannot request resources for someone else", HttpStatusCode.Forbidden)
            }
        }
        val results = mutableListOf<Pair<Long, GrantNotification>>()
        db.withSession(remapExceptions = true) { session ->
            request.items.forEach { createRequest ->
                val applicationId = session.sendPreparedStatement(
                    {
                        setParameter("username", actorAndProject.actor.safeUsername())
                        val recipient = createRequest.document.recipient
                        setParameter(
                            "grant_recipient", when (recipient) {
                                is GrantApplication.Recipient.ExistingProject -> recipient.id
                                is GrantApplication.Recipient.NewProject -> recipient.title
                                is GrantApplication.Recipient.PersonalWorkspace -> recipient.username
                            }
                        )
                        setParameter(
                            "grant_recipient_type", when (recipient) {
                                is GrantApplication.Recipient.ExistingProject -> GrantApplication.Recipient.EXISTING_PROJECT_TYPE
                                is GrantApplication.Recipient.NewProject -> GrantApplication.Recipient.NEW_PROJECT_TYPE
                                is GrantApplication.Recipient.PersonalWorkspace -> GrantApplication.Recipient.PERSONAL_TYPE
                            }
                        )

                    },
                    //TODO() FIX SUBMIT APPLICATION VI HAR FLERE SOURCES
                    """
                        with ids as (
                            insert into "grant".applications
                                (overall_state, requested_by, created_at) 
                            select
                                'IN_PROGRESS', :username, now()
                            where
                                "grant".can_submit_application(:username, :source, :grant_recipient, :grant_recipient_type)
                            returning id
                        )
                        insert into "grant".revisions (application_id, created_at, updated_by, revision_number)
                        select
                            ids.id, now(), :username, 0
                        from ids
                        returning application_id
                        
                        
                    """
                ).rows.singleOrNull()?.getLong(0)
                    ?: throw RPCException(
                        "It looks like you are unable to submit a request to this affiliation. " +
                            "Contact support if this problem persists.",
                        HttpStatusCode.Forbidden
                    )

                insertResources(session, applicationId, createRequest.document.allocationRequests, actorAndProject, 0)
                insertDocuments(session, applicationId, createRequest.document, 0)

                val responseNotification =
                    autoApprove(session, actorAndProject, createRequest, applicationId) ?: listOf(
                        GrantNotification(
                            applicationId,
                            adminMessage = AdminGrantNotificationMessage(
                                { "New grant application to $projectTitle" },
                                "NEW_GRANT_APPLICATION",
                                { Mail.NewGrantApplicationMail(actorAndProject.actor.safeUsername(), projectTitle) },
                                meta = {
                                    JsonObject(
                                        mapOf(
                                            "grantRecipient" to defaultMapper.encodeToJsonElement(createRequest.document.recipient),
                                            "appId" to JsonPrimitive(applicationId),
                                        )
                                    )
                                }
                            ),
                            userMessage = null
                        )
                    )
                responseNotification.forEach {
                    results.add(applicationId to it)
                }
            }
        }

        results.forEach { pair ->
            val notification = pair.second
            runCatching { notifications.notify(actorAndProject.actor.safeUsername(), notification) }
        }
        return results.map { FindByLongId(it.first) }
    }

    private suspend fun autoApprove(
        session: AsyncDBConnection,
        actorAndProject: ActorAndProject,
        request: CreateApplication,
        applicationId: Long,
    ): List<GrantNotification>? {
        val notifications = mutableListOf<GrantNotification>()
        val projectToResources = mutableMapOf<String, List<GrantApplication.AllocationRequest>>()
        request.document.allocationRequests.forEach {
            val found = projectToResources[it.grantGiver]
            if (found != null) {
                projectToResources[it.grantGiver] = found + listOf(it)
            } else {
                projectToResources[it.grantGiver] = listOf(it)
            }
        }
        projectToResources.forEach { (projectId, requestedResources) ->
            runBlocking {
                if (autoApproveApplicationCheck(
                        session,
                        requestedResources,
                        projectId,
                        actorAndProject.actor.safeUsername()
                    )
                ) {
                    //Get pi of approving project
                    val piOfProject = session.sendPreparedStatement(
                        {
                            setParameter("projectId", projectId)
                        },
                        """
                        select username
                        from project.project_members 
                        where role = 'PI' and project_id = :projectId
                    """
                    ).rows
                        .firstOrNull()
                        ?.getString(0)
                        ?: throw RPCException.fromStatusCode(
                            HttpStatusCode.InternalServerError,
                            "More than one or no PI found"
                        )

                    //update status of the application to APPROVED
                    session.sendPreparedStatement(
                        {
                            setParameter("state", GrantApplication.State.APPROVED.name)
                            setParameter("username", piOfProject)
                            setParameter("appId", applicationId)
                            setParameter("projectID", projectId)
                        },
                        """
                            update "grant".grant_giver_approval gga
                            set
                                state = :state,
                                updated_by = :username
                            where gga.application_id = :appId and project_id = :projectID
                        """
                    )

                    //grant resource
                    onApplicationApprove(session, piOfProject, applicationId)

                    val statusTitle = "Approved"
                    notifications.add(
                        GrantNotification(
                            applicationId,
                            adminMessage =
                            AdminGrantNotificationMessage(
                                { "Grant application updated ($statusTitle)" },
                                GRANT_APP_RESPONSE,
                                {
                                    Mail.GrantApplicationStatusChangedToAdmin(
                                        GrantApplication.State.APPROVED.name,
                                        projectTitle,
                                        requestedBy,
                                        grantRecipientTitle
                                    )
                                },
                                meta = {
                                    JsonObject(
                                        mapOf(
                                            "grantRecipient" to defaultMapper.encodeToJsonElement(grantRecipient),
                                            "appId" to JsonPrimitive(applicationId),
                                        )
                                    )
                                }
                            ),
                            userMessage =
                            UserGrantNotificationMessage(
                                { "Grant application updated ($statusTitle)" },
                                GRANT_APP_RESPONSE,
                                {
                                    Mail.GrantApplicationApproveMail(projectTitle)
                                },
                                meta = {
                                    JsonObject(
                                        mapOf(
                                            "grantRecipient" to defaultMapper.encodeToJsonElement(grantRecipient),
                                            "appId" to JsonPrimitive(applicationId),
                                        )
                                    )
                                }
                            )
                        )
                    )
                }
            }
        }
        return if(notifications.isEmpty()) null else notifications
    }

    private suspend fun autoApproveApplicationCheck(
        session: AsyncDBConnection,
        requestedResources: List<GrantApplication.AllocationRequest>,
        grantGiverId: String,
        applicantId: String
    ): Boolean {
        val numberOfAllowedToAutoApprove = session.sendPreparedStatement(
            {
                setParameter("product_categories", requestedResources.map { it.category })
                setParameter("product_providers", requestedResources.map { it.provider })
                setParameter("balances_requested", requestedResources.map { it.balanceRequested })
                setParameter("projectId", grantGiverId)
                setParameter("username", applicantId)
            },
            """
                with
                    requests as (
                        select
                            unnest(:balances_requested::bigint[]) requested,
                            unnest(:product_categories::text[]) product_category,
                            unnest(:product_providers::text[]) product_provider
                    )
                select count(*)
                from
                    requests req join
                    accounting.product_categories pc on
                        (req.product_provider = pc.provider and
                        req.product_category = pc.category) join
                    "grant".automatic_approval_limits aal on
                        (pc.id = aal.product_category and
                        req.requested < aal.maximum_credits) join
                    "grant".automatic_approval_users aau on
                        (
                            aal.project_id = aau.project_id and
                            aal.project_id = :projectId
                        ) join
                    auth.principals prin on
                        (
                            aau.type = 'anyone' or
                            (
                                aau.type = 'wayf' and
                                aau.applicant_id = prin.org_id
                            ) or
                            (
                                aau.type = 'email' and
                                prin.email like '%@' || aau.applicant_id
                            )
                        ) and prin.id = :username
            """
        ).rows
            .firstOrNull()
            ?.getLong(0)
            ?: throw RPCException.fromStatusCode(HttpStatusCode.BadRequest)
        return numberOfAllowedToAutoApprove == requestedResources.size.toLong()
    }

    private fun insertDocuments(
        session: AsyncDBConnection,
        applicationId: Long,
        documents: GrantApplication.Document,
        revisionNumber: Int
    ) {
        runBlocking {
            session
                .sendPreparedStatement(
                    {
                        setParameter("application_id", applicationId)
                        setParameter("revision_number", revisionNumber)
                        val recipient = documents.recipient
                        setParameter(
                            "grant_recipient", when (recipient) {
                                is GrantApplication.Recipient.ExistingProject -> recipient.id
                                is GrantApplication.Recipient.NewProject -> recipient.title
                                is GrantApplication.Recipient.PersonalWorkspace -> recipient.username
                            }
                        )
                        setParameter(
                            "grant_recipient_type", when (recipient) {
                                is GrantApplication.Recipient.ExistingProject -> GrantApplication.Recipient.EXISTING_PROJECT_TYPE
                                is GrantApplication.Recipient.NewProject -> GrantApplication.Recipient.NEW_PROJECT_TYPE
                                is GrantApplication.Recipient.PersonalWorkspace -> GrantApplication.Recipient.PERSONAL_TYPE
                            }
                        )
                        val form = documents.form
                        setParameter(
                            "form", when (form) {
                                is GrantApplication.Form.PlainText -> when (recipient) {
                                    is GrantApplication.Recipient.ExistingProject -> form.existingProject
                                    is GrantApplication.Recipient.NewProject -> form.newProject
                                    is GrantApplication.Recipient.PersonalWorkspace -> form.personalProject
                                }
                            }
                        )
                        setParameter("reference_id", documents.referenceId)
                    },
                    """
                        insert into "grant".documents (application_id, revision_number, recipient, recipient_type, form, reference_id) VALUES 
                        (:application_id, :revision_number, :grant_recipient, :grant_recipient_type, :form, :reference_id)
                    """.trimIndent()
                )
        }
    }

    private suspend fun insertResources(
        session: AsyncDBConnection,
        applicationId: Long,
        resources: List<GrantApplication.AllocationRequest>,
        actorAndProject: ActorAndProject,
        revisionNumber: Int
    ) {
        val allocationsApproved = session.sendPreparedStatement(
            {
                setParameter("id", applicationId)
                resources.split {
                    into("source_allocations") { it.sourceAllocation }
                    into("categories") { it.category }
                    into("providers") { it.provider }
                }
            },
            """
                with requests as (
                    select
                        :id application_id,
                        unnest(:categories::text[]) category,
                        unnest(:providers::text[]) provider_id,
                        unnest(:source_allocations::bigint[]) source_allocation
                ),
                requested_inserts as (
                    SELECT req.source_allocation, pc.category, pc.provider
                    FROM 
                        requests req
                        join accounting.wallet_allocations wall on wall.id = req.source_allocation
                        join accounting.wallets w on wall.associated_wallet = w.id
                        join accounting.product_categories pc on w.category = pc.id
                        join accounting.wallet_owner wo on w.owned_by = wo.id
                        join "grant".applications app on req.application_id = app.id
                    where pc.category = req.category and pc.provider = req.provider_id
                )
                
                select (select count(*) from requests) - (select count(*) from requested_inserts) as totalCount
            """
        ).rows
            .single()
            .getLong(0)!! == 0L
        if (!allocationsApproved) {
            throw RPCException.fromStatusCode(HttpStatusCode.BadRequest, "Allocations does not match products")
        }


        session.sendPreparedStatement(
            {
                setParameter("id", applicationId)
                setParameter("grant_applier", actorAndProject.actor.username)
                setParameter("revision_number", revisionNumber)
                resources.split {
                    into("credits_requested") { it.balanceRequested }
                    into("categories") { it.category }
                    into("providers") { it.provider }
                    into("source_allocations") { it.sourceAllocation}
                    into("start_dates") {it.period.start}
                    into("end_dates") {it.period.end}
                    into("grant_givers") {it.grantGiver}
                }
            },
            """
                insert into "grant".requested_resources
                    (application_id, credits_requested, product_category, source_allocation, start_date, end_date, grant_giver, :revision_number) 
                with requests as (
                    select
                        :id application_id,
                        unnest(:credits_requested::bigint[]) credits_requested,
                        unnest(:categories::text[]) category,
                        unnest(:providers::text[]) provider_id,
                        unnest(:source_allocations::text[]) source_allocation,
                        unnest(:start_dates::bigint[]) start_date,
                        unnest(:end_dates::bigint[]) end_date,
                        unnest(:grant_givers::text[]) grant_giver
                )
                select
                    req.application_id, req.credits_requested, pc.id, req.source_allocation, to_timestamp(req.start_date), to_timestamp(req.end_date), req.grant_giver, :revision_number
                from
                    requests req join
                    accounting.product_categories pc on
                        pc.category = req.category and
                        pc.provider = req.provider_id
            """
        )
    }

    suspend fun editReferenceID(
        actorAndProject: ActorAndProject,
        request: BulkRequest<UpdateReferenceIdRequest>
    ) {
        val filteredRequests = request.items.mapNotNull { editRequest ->
            if (editRequest.newReferenceId == null) {
                null
            } else {
                editRequest
            }
        }
        if (filteredRequests.isEmpty()) {
            return
        }

        db.withSession(remapExceptions = true) { session ->
            filteredRequests.forEach { req ->
                session.sendPreparedStatement(
                    {
                        setParameter("username", actorAndProject.actor.safeUsername()) //TODO() IS THIS FINE?
                        setParameter("id", req.id)
                        setParameter("newReference", req.newReferenceId)
                    },
                    """
                    with
                        permission_check as (
                            select app.id, pm.username is not null as is_approver
                            from
                                "grant".applications app left join
                                project.project_members pm on
                                    app.resources_owned_by = pm.project_id and
                                    (pm.role = 'ADMIN' or pm.role = 'PI') and
                                    pm.username = :username
                            where 
                                app.id = :id and
                                pm.username = :username
                                
                        )
                    update "grant".applications app
                    set reference_id = :newReference
                    from permission_check pc
                    where app.id = pc.id and pc.is_approver
                """
                )
            }
        }
    }

    suspend fun editApplication(
        actorAndProject: ActorAndProject,
        request: EditApplicationRequest
    ) {
        //TODO() USE REVISION
        db.withSession(remapExceptions = true) { session ->
            val success = session.sendPreparedStatement(
                {
                    setParameter("username", actorAndProject.actor.safeUsername())
                    setParameter("id", "")//request.id)
                    setParameter("document", "") //request.newDocument)
                },
                """
                    with
                        permission_check as (
                            select app.id, pm.username is not null as is_approver, app.document as current_document
                            from
                                "grant".applications app left join
                                project.project_members pm on
                                    app.resources_owned_by = pm.project_id and
                                    (pm.role = 'ADMIN' or pm.role = 'PI') and
                                    pm.username = :username
                            where 
                                app.id = :id and
                                app.status = 'IN_PROGRESS' and
                                (
                                    app.requested_by = :username or
                                    pm.username = :username
                                )
                        ),
                        update_table as (
                            select id, case when is_approver then :document else current_document end as new_document
                            from permission_check
                        )
                    update "grant".applications app
                    set document = new_document
                    from update_table ut
                    where app.id = ut.id
                """
            ).rowsAffected > 0L

            if (!success) {
                throw RPCException(
                    "Unable to update application. Has it already been closed?",
                    HttpStatusCode.BadRequest
                )
            }

            session.sendPreparedStatement(
                { setParameter("id", "")},//request.id) },
                "delete from \"grant\".requested_resources where application_id = :id"
            )

            //insertResources(session, request.id, request.newResources)

            notifications.notify(
                actorAndProject.actor.safeUsername(),
                GrantNotification(
                    123,//request.id,
                    adminMessage =
                    AdminGrantNotificationMessage(
                        { "Grant application updated" },
                        "GRANT_APPLICATION_UPDATED",
                        { Mail.GrantApplicationUpdatedMailToAdmins(projectTitle, requestedBy, grantRecipientTitle) },
                        meta = {
                            JsonObject(
                                mapOf(
                                    "grantRecipient" to defaultMapper.encodeToJsonElement(grantRecipient)
                                    //"appId" to JsonPrimitive(request.id),
                                )
                            )
                        }
                    ),
                    userMessage =
                    UserGrantNotificationMessage(
                        { "Grant application updated" },
                        "GRANT_APPLICATION_UPDATED",
                        { Mail.GrantApplicationUpdatedMail(projectTitle, requestedBy) },
                        meta = {
                            JsonObject(
                                mapOf(
                                    "grantRecipient" to defaultMapper.encodeToJsonElement(grantRecipient)
                                    //"appId" to JsonPrimitive(request.id),
                                )
                            )
                        }
                    )
                ),
            )
        }
    }

    suspend fun updateStatus(
        actorAndProject: ActorAndProject,
        request: BulkRequest<UpdateApplicationState>
    ) {
        request.items.forEach { update ->
            require(update.newState != GrantApplication.State.IN_PROGRESS) { "New status can only be APPROVED, REJECTED or CLOSED!" }
        }
        db.withSession(remapExceptions = true) { session ->
            request.items.forEach { update ->
                val id = update.applicationId
                val newState = update.newState
                val success = session.sendPreparedStatement(
                    {
                        setParameter("username", actorAndProject.actor.safeUsername())
                        setParameter("id", id)
                        setParameter("status", newState.name)
                        setParameter("should_be_approver", newState != GrantApplication.State.CLOSED)
                    },
                    """
                    with
                        permission_check as (
                            select app.id, pm.username is not null as is_approver
                            from
                                "grant".applications app left join
                                project.project_members pm on
                                    app.resources_owned_by = pm.project_id and
                                    (pm.role = 'ADMIN' or pm.role = 'PI') and
                                    pm.username = :username
                            where 
                                app.id = :id and
                                app.status = 'IN_PROGRESS' and
                                (
                                    app.requested_by = :username or
                                    pm.username = :username
                                )
                        ),
                        update_table as (
                            select id from permission_check where is_approver = :should_be_approver
                        )
                    update "grant".applications app
                    set
                        status = :status,
                        status_changed_by = :username
                    from update_table ut
                    where app.id = ut.id
                """,
                ).rowsAffected > 0L

                if (!success) {
                    throw RPCException(
                        "Unable to update the status. Has the application already been closed?",
                        HttpStatusCode.BadRequest
                    )
                }

                if (newState == GrantApplication.State.APPROVED) {
                    onApplicationApprove(session, actorAndProject.actor.safeUsername(), update.applicationId)
                }

                if (update.notify) {
                    val statusTitle = when (newState) {
                        GrantApplication.State.APPROVED -> "Approved"
                        GrantApplication.State.REJECTED -> "Rejected"
                        GrantApplication.State.CLOSED -> "Closed"
                        GrantApplication.State.IN_PROGRESS -> "In Progress"
                    }
                    notifications.notify(
                        actorAndProject.actor.safeUsername(),
                        GrantNotification(
                            id,
                            adminMessage =
                            AdminGrantNotificationMessage(
                                { "Grant application updated ($statusTitle)" },
                                GRANT_APP_RESPONSE,
                                {
                                    Mail.GrantApplicationStatusChangedToAdmin(
                                        update.newState.name,
                                        projectTitle,
                                        requestedBy,
                                        grantRecipientTitle
                                    )
                                },
                                meta = {
                                    JsonObject(
                                        mapOf(
                                            "grantRecipient" to defaultMapper.encodeToJsonElement(grantRecipient),
                                            "appId" to JsonPrimitive(id),
                                        )
                                    )
                                }
                            ),
                            userMessage =
                            UserGrantNotificationMessage(
                                { "Grant application updated ($statusTitle)" },
                                GRANT_APP_RESPONSE,
                                {
                                    when (newState) {
                                        GrantApplication.State.APPROVED -> Mail.GrantApplicationApproveMail(projectTitle)
                                        GrantApplication.State.REJECTED -> Mail.GrantApplicationRejectedMail(projectTitle)
                                        GrantApplication.State.CLOSED -> Mail.GrantApplicationWithdrawnMail(
                                            projectTitle,
                                            actorAndProject.actor.safeUsername()
                                        )
                                        else -> throw IllegalStateException()
                                    }
                                },
                                meta = {
                                    JsonObject(
                                        mapOf(
                                            "grantRecipient" to defaultMapper.encodeToJsonElement(grantRecipient),
                                            "appId" to JsonPrimitive(id),
                                        )
                                    )
                                }
                            )
                        ),
                    )
                }
            }
        }
    }

    suspend fun transferApplication(
        actorAndProject: ActorAndProject,
        request: TransferApplicationRequest
    ) {
        db.withSession(remapExceptions = true) { session ->
            session.sendPreparedStatement(
                {
                    setParameter("username", actorAndProject.actor.safeUsername())
                    setParameter("id", request.applicationId)
                    setParameter("target", request.transferToProjectId)
                },
                """
                    select "grant".transfer_application(:username, :id, :target)
                """
            )
        }
    }

    suspend fun browseApplications(
        actorAndProject: ActorAndProject,
        browseApplicationsRequest: BrowseApplicationsRequest,
        pagination: WithPaginationRequestV2,
        filter: GrantApplicationFilter
    ): PageV2<GrantApplication> {
        return db.paginateV2(
            actorAndProject.actor,
            pagination.normalize(),
            create = { session ->
                session.sendPreparedStatement(
                    {
                        setParameter("username", actorAndProject.actor.safeUsername())
                        setParameter("project", actorAndProject.project)
                        setParameter("ingoing", browseApplicationsRequest.includeIngoingApplications)
                        setParameter("outgoing", browseApplicationsRequest.includeOutgoingApplications)
                        setParameter(
                            "show_active",
                            filter == GrantApplicationFilter.ACTIVE || filter == GrantApplicationFilter.SHOW_ALL
                        )
                        setParameter(
                            "show_inactive",
                            filter == GrantApplicationFilter.INACTIVE || filter == GrantApplicationFilter.SHOW_ALL
                        )
                    },
                    """
                        declare c cursor for
                        with outgoing as (
                            select 
                                apps,
                                array_remove(array_agg("grant".resource_request_to_json(request, pc)), null),
                                owner_project,
                                existing_project,
                                existing_project_pi.username
                            from
                                "grant".applications apps join
                                "grant".requested_resources request on apps.id = request.application_id join
                                accounting.product_categories pc on request.product_category = pc.id join
                                project.projects owner_project on
                                    apps.resources_owned_by = owner_project.id left join
                                project.projects existing_project on
                                    apps.grant_recipient_type = 'existing_project' and
                                    existing_project.id = apps.grant_recipient left join
                                project.project_members existing_project_pi on
                                    existing_project_pi.role = 'PI' and
                                    existing_project_pi.project_id = existing_project.id left join
                                project.project_members user_role_in_existing on
                                    (user_role_in_existing.role = 'ADMIN' or user_role_in_existing.role = 'PI') and
                                    existing_project.id = user_role_in_existing.project_id and
                                    user_role_in_existing.username = :username
                            where
                                requested_by = :username and
                                (
                                    (
                                        grant_recipient_type = 'existing_project' and
                                        existing_project.id = :project and
                                        user_role_in_existing.username = :username
                                    ) or
                                    (
                                        (
                                            grant_recipient_type = 'personal' or
                                            grant_recipient_type = 'new_project'
                                        )
                                    )
                                ) and
                                (:show_active or apps.status != 'IN_PROGRESS') and
                                (:show_inactive or apps.status = 'IN_PROGRESS') and
                                :outgoing
                            group by
                                apps.*, existing_project.*, existing_project_pi.username, owner_project.*,
                                apps.created_at, apps.id
                        ),
                        ingoing as (
                            select 
                                apps,
                                array_remove(array_agg("grant".resource_request_to_json(request, pc)), null),
                                owner_project,
                                existing_project,
                                existing_project_pi.username
                            from
                                "grant".applications apps join
                                project.project_members pm on
                                    pm.project_id = apps.resources_owned_by and
                                    pm.username = :username and
                                    (pm.role = 'ADMIN' or pm.role = 'PI') join
                                "grant".requested_resources request on apps.id = request.application_id join
                                accounting.product_categories pc on request.product_category = pc.id join
                                project.projects owner_project on
                                    owner_project.id = apps.resources_owned_by left join
                                project.projects existing_project on
                                    apps.grant_recipient_type = 'existing_project' and
                                    existing_project.id = apps.grant_recipient left join
                                project.project_members existing_project_pi on
                                    existing_project_pi.role = 'PI' and
                                    existing_project_pi.project_id = existing_project.id
                            where
                                apps.resources_owned_by = :project and
                                (:show_active or apps.status != 'IN_PROGRESS') and
                                (:show_inactive or apps.status = 'IN_PROGRESS') and
                                :ingoing
                            group by
                                apps.*, existing_project.*, existing_project_pi.username, owner_project.*,
                                apps.created_at, apps.id
                        ),
                        all_applications as (
                            select *
                            from ingoing
                            union
                            select *
                            from outgoing
                        )
                        select * 
                        from all_applications
                        order by apps.created_at desc, apps.id;
                    """
                )
            },
            mapper = { _, rows -> rows.map { defaultMapper.decodeFromString(it.getString(0)!!) } }
        )
    }

    private suspend fun onApplicationApprove(
        session: AsyncDBConnection,
        approvedBy: String,
        applicationId: Long
    ) {
        session.sendPreparedStatement(
            {
                setParameter("id", applicationId)
                setParameter("approved_by", approvedBy)
            },
            """select "grant".approve_application(:approved_by, :id)"""
        )

        val createdProject = session.sendPreparedStatement("select project_id from grant_created_projects").rows
            .map { it.getString(0)!! }.singleOrNull()

        if (createdProject != null) {
            projectNotifications.notifyChange(listOf(createdProject), session)
        }

        val providerIds = session.sendPreparedStatement(
            { setParameter("id", applicationId) },
            """
                select distinct pc.provider
                from
                    "grant".requested_resources r join
                    accounting.product_categories pc on
                        r.product_category = pc.id
                where
                    r.application_id = :id
            """
        ).rows.map { it.getString(0)!! }

        providerIds.forEach { provider ->
            val comms = providers.prepareCommunication(provider)
            DepositNotificationsProvider(provider).pullRequest.call(Unit, comms.client)
        }
    }

    companion object : Loggable {
        override val log = logger()
        private const val GRANT_APP_RESPONSE = "GRANT_APPLICATION_RESPONSE"
    }
}
