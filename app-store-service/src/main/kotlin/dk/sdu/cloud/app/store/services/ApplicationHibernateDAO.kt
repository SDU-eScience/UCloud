package dk.sdu.cloud.app.store.services

import dk.sdu.cloud.Role
import dk.sdu.cloud.SecurityPrincipal
import dk.sdu.cloud.app.store.api.*
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.PaginationRequest
import dk.sdu.cloud.service.db.*
import dk.sdu.cloud.service.mapItems
import io.ktor.http.HttpStatusCode
import org.hibernate.query.Query
import java.util.*

@Suppress("TooManyFunctions") // Does not make sense to split
class ApplicationHibernateDAO(
    private val toolDAO: ToolHibernateDAO
) : ApplicationDAO<HibernateSession> {
    private val byNameAndVersionCache = Collections.synchronizedMap(HashMap<NameAndVersion, Pair<Application, Long>>())

    override fun toggleFavorite(session: HibernateSession, user: SecurityPrincipal, name: String, version: String) {
        val foundApp = internalByNameAndVersion(session, name, version) ?: throw ApplicationException.BadApplication()

        val isFavorite = session.typedQuery<Long>(
            """
                select count (A.applicationName)
                from FavoriteApplicationEntity as A
                where A.user = :user
                    and A.applicationName = :name
                    and A.applicationVersion= :version
            """.trimIndent()
        ).setParameter("user", user.username)
            .setParameter("name", name)
            .setParameter("version", version)
            .uniqueResult()

        if (isFavorite != 0L) {
            val query = session.createQuery(
                """
                delete from FavoriteApplicationEntity as A
                where A.user = :user
                    and A.applicationName = :name
                    and A.applicationVersion = :version
                """.trimIndent()
            ).setParameter("user", user.username)
                .setParameter("name", name)
                .setParameter("version", version)

            query.executeUpdate()
        } else {
            session.save(
                FavoriteApplicationEntity(
                    foundApp.id.name,
                    foundApp.id.version,
                    user.username
                )
            )
        }
    }

    override fun retrieveFavorites(
        session: HibernateSession,
        user: SecurityPrincipal,
        paging: NormalizedPaginationRequest
    ): Page<ApplicationSummaryWithFavorite> {
        val itemsInTotal = session.createCriteriaBuilder<Long, FavoriteApplicationEntity>().run {
            criteria.where(entity[FavoriteApplicationEntity::user] equal user.username)
            criteria.select(count(entity))
        }.createQuery(session).uniqueResult()

        val itemsWithoutTags = session.typedQuery<ApplicationEntity>(
            """
                select application
                from FavoriteApplicationEntity as fav, ApplicationEntity as application
                where
                    fav.user = :user and
                    fav.applicationName = application.id.name and
                    fav.applicationVersion = application.id.version
                order by fav.applicationName
            """.trimIndent()
        ).setParameter("user", user.username)
            .paginatedList(paging)
            .asSequence()
            .map { it.toModel() }
            .toList()

        val apps = itemsWithoutTags.map { it.metadata.name }
        val allTags = session
            .criteria<TagEntity> { entity[TagEntity::applicationName] isInCollection (apps) }
            .resultList
        val items = itemsWithoutTags.map { appSummary ->
            val allTagsForApplication = allTags.filter { it.applicationName == appSummary.metadata.name }.map { it.tag }
            ApplicationSummaryWithFavorite(appSummary.metadata, true, allTagsForApplication)
        }


        return Page(
            itemsInTotal.toInt(),
            paging.itemsPerPage,
            paging.page,
            items
        )
    }

    private fun findAppNamesFromTags(session: HibernateSession, tags: List<String>): List<String> {
        return session
            .criteria<TagEntity> {
                (entity[TagEntity::tag] isInCollection tags)
            }.resultList.distinctBy { it.applicationName }.map { it.applicationName }
    }

    private fun findAppsFromAppNames(
        session: HibernateSession,
        applicationNames: List<String>
    ): Pair<Query<ApplicationEntity>, Int> {
        val itemsInTotal = session.typedQuery<Long>(
            """
            select count (A.title)
            from ApplicationEntity as A where (A.createdAt) in (
                select max(createdAt)
                from ApplicationEntity as B
                where (A.title= B.title)
                group by title
            ) and (A.id.name) in (:applications)
            """.trimIndent()
        ).setParameter("applications", applicationNames)
            .uniqueResult()
            .toInt()

        return Pair(
            session.typedQuery<ApplicationEntity>(
                """
            from ApplicationEntity as A where (A.createdAt) in (
                select max(createdAt)
                from ApplicationEntity as B
                where (A.title= B.title)
                group by title
            ) and (A.id.name) in (:applications)
            order by A.title
        """.trimIndent()
            ).setParameter("applications", applicationNames), itemsInTotal
        )
    }


    override fun searchTags(
        session: HibernateSession,
        user: SecurityPrincipal,
        tags: List<String>,
        paging: NormalizedPaginationRequest
    ): Page<ApplicationSummaryWithFavorite> {
        val applications = findAppNamesFromTags(session, tags)

        if (applications.isEmpty()) {
            return preparePageForUser(
                session,
                user.username,
                Page(
                    0,
                    paging.itemsPerPage,
                    paging.page,
                    emptyList()
                )
            ).mapItems { it.withoutInvocation() }
        }

        val (apps, itemsInTotal) = findAppsFromAppNames(session, applications)
        val items = apps.paginatedList(paging)
            .map { it.toModelWithInvocation() }

        return preparePageForUser(
            session,
            user.username,
            Page(
                itemsInTotal,
                paging.itemsPerPage,
                paging.page,
                items
            )
        ).mapItems { it.withoutInvocation() }
    }

    override fun search(
        session: HibernateSession,
        user: SecurityPrincipal,
        query: String,
        paging: NormalizedPaginationRequest
    ): Page<ApplicationSummaryWithFavorite> {
        if (query.isBlank()) {
            return Page(0, paging.itemsPerPage, 0, emptyList())
        }
        val trimmedNormalizedQuery = normalizeQuery(query).trim()
        val keywords = trimmedNormalizedQuery.split(" ")
        if (keywords.size == 1) {
            return doSearch(session, user.username, trimmedNormalizedQuery, paging)
        }
        val firstTenKeywords = keywords.filter { !it.isBlank() }.take(10)
        return doMultiKeywordSearch(session, user.username, firstTenKeywords, paging)
    }

    private fun doMultiKeywordSearch(
        session: HibernateSession,
        user: String,
        keywords: List<String>,
        paging: NormalizedPaginationRequest
    ): Page<ApplicationSummaryWithFavorite> {

        var keywordsQuery = "("
        for (i in keywords.indices) {
            if (i == keywords.lastIndex) {
                keywordsQuery += "lower(A.title) like '%' || :query$i || '%'"
                continue
            }
            keywordsQuery += "lower(A.title) like '%'|| :query$i ||'%' or "
        }
        keywordsQuery += ")"
        val count = session.typedQuery<Long>(
            """
            select count (A.title)
            from ApplicationEntity as A where (A.createdAt) in (
                select max(createdAt)
                from ApplicationEntity as B
                where A.title = B.title
                group by title
            ) and $keywordsQuery
            """.trimIndent()
        ).also {
            for ((i, item) in keywords.withIndex()) {
                it.setParameter("query$i", item)
            }
        }.uniqueResult().toInt()

        val items = session.typedQuery<ApplicationEntity>(
            """
            from ApplicationEntity as A where (A.createdAt) in (
                select max(createdAt)
                from ApplicationEntity as B
                where A.title = B.title
                group by title
            ) and $keywordsQuery
            order by A.title
        """.trimIndent()
        ).also {
            for ((i, item) in keywords.withIndex()) {
                it.setParameter("query$i", item)
            }
        }.paginatedList(paging)
            .map { it.toModelWithInvocation() }


        return preparePageForUser(
            session,
            user,
            Page(
                count,
                paging.itemsPerPage,
                paging.page,
                items
            )
        ).mapItems { it.withoutInvocation() }
    }

    private fun doSearch(
        session: HibernateSession,
        user: String,
        normalizedQuery: String,
        paging: NormalizedPaginationRequest
    ): Page<ApplicationSummaryWithFavorite> {
        val count = session.typedQuery<Long>(
            """
            select count (A.title)
            from ApplicationEntity as A where (A.createdAt) in (
                select max(createdAt)
                from ApplicationEntity as B
                where A.title = B.title
                group by title
            ) and lower(A.title) like '%' || :query || '%'
            """.trimIndent()
        ).setParameter("query", normalizedQuery)
            .uniqueResult()
            .toInt()

        val items = session.typedQuery<ApplicationEntity>(
            """
            from ApplicationEntity as A where (A.createdAt) in (
                select max(createdAt)
                from ApplicationEntity as B
                where A.title = B.title
                group by title
            ) and lower(A.title) like '%' || :query || '%'
            order by A.title
        """.trimIndent()
        ).setParameter("query", normalizedQuery).paginatedList(paging)
            .map { it.toModelWithInvocation() }


        return preparePageForUser(
            session,
            user,
            Page(
                count,
                paging.itemsPerPage,
                paging.page,
                items
            )
        ).mapItems { it.withoutInvocation() }
    }

    override fun findAllByName(
        session: HibernateSession,
        user: SecurityPrincipal?,
        name: String,
        paging: NormalizedPaginationRequest
    ): Page<ApplicationSummaryWithFavorite> {
        return internalFindAllByName(session, user, name, paging).mapItems { it.withoutInvocation() }
    }

    private fun internalFindAllByName(
        session: HibernateSession,
        user: SecurityPrincipal?,
        name: String,
        paging: NormalizedPaginationRequest
    ): Page<ApplicationWithFavorite> {
        return preparePageForUser(
            session,
            user?.username,
            session
                .paginatedCriteria<ApplicationEntity>(
                    pagination = paging,
                    orderBy = { listOf(descending(entity[ApplicationEntity::createdAt])) },
                    predicate = {
                        entity[ApplicationEntity::id][EmbeddedNameAndVersion::name] equal name
                    }
                )
                .mapItems { it.toModelWithInvocation() }
        )
    }

    override fun findByNameAndVersion(
        session: HibernateSession,
        user: SecurityPrincipal?,
        name: String,
        version: String
    ): Application {
        val cacheKey = NameAndVersion(name, version)
        val (cached, expiry) = byNameAndVersionCache[cacheKey] ?: Pair(null, 0L)
        if (cached != null && expiry > System.currentTimeMillis()) return cached

        val result = internalByNameAndVersion(session, name, version)
            ?.toModelWithInvocation() ?: throw ApplicationException.NotFound()

        byNameAndVersionCache[cacheKey] = Pair(result, System.currentTimeMillis() + (1000L * 60 * 60))
        return result
    }

    override fun findByNameAndVersionForUser(
        session: HibernateSession,
        user: SecurityPrincipal,
        name: String,
        version: String
    ): ApplicationWithFavorite {
        val entity = internalByNameAndVersion(session, name, version)?.toModelWithInvocation()
            ?: throw ApplicationException.NotFound()
        return preparePageForUser(session, user.username, Page(1, 1, 0, listOf(entity))).items.first()
    }

    override fun listLatestVersion(
        session: HibernateSession,
        user: SecurityPrincipal?,
        paging: NormalizedPaginationRequest
    ): Page<ApplicationSummaryWithFavorite> {
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
            .map { it.toModelWithInvocation() }


        return preparePageForUser(
            session,
            user?.username,
            Page(
                count,
                paging.itemsPerPage,
                paging.page,
                items
            )
        ).mapItems { it.withoutInvocation() }
    }

    override fun create(
        session: HibernateSession,
        user: SecurityPrincipal,
        description: Application,
        originalDocument: String
    ) {
        val existingOwner = findOwnerOfApplication(session, description.metadata.name)
        if (existingOwner != null && !canUserPerformWriteOperation(existingOwner, user)) {
            throw ApplicationException.NotAllowed()
        }

        val existing = internalByNameAndVersion(session, description.metadata.name, description.metadata.version)
        if (existing != null) throw ApplicationException.AlreadyExists()

        val existingTool = toolDAO.internalByNameAndVersion(
            session,
            description.invocation.tool.name,
            description.invocation.tool.version
        ) ?: throw ApplicationException.BadToolReference()

        val entity = ApplicationEntity(
            user.username,
            Date(),
            Date(),
            description.metadata.authors,
            description.metadata.title,
            description.metadata.description,
            description.metadata.website,
            description.invocation,
            existingTool.tool.info.name,
            existingTool.tool.info.version,
            EmbeddedNameAndVersion(description.metadata.name, description.metadata.version)
        )

        session.save(entity)
    }

    override fun createTags(
        session: HibernateSession,
        user: SecurityPrincipal,
        applicationName: String,
        tags: List<String>
    ) {
        val owner = findOwnerOfApplication(session, applicationName)
            ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound)

        if (!canUserPerformWriteOperation(owner, user)) {
            throw RPCException.fromStatusCode(HttpStatusCode.Forbidden, "Not owner of application")
        }
        tags.forEach { tag ->
            val existing = findTag(session, applicationName, tag)

            if (existing != null) {
                return@forEach
            }
            val entity = TagEntity(
                applicationName,
                tag
            )
            session.save(entity)
        }
    }

    override fun deleteTags(
        session: HibernateSession,
        user: SecurityPrincipal,
        applicationName: String,
        tags: List<String>
    ) {
        val owner = findOwnerOfApplication(session, applicationName)
            ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound)

        if (!canUserPerformWriteOperation(owner, user)) {
            throw RPCException.fromStatusCode(HttpStatusCode.Forbidden, "Not owner of application")
        }

        tags.forEach { tag ->
            val existing = findTag(
                session,
                applicationName,
                tag
            ) ?: return@forEach

            session.delete(existing)
        }
    }

    fun findTag(
        session: HibernateSession,
        name: String,
        tag: String
    ): TagEntity? {
        return session
            .criteria<TagEntity> {
                allOf(
                    entity[TagEntity::tag] equal tag,
                    entity[TagEntity::applicationName] equal name
                )
            }.uniqueResult()
    }

    override fun updateDescription(
        session: HibernateSession,
        user: SecurityPrincipal,
        name: String,
        version: String,
        newDescription: String?,
        newAuthors: List<String>?
    ) {
        val existing = internalByNameAndVersion(session, name, version) ?: throw ApplicationException.NotFound()
        if (!canUserPerformWriteOperation(existing.owner, user)) throw ApplicationException.NotAllowed()

        if (newDescription != null) existing.description = newDescription
        if (newAuthors != null) existing.authors = newAuthors

        // We allow for this to be cached for some time. But this instance might as well clear the cache now.
        byNameAndVersionCache.remove(NameAndVersion(name, version))

        session.update(existing)
    }

    private fun internalByNameAndVersion(
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
    ): Page<ApplicationWithFavorite> {
        if (!user.isNullOrBlank()) {
            val allFavorites = session
                .criteria<FavoriteApplicationEntity> { entity[FavoriteApplicationEntity::user] equal user }
                .list()

            val allApplicationsOnPage = page.items.map { it.metadata.name }.toSet()
            val allTagsForApplicationsOnPage = session
                .criteria<TagEntity> { entity[TagEntity::applicationName] isInCollection (allApplicationsOnPage) }
                .resultList


            val preparedPageItems = page.items.map { item ->
                val isFavorite = allFavorites.any { fav ->
                    fav.applicationName == item.metadata.name &&
                            fav.applicationVersion == item.metadata.version
                }

                val allTagsForApplication = allTagsForApplicationsOnPage
                    .filter { item.metadata.name == it.applicationName }
                    .map { it.tag }
                    .toSet()
                    .toList()

                ApplicationWithFavorite(item.metadata, item.invocation, isFavorite, allTagsForApplication)
            }

            return Page(page.itemsInTotal, page.itemsPerPage, page.pageNumber, preparedPageItems)
        } else {
            val preparedPageItems = page.items.map { item ->
                val allTagsForApplication = session
                    .criteria<TagEntity> { entity[TagEntity::applicationName] equal item.metadata.name }
                    .resultList
                    .map { it.tag }
                    .toSet()
                    .toList()

                ApplicationWithFavorite(item.metadata, item.invocation, false, allTagsForApplication)
            }
            return Page(page.itemsInTotal, page.itemsPerPage, page.pageNumber, preparedPageItems)
        }
    }

    override fun createLogo(session: HibernateSession, user: SecurityPrincipal, name: String, imageBytes: ByteArray) {
        val application =
            findOwnerOfApplication(session, name) ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound)

        if (application != user.username && user.role != Role.ADMIN) {
            throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)
        }

        session.saveOrUpdate(
            ApplicationLogoEntity(name, imageBytes)
        )
    }

    override fun clearLogo(session: HibernateSession, user: SecurityPrincipal, name: String) {
        val application =
            findOwnerOfApplication(session, name) ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound)

        if (application != user.username && user.role != Role.ADMIN) {
            throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)
        }

        session.delete(ApplicationLogoEntity[session, name] ?: return)
    }

    override fun fetchLogo(session: HibernateSession, name: String): ByteArray? {
        val logoFromApp = ApplicationLogoEntity[session, name]?.data
        if (logoFromApp != null) return logoFromApp
        val app = internalFindAllByName(session, null, name, PaginationRequest().normalize()).items.firstOrNull()
            ?: return null
        val toolName = app.invocation.tool.name
        return ToolLogoEntity[session, toolName]?.data
    }

    override fun findLatestByTool(
        session: HibernateSession,
        user: SecurityPrincipal,
        tool: String,
        paging: NormalizedPaginationRequest
    ): Page<Application> {
        val count = session
            .typedQuery<Long>(
                """
                    select count (A.id.name)
                    from ApplicationEntity as A
                    where (A.createdAt) in (
                        select max(createdAt)
                        from ApplicationEntity as B
                        where A.id.name = B.id.name
                        group by id.name
                    ) and A.toolName = :toolName
                """.trimIndent()
            )
            .setParameter("toolName", tool)
            .uniqueResult()
            .toInt()

        val items = session
            .typedQuery<ApplicationEntity>(
                """
                    from ApplicationEntity as A 
                    where 
                        (A.createdAt) in (
                            select max(createdAt)
                            from ApplicationEntity as B
                            where A.id.name = B.id.name
                            group by id.name
                        ) and A.toolName = :toolName
                    order by A.id.name
                """.trimIndent()
            )
            .setParameter("toolName", tool)
            .paginatedList(paging)
            .map { it.toModelWithInvocation() }

        return Page(count, paging.itemsPerPage, paging.page, items)
    }

    override fun advancedSearch(
        session: HibernateSession,
        user: SecurityPrincipal,
        name: String?,
        version: String?,
        versionRange: Pair<String, String>?,
        tags: List<String>?,
        description: String?,
        paging: NormalizedPaginationRequest
    ): Page<ApplicationSummary> {
        if (version != null && versionRange != null) {
            throw RPCException.fromStatusCode(
                HttpStatusCode.BadRequest,
                "Version and version range can't both be defined"
            )
        }

        return session.paginatedCriteria<ApplicationEntity>(
            paging,
            predicate = {
                if (name == null && version == null && versionRange == null && tags == null && description == null)
                    throw RPCException.fromStatusCode(HttpStatusCode.BadRequest, "Must provide any argument")

                val namePredicate =
                    if (name != null) entity[ApplicationEntity::id][EmbeddedNameAndVersion::name] like name
                    else literal(true).toPredicate()
                val versionPredicate =
                    if (version != null) entity[ApplicationEntity::id][EmbeddedNameAndVersion::version] equal version
                    else literal(true).toPredicate()
                val tagPredicate =
                    if (tags != null) {
                        val applications = findAppNamesFromTags(session, tags)
                        entity[ApplicationEntity::id][EmbeddedNameAndVersion::name] isInCollection applications
                    } else literal(true).toPredicate()
                val descriptionPredicate =
                    // FIXME: `like` description only allows exact matches without wildcard operators
                    if (description != null) entity[ApplicationEntity::description] like description
                    else literal(true).toPredicate()
                allOf(namePredicate, versionPredicate, tagPredicate, descriptionPredicate)
            }
        ).mapItems { it.toModel() }
    }

    private fun canUserPerformWriteOperation(owner: String, user: SecurityPrincipal): Boolean {
        if (user.role == Role.ADMIN) return true
        return owner == user.username
    }

    private fun normalizeQuery(query: String): String {
        return query.toLowerCase()
    }
}

internal fun ApplicationEntity.toMetadata(): ApplicationMetadata = ApplicationMetadata(
    id.name,
    id.version,
    authors,
    title,
    description,
    website
)

internal fun ApplicationEntity.toModel(): ApplicationSummary {
    return ApplicationSummary(toMetadata())
}

internal fun ApplicationEntity.toModelWithInvocation(): Application {
    return Application(toMetadata(), application)
}

sealed class ApplicationException(why: String, httpStatusCode: HttpStatusCode) : RPCException(why, httpStatusCode) {
    class NotFound : ApplicationException("Not found", HttpStatusCode.NotFound)
    class NotAllowed : ApplicationException("Not allowed", HttpStatusCode.Forbidden)
    class AlreadyExists : ApplicationException("Already exists", HttpStatusCode.Conflict)
    class BadToolReference : ApplicationException("Tool does not exist", HttpStatusCode.BadRequest)
    class BadApplication : ApplicationException("Application does not exists", HttpStatusCode.NotFound)
}
