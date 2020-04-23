package dk.sdu.cloud.app.store.services

import dk.sdu.cloud.Role
import dk.sdu.cloud.Roles
import dk.sdu.cloud.SecurityPrincipal
import dk.sdu.cloud.app.store.api.*
import dk.sdu.cloud.app.store.services.acl.*
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.service.*
import dk.sdu.cloud.service.db.*
import io.ktor.http.HttpStatusCode
import org.hibernate.query.Query
import java.util.*

@Suppress("TooManyFunctions") // Does not make sense to split
class ApplicationHibernateDAO(
    private val toolDAO: ToolHibernateDAO,
    private val aclDAO: AclHibernateDao
) : ApplicationDAO<HibernateSession> {
    private val byNameAndVersionCache = Collections.synchronizedMap(HashMap<NameAndVersion, Pair<Application, Long>>())

    override fun toggleFavorite(session: HibernateSession, user: SecurityPrincipal, appName: String, appVersion: String) {
        val foundApp = internalByNameAndVersion(session, appName, appVersion) ?: throw ApplicationException.BadApplication()

        val isFavorite = isFavorite(session, user, appName, appVersion)

        if (isFavorite) {
            val query = session.createQuery(
                """
                delete from FavoriteApplicationEntity as A
                where A.user = :user
                    and A.applicationName = :name
                    and A.applicationVersion = :version
                """.trimIndent()
            ).setParameter("user", user.username)
                .setParameter("name", appName)
                .setParameter("version", appVersion)

            query.executeUpdate()
        } else {
            val userHasPermission = internalHasPermission(
                session,
                user,
                foundApp.id.name,
                foundApp.id.version,
                ApplicationAccessRight.LAUNCH
            )

            if (userHasPermission) {
                session.save(
                    FavoriteApplicationEntity(
                        foundApp.id.name,
                        foundApp.id.version,
                        user.username
                    )
                )
            } else {
                throw RPCException("Unauthorized favorite request", HttpStatusCode.Unauthorized)
            }
        }
    }

    /*
     * Avoid using if possible, especially in loops
     */
    private fun internalHasPermission(
        session: HibernateSession,
        user: SecurityPrincipal,
        appName: String,
        appVersion: String,
        permission: ApplicationAccessRight
    ): Boolean {
        if (user.role in Roles.PRIVILEDGED) return true
        if (isPublic(session, user, appName, appVersion)) return true
        return aclDAO.hasPermission(
            session,
            UserEntity(user.username, EntityType.USER),
            appName,
            setOf(permission)
        )
    }

    private fun isFavorite(session: HibernateSession, user: SecurityPrincipal, appName: String, appVersion: String): Boolean {
        return 0L != session.typedQuery<Long>(
            """
                select count (A.applicationName)
                from FavoriteApplicationEntity as A
                where A.user = :user
                    and A.applicationName = :name
                    and A.applicationVersion= :version
            """.trimIndent()
        ).setParameter("user", user.username)
            .setParameter("name", appName)
            .setParameter("version", appVersion)
            .uniqueResult()
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
                left join PermissionEntry as permission
                ON permission.key.applicationName = application.id.name
                where
                    fav.user = :user and
                    fav.applicationName = application.id.name and
                    fav.applicationVersion = application.id.version and
                    (application.isPublic = TRUE or permission.key.userEntity = :user or :role in (:privileged))
                order by fav.applicationName
            """.trimIndent()
        ).setParameter("user", user.username)
            .setParameter("role", user.role)
            .setParameterList("privileged", Roles.PRIVILEDGED)
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

    private fun findAppNamesFromTags(
        session: HibernateSession,
        user: SecurityPrincipal,
        tags: List<String>
    ): List<String> {
        return session.createNativeQuery<TagEntity>(
            """
            select T.application_name, T.tag, T.id from {h-schema}application_tags as T
            where T.tag in (:tags)
                and (
                    exists (
                        select A.is_public from {h-schema}applications as A where A.name = T.application_name and A.is_public = true
                    ) or exists (
                        select P.entity from {h-schema}permissions as P where P.application_name = T.application_name and P.entity = :user
                    ) or :role in (:privileged)
                )
            """.trimIndent(), TagEntity::class.java
        )
            .setParameter("user", user.username)
            .setParameterList("tags", tags)
            .setParameter("role", user.role)
            .setParameterList("privileged", Roles.PRIVILEDGED)
            .resultList.distinctBy { it.applicationName }.map { it.applicationName }
    }

    private fun findAppsFromAppNames(
        session: HibernateSession,
        user: SecurityPrincipal,
        applicationNames: List<String>
    ): Pair<Query<ApplicationEntity>, Int> {
        val itemsInTotal = session.typedQuery<Long>(
            """
            select count (A.title)
            from ApplicationEntity as A
            where (A.createdAt) in (
                select max(B.createdAt)
                from ApplicationEntity as B
                where (A.title = B.title)
                group by title
            ) and (A.id.name) in (:applications) and
            (A.isPublic = true or :user in (
                select P.key.userEntity from PermissionEntry as P where A.id.name = P.key.applicationName
            ) or :role in (:privileged))
            """.trimIndent()
        ).setParameter("applications", applicationNames)
            .setParameter("user", user.username)
            .setParameter("role", user.role)
            .setParameterList("privileged", Roles.PRIVILEDGED)
            .uniqueResult()
            .toInt()

        return Pair(
            session.typedQuery<ApplicationEntity>(
                """
            from ApplicationEntity as A
            where (A.createdAt) in (
                select max(createdAt)
                from ApplicationEntity as B
                where (A.title= B.title)
                group by title
            ) and (A.id.name) in (:applications) and
            (A.isPublic = true or :user in (
                select P.key.userEntity from PermissionEntry as P where A.id.name = P.key.applicationName
            ) or :role in (:privileged))
            order by A.title
        """.trimIndent()
            ).setParameter("applications", applicationNames)
                .setParameter("user", user.username)
                .setParameter("role", user.role)
                .setParameterList("privileged", Roles.PRIVILEDGED)
            , itemsInTotal
        )
    }


    override fun searchTags(
        session: HibernateSession,
        user: SecurityPrincipal,
        tags: List<String>,
        paging: NormalizedPaginationRequest
    ): Page<ApplicationSummaryWithFavorite> {
        val applications = findAppNamesFromTags(session, user, tags)

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

        val (apps, itemsInTotal) = findAppsFromAppNames(session, user, applications)
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
        val keywords = trimmedNormalizedQuery.split(" ").filter { it.isNotBlank() }
        if (keywords.size == 1) {
            return doSearch(session, user, trimmedNormalizedQuery, paging)
        }
        val firstTenKeywords = keywords.filter { !it.isBlank() }.take(10)
        return doMultiKeywordSearch(session, user, firstTenKeywords, paging)
    }

    override fun multiKeywordsearch(
        session: HibernateSession,
        user: SecurityPrincipal,
        keywords: List<String>,
        paging: NormalizedPaginationRequest
    ): List<ApplicationEntity> {
        val keywordsQuery = createKeywordQuery(keywords)

        return createMultiKeyWordApplicationEntityQuery(
            session,
            user,
            keywords,
            keywordsQuery
        ).resultList
    }

    private fun createKeywordQuery(keywords: List<String>): String {
        var keywordsQuery = "("
        for (i in keywords.indices) {
            if (i == keywords.lastIndex) {
                keywordsQuery += "lower(A.title) like '%' || :query$i || '%'"
                continue
            }
            keywordsQuery += "lower(A.title) like '%'|| :query$i ||'%' or "
        }
        keywordsQuery += ")"

        return keywordsQuery
    }

    private fun createMultiKeyWordApplicationEntityQuery(
        session: HibernateSession,
        user: SecurityPrincipal,
        keywords: List<String>,
        keywordsQuery: String
    ): Query<ApplicationEntity> {
        return session.typedQuery<ApplicationEntity>(
            """
                from ApplicationEntity as A
                where (A.createdAt) in (
                    select max(createdAt)
                    from ApplicationEntity as B
                    where A.title = B.title
                    group by title
                ) and $keywordsQuery and
                (A.isPublic = true or :user in (
                    select P.key.userEntity from PermissionEntry as P where P.key.applicationName = A.id.name
                ) or :role in (:privileged))
                order by A.title
            """.trimIndent()
        ).setParameter("user", user.username)
            .setParameter("role", user.role)
            .setParameterList("privileged", Roles.PRIVILEDGED)
            .also {
                for ((i, item) in keywords.withIndex()) {
                    it.setParameter("query$i", item)
                }
            }
    }

    private fun doMultiKeywordSearch(
        session: HibernateSession,
        user: SecurityPrincipal,
        keywords: List<String>,
        paging: NormalizedPaginationRequest
    ): Page<ApplicationSummaryWithFavorite> {

        val keywordsQuery = createKeywordQuery(keywords)
        val count = session.typedQuery<Long>(
            """
            select count (A.title)
            from ApplicationEntity as A
            where (A.createdAt) in (
                select max(createdAt)
                from ApplicationEntity as B
                where A.title = B.title
                group by title
            ) and $keywordsQuery and
            (A.isPublic = true or :user in (
                select P.key.userEntity from PermissionEntry as P where A.id.name = P.key.applicationName 
            ) or :role in (:privileged))
            """.trimIndent()
        ).setParameter("user", user.username)
            .setParameter("role", user.role)
            .setParameterList("privileged", Roles.PRIVILEDGED).also {
                for ((i, item) in keywords.withIndex()) {
                    it.setParameter("query$i", item)
                }
            }.uniqueResult().toInt()

        val items = createMultiKeyWordApplicationEntityQuery(
            session,
            user,
            keywords,
            keywordsQuery
        ).paginatedList(paging)
            .map { it.toModelWithInvocation() }


        return preparePageForUser(
            session,
            user.username,
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
        user: SecurityPrincipal,
        normalizedQuery: String,
        paging: NormalizedPaginationRequest
    ): Page<ApplicationSummaryWithFavorite> {
        val count = session.typedQuery<Long>(
            """
               select count(A.title) from ApplicationEntity as A
               where (A.createdAt) in (
                   select max(B.createdAt)
                   from ApplicationEntity as B
                   where A.title = B.title
                   group by title
               ) and lower(A.title) like '%' || :query || '%' and
               (A.isPublic = true or :user in (
                   select P.key.userEntity from PermissionEntry as P where P.key.applicationName = A.id.name
               ) or :role in (:privileged))
            """.trimIndent()
        ).setParameter("query", normalizedQuery)
            .setParameter("user", user.username)
            .setParameter("role", user.role)
            .setParameterList("privileged", Roles.PRIVILEDGED)
            .singleResult.toInt()

        val items = session.createNativeQuery<ApplicationEntity>(
            """
               select * from {h-schema}applications as A
               where (A.created_at) in (
                   select max(B.created_at)
                   from {h-schema}applications as B
                   where A.title = B.title
                   group by title
               ) and lower(A.title) like '%' || :query || '%' and
               (A.is_public = true or :user in (
                   select P.entity from {h-schema}permissions as P where P.application_name = A.name
               ) or :role in (:privileged))
               order by A.title 
            """.trimIndent(), ApplicationEntity::class.java
        )
            .setParameter("query", normalizedQuery)
            .setParameter("user", user.username)
            .setParameter("role", user.role)
            .setParameterList("privileged", Roles.PRIVILEDGED)
            .paginatedList(paging)
            .map { it.toModelWithInvocation() }

        return preparePageForUser(
            session,
            user.username,
            Page(
                count,
                paging.itemsPerPage,
                paging.page,
                items
            )
        ).mapItems { it.withoutInvocation() }
    }

    fun getAllApps(session: HibernateSession, user: SecurityPrincipal): List<ApplicationEntity> {
        return session.typedQuery<ApplicationEntity>(
            """
                from ApplicationEntity as A
                where lower(A.title) like '%' || :query || '%' and
                    (A.isPublic = true or :user in (
                        select P.key.userEntity from PermissionEntry as P where P.key.applicationName = A.id.name
                    ) or :role in (:privileged))
                order by A.title
            """.trimIndent()
        ).setParameter("query", "")
            .setParameter("role", user.role)
            .setParameterList("privileged", Roles.PRIVILEDGED)
            .setParameter("user", user.username).resultList
    }

    override fun findAllByName(
        session: HibernateSession,
        user: SecurityPrincipal?,
        appName: String,
        paging: NormalizedPaginationRequest
    ): Page<ApplicationSummaryWithFavorite> {
        return internalFindAllByName(session, user, appName, paging).mapItems { it.withoutInvocation() }
    }

    private fun internalFindAllByName(
        session: HibernateSession,
        user: SecurityPrincipal?,
        appName: String,
        paging: NormalizedPaginationRequest
    ): Page<ApplicationWithFavoriteAndTags> {
        return preparePageForUser(
            session,
            user?.username,
            session.createNativeQuery<ApplicationEntity>(
                """
                    select * from {h-schema}applications as A
                    where A.name = :name 
                        and (A.is_public = true or :user in (
                            select entity from {h-schema}permissions as P where P.application_name = A.name
                        ) or :role in (:privileged))
                    order by A.created_at desc
                """.trimIndent(), ApplicationEntity::class.java
            ).setParameter("user", user?.username ?: "")
                .setParameter("name", appName)
                .setParameter("role", user?.role ?: Role.UNKNOWN)
                .setParameterList("privileged", Roles.PRIVILEDGED)
                .resultList.paginate(paging).mapItems { it.toModelWithInvocation() }
        )
    }

    override fun findByNameAndVersion(
        session: HibernateSession,
        user: SecurityPrincipal?,
        appName: String,
        appVersion: String
    ): Application {
        val cacheKey = NameAndVersion(appName, appVersion)
        val (cached, expiry) = byNameAndVersionCache[cacheKey] ?: Pair(null, 0L)
        if (cached != null && expiry > System.currentTimeMillis()) {
            if (internalHasPermission(
                    session,
                    user!!,
                    cached.metadata.name,
                    cached.metadata.version,
                    ApplicationAccessRight.LAUNCH
                )
            ) {
                return cached
            }
        }

        val result = internalByNameAndVersion(session, appName, appVersion)
            ?.toModelWithInvocation() ?: throw ApplicationException.NotFound()

        if (internalHasPermission(
                session,
                user!!,
                result.metadata.name,
                result.metadata.version,
                ApplicationAccessRight.LAUNCH
            )
        ) {
            byNameAndVersionCache[cacheKey] = Pair(result, System.currentTimeMillis() + (1000L * 60 * 60))
            return result
        } else {
            throw ApplicationException.NotFound()
        }
    }

    override fun findBySupportedFileExtension(
        session: HibernateSession,
        user: SecurityPrincipal,
        fileExtensions: Set<String>
    ): List<ApplicationWithExtension> {
        var query = ""
        query += """
            select A.*
            from app_store.favorited_by as F,
                app_store.applications as A
            where F.the_user = :user
              and F.application_name = A.name
              and F.application_version = A.version
              and (A.application -> 'applicationType' = '"WEB"'
                or A.application -> 'applicationType' = '"VNC"'
              ) and (
        """

        for (index in fileExtensions.indices) {
            query += """ A.application -> 'fileExtensions' @> jsonb_build_array(:ext$index) """
            if (index != fileExtensions.size - 1) {
                query += "or "
            }
        }

        query += """
              )
        """

        return session
            .createNativeQuery<ApplicationEntity>(
                query, ApplicationEntity::class.java
            )
            .setParameter("user", user.username)
            .apply {
                fileExtensions.forEachIndexed { index, ext ->
                    setParameter("ext$index", ext)
                }
            }
            .list()
            .filter { entity ->
                entity.application.parameters.all { it.optional }
            }
            .map {
                ApplicationWithExtension(it.toMetadata(), it.application.fileExtensions)
            }

    }

    override fun findByNameAndVersionForUser(
        session: HibernateSession,
        user: SecurityPrincipal,
        appName: String,
        appVersion: String
    ): ApplicationWithFavoriteAndTags {
        if (!internalHasPermission(
                session,
                user,
                appName,
                appVersion,
                ApplicationAccessRight.LAUNCH
            )
        ) throw ApplicationException.NotFound()

        val entity = internalByNameAndVersion(session, appName, appVersion)?.toModelWithInvocation()
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
                    where A.id.name = B.id.name and (
                        A.isPublic = true or
                        :user in (
                            select P.key.userEntity from PermissionEntry as P where P.key.applicationName = A.id.name
                        ) or :role in (:privileged)
                    )
                    group by id.name
                )
            """.trimIndent()
        ).setParameter("user", user?.username ?: "")
            .setParameter("role", user?.role ?: Role.UNKNOWN)
            .setParameterList("privileged", Roles.PRIVILEDGED)
            .uniqueResult()
            .toInt()


        val items = session.typedQuery<ApplicationEntity>(
            """
                from ApplicationEntity as A where (A.createdAt) in (
                    select max(createdAt)
                    from ApplicationEntity as B
                    where A.id.name = B.id.name  and (
                        A.isPublic = true or
                        :user in (
                            select P.key.userEntity from PermissionEntry as P where P.key.applicationName = A.id.name
                        ) or :role in (:privileged)
                    )
                    group by id.name
                )
                order by A.id.name
            """.trimIndent()
        ).setParameter("user", user?.username ?: "")
            .setParameter("role", user?.role ?: Role.UNKNOWN)
            .setParameterList("privileged", Roles.PRIVILEDGED)
            .paginatedList(paging)
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
            true,
            EmbeddedNameAndVersion(description.metadata.name, description.metadata.version)
        )

        session.save(entity)
    }

    override fun delete(session: HibernateSession, user: SecurityPrincipal, appName: String, appVersion: String) {
        val existingOwner = findOwnerOfApplication(session, appName)
        if (existingOwner != null && !canUserPerformWriteOperation(existingOwner, user)) {
            throw ApplicationException.NotAllowed()
        }

        // Prevent deletion of last version of application
        if (internalFindAllByName(session, user, appName, paging = NormalizedPaginationRequest(25, 0)).itemsInTotal <= 1) {
            throw ApplicationException.NotAllowed()
        }

        val existingApp = internalByNameAndVersion(session, appName, appVersion) ?: throw ApplicationException.NotFound()

        session.delete(existingApp)
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
        appName: String,
        tag: String
    ): TagEntity? {
        return session
            .criteria<TagEntity> {
                allOf(
                    entity[TagEntity::tag] equal tag,
                    entity[TagEntity::applicationName] equal appName
                )
            }.uniqueResult()
    }

    override fun findTagsForApp(
        session: HibernateSession,
        applicationName: String
    ): List<TagEntity> {
        return session.criteria<TagEntity> {
            allOf(
                entity[TagEntity::applicationName] equal applicationName
            )
        }.resultList
    }

    override fun updateDescription(
        session: HibernateSession,
        user: SecurityPrincipal,
        appName: String,
        appVersion: String,
        newDescription: String?,
        newAuthors: List<String>?
    ) {
        val existing = internalByNameAndVersion(session, appName, appVersion) ?: throw ApplicationException.NotFound()
        if (!canUserPerformWriteOperation(existing.owner, user)) throw ApplicationException.NotAllowed()

        if (newDescription != null) existing.description = newDescription
        if (newAuthors != null) existing.authors = newAuthors

        // We allow for this to be cached for some time. But this instance might as well clear the cache now.
        byNameAndVersionCache.remove(NameAndVersion(appName, appVersion))

        session.update(existing)
    }

    private fun internalByNameAndVersion(
        session: HibernateSession,
        appName: String,
        appVersion: String
    ): ApplicationEntity? {
        return session
            .criteria<ApplicationEntity> {
                (entity[ApplicationEntity::id][EmbeddedNameAndVersion::name] equal appName) and
                        (entity[ApplicationEntity::id][EmbeddedNameAndVersion::version] equal appVersion)
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

    override fun isOwnerOfApplication(session: HibernateSession, user: SecurityPrincipal, appName: String): Boolean =
        findOwnerOfApplication(session, appName)!! == user.username


    override fun preparePageForUser(
        session: HibernateSession,
        user: String?,
        page: Page<Application>
    ): Page<ApplicationWithFavoriteAndTags> {
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

                ApplicationWithFavoriteAndTags(item.metadata, item.invocation, isFavorite, allTagsForApplication)
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

                ApplicationWithFavoriteAndTags(item.metadata, item.invocation, false, allTagsForApplication)
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

    override fun isPublic(session: HibernateSession, user: SecurityPrincipal, appName: String, appVersion: String): Boolean {
        val result = session.criteria<ApplicationEntity> {
            allOf(
                entity[ApplicationEntity::id][EmbeddedNameAndVersion::name] equal appName,
                entity[ApplicationEntity::id][EmbeddedNameAndVersion::version] equal appVersion
            )
        }

        if (result.uniqueResult() == null) throw ApplicationException.NotFound()

        return result.uniqueResult().isPublic
    }

    override fun setPublic(
        session: HibernateSession,
        user: SecurityPrincipal,
        appName: String,
        appVersion: String,
        public: Boolean
    ) {
        if (user.role !in Roles.PRIVILEDGED) throw ApplicationException.NotAllowed()
        val existing = internalByNameAndVersion(session, appName, appVersion) ?: throw ApplicationException.NotFound()
        if (!canUserPerformWriteOperation(existing.owner, user)) throw ApplicationException.NotAllowed()

        existing.isPublic = public

        session.update(existing)

    }

    override fun findLatestByTool(
        session: HibernateSession,
        user: SecurityPrincipal,
        tool: String,
        paging: NormalizedPaginationRequest
    ): Page<Application> {
        val count = session.typedQuery<Long>(
            """
                select count (A.id.name)
                from ApplicationEntity as A
                where (A.createdAt) in (
                    select max(createdAt)
                    from ApplicationEntity as B
                    where A.id.name = B.id.name
                    group by id.name
                ) and A.toolName = :toolName and (
                    A.isPublic = true or
                    :user in (
                        select P.key.userEntity from PermissionEntry as P where P.key.applicationName = A.id.name
                    ) or :role in (:privileged)
                )
            """.trimIndent()
        )
            .setParameter("toolName", tool)
            .setParameter("user", user.username)
            .setParameter("role", user.role)
            .setParameterList("privileged", Roles.PRIVILEDGED)
            .uniqueResult()
            .toInt()

        val items = session.typedQuery<ApplicationEntity>(
            """
                from ApplicationEntity as A 
                where 
                    (A.createdAt) in (
                        select max(createdAt)
                        from ApplicationEntity as B
                        where A.id.name = B.id.name
                        group by id.name
                    ) and A.toolName = :toolName and (
                        A.isPublic = true or
                        :user in (
                            select P.key.userEntity from PermissionEntry as P where P.key.applicationName = A.id.name
                        ) or :role in (:privileged)
                    )
                order by A.id.name
            """.trimIndent()
        )
            .setParameter("toolName", tool)
            .setParameter("user", user.username)
            .setParameter("role", user.role)
            .setParameterList("privileged", Roles.PRIVILEDGED)
            .paginatedList(paging)
            .map { it.toModelWithInvocation() }

        return Page(count, paging.itemsPerPage, paging.page, items)
    }

    override fun findAllByID(
        session: HibernateSession,
        user: SecurityPrincipal,
        embeddedNameAndVersionList: List<EmbeddedNameAndVersion>,
        paging: NormalizedPaginationRequest
    ): List<ApplicationEntity> {
        return session.criteria<ApplicationEntity>(
            predicate = {
                val idPredicate =
                    if (embeddedNameAndVersionList.isNotEmpty()) {
                        if (embeddedNameAndVersionList.size > 1) {
                            embeddedNameAndVersionList.map { n ->
                                (builder.lower(entity[ApplicationEntity::id][EmbeddedNameAndVersion::name]) equal n.name) and
                                        (builder.lower(entity[ApplicationEntity::id][EmbeddedNameAndVersion::version]) equal n.version)
                            }
                        } else {
                            val id = embeddedNameAndVersionList.first()
                            listOf(
                                (builder.lower(entity[ApplicationEntity::id][EmbeddedNameAndVersion::name]) equal id.name) and
                                        (builder.lower(entity[ApplicationEntity::id][EmbeddedNameAndVersion::version]) equal id.version)
                            )
                        }
                    } else listOf(literal(false).toPredicate())
                allOf(anyOf(*idPredicate.toTypedArray()))
            }
        ).resultList.toList()
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
    website,
    isPublic
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
