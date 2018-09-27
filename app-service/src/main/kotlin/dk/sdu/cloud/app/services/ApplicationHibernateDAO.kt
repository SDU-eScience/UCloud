package dk.sdu.cloud.app.services

import dk.sdu.cloud.app.api.Application
import dk.sdu.cloud.app.api.NormalizedApplicationDescription
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.RPCException
import dk.sdu.cloud.service.db.*
import dk.sdu.cloud.service.mapItems
import io.ktor.http.HttpStatusCode
import java.util.*

class ApplicationHibernateDAO(
    private val toolDAO: ToolHibernateDAO
) : ApplicationDAO<HibernateSession> {

    override fun search(
        session: HibernateSession,
        user: String?,
        query: String,
        paging: NormalizedPaginationRequest
    ): Page<Application> {
        val preparedQuery = "'%"+query+"%'"
        /*return session.paginatedCriteria<ApplicationEntity>(paging) {
            entity[ApplicationEntity::id][EmbeddedNameAndVersion::name] like preparedQuery
        }.mapItems { it.toModel() }*/

        //language=HQL
        val count = session.typedQuery<Long>(
            """
            select count (A.id.name)
            from ApplicationEntity as A where (A.createdAt) in (
                select max(createdAt)
                from ApplicationEntity as B
                where A.id.name = B.id.name
                group by id.name
            ) and A.id.name like ${preparedQuery}
            """.trimIndent()
        ).uniqueResult().toInt()

        //language=HQL
        val items = session.typedQuery<ApplicationEntity>(
            """
            from ApplicationEntity as A where (A.createdAt) in (
                select max(createdAt)
                from ApplicationEntity as B
                where A.id.name = B.id.name
                group by id.name
            ) and A.id.name like ${preparedQuery}
            order by A.id.name
        """.trimIndent()
        ).paginatedList(paging).map { it.toModel() }

        return Page(
            count,
            paging.itemsPerPage,
            paging.page,
            items
        )
    }

    override fun findAllByName(
        session: HibernateSession,
        user: String?,
        name: String,
        paging: NormalizedPaginationRequest
    ): Page<Application> {
        return session.paginatedCriteria<ApplicationEntity>(paging) {
            entity[ApplicationEntity::id][EmbeddedNameAndVersion::name] equal name
        }.mapItems { it.toModel() }
    }

    override fun findByNameAndVersion(
        session: HibernateSession,
        user: String?,
        name: String,
        version: String
    ): Application {
        return internalByNameAndVersion(session, name, version)
            ?.toModel() ?: throw ApplicationException.NotFound()
    }

    override fun listLatestVersion(
        session: HibernateSession,
        user: String?,
        paging: NormalizedPaginationRequest
    ): Page<Application> {
        //language=HQL
        val count = session.typedQuery<Long>(
            """
            select count (A.id.name)
            from ApplicationEntity as A where (A.createdAt) in (
                select max(createdAt)
                from ApplicationEntity as B
                where A.id.name = B.id.name
                group by id.name
            )
            """.trimIndent()
        ).uniqueResult().toInt()

        //language=HQL
        val items = session.typedQuery<ApplicationEntity>(
            """
            from ApplicationEntity as A where (A.createdAt) in (
                select max(createdAt)
                from ApplicationEntity as B
                where A.id.name = B.id.name
                group by id.name
            )
            order by A.id.name
        """.trimIndent()
        ).paginatedList(paging).map { it.toModel() }

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
        description: NormalizedApplicationDescription,
        originalDocument: String
    ) {
        val existingOwner = findOwnerOfApplication(session, description.info.name)
        if (existingOwner != null && existingOwner != user) {
            throw ApplicationException.NotAllowed()
        }

        val existing = internalByNameAndVersion(session, description.info.name, description.info.version)
        if (existing != null) throw ApplicationException.AlreadyExists()

        val existingTool = toolDAO.internalByNameAndVersion(session, description.tool.name, description.tool.version)
                ?: throw ApplicationException.BadToolReference()

        session.save(
            ApplicationEntity(
                user,
                Date(),
                Date(),
                description,
                originalDocument,
                existingTool,
                EmbeddedNameAndVersion(description.info.name, description.info.version)
            )
        )

        description.tags.forEach { tag ->
            session.save(
                TagEntity(
                    EmbeddedNameAndVersion(description.info.name, description.info.version),
                    tag
                )
            )
        }


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

    internal fun internalByNameAndVersion(session: HibernateSession, name: String, version: String): ApplicationEntity? {
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
}

internal fun ApplicationEntity.toModel(): Application {
    return Application(
        owner,
        createdAt.time,
        modifiedAt.time,
        application,
        tool.toModel()
    )
}

sealed class ApplicationException(why: String, httpStatusCode: HttpStatusCode) : RPCException(why, httpStatusCode) {
    class NotFound : ApplicationException("Not found", HttpStatusCode.NotFound)
    class NotAllowed : ApplicationException("Not allowed", HttpStatusCode.Forbidden)
    class AlreadyExists : ApplicationException("Already exists", HttpStatusCode.Conflict)
    class BadToolReference : ApplicationException("Tool does not exist", HttpStatusCode.BadRequest)
}
