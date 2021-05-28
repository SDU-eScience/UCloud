package dk.sdu.cloud.accounting.services.grants

import com.github.jasync.sql.db.RowData
import dk.sdu.cloud.Actor
import dk.sdu.cloud.accounting.api.*
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.*
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.grant.api.Application
import dk.sdu.cloud.grant.api.ApplicationStatus
import dk.sdu.cloud.grant.api.CreateApplication
import dk.sdu.cloud.grant.api.GrantApplicationFilter
import dk.sdu.cloud.grant.api.GrantRecipient
import dk.sdu.cloud.grant.api.ResourceRequest
import dk.sdu.cloud.grant.api.UserCriteria
import dk.sdu.cloud.mail.api.*
import dk.sdu.cloud.mail.api.MailDescriptions
import dk.sdu.cloud.mail.api.SendBulkRequest
import dk.sdu.cloud.mail.api.SendRequest
import dk.sdu.cloud.offset
import dk.sdu.cloud.project.api.*
import dk.sdu.cloud.safeUsername
import dk.sdu.cloud.service.*
import dk.sdu.cloud.service.db.async.*
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement
import java.util.*

object ApplicationTable : SQLTable("grant.applications") {
    val id = long("id", notNull = true)
    val status = text("status", notNull = true)
    val resourcesOwnedBy = text("resources_owned_by", notNull = true)
    val requestedBy = text("requested_by", notNull = true)
    val grantRecipient = text("grant_recipient", notNull = true)
    val grantRecipientType = text("grant_recipient_type", notNull = true)
    val document = text("document", notNull = true)
    val createdAt = timestamp("created_at", notNull = true)
    val updatedAt = timestamp("updated_at", notNull = true)
    val statusChangedBy = text("status_changed_by")
}

object RequestedResourceTable : SQLTable("grant.requested_resources") {
    val applicationId = long("application_id", notNull = true)
    val productCategory = text("product_category", notNull = true)
    val productProvider = text("product_provider", notNull = true)
    val creditsRequested = long("credits_requested", notNull = false)
    val quotaRequestedBytes = long("quota_requested_bytes", notNull = false)
}

class GrantApplicationService(
    private val projects: ProjectCache,
    private val settings: GrantSettingsService,
    private val notifications: GrantNotificationService,
    private val serviceClient: AuthenticatedClient
) {
    private val productCacheByProvider = SimpleCache<String, List<Product>> {
        Products.retrieveAllFromProvider.call(
            RetrieveAllFromProviderRequest(it),
            serviceClient
        ).orNull()
    }

    suspend fun retrieveProducts(
        ctx: DBContext,
        actor: Actor.User,
        resourcesOwnedBy: String,
        grantRecipient: GrantRecipient,
        showHidden: Boolean
    ): List<Product> {
        verifyCanApplyTo(ctx, resourcesOwnedBy, actor, grantRecipient, false)

        val balance = Wallets.retrieveBalance.call(
            RetrieveBalanceRequest(resourcesOwnedBy, WalletOwnerType.PROJECT, false, showHidden),
            serviceClient
        ).orThrow()

        return balance.wallets.flatMap { wb ->
            val allProducts = productCacheByProvider.get(wb.wallet.paysFor.provider) ?: emptyList()
            allProducts.filter { it.category.id == wb.wallet.paysFor.id }
        }
    }

    suspend fun submit(
        ctx: DBContext,
        actor: Actor.User,
        application: CreateApplication
    ): Long {
        val returnedId = ctx.withSession { session ->
            verifyCanApplyTo(session, application.resourcesOwnedBy, actor, application.grantRecipient, true)

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
                                    else -> error("unknown grant recipient")
                                }
                            )

                            setParameter(
                                "grant_recipient_type", when (grantRecipient) {
                                    is GrantRecipient.PersonalProject -> GrantRecipient.PERSONAL_TYPE
                                    is GrantRecipient.ExistingProject -> GrantRecipient.EXISTING_PROJECT_TYPE
                                    is GrantRecipient.NewProject -> GrantRecipient.NEW_PROJECT_TYPE
                                    else -> error("unknown grant recipient")
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
                .get("id")?.let { (it as Number).toLong() }!!

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
                adminMessage = AdminGrantNotificationMessage(
                    { title -> "New grant application to $title" },
                    "NEW_GRANT_APPLICATION",
                    Mail.NewGrantApplicationMail(
                        actor.safeUsername(),
                        returnedApplication.grantRecipientTitle
                    )
                ),
                userMessage = null
            ),
            actor.safeUsername(),
            meta = JsonObject(
                mapOf(
                    "grantRecipient" to defaultMapper.encodeToJsonElement(returnedApplication.grantRecipient),
                    "appId" to JsonPrimitive(returnedApplication.id),
                )
            )
        )

        return returnedId
    }

    private suspend fun verifyCanApplyTo(
        ctx: DBContext,
        resourcesOwnedBy: String,
        actor: Actor.User,
        recipient: GrantRecipient,
        isApplying: Boolean,
    ) {
        ctx.withSession { session ->
            log.debug("verifyCanApplyTo($resourcesOwnedBy, $actor, $recipient, $isApplying)")
            if (isApplying) {
                if (recipient is GrantRecipient.PersonalProject) {
                    if (recipient.username != actor.username) {
                        throw RPCException(
                            "You cannot submit an application on behalf of someone else",
                            HttpStatusCode.Forbidden
                        )
                    }
                }
            }

            if (!isApplying) {
                val isAdminInTargetProject = Projects.viewMemberInProject.call(
                    ViewMemberInProjectRequest(resourcesOwnedBy, actor.username),
                    serviceClient
                ).throwIfInternal().orNull()?.member?.role?.isAdmin() == true

                if (isAdminInTargetProject) return@withSession
            }

            val recipientProjectId = when (recipient) {
                is GrantRecipient.PersonalProject -> null
                is GrantRecipient.ExistingProject -> recipient.projectId
                is GrantRecipient.NewProject -> null
                else -> error("unknown grant recipient")
            }

            if (recipientProjectId != null) {
                val isAdminInRecipientProject = Projects.viewMemberInProject.call(
                    ViewMemberInProjectRequest(recipientProjectId, actor.username),
                    serviceClient
                ).throwIfInternal().orNull()?.member?.role?.isAdmin() == true

                if (!isAdminInRecipientProject) {
                    log.debug("Deny: 1")
                    throw RPCException("You are not allowed to submit to this project", HttpStatusCode.Forbidden)
                }
            }

            // The parent project if that is what we are applying to
            val parentProjectOrNull =
                if (recipientProjectId != null) {
                    Projects.lookupById.call(
                        LookupByIdRequest(recipientProjectId),
                        serviceClient
                    ).orThrow().parent
                } else {
                    null
                }

            // NOTE(Dan): Not applying to a parent project (of which we are an admin of the child project)
            if (parentProjectOrNull == null || resourcesOwnedBy != parentProjectOrNull) {
                val settings = settings.fetchSettings(session, Actor.System, resourcesOwnedBy)
                val isAllowed = settings.allowRequestsFrom.any { it.matches(actor.principal) }
                val emailIsBlacklisted = actor.principal.email != null &&
                    settings.excludeRequestsFrom.any {
                        actor.principal.email!!.endsWith((it as UserCriteria.EmailDomain).domain)
                    }

                if (!isAllowed || emailIsBlacklisted) {
                    log.debug("Deny: 3")
                    throw RPCException(
                        "You are not allowed to submit applications to this project",
                        HttpStatusCode.Forbidden
                    )
                }
            }
        }
    }

    private suspend fun autoApproveApplication(
        ctx: DBContext,
        actor: Actor.User,
        application: Application
    ): Boolean {
        val settings = settings.fetchSettings(ctx, Actor.System, application.resourcesOwnedBy)
        if (actor.principal.email != null && settings.excludeRequestsFrom.any {
                it as UserCriteria.EmailDomain
                actor.principal.email!!.endsWith(it.domain)
            }) {
            throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)
        }
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
                            setParameter("changedBy", "automatic-approval")
                        },
                        """
                            update "grant".applications 
                            set status = :status, status_changed_by = :changedBy
                            where id = :id
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
                adminMessage = AdminGrantNotificationMessage(
                    { "Grant application for subproject automatically approved" },
                    GRANT_APP_RESPONSE,
                    Mail.GrantAppAutoApproveToAdminsMail(
                        application.requestedBy,
                        application.resourcesOwnedByTitle
                    )
                ),
                userMessage = UserGrantNotificationMessage(
                    { "Grant application approved" },
                    GRANT_APP_RESPONSE,
                    Mail.GrantApplicationApproveMail(
                        application.resourcesOwnedByTitle
                    ),
                    application.requestedBy
                )
            ),
            "_ucloud",
            JsonObject(
                mapOf(
                    "grantRecipient" to defaultMapper.encodeToJsonElement(application.grantRecipient),
                    "appId" to JsonPrimitive(application.id),
                )
            )
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
            val (requestedBy, status) = session
                .sendPreparedStatement(
                    {
                        setParameter("username", actor.safeUsername())
                        setParameter("id", id)
                    },
                    """select requested_by, status from "grant".my_applications(:username, true, true) where id = :id"""
                )
                .rows.singleOrNull()?.let { Pair(it.getString(0)!!, ApplicationStatus.valueOf(it.getString(1)!!)) }
                ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound)

            if (status != ApplicationStatus.IN_PROGRESS) {
                throw RPCException("Cannot update a closed application", HttpStatusCode.BadRequest)
            }

            val isCreator = requestedBy == actor.safeUsername()

            if (isCreator) {
                session
                    .sendPreparedStatement(
                        {
                            setParameter("id", id)
                            setParameter("document", newDocument)
                        },

                        """
                            update "grant".applications
                            set document = :document
                            where
                                id = :id and
                                (:isProjectAdmin or requested_by = :requestedBy)
                        """
                    )
            }

            session
                .sendPreparedStatement(
                    { setParameter("id", id) },
                    "delete from \"grant\".requested_resources where application_id = :id"
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
                adminMessage =
                AdminGrantNotificationMessage(
                    { "Grant application updated" },
                    "GRANT_APPLICATION_UPDATED",
                    Mail.GrantApplicationUpdatedMailToAdmins(
                        application.resourcesOwnedByTitle,
                        application.requestedBy,
                        application.grantRecipientTitle
                    )
                ),
                userMessage =
                UserGrantNotificationMessage(
                    { "Grant application updated" },
                    "GRANT_APPLICATION_UPDATED",
                    Mail.GrantApplicationUpdatedMail(
                        application.resourcesOwnedByTitle,
                        application.requestedBy
                    ),
                    application.requestedBy
                )
            ),
            actor.safeUsername(),
            meta = JsonObject(
                mapOf(
                    "grantRecipient" to defaultMapper.encodeToJsonElement(application.grantRecipient),
                    "appId" to JsonPrimitive(application.id),
                )
            )
        )
    }

    suspend fun updateStatus(
        ctx: DBContext,
        actor: Actor,
        id: Long,
        newStatus: ApplicationStatus,
        notifyChange: Boolean? = true
    ) {
        require(newStatus != ApplicationStatus.IN_PROGRESS) { "New status can only be APPROVED, REJECTED or CLOSED!" }

        ctx.withSession { session ->
            val didUpdate = session
                .sendPreparedStatement(
                    {
                        setParameter("status", newStatus.name)
                        setParameter("username", actor.safeUsername())
                        setParameter("id", id)
                    },
                    """
                        update "grant".applications
                        set
                            status = :status,
                            status_changed_by = :changedBy
                        where
                            id in (
                                select id
                                from "grant".my_applications(:username, :status != 'CLOSED', :status = 'CLOSED')
                                where
                                    id = :id and
                                    status = 'IN_PROGRESS'
                            )
                    """
                )
                .rowsAffected > 0L

            if (!didUpdate) throw RPCException("Could not update grant application", HttpStatusCode.BadRequest)

            if (newStatus == ApplicationStatus.APPROVED) {
                onApplicationApproved(actor, viewApplicationById(session, actor, id).first)
            }
        }
        if (notifyChange == true) {
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
                    adminMessage =
                    AdminGrantNotificationMessage(
                        { "Grant application updated ($statusTitle)" },
                        GRANT_APP_RESPONSE,
                        Mail.GrantApplicationStatusChangedToAdmin(
                            newStatus.name,
                            application.resourcesOwnedByTitle,
                            application.requestedBy,
                            application.grantRecipientTitle
                        )
                    ),
                    userMessage =
                    UserGrantNotificationMessage(
                        { "Grant application updated ($statusTitle)" },
                        GRANT_APP_RESPONSE,
                        when (newStatus) {
                            ApplicationStatus.APPROVED -> Mail.GrantApplicationApproveMail(application.resourcesOwnedByTitle)
                            ApplicationStatus.REJECTED -> Mail.GrantApplicationRejectedMail(application.resourcesOwnedByTitle)
                            ApplicationStatus.CLOSED -> Mail.GrantApplicationWithdrawnMail(
                                application.resourcesOwnedByTitle,
                                actor.safeUsername()
                            )
                            else -> throw IllegalStateException()
                        },
                        application.requestedBy
                    )
                ),
                actor.safeUsername(),
                JsonObject(
                    mapOf(
                        "grantRecipient" to defaultMapper.encodeToJsonElement(application.grantRecipient),
                        "appId" to JsonPrimitive(application.id),
                    )
                )
            )
        }
    }

    suspend fun transferApplication(
        ctx: DBContext,
        actor: Actor,
        currentProject: String?,
        applicationId: Long,
        transferToProjectId: String
    ) {
        ctx.withSession(remapExceptions = true) { session ->
            val mailsToSend = session
                .sendPreparedStatement(
                    {
                        setParameter("username", actor.safeUsername())
                        setParameter("id", applicationId)
                        setParameter("target", transferToProjectId)
                    },
                    """
                        select source_title, destination_title, recipient_title, user_to_notify
                        from "grant".transfer_application(:username, :id, :target)
                    """
                )
                .rows.map {
                    SendRequest(
                        it.getString(3)!!,
                        Mail.TransferApplicationMail(
                            it.getString(0)!!,
                            it.getString(1)!!,
                            it.getString(2)!!
                        )
                    )
                }

            MailDescriptions.sendBulk.call(SendBulkRequest(mailsToSend), serviceClient)
        }
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
                    actor.safeUsername(),
                    TransactionType.TRANSFERRED_TO_PERSONAL
                )
            }

            is GrantRecipient.ExistingProject -> {
                grantResourcesToProject(
                    application.resourcesOwnedBy,
                    application.requestedResources,
                    grantRecipient.projectId,
                    WalletOwnerType.PROJECT,
                    serviceClient,
                    actor.safeUsername(),
                    TransactionType.TRANSFERRED_TO_PROJECT
                )
            }

            is GrantRecipient.NewProject -> {
                //Check that grant receiver has enough resources before creating project
                checkBalance(
                    application.requestedResources,
                    application.resourcesOwnedBy,
                    serviceClient,
                    TransactionType.TRANSFERRED_TO_PROJECT
                )

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
                    actor.safeUsername(),
                    TransactionType.TRANSFERRED_TO_PROJECT
                )
            }
        }
    }

    suspend fun listIngoingApplications(
        ctx: DBContext,
        actor: Actor,
        projectId: String,
        pagination: NormalizedPaginationRequest?,
        filter: GrantApplicationFilter
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
                        {
                            setParameter("projectId", projectId)
                            setParameter("statusFilter", when (filter) {
                                GrantApplicationFilter.SHOW_ALL -> ApplicationStatus.values().toList()
                                GrantApplicationFilter.ACTIVE -> listOf(ApplicationStatus.IN_PROGRESS)
                                GrantApplicationFilter.INACTIVE -> listOf(
                                    ApplicationStatus.CLOSED,
                                    ApplicationStatus.APPROVED
                                )
                            }.map { it.name })
                        },

                        """
                            select count(id)::bigint from "grant".applications
                            where resources_owned_by = :projectId and status in (select unnest(:statusFilter::text[]))
                        """
                    ).rows.singleOrNull()?.getLong(0) ?: 0L
            }

            val items = session
                .sendPreparedStatement(
                    {
                        setParameter("projectId", projectId)
                        setParameter("o", pagination?.offset ?: 0)
                        setParameter("l", pagination?.itemsPerPage ?: Int.MAX_VALUE)
                        setParameter("statusFilter", when (filter) {
                            GrantApplicationFilter.SHOW_ALL -> ApplicationStatus.values().toList()
                            GrantApplicationFilter.ACTIVE -> listOf(ApplicationStatus.IN_PROGRESS)
                            GrantApplicationFilter.INACTIVE -> listOf(
                                ApplicationStatus.CLOSED,
                                ApplicationStatus.APPROVED
                            )
                        }.map { it.name })
                    },

                    """
                        select *
                        from "grant".applications a left outer join "grant".requested_resources r on (a.id = r.application_id)
                        where
                            a.resources_owned_by = :projectId and
                            a.status in (select unnest(:statusFilter::text[]))
                        order by a.updated_at desc
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
        pagination: NormalizedPaginationRequest?,
        filter: GrantApplicationFilter
    ): Page<Application> {
        return ctx.withSession { session ->
            val itemsInTotal = if (pagination == null) {
                null
            } else {
                session
                    .sendPreparedStatement(
                        {
                            setParameter("requestedBy", actor.safeUsername())
                            setParameter("statusFilter", when (filter) {
                                GrantApplicationFilter.SHOW_ALL -> ApplicationStatus.values().toList()
                                GrantApplicationFilter.ACTIVE -> listOf(ApplicationStatus.IN_PROGRESS)
                                GrantApplicationFilter.INACTIVE -> listOf(
                                    ApplicationStatus.CLOSED,
                                    ApplicationStatus.APPROVED
                                )
                            }.map { it.name })
                        },

                        """
                            select count(id)::bigint from "grant".applications
                            where
                                requested_by = :requestedBy and
                                status in (select unnest(:statusFilter::text[]))
                        """
                    ).rows.singleOrNull()?.getLong(0) ?: 0L
            }

            val items = session
                .sendPreparedStatement(
                    {
                        setParameter("requestedBy", actor.safeUsername())
                        setParameter("o", pagination?.offset ?: 0)
                        setParameter("l", pagination?.itemsPerPage ?: Int.MAX_VALUE)
                        setParameter("statusFilter", when (filter) {
                            GrantApplicationFilter.SHOW_ALL -> ApplicationStatus.values().toList()
                            GrantApplicationFilter.ACTIVE -> listOf(ApplicationStatus.IN_PROGRESS)
                            GrantApplicationFilter.INACTIVE -> listOf(
                                ApplicationStatus.CLOSED,
                                ApplicationStatus.APPROVED,
                                ApplicationStatus.REJECTED
                            )
                        }.map { it.name })
                    },

                    """
                        select *
                        from "grant".applications a, "grant".requested_resources r
                        where
                            a.requested_by = :requestedBy and
                            a.status in (select unnest(:statusFilter::text[])) and
                            a.id = r.application_id
                        order by a.updated_at desc
                        limit :l
                        offset :o
                    """
                )
                .rows.toApplications()

            Page.forRequest(pagination, itemsInTotal?.toInt(), items.toList())
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
                    else -> error("unknown grant recipient")
                },
                when (grantRecipient) {
                    is GrantRecipient.PersonalProject -> grantRecipient.username
                    is GrantRecipient.ExistingProject ->
                        projects.ancestors.get(grantRecipient.projectId)?.last()?.title ?: grantRecipient.projectId
                    is GrantRecipient.NewProject -> grantRecipient.projectTitle
                    else -> error("unknown grant recipient")
                },
                it.getField(ApplicationTable.createdAt).toDate().time,
                it.getField(ApplicationTable.updatedAt).toDate().time,
                it.getField(ApplicationTable.statusChangedBy)
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

suspend fun checkBalance(
    resources: List<ResourceRequest>,
    projectId: String,
    serviceClient: AuthenticatedClient,
    transactionType: TransactionType
) {
    val limitCheckId = UUID.randomUUID().toString()
    val later = Time.now() + (1000 * 60 * 60)
    Wallets.reserveCreditsBulk.call(
        ReserveCreditsBulkRequest(
            resources.mapIndexedNotNull { idx, resource ->
                val creditsRequested = resource.creditsRequested ?: return@mapIndexedNotNull null
                val paysFor = ProductCategoryId(resource.productCategory, resource.productProvider)
                ReserveCreditsRequest(
                    limitCheckId + idx,
                    creditsRequested,
                    later,
                    Wallet(projectId, WalletOwnerType.PROJECT, paysFor),
                    productId = "",
                    productUnits = 0,
                    jobInitiatedBy = "_ucloud",
                    discardAfterLimitCheck = true,
                    transactionType = transactionType
                )
            }
        ),
        serviceClient
    ).orThrow()
}


suspend fun grantResourcesToProject(
    sourceProject: String,
    resources: List<ResourceRequest>,
    targetWallet: String,
    targetWalletType: WalletOwnerType,
    serviceClient: AuthenticatedClient,
    initiatedBy: String = "_ucloud",
    transactionType: TransactionType
) {
    // Start by verifying this project has enough resources
    // TODO This isn't really enough since we still have potential race conditions but this is
    //  extremely hard to deal with this under this microservice architecture.
    checkBalance(resources, sourceProject, serviceClient, transactionType)
    /*
    val usage = FileDescriptions.retrieveQuota.call(
        RetrieveQuotaRequest(projectHomeDirectory(sourceProject)),
        serviceClient
    ).orThrow()

    val quotaRequested = resources.asSequence()
        .map { resource -> resource.quotaRequested ?: 0L }
        .sum()

    if (usage.quotaInBytes - quotaRequested < 0L) {
        throw RPCException("Insufficient quota available from source project", HttpStatusCode.PaymentRequired)
    }
     */

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

            /*
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
             */
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

            /*
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
             */
        }
    }
}
