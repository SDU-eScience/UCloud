package dk.sdu.cloud.app.store.services

import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.readValues
import com.github.jasync.sql.db.RowData
import dk.sdu.cloud.Role
import dk.sdu.cloud.Roles
import dk.sdu.cloud.SecurityPrincipal
import dk.sdu.cloud.app.store.api.*
import dk.sdu.cloud.app.store.services.ApplicationTable.application
import dk.sdu.cloud.app.store.services.acl.*
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.service.*
import dk.sdu.cloud.service.db.*
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.getField
import dk.sdu.cloud.service.db.async.sendPreparedStatement
import dk.sdu.cloud.service.db.async.withSession
import io.ktor.http.HttpStatusCode
import org.hibernate.query.Query
import java.util.*

@Suppress("TooManyFunctions") // Does not make sense to split
class AppStoreAsyncDAO(
    private val toolDAO: ToolHibernateDAO,
    private val aclDAO: AclHibernateDao
) : ApplicationDAO {
    private val byNameAndVersionCache = Collections.synchronizedMap(HashMap<NameAndVersion, Pair<Application, Long>>())

    private suspend fun findAppNamesFromTags(
        ctx: DBContext,
        user: SecurityPrincipal,
        project: String?,
        memberGroups: List<String>,
        tags: List<String>
    ): List<String> {
        return session.createNativeQuery<TagEntity>(
            """
            select T.application_name, T.tag, T.id from {h-schema}application_tags as T, {h-schema}applications as A
            where T.application_name = A.name and T.tag in (:tags) and (
                (
                    A.is_public = TRUE
                ) or (
                    cast(:project as text) is null and :user in (
                        select P1.username from {h-schema}permissions as P1 where P1.application_name = A.name
                    )
                ) or (
                    cast(:project as text) is not null and exists (
                        select P2.project_group from {h-schema}permissions as P2 where
                            P2.application_name = A.name and
                                P2.project = cast(:project as text) and
                                P2.project_group in (:groups)
                    )
                ) or (
                    :role in (:privileged)
                )
            ) 
            """.trimIndent(), TagEntity::class.java
        )
            .setParameter("user", user.username)
            .setParameter("project", project)
            .setParameterList("groups", memberGroups)
            .setParameterList("tags", tags)
            .setParameter("role", user.role)
            .setParameterList("privileged", Roles.PRIVILEDGED)
            .resultList.distinctBy { it.applicationName }.map { it.applicationName }
    }

    private suspend fun findAppsFromAppNames(
        ctx: DBContext,
        user: SecurityPrincipal,
        project: String?,
        memberGroups: List<String>,
        applicationNames: List<String>
    ): Pair<Query<ApplicationEntity>, Int> {

        val itemsInTotal = session.createNativeQuery<ApplicationEntity>(
            """
            select A.*
            from {h-schema}applications as A
            where (A.created_at) in (
                select max(B.created_at)
                from {h-schema}applications as B
                where (A.title = B.title)
                group by title
            ) and (A.name) in (:applications) and (
                (
                    A.is_public = TRUE
                ) or (
                    cast(:project as text) is null and :user in (
                        select P1.username from {h-schema}permissions as P1 where P1.application_name = A.name
                    )
                ) or (
                    cast(:project as text) is not null and exists (
                        select P2.project_group from {h-schema}permissions as P2 where
                            P2.application_name = A.name and
                                P2.project = cast(:project as text) and
                                P2.project_group in (:groups)
                    )
                ) or (
                    :role in (:privileged)
                ) 
            )
            """.trimIndent(), ApplicationEntity::class.java
        )
            .setParameterList("applications", applicationNames)
            .setParameter("user", user.username)
            .setParameter("project", project)
            .setParameterList("groups", memberGroups)
            .setParameter("role", user.role)
            .setParameterList("privileged", Roles.PRIVILEDGED)
            .resultList.size

        return Pair(
            session.createNativeQuery<ApplicationEntity>(
                """
                select A.*
                from {h-schema}applications as A
                where (A.created_at) in (
                    select max(created_at)
                    from {h-schema}applications as B
                    where (A.title= B.title)
                    group by title
                ) and (A.name) in (:applications) and (
                    (
                        A.is_public = TRUE
                    ) or (
                        cast(:project as text) is null and :user in (
                            select P1.username from {h-schema}permissions as P1 where P1.application_name = A.name
                        )
                    ) or (
                        cast(:project as text) is not null and exists (
                            select P2.project_group from {h-schema}permissions as P2 where
                                P2.application_name = A.name and
                                P2.project = cast(:project as text) and
                                P2.project_group in (:groups)
                        )
                    ) or (
                        :role in (:privileged)
                    ) 
                )
                order by A.title
            """.trimIndent(), ApplicationEntity::class.java
            )
                .setParameterList("applications", applicationNames)
                .setParameter("user", user.username)
                .setParameter("project", project)
                .setParameterList("groups", memberGroups)
                .setParameter("role", user.role)
                .setParameterList("privileged", Roles.PRIVILEDGED)
            , itemsInTotal
        )
    }

    suspend fun getAllApps(ctx: DBContext, user: SecurityPrincipal): List<ApplicationEntity> {
        return session.typedQuery<ApplicationEntity>(
            """
                from ApplicationEntity as A
                where lower(A.title) like '%' || :query || '%' and
                    (A.isPublic = true or :user in (
                        select P.key.user from PermissionEntry as P where P.key.applicationName = A.id.name
                    ) or :role in (:privileged))
                order by A.title
            """.trimIndent()
        ).setParameter("query", "")
            .setParameter("role", user.role)
            .setParameterList("privileged", Roles.PRIVILEDGED)
            .setParameter("user", user.username).resultList
    }

    override suspend fun findAllByName(
        ctx: DBContext,
        user: SecurityPrincipal?,
        currentProject: String?,
        projectGroups: List<String>,
        appName: String,
        paging: NormalizedPaginationRequest
    ): Page<ApplicationSummaryWithFavorite> {
        return internalFindAllByName(
            session,
            user,
            currentProject,
            projectGroups,
            appName,
            paging
        ).mapItems { it.withoutInvocation() }
    }

    override suspend fun findByNameAndVersion(
        ctx: DBContext,
        user: SecurityPrincipal?,
        currentProject: String?,
        projectGroups: List<String>,
        appName: String,
        appVersion: String
    ): Application {
        val cacheKey = NameAndVersion(appName, appVersion)
        val (cached, expiry) = byNameAndVersionCache[cacheKey] ?: Pair(null, 0L)
        if (cached != null && expiry > System.currentTimeMillis()) {
            if (internalHasPermission(
                    session,
                    user!!,
                    currentProject,
                    projectGroups,
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
                currentProject,
                projectGroups,
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

    override suspend fun findBySupportedFileExtension(
        ctx: DBContext,
        user: SecurityPrincipal,
        currentProject: String?,
        projectGroups: List<String>,
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

    override suspend fun findByNameAndVersionForUser(
        ctx: DBContext,
        user: SecurityPrincipal,
        currentProject: String?,
        projectGroups: List<String>,
        appName: String,
        appVersion: String
    ): ApplicationWithFavoriteAndTags {
        if (!internalHasPermission(
                session,
                user,
                currentProject,
                projectGroups,
                appName,
                appVersion,
                ApplicationAccessRight.LAUNCH
            )
        ) throw ApplicationException.NotFound()

        val entity = internalByNameAndVersion(session, appName, appVersion)?.toModelWithInvocation()
            ?: throw ApplicationException.NotFound()

        return preparePageForUser(session, user.username, Page(1, 1, 0, listOf(entity))).items.first()
    }

    override suspend fun listLatestVersion(
        ctx: DBContext,
        user: SecurityPrincipal?,
        currentProject: String?,
        projectGroups: List<String>,
        paging: NormalizedPaginationRequest
    ): Page<ApplicationSummaryWithFavorite> {
        val groups = if (projectGroups.isEmpty()) {
            listOf("")
        } else {
            projectGroups
        }

        val userString = if (user != null) {
            if (user.username != null) {
                user.username
            } else {
                ""
            }
        } else {
            ""
        }

        val cleanRole = if (user != null) {
            if (user.role != null) {
                user.role
            } else {
                Role.UNKNOWN
            }
        } else {
            Role.UNKNOWN
        }

        val count = session.createNativeQuery<ApplicationEntity>(
            """
                select A.*
                from {h-schema}applications as A where (A.created_at) in (
                    select max(created_at)
                    from {h-schema}applications as B
                    where A.name = B.name and (
                        (
                            B.is_public = TRUE
                        ) or (
                            cast(:project as text) is null and :user in (
                                select P1.username from {h-schema}permissions as P1 where P1.application_name = B.name
                            )
                        ) or (
                            cast(:project as text) is not null and exists (
                                select P2.project_group from {h-schema}permissions as P2 where
                                    P2.application_name = B.name and
                                        P2.project = cast(:project as text) and
                                        P2.project_group in (:groups)
                            )
                        ) or (
                            :role in (:privileged)
                        )
                    )
                )
            """.trimIndent(), ApplicationEntity::class.java
        ).setParameter("user", userString)
            .setParameter("role", cleanRole)
            .setParameter("project", currentProject)
            .setParameterList("groups", groups)
            .setParameterList("privileged", Roles.PRIVILEDGED)
            .list().size

        val items = session.createNativeQuery<ApplicationEntity>(
            """ select A.*
                from {h-schema}applications as A where (A.created_at) in (
                    select max(created_at)
                    from {h-schema}applications as B
                    where A.name = B.name and (
                         (
                             B.is_public = TRUE
                         ) or (
                             cast(:project as text) is null and :user in (
                                 select P1.username from app_store.permissions as P1 where P1.application_name = B.name
                             )
                         ) or (
                             cast(:project as text) is not null and exists (
                                 select P2.project_group from app_store.permissions as P2 where
                                     P2.application_name = B.name and
                                         P2.project = cast(:project as text) and
                                         P2.project_group in (:groups)
                             )
                         ) or (
                             :role in (:privileged)
                         )
                    )
                    group by B.name
                )
                order by A.name
            """.trimIndent(), ApplicationEntity::class.java
        ).setParameter("user", userString)
            .setParameter("role", cleanRole)
            .setParameter("project", currentProject)
            .setParameterList("groups", groups)
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

    override suspend fun create(
        ctx: DBContext,
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

    override suspend fun delete(
        ctx: DBContext,
        user: SecurityPrincipal,
        project: String?,
        projectGroups: List<String>,
        appName: String,
        appVersion: String
    ) {
        val existingOwner = findOwnerOfApplication(session, appName)
        if (existingOwner != null && !canUserPerformWriteOperation(existingOwner, user)) {
            throw ApplicationException.NotAllowed()
        }

        // Prevent deletion of last version of application
        if (internalFindAllByName(
                session,
                user,
                project,
                projectGroups,
                appName,
                paging = NormalizedPaginationRequest(25, 0)
            ).itemsInTotal <= 1
        ) {
            throw ApplicationException.NotAllowed()
        }

        val existingApp =
            internalByNameAndVersion(session, appName, appVersion) ?: throw ApplicationException.NotFound()

        cleanupBeforeDelete(session, existingApp.id.name, existingApp.id.version)

        session.delete(existingApp)
    }

    private suspend fun cleanupBeforeDelete(ctx: DBContext, appName: String, appVersion: String) {
        val favoriteAppEntities = session.criteria<FavoriteApplicationEntity> {
            allOf(
                entity[FavoriteApplicationEntity::applicationName] equal appName,
                entity[FavoriteApplicationEntity::applicationVersion] equal appVersion
            )
        }.resultList

        favoriteAppEntities.forEach { favorite ->
            session.delete(favorite)
        }
    }

    override suspend fun updateDescription(
        ctx: DBContext,
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

    override suspend fun isOwnerOfApplication(ctx: DBContext, user: SecurityPrincipal, appName: String): Boolean =
        findOwnerOfApplication(session, appName)!! == user.username


    override suspend fun preparePageForUser(
        ctx: DBContext,
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

    override suspend fun findLatestByTool(
        ctx: DBContext,
        user: SecurityPrincipal,
        project: String?,
        projectGroups: List<String>,
        tool: String,
        paging: NormalizedPaginationRequest
    ): Page<Application> {
        val groups = if (projectGroups.isEmpty()) {
            listOf("")
        } else {
            projectGroups
        }

        val count = session.createNativeQuery<ApplicationEntity>(
            """
                select A.*
                from {h-schema}applications as A
                where (A.created_at) in (
                    select max(created_at)
                    from {h-schema}applications as B
                    where A.name = B.name
                    group by name
                ) and A.tool_name = :toolName and (
                    (
                        A.is_public = TRUE
                    ) or (
                        cast(:project as text) is null and :user in (
                            select P1.username from app_store.permissions as P1 where P1.application_name = A.name
                        )
                    ) or (
                        cast(:project as text) is not null and exists (
                            select P2.project_group from app_store.permissions as P2 where
                                P2.application_name = A.name and
                                P2.project = cast(:project as text) and
                                P2.project_group in (:groups)
                        )
                    ) or (
                        :role in (:privileged)
                    ) 
                )
            """.trimIndent(), ApplicationEntity::class.java
        )
            .setParameter("toolName", tool)
            .setParameter("user", user.username)
            .setParameter("role", user.role)
            .setParameter("project", project)
            .setParameterList("groups", groups)
            .setParameterList("privileged", Roles.PRIVILEDGED)
            .resultList.size

        val items = session.createNativeQuery<ApplicationEntity>(
            """select A.*
                from {h-schema}applications as A 
                where 
                    (A.created_at) in (
                        select max(created_at)
                        from {h-schema}applications as B
                        where A.name = B.name
                        group by name
                    ) and A.tool_name = :toolName and (
                        (
                            A.is_public = TRUE
                        ) or (
                            cast(:project as text) is null and :user in (
                                select P1.username from {h-schema}permissions as P1 where P1.application_name = A.name
                            )
                        ) or (
                            cast(:project as text) is not null and exists (
                                select P2.project_group
                                from {h-schema}permissions as P2
                                where
                                    P2.application_name = A.name and
                                    P2.project = cast(:project as text) and
                                    P2.project_group in (:groups)
                            )
                        ) or (
                            :role in (:privileged)
                        ) 
                    )
                order by A.name
            """.trimIndent(), ApplicationEntity::class.java
        )
            .setParameter("toolName", tool)
            .setParameter("user", user.username)
            .setParameter("role", user.role)
            .setParameter("project", project)
            .setParameterList("groups", groups)
            .setParameterList("privileged", Roles.PRIVILEDGED)
            .paginatedList(paging)
            .map { it.toModelWithInvocation() }

        return Page(count, paging.itemsPerPage, paging.page, items)
    }

    override suspend fun findAllByID(
        ctx: DBContext,
        user: SecurityPrincipal,
        project: String?,
        projectGroups: List<String>,
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
}

internal fun RowData.toApplicationMetadata(): ApplicationMetadata {
    val authors = defaultMapper.readValue<List<String>>(getField(ApplicationTable.authors))

    ApplicationMetadata(
        getField(ApplicationTable.idName),
        getField(ApplicationTable.idVersion),
        authors,
        getField(ApplicationTable.title),
        getField(ApplicationTable.description),
        getField(ApplicationTable.website),
        getField(ApplicationTable.isPublic)
    )
}

internal fun RowData.toApplicationSummary(): ApplicationSummary {
    return ApplicationSummary(toApplicationMetadata())
}

internal fun RowData.toApplicationWithInvocation(): Application {
    return Application(
        toApplicationMetadata(),
        defaultMapper.readValue<ApplicationInvocationDescription>(getField(ApplicationTable.application))
    )
}

sealed class ApplicationException(why: String, httpStatusCode: HttpStatusCode) : RPCException(why, httpStatusCode) {
    class NotFound : ApplicationException("Not found", HttpStatusCode.NotFound)
    class NotAllowed : ApplicationException("Not allowed", HttpStatusCode.Forbidden)
    class AlreadyExists : ApplicationException("Already exists", HttpStatusCode.Conflict)
    class BadToolReference : ApplicationException("Tool does not exist", HttpStatusCode.BadRequest)
    class BadApplication : ApplicationException("Application does not exists", HttpStatusCode.NotFound)
}
