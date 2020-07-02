package dk.sdu.cloud.grant.services

import com.github.jasync.sql.db.RowData
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.grant.api.Application
import dk.sdu.cloud.grant.api.ApplicationStatus
import dk.sdu.cloud.grant.api.CreateApplication
import dk.sdu.cloud.grant.api.GrantRecipient
import dk.sdu.cloud.grant.api.ResourceRequest
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
    private val settings: SettingsService
) {
    suspend fun submit(
        ctx: DBContext,
        actor: Actor,
        application: CreateApplication
    ): Long {
        if (actor !is Actor.User) throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)

        return ctx.withSession { session ->
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
                            setParameter("grant_recipient", when (grantRecipient) {
                                is GrantRecipient.PersonalProject -> grantRecipient.username
                                is GrantRecipient.ExistingProject -> grantRecipient.projectId
                                is GrantRecipient.NewProject -> grantRecipient.projectTitle
                            })

                            setParameter("grant_recipient_type", when (grantRecipient) {
                                is GrantRecipient.PersonalProject -> GrantRecipient.PERSONAL_TYPE
                                is GrantRecipient.ExistingProject -> GrantRecipient.EXISTING_PROJECT_TYPE
                                is GrantRecipient.NewProject -> GrantRecipient.NEW_PROJECT_TYPE
                            })
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
    }

    suspend fun updateStatus(
        ctx: DBContext,
        actor: Actor,
        id: Long,
        newStatus: ApplicationStatus
    ) {
        require(newStatus != ApplicationStatus.IN_PROGRESS) { "New status can only be APPROVED or REJECTED!" }
        ctx.withSession { session ->
            val updatedProjectId = session
                .sendPreparedStatement(
                    {
                        setParameter("id", id)
                        setParameter("status", newStatus.name)
                    },
                    """
                        update "grant".applications 
                        set status = :status
                        where id = :id
                        returning resources_owned_by
                    """
                )
                .rows.singleOrNull()?.getString(0) ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound)

            if (!projects.isAdminOfParent(updatedProjectId, actor)) {
                throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
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
                        from "grant".applications a, requested_resources r
                        where 
                            a.resources_owned_by = :projectId and 
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
                            add(ResourceRequest(
                                it.getField(RequestedResourceTable.productCategory),
                                it.getField(RequestedResourceTable.productProvider),
                                it.getFieldNullable(RequestedResourceTable.creditsRequested),
                                it.getFieldNullable(RequestedResourceTable.quotaRequestedBytes)
                            ))
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
    }
}
