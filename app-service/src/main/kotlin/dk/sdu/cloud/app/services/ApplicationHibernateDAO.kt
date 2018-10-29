package dk.sdu.cloud.app.services

import dk.sdu.cloud.app.api.Application
import dk.sdu.cloud.app.api.ApplicationForUser
import dk.sdu.cloud.app.api.NormalizedApplicationDescription
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.RPCException
import dk.sdu.cloud.service.db.HibernateSession
import dk.sdu.cloud.service.db.createCriteriaBuilder
import dk.sdu.cloud.service.db.createQuery
import dk.sdu.cloud.service.db.criteria
import dk.sdu.cloud.service.db.get
import dk.sdu.cloud.service.db.paginatedCriteria
import dk.sdu.cloud.service.db.paginatedList
import dk.sdu.cloud.service.db.typedQuery
import dk.sdu.cloud.service.mapItems
import io.ktor.http.HttpStatusCode
import java.util.Date

@Suppress("TooManyFunctions") //Does not make sense to split
class ApplicationHibernateDAO(
    private val toolDAO: ToolHibernateDAO
) : ApplicationDAO<HibernateSession> {

    override fun toggleFavorite(
        session: HibernateSession,
        user: String,
        name: String,
        version: String
    ) {
        val foundApp = internalByNameAndVersion(session, name, version) ?: throw
        ApplicationException.BadApplication()

        val isFavorite = session.typedQuery<Long>(
            """
                select count (A.application.id.name)
                from FavoriteApplicationEntity as A
                where A.user = :user
                    and A.application.id.name = :name
                    and A.application.id.version = :version
            """.trimIndent()
        ).setParameter("user", user)
            .setParameter("name", name)
            .setParameter("version", version)
            .uniqueResult()

        if (isFavorite != 0L) {
            val query = session.createQuery(
                """
                delete from FavoriteApplicationEntity as A
                where A.user = :user
                    and A.application.id.name = :name
                    and A.application.id.version = :version
                """.trimIndent()
            ).setParameter("user", user)
                .setParameter("name", name)
                .setParameter("version", version)

            query.executeUpdate()
        } else {
            session.save(
                FavoriteApplicationEntity(
                    foundApp,
                    user
                )
            )
        }
    }

    override fun retrieveFavorites(
        session: HibernateSession,
        user: String,
        paging: NormalizedPaginationRequest
    ): Page<ApplicationForUser> {
        val itemsInTotal = session.createCriteriaBuilder<Long, FavoriteApplicationEntity>().run {
            criteria.where(entity[FavoriteApplicationEntity::user] equal user)
            criteria.select(count(entity))
        }.createQuery(session).uniqueResult()

        val items = session.typedQuery<ApplicationEntity>(
            """
                select application
                from FavoriteApplicationEntity as A
                where user = :user
                order by A.application.id.name
            """.trimIndent()
        ).setParameter("user", user)
            .paginatedList(paging)
            .map { it.toModel() }

        val preparedForUserPageItems = mutableListOf<ApplicationForUser>()
        items.forEach {
            preparedForUserPageItems.add(ApplicationForUser(it, true))
        }

        return Page(
            itemsInTotal.toInt(),
            paging.itemsPerPage,
            paging.page,
            preparedForUserPageItems
        )
    }

    override fun searchTags(
        session: HibernateSession,
        user: String,
        query: String,
        paging: NormalizedPaginationRequest
    ): Page<ApplicationForUser> {
        val itemsInTotal = session.createCriteriaBuilder<Long, ApplicationTagEntity>().run {
            criteria.where(entity[ApplicationTagEntity::tag] equal query)
            criteria.select(count(entity))
        }.createQuery(session).uniqueResult()

        val items = session.typedQuery<ApplicationEntity>(
            """
                select application
                from ApplicationTagEntity
                where tag=:query
                order by application
            """.trimIndent()
        ).setParameter("query", query)
            .paginatedList(paging)
            .map { it.toModel() }

        return preparePageForUser(
            session,
            user,
            Page(
                itemsInTotal.toInt(),
                paging.itemsPerPage,
                paging.page,
                items
            )
        )
    }

    override fun search(
        session: HibernateSession,
        user: String,
        query: String,
        paging: NormalizedPaginationRequest
    ): Page<ApplicationForUser> {
        val count = session.typedQuery<Long>(
            """
                select count (A.id.name)
                from ApplicationEntity as A where (A.createdAt) in (
                    select max(createdAt)
                    from ApplicationEntity as B
                    where A.id.name = B.id.name
                    group by id.name
                ) and A.id.name like '%' || :query || '%'
                """.trimIndent()
        ).setParameter("query", query)
            .uniqueResult()
            .toInt()

        val items = session.typedQuery<ApplicationEntity>(
            """
                    from ApplicationEntity as A where (A.createdAt) in (
                        select max(createdAt)
                        from ApplicationEntity as B
                        where A.id.name = B.id.name
                        group by id.name
                    ) and A.id.name like '%' || :query || '%'
                    order by A.id.name
                """.trimIndent()
        ).setParameter("query", query)
            .paginatedList(paging)
            .map { it.toModel() }

        return preparePageForUser(
            session,
            user,
            Page(
                count,
                paging.itemsPerPage,
                paging.page,
                items
            )
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
    ): Page<ApplicationForUser> {
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
        ).uniqueResult()
            .toInt()

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
        ).paginatedList(paging)
            .map { it.toModel() }

        return preparePageForUser(
            session,
            user,
            Page(
                count,
                paging.itemsPerPage,
                paging.page,
                items
            )
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

        val entity = ApplicationEntity(
            user,
            Date(),
            Date(),
            description,
            originalDocument,
            existingTool,
            EmbeddedNameAndVersion(description.info.name, description.info.version)
        )
        session.save(entity)

        description.tags.forEach { tag ->
            session.save(ApplicationTagEntity(entity, tag))
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

    internal fun internalByNameAndVersion(
        session: HibernateSession,
        name: String,
        version: String
    ): ApplicationEntity? {
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

    private fun preparePageForUser(
        session: HibernateSession,
        user: String?,
        page: Page<Application>
    ): Page<ApplicationForUser> {
        if (!user.isNullOrBlank()) {
            val allFavorites = session
                .criteria<FavoriteApplicationEntity> { entity[FavoriteApplicationEntity::user] equal user }
                .list()

            val preparedPageItems = page.items.map { item ->
                val isFavorite = allFavorites.any { fav ->
                    fav.application.id.name == item.description.info.name &&
                            fav.application.id.version == item.description.info.version
                }

                ApplicationForUser(item, isFavorite)
            }

            return Page(page.itemsInTotal, page.itemsPerPage, page.pageNumber, preparedPageItems)
        } else {
            val preparedPageItems = page.items.map { ApplicationForUser(it, false) }
            return Page(page.itemsInTotal, page.itemsPerPage, page.pageNumber, preparedPageItems)
        }
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
    class BadApplication : ApplicationException("Application does not exists", HttpStatusCode.BadRequest)
}
