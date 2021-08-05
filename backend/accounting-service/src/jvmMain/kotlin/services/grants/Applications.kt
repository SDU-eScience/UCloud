package dk.sdu.cloud.accounting.services.grants

import dk.sdu.cloud.ActorAndProject
import dk.sdu.cloud.PageV2
import dk.sdu.cloud.WithPaginationRequestV2
import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.grant.api.Application
import dk.sdu.cloud.grant.api.ApplicationStatus
import dk.sdu.cloud.grant.api.CreateApplication
import dk.sdu.cloud.grant.api.EditApplicationRequest
import dk.sdu.cloud.grant.api.GrantApplicationFilter
import dk.sdu.cloud.grant.api.GrantRecipient
import dk.sdu.cloud.grant.api.GrantsRetrieveProductsRequest
import dk.sdu.cloud.grant.api.ResourceRequest
import dk.sdu.cloud.grant.api.TransferApplicationRequest
import dk.sdu.cloud.mail.api.Mail
import dk.sdu.cloud.safeUsername
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.db.async.AsyncDBConnection
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.paginateV2
import dk.sdu.cloud.service.db.async.sendPreparedStatement
import dk.sdu.cloud.service.db.async.withSession
import io.ktor.http.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement

class GrantApplicationService(
    private val db: DBContext,
    private val notifications: GrantNotificationService,
) {
    suspend fun retrieveProducts(
        actorAndProject: ActorAndProject,
        request: GrantsRetrieveProductsRequest,
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
                    with can_submit_application as (
                        select "grant".can_submit_application(:username, :source, :grant_recipient,
                            :grant_recipient_type) can_submit
                    )
                    select accounting.product_to_json(p, pc, null)
                    from
                        can_submit_application join
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
                """
            )
        }.rows.map { defaultMapper.decodeFromString(it.getString(0)!!) }
    }

    suspend fun submit(
        actorAndProject: ActorAndProject,
        request: CreateApplication
    ): Long {
        val recipient = request.grantRecipient
        if (recipient is GrantRecipient.PersonalProject && recipient.username != actorAndProject.actor.safeUsername()) {
            throw RPCException("Cannot request resources for someone else", HttpStatusCode.Forbidden)
        }

        return db.withSession(remapExceptions = true) { session ->
            val applicationId = session.sendPreparedStatement(
                {
                    setParameter("source", request.resourcesOwnedBy)
                    setParameter("username", actorAndProject.actor.safeUsername())
                    setParameter("document", request.document)
                    setParameter(
                        "grant_recipient", when (recipient) {
                            is GrantRecipient.ExistingProject -> recipient.projectId
                            is GrantRecipient.NewProject -> recipient.projectTitle
                            is GrantRecipient.PersonalProject -> recipient.username
                        }
                    )
                    setParameter(
                        "grant_recipient_type", when (recipient) {
                            is GrantRecipient.ExistingProject -> GrantRecipient.EXISTING_PROJECT_TYPE
                            is GrantRecipient.NewProject -> GrantRecipient.NEW_PROJECT_TYPE
                            is GrantRecipient.PersonalProject -> GrantRecipient.PERSONAL_TYPE
                        }
                    )
                },
                """
                    insert into "grant".applications
                        (status, resources_owned_by, requested_by, grant_recipient, grant_recipient_type, document) 
                    select
                        'IN_PROGRESS', :source, :username, :grant_recipient, :grant_recipient_type, :document
                    where
                        "grant".can_submit_application(:username, :source, :grant_recipient, :grant_recipient_type)
                    returning id
                """
            ).rows.singleOrNull()?.getLong(0)
                ?: throw RPCException(
                    "It looks like you are unable to submit a request to this affiliation. " +
                            "Contact support if this problem persists.",
                    HttpStatusCode.Forbidden
                )

            insertResources(session, applicationId, request.requestedResources)

            // TODO Auto-approve

            notifications.notify(
                actorAndProject.actor.safeUsername(),
                GrantNotification(
                    applicationId,
                    adminMessage = AdminGrantNotificationMessage(
                        { "New grant application to $projectTitle" },
                        "NEW_GRANT_APPLICATION",
                        { Mail.NewGrantApplicationMail(actorAndProject.actor.safeUsername(), projectTitle) },
                        meta = {
                            JsonObject(
                                mapOf(
                                    "grantRecipient" to defaultMapper.encodeToJsonElement(request.grantRecipient),
                                    "appId" to JsonPrimitive(applicationId),
                                )
                            )
                        }
                    ),
                    userMessage = null
                )
            )

            applicationId
        }
    }

    private suspend fun insertResources(
        session: AsyncDBConnection,
        applicationId: Long,
        resources: List<ResourceRequest>
    ) {
        session.sendPreparedStatement(
            {
                setParameter("id", applicationId)
                resources.split {
                    into("credits_requested") { it.creditsRequested }
                    into("quota_requested") { it.quotaRequested }
                    into("categories") { it.productCategory }
                    into("providers") { it.productProvider }
                }
            },
            """
                    insert into "grant".requested_resources
                        (application_id, credits_requested, quota_requested_bytes, product_category) 
                    with requests as (
                        select :id application_id, unnest(:credits_requested::bigint[]) credits_requested,
                               unnest(:quota_requested::bigint[]) quota_requested,
                               unnest(:categories::text[]) category, unnest(:providers::text[]) provider_id
                    )
                    select
                        req.application_id, req.credits_requested, req.quota_requested, pc.id
                    from
                        requests req join
                        accounting.product_categories pc on
                            pc.category = req.category and
                            pc.provider = req.provider_id
                """
        )
    }

    suspend fun editApplication(
        actorAndProject: ActorAndProject,
        request: EditApplicationRequest
    ) {
        db.withSession(remapExceptions = true) { session ->
            val success = session.sendPreparedStatement(
                {
                    setParameter("username", actorAndProject.actor.safeUsername())
                    setParameter("id", request.id)
                    setParameter("document", request.newDocument)
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
                { setParameter("id", request.id) },
                "delete from \"grant\".requested_resources where application_id = :id"
            )

            insertResources(session, request.id, request.newResources)

            notifications.notify(
                actorAndProject.actor.safeUsername(),
                GrantNotification(
                    request.id,
                    adminMessage =
                    AdminGrantNotificationMessage(
                        { "Grant application updated" },
                        "GRANT_APPLICATION_UPDATED",
                        { Mail.GrantApplicationUpdatedMailToAdmins(projectTitle, requestedBy, grantRecipientTitle) }
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
                                    "appId" to JsonPrimitive(request.id),
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
        id: Long,
        newStatus: ApplicationStatus,
        notifyChange: Boolean? = true
    ) {
        require(newStatus != ApplicationStatus.IN_PROGRESS) { "New status can only be APPROVED, REJECTED or CLOSED!" }
        db.withSession(remapExceptions = true) { session ->
            val success = session.sendPreparedStatement(
                {
                    setParameter("username", actorAndProject.actor.safeUsername())
                    setParameter("id", id)
                    setParameter("status", newStatus.name)
                    setParameter("should_be_approver", newStatus != ApplicationStatus.CLOSED)
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

            if (newStatus == ApplicationStatus.APPROVED) {
                onApplicationApprove(session, actorAndProject.actor.safeUsername(), id)
            }

            if (notifyChange == true) {
                val statusTitle = when (newStatus) {
                    ApplicationStatus.APPROVED -> "Approved"
                    ApplicationStatus.REJECTED -> "Rejected"
                    ApplicationStatus.CLOSED -> "Closed"
                    ApplicationStatus.IN_PROGRESS -> "In Progress"
                }
                notifications.notify(
                    actorAndProject.actor.safeUsername(),
                    GrantNotification(
                        id,
                        adminMessage =
                        AdminGrantNotificationMessage(
                            { "Grant application updated ($statusTitle)" },
                            GRANT_APP_RESPONSE,
                            { Mail.GrantApplicationStatusChangedToAdmin(
                                newStatus.name,
                                projectTitle,
                                requestedBy,
                                grantRecipientTitle
                            ) }
                        ),
                        userMessage =
                        UserGrantNotificationMessage(
                            { "Grant application updated ($statusTitle)" },
                            GRANT_APP_RESPONSE,
                            {
                                when (newStatus) {
                                    ApplicationStatus.APPROVED -> Mail.GrantApplicationApproveMail(projectTitle)
                                    ApplicationStatus.REJECTED -> Mail.GrantApplicationRejectedMail(projectTitle)
                                    ApplicationStatus.CLOSED -> Mail.GrantApplicationWithdrawnMail(
                                        projectTitle,
                                        actorAndProject.actor.safeUsername()
                                    )
                                    else -> throw IllegalStateException()
                                }
                            },
                            meta = {
                                JsonObject(mapOf(
                                    "grantRecipient" to defaultMapper.encodeToJsonElement(grantRecipient),
                                    "appId" to JsonPrimitive(id),
                                ))
                            }
                        )
                    ),

                )
            }
        }
    }

    suspend fun transferApplication(
        actor: ActorAndProject,
        request: TransferApplicationRequest
    ) {
        /*
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
         */
    }

    suspend fun browseIngoingApplications(
        actorAndProject: ActorAndProject,
        pagination: WithPaginationRequestV2,
        filter: GrantApplicationFilter
    ): PageV2<Application> {
        return db.paginateV2(
            actorAndProject.actor,
            pagination.normalize(),
            create = { session ->
                session.sendPreparedStatement(
                    {
                        setParameter("username", actorAndProject.actor.safeUsername())
                        setParameter("project", actorAndProject.project)
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
                        select "grant".application_to_json(
                            apps,
                            array_remove(array_agg("grant".resource_request_to_json(request, pc)), null),
                            owner_project,
                            existing_project,
                            existing_project_pi.username
                        )
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
                            (:show_inactive or apps.status = 'IN_PROGRESS')
                        group by
                            apps.*, existing_project.*, existing_project_pi.username, owner_project.*,
                            apps.created_at, apps.id
                        order by
                            apps.created_at desc, apps.id
                    """
                )
            },
            mapper = { _, rows -> rows.map { defaultMapper.decodeFromString(it.getString(0)!!) } }
        )
    }

    suspend fun browseOutgoingApplications(
        actorAndProject: ActorAndProject,
        pagination: WithPaginationRequestV2,
        filter: GrantApplicationFilter
    ): PageV2<Application> {
        return db.paginateV2(
            actorAndProject.actor,
            pagination.normalize(),
            create = { session ->
                session.sendPreparedStatement(
                    {
                        setParameter("username", actorAndProject.actor.safeUsername())
                        setParameter("project", actorAndProject.project)
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
                        select "grant".application_to_json(
                            apps,
                            array_remove(array_agg("grant".resource_request_to_json(request, pc)), null),
                            owner_project,
                            existing_project,
                            existing_project_pi.username
                        )
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
                            (:show_inactive or apps.status = 'IN_PROGRESS')
                        group by
                            apps.*, existing_project.*, existing_project_pi.username, owner_project.*,
                            apps.created_at, apps.id
                        order by
                            apps.created_at desc, apps.id
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
    }

    companion object : Loggable {
        override val log = logger()
        private const val GRANT_APP_RESPONSE = "GRANT_APPLICATION_RESPONSE"
    }
}
