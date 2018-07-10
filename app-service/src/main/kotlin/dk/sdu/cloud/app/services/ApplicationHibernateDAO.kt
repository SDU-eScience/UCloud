package dk.sdu.cloud.app.services

import dk.sdu.cloud.app.api.NewApplication
import dk.sdu.cloud.app.api.NewNormalizedApplicationDescription
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.db.*
import dk.sdu.cloud.service.mapItems
import org.hibernate.annotations.Type
import java.io.Serializable
import java.util.*
import javax.persistence.*

/**
 * Updated in:
 *
 * - V3__Applications.sql
 */
@Entity
@Table(name = "applications")
class ApplicationEntity(
    var owner: String,

    @Temporal(TemporalType.TIMESTAMP)
    var createdAt: Date,

    @Temporal(TemporalType.TIMESTAMP)
    var modifiedAt: Date,

    @Type(type = JSONB_TYPE)
    var application: NewNormalizedApplicationDescription,

    // Note: This is just the original document. We _do not_ attempt to keep this synchronized with changes
    // to description etc.
    //
    // In case this is used for migration we should apply these updates on top of it!
    @Column(length = 1024 * 64)
    var originalDocument: String,

    @EmbeddedId
    var id: EmbeddedNameAndVersion
) {
    companion object : HibernateEntity<ApplicationEntity>, WithId<EmbeddedNameAndVersion>
}

data class EmbeddedNameAndVersion(
    var name: String = "",
    var version: String = ""
) : Serializable

class ApplicationHibernateDAO : ApplicationDAO2<HibernateSession> {
    override fun findAllByName(
        session: HibernateSession,
        user: String?,
        name: String,
        paging: NormalizedPaginationRequest
    ): Page<NewApplication> {
        return session.paginatedCriteria<ApplicationEntity>(paging) {
            entity[ApplicationEntity::id][EmbeddedNameAndVersion::name] equal name
        }.mapItems { it.toApplication() }
    }

    override fun findByNameAndVersion(
        session: HibernateSession,
        user: String?,
        name: String,
        version: String
    ): NewApplication {
        return internalByNameAndVersion(session, name, version)
            ?.toApplication() ?: throw ApplicationException.NotFound()
    }

    override fun listLatestVersion(
        session: HibernateSession,
        user: String?,
        paging: NormalizedPaginationRequest
    ): Page<NewApplication> {
        //language=HQL
        val count = session.typedQuery<Long>(
            """
            select count (id.name)
            from ApplicationEntity where (id.name, createdAt) in (
                select id.name, max(createdAt)
                from ApplicationEntity
                group by id.name
            )
        """.trimIndent()
        ).uniqueResult().toInt()

        //language=HQL
        val items = session.typedQuery<ApplicationEntity>(
            """
            from ApplicationEntity where (id.name, createdAt) in (
                select id.name, max(createdAt)
                from ApplicationEntity
                group by id.name
            )
        """.trimIndent()
        ).paginatedList(paging).map { it.toApplication() }

        return Page(
            count,
            paging.itemsPerPage,
            paging.page,
            items
        )
    }

    override fun create(
        session: HibernateSession,
        user: String,
        description: NewNormalizedApplicationDescription,
        originalDocument: String
    ) {
        val existingOwner = findOwnerOfApplication(session, description.info.name)
        if (existingOwner != null && existingOwner != user) {
            throw ApplicationException.NotAllowed()
        }

        val existing = internalByNameAndVersion(session, description.info.name, description.info.version)
        if (existing != null) throw ApplicationException.AlreadyExists()

        session.save(
            ApplicationEntity(
                user,
                Date(),
                Date(),
                description,
                originalDocument,
                EmbeddedNameAndVersion(description.info.name, description.info.version)
            )
        )
    }

    override fun updateDescription(
        session: HibernateSession,
        user: String,
        name: String,
        version: String,
        newDescription: String?,
        newAuthors: List<String>?
    ) {
        val existing = internalByNameAndVersion(session, name, version) ?: throw ApplicationException.NotFound()
        if (existing.owner != user) throw ApplicationException.NotAllowed()

        val newApplication = existing.application.let {
            if (newDescription != null) it.copy(description = newDescription)
            else it
        }.let {
            if (newAuthors != null) it.copy(authors = newAuthors)
            else it
        }

        existing.application = newApplication
        session.update(existing)
    }

    private fun internalByNameAndVersion(session: HibernateSession, name: String, version: String): ApplicationEntity? {
        return session
            .criteria<ApplicationEntity> {
                (entity[ApplicationEntity::id][EmbeddedNameAndVersion::name] equal name) and
                        (entity[ApplicationEntity::id][EmbeddedNameAndVersion::version] equal version)
            }
            .uniqueResult()
    }

    private fun findOwnerOfApplication(session: HibernateSession, applicationName: String): String? {
        return session.criteria<ApplicationEntity> {
            entity[ApplicationEntity::id][EmbeddedNameAndVersion::name] equal applicationName
        }.apply {
            maxResults = 1
        }.uniqueResult()?.owner
    }

    private fun ApplicationEntity.toApplication(): NewApplication {
        return NewApplication(
            createdAt.time,
            modifiedAt.time,
            application
        )
    }
}

sealed class ApplicationException(why: String) : RuntimeException(why) {
    class NotFound : ApplicationException("Not found")
    class NotAllowed : ApplicationException("Not allowed")
    class AlreadyExists : ApplicationException("Already exists")
}
