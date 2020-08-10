package dk.sdu.cloud.grant.services

import com.github.jasync.sql.db.RowData
import dk.sdu.cloud.accounting.api.*
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.calls.client.withProject
import dk.sdu.cloud.file.api.*
import dk.sdu.cloud.grant.api.Application
import dk.sdu.cloud.grant.api.ApplicationStatus
import dk.sdu.cloud.grant.api.CreateApplication
import dk.sdu.cloud.grant.api.GrantRecipient
import dk.sdu.cloud.grant.api.ResourceRequest
import dk.sdu.cloud.grant.utils.autoApproveTemplate
import dk.sdu.cloud.grant.utils.newIngoingApplicationTemplate
import dk.sdu.cloud.grant.utils.responseTemplate
import dk.sdu.cloud.grant.utils.updatedTemplate
import dk.sdu.cloud.project.api.CreateProjectRequest
import dk.sdu.cloud.project.api.Projects
import dk.sdu.cloud.service.*
import dk.sdu.cloud.service.db.async.*
import io.ktor.http.HttpStatusCode

object ApplicationTable : SQLTable("applications") {
    val id = long("id", notNull = true)
    val status = text("status", notNull = true)
    val resourcesOwnedBy = text("resources_owned_by", notNull = true)
    val requestedBy = text("requested_by", notNull = true)
    val grantRecipient = text("grant_recipient", notNull = true)
    val grantRecipientType = text("grant_recipient_type", notNull = true)
    val document = text("document", notNull = true)
    val createdAt = timestamp("created_at", notNull = true)
    val updatedAt = timestamp("updated_at", notNull = true)
}

object RequestedResourceTable : SQLTable("requested_resources") {
    val applicationId = long("application_id", notNull = true)
    val productCategory = text("product_category", notNull = true)
    val productProvider = text("product_provider", notNull = true)
    val creditsRequested = long("credits_requested", notNull = false)
    val quotaRequestedBytes = long("quota_requested_bytes", notNull = false)
}

class ApplicationService(
    private val projects: ProjectCache,
    private val settings: SettingsService,
    private val notifications: NotificationService,
    private val serviceClient: AuthenticatedClient
) {
    suspend fun submit(
        ctx: DBContext,
        actor: Actor.User,
        application: CreateApplication
    ): Long {
        val returnedId = ctx.withSession { session ->
            val settings = settings.fetchSettings(session, Actor.System, application.resourcesOwnedBy)
            if (!settings.allowRequestsFrom.any { it.matches(actor.principal) }) {
                throw RPCException(
                    "You are not allowed to submit applications to this project",
                    HttpStatusCode.Forbidden
                )
            }

            val id = session
                .sendPreparedStatement(
                    {
                        with(application) {
                            setParameter("status", ApplicationStatus.IN_PROGRESS.name)
                            setParameter("resources_owned_by", resourcesOwnedBy)
                            setParameter("requested_by", actor.safeUsername())
                            setParameter("document", document)

                            val grantRecipient = this.grantRecipient
                            setParameter(
                                "grant_recipient", when (grantRecipient) {
                                    is GrantRecipient.PersonalProject -> grantRecipient.username
                                    is GrantRecipient.ExistingProject -> grantRecipient.projectId
                                    is GrantRecipient.NewProject -> grantRecipient.projectTitle
                                }
                            )

                            setParameter(
                                "grant_recipient_type", when (grantRecipient) {
                                    is GrantRecipient.PersonalProject -> GrantRecipient.PERSONAL_TYPE
                                    is GrantRecipient.ExistingProject -> GrantRecipient.EXISTING_PROJECT_TYPE
                                    is GrantRecipient.NewProject -> GrantRecipient.NEW_PROJECT_TYPE
                                }
                            )
                        }
                    },
                    //language=sql
                    """
                        insert into "grant".applications(
                            status,
                            resources_owned_by,
                            requested_by,
                            grant_recipient,
                            grant_recipient_type,
                            document
                        ) values (
                            :status,
                            :resources_owned_by,
                            :requested_by,
                            :grant_recipient,
                            :grant_recipient_type,
                            :document
                        ) returning id
                    """
                )
                .rows
                .single()
                .getLong("id")!!

            insertResources(session, application.requestedResources, id)

            id
        }

        lateinit var returnedApplication: Application
        ctx.withSession { session ->
            returnedApplication = viewApplicationById(session, actor, returnedId).first
        }

        if (autoApproveApplication(ctx, actor, returnedApplication)) {
            // Notifications are sent by autoApprover
            return returnedId
        }

        notifications.notify(
            GrantNotification(
                returnedApplication,
                adminMessage = GrantNotificationMessage(
                    "New Grant Application",
                    "NEW_GRANT_APPLICATION",
                    message = { user, projectTitle ->
                        newIngoingApplicationTemplate(user, actor.safeUsername(), projectTitle)
                    }
                ),
                userMessage = null
            ),
            actor.safeUsername(),
            meta = mapOf("grantRecipient" to returnedApplication.grantRecipient, "appId" to returnedApplication.id)
        )

        return returnedId
    }

    private suspend fun autoApproveApplication(
        ctx: DBContext,
        actor: Actor.User,
        application: Application
    ): Boolean {
        val settings = settings.fetchSettings(ctx, Actor.System, application.resourcesOwnedBy)
        val matchesUserCriteria = settings.automaticApproval.from.any { it.matches(actor.principal) }
        if (settings.automaticApproval.maxResources.isEmpty()) return false
        if (!matchesUserCriteria) return false
        val matchesResources = application
            .requestedResources
            .all { requested ->
                settings.automaticApproval.maxResources.any { max ->
                    val maxCredits = max.creditsRequested
                    val maxQuota = max.quotaRequested
                    if (maxCredits != null) {
                        val creditsRequested = requested.creditsRequested
                        creditsRequested != null && creditsRequested <= maxCredits
                    } else {
                        require(maxQuota != null)
                        val quotaRequested = requested.quotaRequested
                        quotaRequested != null && quotaRequested <= maxQuota
                    }
                }
            }

        if (!matchesResources) return false

        try {
            ctx.withSession { session ->
                session
                    .sendPreparedStatement(
                        {
                            setParameter("status", ApplicationStatus.APPROVED.name)
                            setParameter("id", application.id)
                        },
                        """
                            update applications set status = :status where id = :id
                        """
                    )

                onApplicationApproved(
                    Actor.System,
                    application
                )
            }
        } catch (ex: Throwable) {
            log.warn("Failed to automatically approve application\n${ex.stackTraceToString()}")
            return false
        }

        notifications.notify(
            GrantNotification(
                application,
                adminMessage = GrantNotificationMessage(
                    "Grant Application for Subproject Automatically Approved",
                    GRANT_APP_RESPONSE,
                    message = { user, title ->
                        autoApproveTemplate(user, application.requestedBy, title)
                    }
                ),
                userMessage = GrantNotificationMessage(
                    "Grant Application Approved",
                    GRANT_APP_RESPONSE,
                    message = { user, title ->
                        responseTemplate(ApplicationStatus.APPROVED, user, "UCloud", title)
                    }
                )
            ),
            "_ucloud",
            mapOf("grantRecipient" to application.grantRecipient, "appId" to application.id)
        )
        return true
    }

    private suspend fun insertResources(
        ctx: DBContext,
        requestedResources: List<ResourceRequest>,
        id: Long
    ) {
        ctx.withSession { session ->
            requestedResources.forEach { request ->
                session.insert(RequestedResourceTable) {
                    set(RequestedResourceTable.applicationId, id)
                    set(RequestedResourceTable.productCategory, request.productCategory)
                    set(RequestedResourceTable.productProvider, request.productProvider)
                    set(RequestedResourceTable.creditsRequested, request.creditsRequested)
                    set(RequestedResourceTable.quotaRequestedBytes, request.quotaRequested)
                }
            }
        }
    }

    suspend fun updateApplication(
        ctx: DBContext,
        actor: Actor,
        id: Long,
        newDocument: String,
        newResources: List<ResourceRequest>
    ) {
        ctx.withSession { session ->
            val projectId = session
                .sendPreparedStatement(
                    { setParameter("id", id) },
                    """select resources_owned_by from "grant".applications where id = :id"""
                )
                .rows.singleOrNull()?.getString(0) ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound)

            val isProjectAdmin = projects.isAdminOfProject(projectId, actor)
            val success = session
                .sendPreparedStatement(
                    {
                        setParameter("requestedBy", actor.safeUsername())
                        setParameter("isProjectAdmin", isProjectAdmin)
                        setParameter("id", id)
                        setParameter("document", newDocument)
                    },

                    //language=sql
                    """
                        update applications 
                        set document = :document 
                        where 
                            id = :id and
                            (:isProjectAdmin or requested_by = :requestedBy)
                    """
                )
                .rowsAffected > 0L

            if (!success) throw RPCException.fromStatusCode(HttpStatusCode.NotFound)

            // At this point we are authorized to make changes

            session
                .sendPreparedStatement(
                    { setParameter("id", id) },
                    //language=sql
                    "delete from requested_resources where application_id = :id"
                )

            insertResources(session, newResources, id)
        }

        lateinit var application: Application
        ctx.withSession { session ->
            application = viewApplicationById(session, actor, id).first
        }
        notifications.notify(
            GrantNotification(
                application,
                GrantNotificationMessage(
                    "Grant Application Updated",
                    "GRANT_APPLICATION_UPDATED",
                    message = { user, projectTitle ->
                        updatedTemplate(projectTitle, user, actor.safeUsername())
                    }
                )
            ),
            actor.safeUsername(),
            meta = mapOf("grantRecipient" to application.grantRecipient, "appId" to application.id)
        )
    }

    suspend fun updateStatus(
        ctx: DBContext,
        actor: Actor,
        id: Long,
        newStatus: ApplicationStatus
    ) {
        require(newStatus != ApplicationStatus.IN_PROGRESS) { "New status can only be APPROVED, REJECTED or CLOSED!" }

        ctx.withSession { session ->
            val (updatedProjectId, requestedBy) = session
                .sendPreparedStatement(
                    {
                        setParameter("id", id)
                        setParameter("status", newStatus.name)
                    },
                    """
                        update "grant".applications 
                        set status = :status
                        where id = :id
                        returning resources_owned_by, requested_by
                    """
                )
                .rows
                .singleOrNull()
                ?.let { Pair(it.getString(0)!!, it.getString(1)!!) }
                ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound)

            if (!projects.isAdminOfParent(updatedProjectId, actor) && newStatus != ApplicationStatus.CLOSED) {
                throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
            }

            if (newStatus == ApplicationStatus.CLOSED && requestedBy != actor.safeUsername()) {
                throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
            }

            if (newStatus == ApplicationStatus.APPROVED) {
                onApplicationApproved(actor, viewApplicationById(session, actor, id).first)
            }
        }

        lateinit var application: Application
        ctx.withSession { session ->
            application = viewApplicationById(session, actor, id).first
        }
        val statusTitle = when (newStatus) {
            ApplicationStatus.APPROVED -> "Approved"
            ApplicationStatus.REJECTED -> "Rejected"
            ApplicationStatus.CLOSED -> "Closed"
            ApplicationStatus.IN_PROGRESS -> "In Progress"
        }
        notifications.notify(
            GrantNotification(
                application,
                GrantNotificationMessage(
                    "Grant Application $statusTitle",
                    GRANT_APP_RESPONSE,
                    message = { user, projectTitle ->
                        responseTemplate(
                            newStatus,
                            user,
                            actor.safeUsername(),
                            projectTitle
                        )
                    }
                )
            ),
            actor.safeUsername(),
            mapOf("grantRecipient" to application.grantRecipient, "appId" to application.id)
        )
    }

    private suspend fun onApplicationApproved(
        actor: Actor,
        application: Application
    ) {
        when (val grantRecipient = application.grantRecipient) {
            is GrantRecipient.PersonalProject -> {
                grantResourcesToProject(
                    application.resourcesOwnedBy,
                    application.requestedResources,
                    grantRecipient.username,
                    WalletOwnerType.USER,
                    serviceClient,
                    actor.safeUsername()
                )
            }

            is GrantRecipient.ExistingProject -> {
                grantResourcesToProject(
                    application.resourcesOwnedBy,
                    application.requestedResources,
                    grantRecipient.projectId,
                    WalletOwnerType.PROJECT,
                    serviceClient,
                    actor.safeUsername()
                )
            }

            is GrantRecipient.NewProject -> {
                val (newProjectId) = Projects.create.call(
                    CreateProjectRequest(
                        grantRecipient.projectTitle,
                        application.resourcesOwnedBy,
                        principalInvestigator = application.requestedBy
                    ),
                    serviceClient
                ).orThrow()

                grantResourcesToProject(
                    application.resourcesOwnedBy,
                    application.requestedResources,
                    newProjectId,
                    WalletOwnerType.PROJECT,
                    serviceClient,
                    actor.safeUsername()
                )
            }
        }
    }



    suspend fun listIngoingApplications(
        ctx: DBContext,
        actor: Actor,
        projectId: String,
        pagination: NormalizedPaginationRequest?
    ): Page<Application> {
        if (!projects.isAdminOfProject(projectId, actor)) {
            throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)
        }

        return ctx.withSession { session ->
            val itemsInTotal = if (pagination == null) {
                null
            } else {
                session
                    .sendPreparedStatement(
                        { setParameter("projectId", projectId) },

                        """
                            select count(id)::bigint from "grant".applications 
                            where resources_owned_by = :projectId and status = 'IN_PROGRESS'
                        """
                    ).rows.singleOrNull()?.getLong(0) ?: 0L
            }

            val items = session
                .sendPreparedStatement(
                    {
                        setParameter("projectId", projectId)
                        setParameter("o", pagination?.offset ?: 0)
                        setParameter("l", pagination?.itemsPerPage ?: Int.MAX_VALUE)
                    },

                    """
                        select *    
                        from "grant".applications a left outer join requested_resources r on (a.id = r.application_id)
                        where 
                            a.resources_owned_by = :projectId and 
                            a.status = 'IN_PROGRESS'
                        order by a.updated_at
                        limit :l
                        offset :o
                    """
                )
                .rows.toApplications()

            Page.forRequest(pagination, itemsInTotal?.toInt(), items.toList())
        }
    }

    suspend fun listOutgoingApplications(
        ctx: DBContext,
        actor: Actor,
        pagination: NormalizedPaginationRequest?
    ): Page<Application> {
        return ctx.withSession { session ->
            val itemsInTotal = if (pagination == null) {
                null
            } else {
                session
                    .sendPreparedStatement(
                        { setParameter("requestedBy", actor.safeUsername()) },

                        """
                            select count(id)::bigint from "grant".applications 
                            where requested_by = :requestedBy and status = 'IN_PROGRESS'
                        """
                    ).rows.singleOrNull()?.getLong(0) ?: 0L
            }

            val items = session
                .sendPreparedStatement(
                    {
                        setParameter("requestedBy", actor.safeUsername())
                        setParameter("o", pagination?.offset ?: 0)
                        setParameter("l", pagination?.itemsPerPage ?: Int.MAX_VALUE)
                    },

                    """
                        select *    
                        from "grant".applications a, requested_resources r
                        where 
                            a.requested_by = :requestedBy and 
                            a.status = 'IN_PROGRESS' and 
                            a.id = r.application_id
                        order by a.updated_at
                        limit :l
                        offset :o
                    """
                )
                .rows.toApplications()

            Page.forRequest(pagination, itemsInTotal?.toInt(), items.toList())
        }
    }

    suspend fun findActiveApplication(
        ctx: DBContext,
        actor: Actor,
        projectId: String
    ): Application? {
        return ctx.withSession { session ->
            session
                .sendPreparedStatement(
                    {
                        setParameter("projectId", projectId)
                        setParameter("requestedBy", actor.safeUsername())
                    },
                    """
                        select * 
                        from "grant".applications a, requested_resources r
                        where
                            a.resources_owned_by = :projectId and
                            a.requested_by = :requestedBy and
                            a.status = 'IN_PROGRESS' and
                            a.id = r.application_id
                    """
                )
                .rows.toApplications().singleOrNull()
        }
    }

    suspend fun viewApplicationById(ctx: DBContext, actor: Actor, id: Long): Pair<Application, Boolean> {
        return ctx.withSession { session ->
            val application = session
                .sendPreparedStatement(
                    { setParameter("id", id) },
                    """
                        select * 
                        from "grant".applications a left outer join "grant".requested_resources r on a.id = r.application_id
                        where a.id = :id
                    """
                )
                .rows.toApplications().singleOrNull() ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound)

            log.debug("Found application: $application")

            val isApprover = projects.isAdminOfProject(application.resourcesOwnedBy, actor)
            if (application.requestedBy != actor.safeUsername() && !isApprover) {
                throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)
            }

            Pair(application, isApprover)
        }
    }

    @OptIn(ExperimentalStdlibApi::class)
    private suspend fun Iterable<RowData>.toApplications(): Collection<Application> {
        return map {
            val resourcesOwnedBy = it.getField(ApplicationTable.resourcesOwnedBy)

            val grantRecipient = grantRecipientFromTable(
                it.getField(ApplicationTable.grantRecipient),
                it.getField(ApplicationTable.grantRecipientType)
            )

            val requestedBy = it.getField(ApplicationTable.requestedBy)

            Application(
                ApplicationStatus.valueOf(it.getField(ApplicationTable.status)),
                resourcesOwnedBy,
                requestedBy,
                grantRecipient,
                it.getField(ApplicationTable.document),
                buildList {
                    val productCategory = it.getFieldNullable(RequestedResourceTable.productCategory)
                    if (productCategory != null) {
                        add(
                            ResourceRequest(
                                it.getField(RequestedResourceTable.productCategory),
                                it.getField(RequestedResourceTable.productProvider),
                                it.getFieldNullable(RequestedResourceTable.creditsRequested),
                                it.getFieldNullable(RequestedResourceTable.quotaRequestedBytes)
                            )
                        )
                    }
                },
                it.getField(ApplicationTable.id),
                projects.ancestors.get(resourcesOwnedBy)?.last()?.title ?: resourcesOwnedBy,
                when (grantRecipient) {
                    is GrantRecipient.PersonalProject -> grantRecipient.username
                    is GrantRecipient.ExistingProject ->
                        projects.principalInvestigators.get(grantRecipient.projectId) ?: requestedBy
                    is GrantRecipient.NewProject -> requestedBy
                },
                when (grantRecipient) {
                    is GrantRecipient.PersonalProject -> grantRecipient.username
                    is GrantRecipient.ExistingProject ->
                        projects.ancestors.get(grantRecipient.projectId)?.last()?.title ?: grantRecipient.projectId
                    is GrantRecipient.NewProject -> grantRecipient.projectTitle
                },
                it.getField(ApplicationTable.createdAt).toDate().time,
                it.getField(ApplicationTable.updatedAt).toDate().time
            )
        }
            .groupingBy { it.id }
            .reduce { _, accumulator, element ->
                accumulator.copy(requestedResources = accumulator.requestedResources + element.requestedResources)
            }
            .values
    }

    private fun grantRecipientFromTable(id: String, type: String): GrantRecipient {
        return when (type) {
            GrantRecipient.PERSONAL_TYPE -> GrantRecipient.PersonalProject(id)
            GrantRecipient.EXISTING_PROJECT_TYPE -> GrantRecipient.ExistingProject(id)
            GrantRecipient.NEW_PROJECT_TYPE -> GrantRecipient.NewProject(id)
            else -> throw IllegalArgumentException("Unknown type")
        }
    }

    companion object : Loggable {
        override val log = logger()
        private const val GRANT_APP_RESPONSE = "GRANT_APPLICATION_RESPONSE"
    }
}

suspend fun grantResourcesToProject(
    sourceProject: String,
    resources: List<ResourceRequest>,
    targetWallet: String,
    targetWalletType: WalletOwnerType,
    serviceClient: AuthenticatedClient,
    initiatedBy: String = "_ucloud"
) {
    when (targetWalletType) {
        WalletOwnerType.PROJECT -> {
            Wallets.addToBalanceBulk.call(
                AddToBalanceBulkRequest(resources.mapNotNull { resource ->
                    val creditsRequested = resource.creditsRequested ?: return@mapNotNull null
                    val paysFor = ProductCategoryId(resource.productCategory, resource.productProvider)

                    AddToBalanceRequest(
                        Wallet(
                            targetWallet,
                            WalletOwnerType.PROJECT,
                            paysFor
                        ),
                        creditsRequested
                    )
                }),
                serviceClient
            ).orThrow()

            resources.forEach { resource ->
                val quotaRequested = resource.quotaRequested ?: return@forEach
                FileDescriptions.updateQuota.call(
                    UpdateQuotaRequest(
                        projectHomeDirectory(targetWallet),
                        quotaRequested,
                        additive = true
                    ),
                    serviceClient
                ).orThrow()
            }
        }

        WalletOwnerType.USER -> {
            val requests = resources.mapNotNull { resource ->
                val creditsRequested = resource.creditsRequested ?: return@mapNotNull null
                val paysFor = ProductCategoryId(resource.productCategory, resource.productProvider)

                SingleTransferRequest(
                    initiatedBy,
                    creditsRequested,
                    Wallet(
                        sourceProject,
                        WalletOwnerType.PROJECT,
                        paysFor
                    ),
                    Wallet(
                        targetWallet,
                        WalletOwnerType.USER,
                        paysFor
                    )
                )
            }

            Wallets.transferToPersonal.call(
                TransferToPersonalRequest(requests),
                serviceClient
            ).orThrow()

            resources.forEach { resource ->
                val quotaRequested = resource.quotaRequested ?: return@forEach
                FileDescriptions.transferQuota.call(
                    TransferQuotaRequest(
                        homeDirectory(targetWallet),
                        quotaRequested
                    ),
                    serviceClient.withProject(sourceProject)
                ).orThrow()
            }
        }
    }
}
