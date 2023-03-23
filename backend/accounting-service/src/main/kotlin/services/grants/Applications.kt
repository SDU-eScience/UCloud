package dk.sdu.cloud.accounting.services.grants

import dk.sdu.cloud.*
import dk.sdu.cloud.accounting.api.*
import dk.sdu.cloud.accounting.services.projects.v2.ProviderNotificationService
import dk.sdu.cloud.accounting.services.wallets.AccountingService
import dk.sdu.cloud.accounting.util.Providers
import dk.sdu.cloud.accounting.util.SimpleProviderCommunication
import dk.sdu.cloud.calls.*
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
    private val accounting: AccountingService
) {
    suspend fun retrieveProducts(
        actorAndProject: ActorAndProject,
        request: GrantsBrowseProductsRequest,
    ): List<Product> {
        return db.withSession(remapExceptions = true) { session ->
            val allowed = session.sendPreparedStatement(
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
                    select *
                    from permission_check
                """
            ).rows
                .singleOrNull()
                ?.getBoolean(0) ?: throw RPCException.fromStatusCode(
                HttpStatusCode.InternalServerError,
                "Permissions wrong"
            )

            val products = if (allowed) {
                val owner = WalletOwner.Project(request.projectId)
                val products = accounting.retrieveWalletsInternal(
                    ActorAndProject(Actor.System, null),
                    owner
                ).mapNotNull { wallet ->
                    val now = System.currentTimeMillis()
                    val activeAllocs = wallet.allocations.mapNotNull { alloc ->
                        if (alloc.startDate <= now && (alloc.endDate ?: Long.MAX_VALUE) > now) {
                            alloc
                        } else {
                            null
                        }
                    }
                    if (activeAllocs.isEmpty()) {
                        null
                    } else {
                        wallet.copy(allocations = activeAllocs)
                    }
                }.map {
                    it.paysFor
                }

                val results = session.sendPreparedStatement(
                    {
                        products.split {
                            into("providers") {it.provider}
                            into("category_names") { it.name}
                        }
                    },
                    """
                        with names_and_providers as (
                            select 
                                unnest(:providers::text[]) provider,
                                unnest(:category_names::text[]) category_name
                        )
                        select accounting.product_to_json(p, pc, null)
                        from
                            accounting.products p join
                            accounting.product_categories pc on p.category = pc.id join 
                            names_and_providers nap on pc.provider = nap.provider and pc.category = nap.category_name                        
                        order by pc.provider, pc.category
                    """.trimIndent()
                ).rows.map { defaultMapper.decodeFromString<Product>(it.getString(0)!!) }
                results
            } else {
                throw RPCException.fromStatusCode(HttpStatusCode.Forbidden, "Not allowed to make requests to grant")
            }

            return@withSession products
        }
    }

    suspend fun retrieveGrantApplication(
        applicationId: Long,
        actorAndProject: ActorAndProject,
        ctx: DBContext = db,
    ): GrantApplication {
        val application = ctx.withSession { session ->
            checkReadPermission(session, actorAndProject, applicationId)
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
    ): BulkResponse<FindByLongId> {
        request.items.forEach { createRequest ->
            val recipient = createRequest.document.recipient
            if (recipient is GrantApplication.Recipient.PersonalWorkspace && recipient.username != actorAndProject.actor.safeUsername()) {
                throw RPCException("Cannot request resources for someone else", HttpStatusCode.Forbidden)
            }
            if (recipient is GrantApplication.Recipient.NewProject && createRequest.document.parentProjectId == null) {
                throw RPCException("Missing parent ID when creating new project", HttpStatusCode.BadRequest)
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
                        setParameter("grant_recipient", recipientToSqlField(recipient))
                        setParameter("grant_recipient_type", recipientToSqlType(recipient))
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

                insertDocument(session, actorAndProject, applicationId, createRequest.document, true)

                // TODO(Dan): This code is seemingly duplicated later. We should only have one branch for this.
                val (allApproved, grantGivers) = retrieveGrantGiversStates(session, applicationId)

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
                    onApplicationApprove(
                        session,
                        applicationId,
                        createRequest.document.parentProjectId,
                        actorAndProject
                    )
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

        return BulkResponse(results.map { FindByLongId(it.first) }.toSet().toList())
    }

    private suspend fun retrieveGrantGiversStates(
        session: AsyncDBConnection,
        applicationId: Long
    ): Pair<Boolean, List<GrantApplication.GrantGiverApprovalState>> {
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
        val allApproved = grantGiverApprovalStates.all { it.state == GrantApplication.State.APPROVED }

        return Pair(allApproved, grantGiverApprovalStates)
    }

    private suspend fun insertDocument(
        session: AsyncDBConnection,
        actorAndProject: ActorAndProject,
        applicationId: Long,
        document: GrantApplication.Document,
        isInitialSubmission: Boolean = false,
    ) {
        var revisionNumber = 0
        checkReferenceID(document.referenceId)
        val recipient = document.recipient
        val allocationRequests = if (!isInitialSubmission) {
            val application = retrieveGrantApplication(applicationId, actorAndProject, session)

            revisionNumber = session.sendPreparedStatement(
                {
                    setParameter("app_id", applicationId)
                    setParameter("comment", document.revisionComment)
                    setParameter("username", actorAndProject.actor.safeUsername())
                },
                """
                    insert into "grant".revisions (application_id, created_at, updated_by, revision_number, revision_comment)
                    select :app_id, now(), :username, max(r.revision_number) + 1, :comment
                    from "grant".revisions r
                    where r.application_id = :app_id
                    returning revision_number
                """
            ).rows.single().getInt(0)!!

            val canChangeAll = when (recipient) {
                is GrantApplication.Recipient.NewProject ->
                    application.createdBy == actorAndProject.actor.safeUsername()

                is GrantApplication.Recipient.ExistingProject ->
                    recipient.id == actorAndProject.project

                is GrantApplication.Recipient.PersonalWorkspace ->
                    application.createdBy == actorAndProject.actor.safeUsername()
            }

            if (canChangeAll) {
                document.allocationRequests
            } else {
                val newRequests = document.allocationRequests
                val oldRequests = application.currentRevision.document.allocationRequests
                val newRequestsAccountedFor = mutableListOf<GrantApplication.AllocationRequest>()
                //If you, as the grant giver, attempt to update one of your allocations requests, we leave those
                //requests that are not associated with your project and updates the values of those that were in
                // the old grant application.
                val updatedRequests = oldRequests.mapNotNull { oldReq ->
                    if (oldReq.grantGiver != actorAndProject.project) {
                        oldReq
                    } else {
                        val updatedRequest = newRequests.find { newReq ->
                            newReq.category == oldReq.category && newReq.provider == oldReq.provider
                        }
                        if (updatedRequest != null) {
                            newRequestsAccountedFor.add(updatedRequest)
                        }
                        updatedRequest
                    }
                }

                val remainingNewRequests = newRequests.mapNotNull { newReq ->
                    if (!newRequestsAccountedFor.contains(newReq)) {
                        newReq
                    } else null
                }.toMutableList()

                remainingNewRequests.addAll(updatedRequests)
                remainingNewRequests
            }
        } else {
            // No additional checks needed on initial submission
            document.allocationRequests
        }

        if (allocationRequests.isEmpty()) {
            throw RPCException.fromStatusCode(
                HttpStatusCode.BadRequest,
                "Applications without resource requests not allowed"
            )
        }
        val validRequests = allocationRequests.map{ req ->
            accounting.retrieveAllocationsInternal(
                ActorAndProject(Actor.System, null),
                WalletOwner.Project(req.grantGiver),
                ProductCategoryId(req.category, req.provider)
            ).isNotEmpty()
        }.filter { it }

        val requestIsValid = validRequests.size == allocationRequests.size
        if (!requestIsValid) {
            throw RPCException.fromStatusCode(HttpStatusCode.BadRequest, "Allocations does not match products")
        }

        session.sendPreparedStatement(
            {
                setParameter("id", applicationId)
                setParameter("grant_applier", actorAndProject.actor.username)
                setParameter("revision_number", revisionNumber)
                allocationRequests.split {
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
                    req.application_id, req.credits_requested, null, pc.id, req.source_allocation, to_timestamp(req.start_date / 1000), to_timestamp(req.end_date / 1000), req.grant_giver, :revision_number
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
                    setParameter("grant_recipient", recipientToSqlField(document.recipient))
                    setParameter("grant_recipient_type", recipientToSqlType(document.recipient))
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

    private fun recipientToSqlType(recipient: GrantApplication.Recipient) = when (recipient) {
        is GrantApplication.Recipient.ExistingProject -> GrantApplication.Recipient.EXISTING_PROJECT_TYPE
        is GrantApplication.Recipient.NewProject -> GrantApplication.Recipient.NEW_PROJECT_TYPE
        is GrantApplication.Recipient.PersonalWorkspace -> GrantApplication.Recipient.PERSONAL_TYPE
    }

    private fun recipientToSqlField(recipient: GrantApplication.Recipient) = when (recipient) {
        is GrantApplication.Recipient.ExistingProject -> recipient.id
        is GrantApplication.Recipient.NewProject -> recipient.title
        is GrantApplication.Recipient.PersonalWorkspace -> recipient.username
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

    private suspend fun checkReadPermission(
        session: AsyncDBConnection,
        actorAndProject: ActorAndProject,
        appId: Long
    ) {
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
                        (
                            rr.grant_giver = pm.project_id and
                            (pm.role = 'ADMIN' or pm.role = 'PI') and
                            pm.username = :username
                        ) or 
                        (
                            (f.recipient_type = 'existing_project') and
                            pm.project_id = f.recipient and
                            (pm.role = 'ADMIN' or pm.role = 'PI') and
                            pm.username = :username
                        ) or 
                        (
                            f.recipient_type = 'personal' and 
                            f.recipient = :username
                        ) or
                        (
                            f.recipient_type = 'new_project' and 
                            app.requested_by = :username
                        )
                where
                    f.application_id = :id;
            """
        ).rows.size > 0L

        if (!permission) {
            throw RPCException(
                "Unable to retrieve application. Has it already been closed?",
                HttpStatusCode.BadRequest
            )
        }
    }

    suspend fun editApplication(
        actorAndProject: ActorAndProject,
        requests: BulkRequest<EditApplicationRequest>,
        session: DBContext? = null
    ) {
        db.withSession(remapExceptions = true) { session ->
            requests.items.forEach { request ->
                // NOTE(Dan): Permission check is done by insertDocument
                insertDocument(session, actorAndProject, request.applicationId, request.document)

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
                            update "grant".applications 
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

                if (allocations.isEmpty()) {
                    throw RPCException(
                        "At least one resource must be requested to approve the application.",
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

                checkReadPermission(session, actorAndProject, id)

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
                            """
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
                        onApplicationApprove(session, update.applicationId, parentId, actorAndProject)
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
                                    """
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
                        
                        with all_applications as (
                            select id, created_at
                            from (
                                select distinct
                                    apps.id,
                                    apps.created_at,
                                    rank() over (partition by apps.id order by r.revision_number desc) as rank
                                from
                                    "grant".applications apps join
                                    
                                    "grant".forms f on apps.id = f.application_id join
                                    
                                    "grant".revisions r on apps.id = r.application_id left join
                                    
                                    "grant".requested_resources resource on 
                                        apps.id = resource.application_id and 
                                        resource.revision_number = r.revision_number and
                                        resource.grant_giver = :project left join
                                        
                                    project.projects existing_project on
                                        f.recipient_type = 'existing_project' and
                                        existing_project.id = f.recipient left join
                                        
                                    project.project_members user_role_in_existing on
                                        existing_project.id = user_role_in_existing.project_id and
                                        user_role_in_existing.username = :username left join
                                        
                                    project.project_members user_role_in_grant_giver on
                                        user_role_in_grant_giver.project_id = resource.grant_giver and
                                        user_role_in_grant_giver.username = :username
                                where
                                    (
                                        (
                                            :outgoing and (
                                                requested_by = :username and
                                                (
                                                    (
                                                        recipient_type = 'existing_project' and
                                                        existing_project.id = :project and
                                                        (
                                                            user_role_in_existing.role = 'PI' or
                                                            user_role_in_existing.role = 'ADMIN'
                                                        )
                                                    ) or
                                                    (
                                                        :project::text is null and
                                                        (
                                                            recipient_type = 'personal' or
                                                            recipient_type = 'new_project'
                                                        )
                                                    )
                                                )
                                            )
                                        ) or
                                        (
                                            :ingoing and (
                                                user_role_in_grant_giver.role = 'PI' or
                                                user_role_in_grant_giver.role = 'ADMIN'
                                            )
                                        )
                                    ) and
                                    (:show_active or apps.overall_state != 'IN_PROGRESS') and
                                    (:show_inactive or apps.overall_state = 'IN_PROGRESS')   
                            ) t
                            where
                                t.rank = 1
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
        parentId: String?,
        actorAndProject: ActorAndProject
    ) {
        val application = retrieveGrantApplication(applicationId, actorAndProject, session)

        //creating project and PI if newProject and returning workspaceID
        val (workspaceId, type) = when (application.currentRevision.document.recipient) {
            is GrantApplication.Recipient.NewProject -> {
                val recipient = application.currentRevision.document.recipient as GrantApplication.Recipient.NewProject
                val createdProject = session.sendPreparedStatement(
                    {
                        setParameter("parent_id", parentId)
                        setParameter("pi", application.createdBy)
                        setParameter("title", recipient.title)
                    },
                    """ 
                    with created_project as (
                        insert into project.projects (id, created_at, modified_at, title, archived, parent, dmp, subprojects_renameable)
                        select uuid_generate_v4()::text, now(), now(), :title, false, :parent_id, null, false
                        returning id
                    ),
                    created_user as (
                        insert into project.project_members (created_at, modified_at, role, username, project_id)
                        select now(), now(), 'PI', :pi, id
                        from created_project
                    )
                    select * from created_project
                """.trimIndent()
                ).rows
                    .singleOrNull()
                    ?.getString(0)
                    ?: throw RPCException.fromStatusCode(
                        HttpStatusCode.InternalServerError,
                        "Error in creating project and PI"
                    )

                projectNotifications.notifyChange(listOf(createdProject), session)

                Pair(createdProject, GrantApplication.Recipient.NewProject)

            }

            is GrantApplication.Recipient.ExistingProject -> {
                val recipient =
                    application.currentRevision.document.recipient as GrantApplication.Recipient.ExistingProject
                Pair(recipient.id, GrantApplication.Recipient.ExistingProject)
            }

            is GrantApplication.Recipient.PersonalWorkspace -> {
                val recipient =
                    application.currentRevision.document.recipient as GrantApplication.Recipient.PersonalWorkspace
                Pair(recipient.username, GrantApplication.Recipient.PersonalWorkspace)
            }
        }

        val requestItems = application.currentRevision.document.allocationRequests.map {
            if (it.sourceAllocation == null ) throw RPCException.fromStatusCode(HttpStatusCode.BadRequest, "Source Allocations not chosen")
            DepositToWalletRequestItem(
                recipient = if (type == GrantApplication.Recipient.PersonalWorkspace) WalletOwner.User(workspaceId) else WalletOwner.Project(
                    workspaceId
                ),
                sourceAllocation = it.sourceAllocation.toString(),
                amount = it.balanceRequested!!,
                description = "Granted In $applicationId",
                startDate = it.period.start,
                endDate = it.period.end,
            )
        }

        accounting.deposit(actorAndProject, bulkRequestOf(requestItems))

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
