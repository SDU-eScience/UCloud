package dk.sdu.cloud.accounting.services.grants

import dk.sdu.cloud.*
import dk.sdu.cloud.accounting.api.DepositNotificationsProvider
import dk.sdu.cloud.accounting.api.Product
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

    suspend fun retrieveGrantApplication(applicationId: Long, actorAndProject: ActorAndProject): GrantApplication {
        val application = db.withSession { session ->
            permissionCheck(session, actorAndProject, applicationId)
            session.sendPreparedStatement(
                {
                    setParameter("app_in", applicationId)
                },
                """
                    select "grant".application_to_json(:app_in) 
                """
            ).rows
                .singleOrNull()
                ?.getString(0)
                ?: throw RPCException("Did not find a single application", HttpStatusCode.NotFound)
        }
        return defaultMapper.decodeFromString(application)
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
            if (recipient is GrantApplication.Recipient.NewProject && createRequest.document.parentProjectId == null) {
                throw RPCException("Missing parent ID when new project", HttpStatusCode.BadRequest)
            }
        }
        val results = mutableListOf<Pair<Long, GrantNotification>>()
        db.withSession(remapExceptions = true) { session ->
            request.items.forEach { createRequest ->

                val sourceProjects = createRequest.document.allocationRequests.map {
                    it.grantGiver
                }.toSet().toList()

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
                        setParameter("sources", sourceProjects)

                    },
                    """
                        with ids as (
                            insert into "grant".applications
                                (overall_state, requested_by, created_at) 
                            select
                                'IN_PROGRESS', :username, now()
                            where
                                "grant".can_submit_application(:username, :sources::text[], :grant_recipient, :grant_recipient_type)
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

                session.sendPreparedStatement(
                    {
                        setParameter("sources", sourceProjects)
                        setParameter("app_id", applicationId)
                    },
                    """
                        with pit as (
                            select id, title
                            from project.projects pr 
                            where pr.id in (select unnest(:sources::text[]))
                        )
                        insert into "grant".grant_giver_approvals (application_id, project_id, project_title, state, updated_by, last_update) 
                        select 
                            :app_id, pit.id, pit.title, 'IN_PROGRESS', '_ucloud', now()
                        from pit
                    """.trimIndent()
                )

                insertDocument(session, actorAndProject, applicationId, createRequest.document, 0, true)
                autoApprove(session, actorAndProject, createRequest, applicationId)

                val (allApproved, grantGivers) = retrieveGrantGiversStates(session, applicationId)
                println(allApproved)
                println(grantGivers)
                if (allApproved) {
                    session.sendPreparedStatement(
                        {
                            setParameter("app_in", applicationId)
                        },
                        """
                            update "grant".applications 
                            set overall_state = 'APPROVED', updated_at = now()
                            where id = :app_in
                        """
                    )
                    onApplicationApprove(session, applicationId, createRequest.document.parentProjectId)
                    val notification = GrantNotification(
                        applicationId,
                        adminMessage =
                        AdminGrantNotificationMessage(
                            { "Grant application updated (approved)" },
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
                            { "Grant application updated (approved)" },
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
                    results.add(applicationId to notification)
                } else {
                    val missingApprovals = grantGivers.mapNotNull {
                        if (it.state != GrantApplication.State.APPROVED) {
                            it
                        } else {
                            null
                        }
                    }
                    val responseNotification =
                        missingApprovals.map {
                            GrantNotification(
                                applicationId,
                                adminMessage = AdminGrantNotificationMessage(
                                    { "New grant application to ${it.projectTitle}" },
                                    "NEW_GRANT_APPLICATION",
                                    {
                                        Mail.NewGrantApplicationMail(
                                            actorAndProject.actor.safeUsername(),
                                            it.projectTitle
                                        )
                                    },
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
                        }
                    responseNotification.forEach {
                        results.add(applicationId to it)
                    }
                }

            }
        }

        results.forEach { pair ->
            val notification = pair.second
            runCatching { notifications.notify(actorAndProject.actor.safeUsername(), notification) }
        }

        return results.map { FindByLongId(it.first) }.toSet().toList()
    }

    private suspend fun retrieveGrantGiversStates(
        session: AsyncDBConnection,
        applicationId: Long
    ): Pair<Boolean, List<GrantApplication.GrantGiverApprovalState>> {
        var allApproved = true
        val grantGiverApprovalStates = runBlocking {
            session.sendPreparedStatement(
                {
                    setParameter("app_id", applicationId)
                },
                """
                    select project_id, project_title, state
                    from "grant".grant_giver_approvals
                    where application_id = :app_id
                """.trimIndent()
            ).rows
                .map {
                    GrantApplication.GrantGiverApprovalState(
                        projectId = it.getString(0)!!,
                        projectTitle = it.getString(1)!!,
                        state = GrantApplication.State.valueOf(it.getString(2)!!)
                    )
                }

        }
        grantGiverApprovalStates.forEach {
            if (it.state != GrantApplication.State.APPROVED) {
                allApproved = false
            }
        }
        return Pair(allApproved, grantGiverApprovalStates)
    }

    private suspend fun autoApprove(
        session: AsyncDBConnection,
        actorAndProject: ActorAndProject,
        request: CreateApplication,
        applicationId: Long,
    ) {
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
                            update "grant".grant_giver_approvals gga
                            set
                                state = :state,
                                updated_by = :username
                            where gga.application_id = :appId and project_id = :projectID
                        """
                    )
                }
            }
        }
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
                            unnest(:balances_requested::bigint[]) balances_requested,
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
                        req.balances_requested < aal.maximum_credits) join
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

    private fun insertDocument(
        session: AsyncDBConnection,
        actorAndProject: ActorAndProject,
        applicationId: Long,
        document: GrantApplication.Document,
        revisionNumber: Int,
        isSubmit: Boolean = false
    ) {
        runBlocking {
            // TODO(Jonas):
            // Fetch existing allocation requests, ONLY update these allocations requests based on actorAndProject
            // project where grantGiver == actorAndProject.project. UNLESS request is from recipient.
            val recipient = document.recipient
            val allocationRequests = if (!isSubmit) {
                // Note(Jonas): Only if an application with resources already exists, retrieveGrantApplication can be called
                // For a new submission, this is only added later in this function.
                val application = retrieveGrantApplication(applicationId, actorAndProject)
                val canChangeAll = when (recipient) {
                    is GrantApplication.Recipient.NewProject ->
                        application.createdBy == actorAndProject.actor.safeUsername()

                    is GrantApplication.Recipient.ExistingProject ->
                        recipient.id == actorAndProject.project

                    is GrantApplication.Recipient.PersonalWorkspace ->
                        application.createdBy == actorAndProject.actor.safeUsername()
                }

                if (canChangeAll) document.allocationRequests else {
                    val newRequests = document.allocationRequests
                    val oldRequests = application.currentRevision.document.allocationRequests
                    oldRequests.mapNotNull { oldReq ->
                        if (oldReq.grantGiver != actorAndProject.project) oldReq
                        else {
                            newRequests.find { newReq ->
                                newReq.category == oldReq.category && newReq.provider == oldReq.provider
                            }
                        }
                    }
                }
            } else {
                document.allocationRequests
            }

            checkReferenceID(document.referenceId)
            val allocationsApproved = session.sendPreparedStatement(
                {
                    setParameter("id", applicationId)
                    allocationRequests.split {
                        into("source_allocations") { it.sourceAllocation }
                        into("categories") { it.category }
                        into("providers") { it.provider }
                        into("grant_givers") { it.grantGiver }
                    }
                },
                """
                    with requests as (
                        select
                            :id application_id,
                            unnest(:categories::text[]) category,
                            unnest(:providers::text[]) provider_id,
                            unnest(:source_allocations::bigint[]) source_allocation,
                            unnest(:grant_givers::text[]) grant_giver
                    ),
                    requested_inserts as (
                        SELECT req.source_allocation, pc.category, pc.provider
                        FROM 
                            requests req
                            join accounting.product_categories pc on pc.provider = req.provider_id and pc.category = req.category
                            join accounting.wallets w on pc.id = w.category
                            join accounting.wallet_owner wo on w.owned_by = wo.id
                            left join accounting.wallet_allocations wall on w.id = wall.associated_wallet 
                            join "grant".applications app on req.application_id = app.id
                        where req.grant_giver = wo.project_id
                    )
                    
                    select (select count(*) from requests) - (select count(*) from requested_inserts) as totalCount
                """
            ).rows
                .single()
                .getLong(0) != 0L

            if (allocationsApproved) {
                throw RPCException.fromStatusCode(HttpStatusCode.BadRequest, "Allocations does not match products")
            }

            session.sendPreparedStatement(
                {
                    setParameter("id", applicationId)
                    setParameter("grant_applier", actorAndProject.actor.username)
                    setParameter("revision_number", revisionNumber)
                    document.allocationRequests.split {
                        into("credits_requested") { it.balanceRequested }
                        into("categories") { it.category }
                        into("providers") { it.provider }
                        into("source_allocations") { it.sourceAllocation }
                        into("start_dates") { it.period.start }
                        into("end_dates") { it.period.end }
                        into("grant_givers") { it.grantGiver }
                    }
                },
                """
                    insert into "grant".requested_resources
                        (application_id, credits_requested, quota_requested_bytes, product_category, source_allocation, start_date, end_date, grant_giver, revision_number) 
                    with requests as (
                        select
                            :id application_id,
                            unnest(:credits_requested::bigint[]) credits_requested,
                            unnest(:categories::text[]) category,
                            unnest(:providers::text[]) provider_id,
                            unnest(:source_allocations::bigint[]) source_allocation,
                            unnest(:start_dates::bigint[]) start_date,
                            unnest(:end_dates::bigint[]) end_date,
                            unnest(:grant_givers::text[]) grant_giver
                    )
                    select
                        req.application_id, req.credits_requested, null, pc.id, req.source_allocation, to_timestamp(req.start_date), to_timestamp(req.end_date), req.grant_giver, :revision_number
                    from
                        requests req join
                        accounting.product_categories pc on
                            pc.category = req.category and
                            pc.provider = req.provider_id
                """
            )

            session
                .sendPreparedStatement(
                    {
                        setParameter("application_id", applicationId)
                        setParameter("revision_number", revisionNumber)
                        val recipient = document.recipient
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
                        val formText = when (val form = document.form) {
                            is GrantApplication.Form.PlainText -> form.text
                        }
                        setParameter("form", formText)
                        setParameter("reference_id", document.referenceId)
                        setParameter("parent_project_id", document.parentProjectId)
                    },
                    """
                        insert into "grant".forms (application_id, revision_number, recipient, recipient_type, form, reference_id, parent_project_id) VALUES 
                        (:application_id, :revision_number, :grant_recipient, :grant_recipient_type, :form, :reference_id, :parent_project_id)
                    """.trimIndent()
                )
        }
    }

    private fun checkReferenceID(newReferenceId: String?) {
        val errorMessage = "DeiC is a reserved keyword."
        val deicUniList = listOf("KU", "DTU", "AU", "SDU", "AAU", "RUC", "ITU", "CBS")
        if (newReferenceId != null) {
            if (newReferenceId.lowercase().startsWith("deic")) {
                val splitId = newReferenceId.split("-")
                when {
                    splitId.size != 4 -> {
                        throw RPCException.fromStatusCode(
                            HttpStatusCode.BadRequest,
                            "$errorMessage It seems like you are not following request format. DeiC-XX-YY-NUMBER"
                        )
                    }

                    splitId.first() != "DeiC" -> {
                        throw RPCException.fromStatusCode(
                            HttpStatusCode.BadRequest,
                            "$errorMessage First part should be DeiC."
                        )
                    }

                    !deicUniList.contains(splitId[1]) -> {
                        throw RPCException.fromStatusCode(
                            HttpStatusCode.BadRequest,
                            "$errorMessage Uni value is not listed in DeiC. If you think this is a mistake, please contact UCloud"
                        )
                    }

                    splitId[2].length != 2 -> {
                        throw RPCException.fromStatusCode(
                            HttpStatusCode.BadRequest,
                            errorMessage + " Allocation category wrong fornat"
                        )
                    }

                    !splitId[2].contains(Regex("""[LNSI][1-5]$""")) -> {
                        throw RPCException.fromStatusCode(
                            HttpStatusCode.BadRequest,
                            errorMessage + " Allocation category has wrong format."
                        )
                    }

                    !splitId[3].contains(Regex("""^\d+$""")) ->
                        throw RPCException.fromStatusCode(
                            HttpStatusCode.BadRequest,
                            errorMessage + " Only supports numeric local ids"
                        )
                }
            }
        }
    }

    private suspend fun permissionCheck(session: AsyncDBConnection, actorAndProject: ActorAndProject, appId: Long) {
        val permission = session.sendPreparedStatement(
            {
                setParameter("username", actorAndProject.actor.safeUsername())
                setParameter("id", appId)
            },
            """
                with max_revision as (
                    select max(revision_number) newest, application_id
                    from "grant".revisions
                    where application_id = :id
                    group by application_id
                )
                select distinct app.id
                from
                    "grant".applications app join
                    max_revision mr on app.id = mr.application_id join
                    "grant".requested_resources rr on app.id = rr.application_id and mr.newest = rr.revision_number join
                    "grant".forms f on app.id = f.application_id and mr.newest = f.revision_number join
                    project.project_members pm on
                        (rr.grant_giver = pm.project_id and
                        (pm.role = 'ADMIN' or pm.role = 'PI') and
                        pm.username = :username
                    ) or (
                        (f.recipient_type = 'existing_project') and
                        pm.project_id = f.recipient and
                        (pm.role = 'ADMIN' or pm.role = 'PI') and
                        pm.username = :username
                    ) or (f.recipient_type = 'personal' and f.recipient = :username)
                    or (f.recipient_type = 'new_project' and app.requested_by = :username)
                where f.application_id = :id;
            """, debug = true
        ).rows.size > 0L

        if (!permission) {
            throw RPCException(
                "Unable to update application. Has it already been closed?",
                HttpStatusCode.BadRequest
            )
        }
    }

    suspend fun editApplication(
        actorAndProject: ActorAndProject,
        requests: BulkRequest<EditApplicationRequest>
    ) {
        db.withSession(remapExceptions = true) { session ->
            requests.items.forEach { request ->
                permissionCheck(session, actorAndProject, request.applicationId)

                val currentRevision = getCurrentRevision(session, request.applicationId)
                session.sendPreparedStatement(
                    {
                        setParameter("revision", currentRevision + 1)
                        setParameter("app_id", request.applicationId)
                        setParameter("comment", request.document.revisionComment)
                        setParameter("username", actorAndProject.actor.safeUsername())
                    },
                    """
                        insert into "grant".revisions (application_id, created_at, updated_by, revision_number, revision_comment) VALUES 
                        (:app_id, now(), :username, :revision, :comment)
                    """
                )
                insertDocument(session, actorAndProject, request.applicationId, request.document, currentRevision + 1)

                notifications.notify(
                    actorAndProject.actor.safeUsername(),
                    GrantNotification(
                        request.applicationId,
                        adminMessage =
                        AdminGrantNotificationMessage(
                            { "Grant application updated" },
                            "GRANT_APPLICATION_UPDATED",
                            {
                                Mail.GrantApplicationUpdatedMailToAdmins(
                                    projectTitle,
                                    requestedBy,
                                    grantRecipientTitle
                                )
                            },
                            meta = {
                                JsonObject(
                                    mapOf(
                                        "grantRecipient" to defaultMapper.encodeToJsonElement(grantRecipient),
                                        "appId" to JsonPrimitive(request.applicationId),
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
                                        "grantRecipient" to defaultMapper.encodeToJsonElement(grantRecipient),
                                        "appId" to JsonPrimitive(request.applicationId),
                                    )
                                )
                            }
                        )
                    )
                )
            }
        }
    }

    suspend fun closeApplication(
        actorAndProject: ActorAndProject,
        request: BulkRequest<UpdateApplicationState>
    ) {
        request.items.forEach { update ->
            require(update.newState == GrantApplication.State.CLOSED) { "Closing status can only be CLOSED!" }
        }

        db.withSession(remapExceptions = true) { session ->
            request.items.forEach { update ->
                val id = update.applicationId
                val success = session.sendPreparedStatement(
                    {
                        setParameter("user", actorAndProject.actor.safeUsername())
                        setParameter("application_id", id)
                        setParameter("state", update.newState.name)
                    },
                    """
                        with update as (
                            UPDATE "grant".applications 
                            set overall_state = 'CLOSED', updated_at = now()
                            where id = :application_id and requested_by = :user
                            returning id
                        )
                        update "grant".grant_giver_approvals
                        set state = :state, updated_by = :user, last_update = now()
                        from update
                        where application_id = update.id
                    """
                ).rowsAffected > 0
                if (!success) {
                    throw RPCException(
                        "Unable to close application. Has the application already been closed?",
                        HttpStatusCode.BadRequest
                    )
                }
            }
        }
    }

    // TODO(Jonas): Only allow approving a grant application if #source-allocations > 0 for the approver
    suspend fun updateStatus(
        actorAndProject: ActorAndProject,
        request: BulkRequest<UpdateApplicationState>
    ) {
        db.withSession(remapExceptions = true) { session ->
            for (reqItem in request.items) {
                if (reqItem.newState != GrantApplication.State.APPROVED) continue
                val applicationId = reqItem.applicationId
                val application = retrieveGrantApplication(applicationId, actorAndProject)
                val allocations = application.currentRevision.document.allocationRequests
                    .filter { it.grantGiver == actorAndProject.project }

                val hasAllAllocationsSet = allocations.all { it.sourceAllocation != null }
                if (!hasAllAllocationsSet) {
                    throw RPCException(
                        "Not all requested resources have a source allocation assigned. " +
                            "You must select one for each resource.",
                        HttpStatusCode.BadRequest
                    )
                }

                val invalidNewStates = listOf(GrantApplication.State.APPROVED, GrantApplication.State.CLOSED)
                if (application.status.overallState in invalidNewStates) {
                    throw RPCException(
                        "An approved or withdrawn application cannot be changed.",
                        HttpStatusCode.BadRequest
                    )
                }
            }

            // TODO: Should be one db-access, not one for each request.
            request.items.forEach { update ->
                val id = update.applicationId
                val newState = update.newState
                val approvingProject = actorAndProject.project

                permissionCheck(session, actorAndProject, id)

                if (actorAndProject.project == null) {
                    throw RPCException(
                        "Cant change application state from personal workspace",
                        HttpStatusCode.BadRequest
                    )
                } else {
                    val success = session.sendPreparedStatement(
                        {
                            setParameter("username", actorAndProject.actor.safeUsername())
                            setParameter("app_id", id)
                            setParameter("state", newState.name)
                            setParameter("project_id", approvingProject)
                        },
                        // TODO(Jonas): This change might be important.
                        """
                            update "grant".grant_giver_approvals
                            set state = :state, updated_by = :username, last_update = now() 
                            where project_id = :project_id and
                                application_id = :app_id;
                        """,
                    ).rowsAffected > 0L

                    if (!success) {
                        throw RPCException(
                            "Unable to update the status. Has the application already been closed?",
                            HttpStatusCode.BadRequest
                        )
                    }

                }

                // Note(Jonas): This would be called for each request, potentially setting reject multiple times, right?
                val newOverallState = if (update.newState == GrantApplication.State.REJECTED) {
                    session.sendPreparedStatement(
                        {
                            setParameter("app_id", update.applicationId)
                            setParameter("state", update.newState.name)
                            setParameter("user", actorAndProject.actor.safeUsername())
                            setParameter("project_id", approvingProject)
                        },
                        """
                            with overall as (
                                update "grant".applications
                                set overall_state = :state, updated_at = now()
                                where id = :app_id
                                returning id
                            )
                            update "grant".grant_giver_approvals
                            set state = :state, updated_by = :user, last_update = now()
                            from overall
                            where application_id = overall.id and project_id = :project_id
                        """
                    )
                    update.newState
                } else {
                    val (approved, states) = retrieveGrantGiversStates(session, update.applicationId)

                    if (approved) {
                        session.sendPreparedStatement(
                            {
                                setParameter("app_id", update.applicationId)
                            },
                            """
                            update "grant".applications
                            set overall_state = 'APPROVED', updated_at = now()
                            where id = :app_id
                        """, debug = true
                        )
                        val currentRevision = getCurrentRevision(session, update.applicationId)
                        val parentId = session.sendPreparedStatement(
                            {
                                setParameter("app_id", update.applicationId)
                                setParameter("revision", currentRevision)
                            },
                            """
                                select parent_project_id
                                from "grant".forms 
                                where application_id = :app_id
                                    and revision_number = :revision
                            """
                        ).rows
                            .singleOrNull()
                            ?.getString(0)
                        onApplicationApprove(session, update.applicationId, parentId)
                        GrantApplication.State.APPROVED
                    } else {
                        val overall = session.sendPreparedStatement(
                            {
                                setParameter("app_id", update.applicationId)
                            },
                            """
                                select overall_state
                                from "grant".applications 
                                where id = :app_id
                            """
                        ).rows.singleOrNull()?.getString(0) ?: throw RPCException(
                            "Could not find application",
                            HttpStatusCode.NotFound
                        )

                        val stateAsEnum = GrantApplication.State.valueOf(overall)
                        if (stateAsEnum != GrantApplication.State.CLOSED) {
                            // Note(Jonas): Not rejected, not all approved, not closed
                            val reversibleStates =
                                listOf(GrantApplication.State.IN_PROGRESS, GrantApplication.State.APPROVED)
                            if (states.all { s -> s.state in reversibleStates }) {
                                session.sendPreparedStatement(
                                    {
                                        setParameter("app_id", update.applicationId)
                                    },
                                    """
                                    update "grant".applications
                                    set overall_state = 'IN_PROGRESS', updated_at = now()
                                    where id = :app_id
                                """, debug = true
                                )
                            }
                            GrantApplication.State.IN_PROGRESS
                        } else when (overall) {
                            "APPROVED" -> GrantApplication.State.APPROVED
                            "CLOSED" -> GrantApplication.State.CLOSED
                            "REJECTED" -> GrantApplication.State.REJECTED
                            "IN_PROGRESS" -> GrantApplication.State.IN_PROGRESS
                            else -> throw RPCException("Wrong state", HttpStatusCode.InternalServerError)
                        }
                    }

                }

                if (update.notify) {
                    val statusTitle = when (newOverallState) {
                        GrantApplication.State.APPROVED -> "Approved"
                        GrantApplication.State.REJECTED -> "Rejected"
                        GrantApplication.State.CLOSED -> "Closed"
                        else -> {
                            return@forEach
                        }
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
                                    when (newOverallState) {
                                        GrantApplication.State.APPROVED -> Mail.GrantApplicationApproveMail(projectTitle)
                                        GrantApplication.State.REJECTED -> Mail.GrantApplicationRejectedMail(
                                            projectTitle
                                        )

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

    private suspend fun getCurrentRevision(
        session: AsyncDBConnection,
        applicationId: Long
    ): Int {
        return session
            .sendPreparedStatement(
                {
                    setParameter("app_id", applicationId)
                },
                """
                    select max(revision_number)::int as newest
                    from "grant".revisions
                    where application_id = :app_id
                """
            ).rows
            .singleOrNull()?.getInt(0) ?: throw RPCException("No Revision found", HttpStatusCode.NotFound)
    }

    suspend fun transferApplication(
        actorAndProject: ActorAndProject,
        request: BulkRequest<TransferApplicationRequest>
    ) {
        db.withSession(remapExceptions = true) { session ->
            request.items.forEach { req ->
                val currentRevision = getCurrentRevision(session, req.applicationId)
                session.sendPreparedStatement(
                    {
                        setParameter("username", actorAndProject.actor.safeUsername())
                        setParameter("id", req.applicationId)
                        setParameter(
                            "source_id",
                            actorAndProject.project ?: throw RPCException(
                                "Only possible from project",
                                HttpStatusCode.BadRequest
                            )
                        )
                        setParameter("target", req.transferToProjectId)
                        setParameter("newest_revision", currentRevision)
                        setParameter("revision_comment", req.revisionComment)
                    },
                    """
                    select "grant".transfer_application(:username, :id, :source_id, :target, :newest_revision, :revision_comment)
                """
                )
            }
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
                                apps.id,
                                r.revision_number,
                                apps,
                                r, 
                                array_remove(array_agg("grant".resource_request_to_json(request, pc)), null),
                                owner_project,
                                existing_project,
                                existing_project_pi.username,
                                apps.created_at
                            from
                                "grant".applications apps join
                                "grant".requested_resources request on apps.id = request.application_id join
                                "grant".revisions r on apps.id = r.application_id join
                                accounting.product_categories pc on request.product_category = pc.id join
                                "grant".forms f on apps.id = f.application_id and f.revision_number = r.revision_number join
                                project.projects owner_project on
                                    request.grant_giver = owner_project.id left join
                                project.projects existing_project on
                                    f.recipient_type = 'existing_project' and
                                    existing_project.id = f.recipient left join
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
                                        recipient_type = 'existing_project' and
                                        existing_project.id = :project and
                                        user_role_in_existing.username = :username
                                    ) or
                                    (
                                        (
                                            recipient_type = 'personal' or
                                            recipient_type = 'new_project'
                                        )
                                    )
                                ) and
                                (:show_active or apps.overall_state != 'IN_PROGRESS') and
                                (:show_inactive or apps.overall_state = 'IN_PROGRESS') and
                                :outgoing
                            group by
                                apps.*, r.*, existing_project.*, existing_project_pi.username, owner_project.*,
                                apps.created_at, apps.id, r.revision_number
                        ),
                        ingoing as (
                            select 
                                apps.id,
                                r.revision_number,
                                apps,
                                r,
                                array_remove(array_agg("grant".resource_request_to_json(request, pc)), null),
                                owner_project,
                                existing_project,
                                existing_project_pi.username,
                                apps.created_at
                            from
                                "grant".applications apps join
                                "grant".revisions r on apps.id = r.application_id join 
                                "grant".forms f on apps.id = f.application_id and f.revision_number = r.revision_number join
                                "grant".requested_resources request on apps.id = request.application_id join                               
                                project.project_members pm on
                                    pm.project_id = request.grant_giver and
                                    pm.username = :username and
                                    (pm.role = 'ADMIN' or pm.role = 'PI') join
                                accounting.product_categories pc on request.product_category = pc.id join
                                project.projects owner_project on
                                    owner_project.id = request.grant_giver left join
                                project.projects existing_project on
                                    f.recipient_type = 'existing_project' and
                                    existing_project.id = f.recipient left join
                                project.project_members existing_project_pi on
                                    existing_project_pi.role = 'PI' and
                                    existing_project_pi.project_id = existing_project.id
                            where
                                request.grant_giver = :project and
                                (:show_active or apps.overall_state != 'IN_PROGRESS') and
                                (:show_inactive or apps.overall_state = 'IN_PROGRESS') and
                                :ingoing
                            group by
                                apps.*, r.*, existing_project.*, existing_project_pi.username, owner_project.*,
                                apps.created_at, apps.id, r.revision_number
                        ),
                        all_applications as (
                            select distinct(id), created_at
                            from ingoing
                            union
                            select distinct(id), created_at
                            from outgoing
                        )
                        select "grant".application_to_json(id) 
                        from all_applications 
                        order by created_at desc, id;
                    """
                )
            },
            mapper = { _, rows ->
                rows.map {
                    defaultMapper.decodeFromString(it.getString(0)!!)
                }
            }
        )
    }

    private suspend fun onApplicationApprove(
        session: AsyncDBConnection,
        applicationId: Long,
        parentId: String?
    ) {
        session.sendPreparedStatement(
            {
                setParameter("id", applicationId)
                setParameter("parent", parentId)
            },
            """select "grant".approve_application(:id, :parent::text)""", debug = true
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
        ).rows
            .map {
                it.getString(0)!!
            }

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
