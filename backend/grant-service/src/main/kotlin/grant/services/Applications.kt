package dk.sdu.cloud.grant.services

import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.grant.api.Application
import dk.sdu.cloud.grant.api.ApplicationStatus
import dk.sdu.cloud.grant.api.GrantRecipient
import dk.sdu.cloud.grant.api.ResourceRequest
import dk.sdu.cloud.service.Actor
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.SQLTable
import dk.sdu.cloud.service.db.async.insert
import dk.sdu.cloud.service.db.async.long
import dk.sdu.cloud.service.db.async.paginatedQuery
import dk.sdu.cloud.service.db.async.sendPreparedStatement
import dk.sdu.cloud.service.db.async.text
import dk.sdu.cloud.service.db.async.timestamp
import dk.sdu.cloud.service.db.async.withSession
import dk.sdu.cloud.service.safeUsername
import io.ktor.http.HttpStatusCode

object ApplicationTable : SQLTable("applications") {
    val id = text("id", notNull = true)
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

class ApplicationService {
    suspend fun submit(
        ctx: DBContext,
        actor: Actor,
        application: Application
    ): Long {
        return ctx.withSession { session ->
            // TODO Check some permissions
            val id = session
                .sendPreparedStatement(
                    {
                        with(application) {
                            setParameter("status", status.name)
                            setParameter("resources_owned_by", resourcesOwnedBy)
                            setParameter("requested_by", actor.safeUsername())
                            setParameter("document", document)

                            val grantRecipient = this.grantRecipient
                            setParameter("grant_recipient", when (grantRecipient) {
                                is GrantRecipient.PersonalProject -> grantRecipient.username
                                is GrantRecipient.ExistingProject -> grantRecipient.projectId
                                is GrantRecipient.NewProject -> grantRecipient.projectTitle
                            })

                            setParameter("grant_recipient_title", when (grantRecipient) {
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

            insertResources(session, application.requestedResource, id)

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

    private suspend fun checkIfWeAreProjectAdmin(): Boolean = TODO()

    suspend fun updateApplication(
        ctx: DBContext,
        actor: Actor,
        id: Long,
        newDocument: String,
        newResources: List<ResourceRequest>
    ) {
        ctx.withSession { session ->
            val isProjectAdmin = checkIfWeAreProjectAdmin()
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
            // TODO ACL
            session
                .sendPreparedStatement(
                    {
                        setParameter("id", id)
                        setParameter("status", newStatus.name)
                    },
                    """
                        update "grant".applications 
                        set
                            status = :status
                        where
                            id = :id
                    """
                )
        }
    }

    suspend fun listIngoingApplications(
        ctx: DBContext,
        actor: Actor,
        projectId: String,
        pagination: NormalizedPaginationRequest?
    ): Page<Unit> {
        ctx.withSession { session ->
            // TODO ACL

            session
                .paginatedQuery(
                    pagination,
                    {
                        setParameter("projectId", projectId)
                    },
                    """
                        from "grant".applications a
                        where 
                            a.resources_owned_by = :projectId and
                            a.status = 'IN_PROGRESS'
                    """,

                    """
                       order by "grant".applications.updated_at
                    """
                )
        }
        TODO()
    }

    suspend fun listOutgoingApplications(
        ctx: DBContext,
        actor: Actor,
        pagination: NormalizedPaginationRequest?
    ): Page<Unit> {
        ctx.withSession { session ->
            session
                .paginatedQuery(
                    pagination,
                    {
                        setParameter("requestedBy", actor.safeUsername())
                    },
                    """
                        from "grant".applications a
                        where
                            a.requested_by = :requestedBy and
                            a.status = 'IN_PROGRESS'
                    """,
                    """
                        order by "grant".applications.updated_at
                    """
                )
        }
        TODO()
    }

    suspend fun findActiveApplication(
        ctx: DBContext,
        actor: Actor,
        projectId: String
    ): Unit {
        ctx.withSession { session ->
            session
                .sendPreparedStatement(
                    {
                        setParameter("projectId", projectId)
                        setParameter("requestedBy", actor.safeUsername())
                    },
                    """
                        select * 
                        from "grant".applications a
                        where
                            a.resources_owned_by = :projectId and
                            a.requested_by = :requestedBy and
                            a.status = 'IN_PROGRESS'
                    """
                )
        }
        TODO()
    }
}
