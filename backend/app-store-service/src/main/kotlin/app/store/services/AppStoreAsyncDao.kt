package dk.sdu.cloud.app.store.services

import com.fasterxml.jackson.module.kotlin.readValue
import com.github.jasync.sql.db.RowData
import dk.sdu.cloud.Role
import dk.sdu.cloud.Roles
import dk.sdu.cloud.SecurityPrincipal
import dk.sdu.cloud.app.store.api.*
import dk.sdu.cloud.app.store.services.acl.AclAsyncDao
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.service.*
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.getField
import dk.sdu.cloud.service.db.async.insert
import dk.sdu.cloud.service.db.async.sendPreparedStatement
import dk.sdu.cloud.service.db.async.withSession
import io.ktor.http.HttpStatusCode
import org.joda.time.DateTimeZone
import org.joda.time.LocalDateTime
import java.util.*

@Suppress("TooManyFunctions") // Does not make sense to split
class AppStoreAsyncDao(
    private val toolDAO: ToolAsyncDao,
    private val aclDAO: AclAsyncDao,
    private val publicAsyncDao: ApplicationPublicAsyncDao
) {
    private val byNameAndVersionCache = Collections.synchronizedMap(HashMap<NameAndVersion, Pair<Application, Long>>())

    internal suspend fun findAppNamesFromTags(
        ctx: DBContext,
        user: SecurityPrincipal,
        project: String?,
        memberGroups: List<String>,
        tags: List<String>
    ): List<String> {
        return ctx.withSession { session ->
            session
                .sendPreparedStatement(
                    {
                        setParameter("user", user.username)
                        setParameter("project", project)
                        setParameter("groups", memberGroups)
                        setParameter("tags", tags)
                        setParameter("isAdmin", Roles.PRIVILEGED.contains(user.role))
                    },
                    """
                    SELECT T.application_name, T.tag, T.id FROM application_tags AS T, applications AS A
                    WHERE T.application_name = A.name AND T.tag IN (select unnest(:tags::text[])) AND (
                        (
                            A.is_public = TRUE
                        ) OR (
                            cast(:project as text) is null AND :user IN (
                                SELECT P1.username FROM permissions AS P1 WHERE P1.application_name = A.name
                            )
                        ) OR (
                            cast(:project as text) IS not null AND exists (
                                SELECT P2.project_group FROM permissions AS P2 WHERE
                                    P2.application_name = A.name AND
                                    P2.project = cast(:project as text) AND
                                    P2.project_group IN (select unnest (:groups::text[]))
                            )
                        ) or (
                            :isAdmin
                        )
                    ) 
                    """
                )
                .rows
                .toList()
                .distinctBy {
                    it.getField(TagTable.applicationName)
                }
                .map {
                    it.getField(TagTable.applicationName)
                }
        }
    }

    internal suspend fun findAppsFromAppNames(
        ctx: DBContext,
        user: SecurityPrincipal,
        project: String?,
        memberGroups: List<String>,
        applicationNames: List<String>
    ): Pair<List<Application>, Int> {
        val items = ctx.withSession { session ->
            session
                .sendPreparedStatement(
                    {
                        setParameter("applications", applicationNames)
                        setParameter("user", user.username)
                        setParameter("project", project)
                        setParameter("groups", memberGroups)
                        setParameter("isAdmin", Roles.PRIVILEGED.contains(user.role))
                    },
                    """
                SELECT A.*
                FROM applications as A
                WHERE (A.created_at) IN (
                    SELECT MAX(B.created_at)
                    FROM applications as B
                    WHERE (A.title = B.title)
                    GROUP BY title
                ) AND (A.name) IN (select unnest(:applications::text[])) AND (
                    (
                        A.is_public = TRUE
                    ) or (
                        cast(:project as text) is null and :user in (
                            SELECT P1.username FROM permissions AS P1 WHERE P1.application_name = A.name
                        )
                    ) or (
                        cast(:project as text) is not null AND exists (
                            SELECT P2.project_group FROM permissions AS P2 WHERE
                                P2.application_name = A.name and
                                P2.project = cast(:project as text) and
                                P2.project_group in (select unnest(:groups::text[]))
                        )
                    ) or (
                        :isAdmin
                    ) 
                )
                """
                )
                .rows
                .toList()
                .map { it.toApplicationWithInvocation() }

        }

        return Pair(
            items, items.size
        )
    }

    suspend fun getAllApps(ctx: DBContext, user: SecurityPrincipal): List<RowData> {
        return ctx.withSession { session ->
            session
                .sendPreparedStatement(
                    {
                        setParameter("query", "")
                        setParameter("isAdmin", Roles.PRIVILEGED.contains(user.role))
                        setParameter("user", user.username)
                    },
                    """
                    SELECT *
                    FROM applications as A
                    WHERE LOWER(title) like '%' || :query || '%' AND
                    (
                        is_public = TRUE OR 
                        :user IN (
                            SELECT P.username FROM permissions AS P WHERE P.application_name = A.name
                        ) OR :isAdmin
                    )
                    ORDER BY A.title
                """
                )
                .rows
                .toList()
        }
    }

    suspend fun findAllByName(
        ctx: DBContext,
        user: SecurityPrincipal?,
        currentProject: String?,
        projectGroups: List<String>,
        appName: String,
        paging: NormalizedPaginationRequest
    ): Page<ApplicationSummaryWithFavorite> {
        return ctx.withSession { session ->
            internalFindAllByName(
                session,
                user,
                currentProject,
                projectGroups,
                appName,
                paging,
                this
            ).mapItems { it.withoutInvocation() }
        }
    }

    suspend fun findByNameAndVersion(
        ctx: DBContext,
        user: SecurityPrincipal?,
        currentProject: String?,
        projectGroups: List<String>,
        appName: String,
        appVersion: String
    ): Application {
        val cacheKey = NameAndVersion(appName, appVersion)
        val (cached, expiry) = byNameAndVersionCache[cacheKey] ?: Pair(null, 0L)
        if (cached != null && expiry > Time.now()) {
            val hasPermission = ctx.withSession { session ->
                    internalHasPermission(
                        session,
                        user!!,
                        currentProject,
                        projectGroups,
                        cached.metadata.name,
                        cached.metadata.version,
                        ApplicationAccessRight.LAUNCH,
                        publicAsyncDao,
                        aclDAO
                    )
                }
            if (hasPermission) {
                return cached
            }
        }

        val result = ctx.withSession { session -> internalByNameAndVersion(session, appName, appVersion)}
            ?.toApplicationWithInvocation() ?: throw ApplicationException.NotFound()

        val hasPermission = ctx.withSession { session ->
                internalHasPermission(
                    session,
                    user!!,
                    currentProject,
                    projectGroups,
                    result.metadata.name,
                    result.metadata.version,
                    ApplicationAccessRight.LAUNCH,
                    publicAsyncDao,
                    aclDAO
                )
            }
        if (hasPermission) {
            byNameAndVersionCache[cacheKey] = Pair(result, Time.now() + (1000L * 60 * 60))
            return result
        } else {
            throw ApplicationException.NotFound()
        }
    }

    suspend fun findBySupportedFileExtension(
        ctx: DBContext,
        user: SecurityPrincipal,
        currentProject: String?,
        projectGroups: List<String>,
        fileExtensions: Set<String>
    ): List<ApplicationWithExtension> {
        return ctx.withSession { session ->
            session
                .sendPreparedStatement(
                    {
                        setParameter("user", user.username)
                        setParameter("ext", fileExtensions.toList())
                    },
                    """
                        SELECT *
                            FROM favorited_by as F,
                            applications as A
                        WHERE F.the_user = :user
                            AND F.application_name = A.name
                            AND F.application_version = A.version
                            AND (A.application -> 'applicationType' = '"WEB"' OR A.application -> 'applicationType' = '"VNC"') 
                            AND (A.application -> 'fileExtensions' ??| :ext::text[])
                    """
                )
                .rows
                .toList()
                .filter { rowData ->
                    defaultMapper.readValue<ApplicationInvocationDescription>(rowData.getField(ApplicationTable.application))
                        .parameters.all {
                        it.optional
                    }
                }
                .map {
                    ApplicationWithExtension(
                        it.toApplicationMetadata(),
                        defaultMapper.readValue<ApplicationInvocationDescription>(it.getField(ApplicationTable.application)).fileExtensions
                    )
                }
        }
    }

    suspend fun findByNameAndVersionForUser(
        ctx: DBContext,
        user: SecurityPrincipal,
        currentProject: String?,
        projectGroups: List<String>,
        appName: String,
        appVersion: String
    ): ApplicationWithFavoriteAndTags {
        if (!ctx.withSession { session ->
                internalHasPermission(
                    session,
                    user,
                    currentProject,
                    projectGroups,
                    appName,
                    appVersion,
                    ApplicationAccessRight.LAUNCH,
                    publicAsyncDao,
                    aclDAO
                )
            }
        ) throw ApplicationException.NotFound()

        val entity = ctx.withSession { session ->
            internalByNameAndVersion(session, appName, appVersion)?.toApplicationWithInvocation()
        } ?: throw ApplicationException.NotFound()

        return ctx.withSession { session -> preparePageForUser(session, user.username, Page(1, 1, 0, listOf(entity))).items.first()}
    }

    suspend fun listLatestVersion(
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

        val cleanRole = if (user != null) {
            if (user.role != null) {
                user.role
            } else {
                Role.UNKNOWN
            }
        } else {
            Role.UNKNOWN
        }

        val items = ctx.withSession { session ->
            session
                .sendPreparedStatement(
                    {
                        setParameter("project", currentProject)
                        setParameter("groups", groups)
                        setParameter("isAdmin", Roles.PRIVILEGED.contains(cleanRole))
                        setParameter("user", user?.username ?: "")
                    },
                    """
                    SELECT A.*
                    FROM applications AS A WHERE (A.created_at) IN (
                        SELECT MAX(created_at)
                        FROM applications as B
                        WHERE A.name = B.name AND (
                            (
                                B.is_public = TRUE
                            ) or (
                                cast(:project as text) is null and :user in (
                                    SELECT P1.username FROM permissions AS P1 WHERE P1.application_name = B.name
                                )
                            ) or (
                                cast(:project as text) is not null AND exists (
                                    SELECT P2.project_group FROM permissions AS P2 WHERE
                                        P2.application_name = B.name AND
                                        P2.project = cast(:project as text) AND
                                        P2.project_group IN ( select unnest(:groups::text[]))
                                )
                            ) or (
                                :isAdmin
                            )
                        )
                        GROUP BY B.name
                    )
                    ORDER BY A.name
                    """
                )
                .rows
        }

        return ctx.withSession { session ->
            preparePageForUser(
                session,
                user?.username,
                Page(
                    items.size,
                    paging.itemsPerPage,
                    paging.page,
                    items.map { it.toApplicationWithInvocation() }
                )
            ).mapItems { it.withoutInvocation() }
        }
    }

    suspend fun create(
        ctx: DBContext,
        user: SecurityPrincipal,
        description: Application,
        originalDocument: String
    ) {
        val existingOwner = ctx.withSession { session -> findOwnerOfApplication(session, description.metadata.name)}
        if (existingOwner != null && !canUserPerformWriteOperation(existingOwner, user)) {
            throw ApplicationException.NotAllowed()
        }

        val existing = ctx.withSession { session -> internalByNameAndVersion(session, description.metadata.name, description.metadata.version)}
        if (existing != null) throw ApplicationException.AlreadyExists()

        val existingTool = ctx.withSession { session ->
            toolDAO.internalByNameAndVersion(
                session,
                description.invocation.tool.name,
                description.invocation.tool.version
            )
        } ?: throw ApplicationException.BadToolReference()

        ctx.withSession { session ->
            session.insert(ApplicationTable) {
                set(ApplicationTable.owner, user.username)
                set(ApplicationTable.createdAt, LocalDateTime(Time.now(), DateTimeZone.UTC))
                set(ApplicationTable.modifiedAt, LocalDateTime(Time.now(), DateTimeZone.UTC))
                set(ApplicationTable.authors, defaultMapper.writeValueAsString(description.metadata.authors))
                set(ApplicationTable.title, description.metadata.title)
                set(ApplicationTable.description, description.metadata.description)
                set(ApplicationTable.website, description.metadata.website)
                set(ApplicationTable.toolName, existingTool.getField(ToolTable.idName))
                set(ApplicationTable.toolVersion, existingTool.getField(ToolTable.idVersion))
                set(ApplicationTable.isPublic, description.metadata.isPublic)
                set(ApplicationTable.idName, description.metadata.name)
                set(ApplicationTable.idVersion, description.metadata.version)
                set(ApplicationTable.application, defaultMapper.writeValueAsString(description.invocation))

            }
        }
    }

    suspend fun delete(
        ctx: DBContext,
        user: SecurityPrincipal,
        project: String?,
        projectGroups: List<String>,
        appName: String,
        appVersion: String
    ) {
        val existingOwner = ctx.withSession { session -> findOwnerOfApplication(session, appName) }
        if (existingOwner != null && !canUserPerformWriteOperation(existingOwner, user)) {
            throw ApplicationException.NotAllowed()
        }

        // Prevent deletion of last version of application
        val isLast = ctx.withSession { session ->
                internalFindAllByName(
                    session,
                    user,
                    project,
                    projectGroups,
                    appName,
                    NormalizedPaginationRequest(25, 0),
                    this
                ).itemsInTotal <= 1
            }
        if (isLast) {
            throw ApplicationException.NotAllowed()
        }
        ctx.withSession { session ->
            val existingApp =
                internalByNameAndVersion(session, appName, appVersion) ?: throw ApplicationException.NotFound()

            cleanupBeforeDelete(
                session,
                existingApp.getField(ApplicationTable.idName),
                existingApp.getField(ApplicationTable.idVersion))

            session
                .sendPreparedStatement(
                    {
                        setParameter("appname", existingApp.getField(ApplicationTable.idName))
                        setParameter("appversion", existingApp.getField(ApplicationTable.idVersion))
                    },
                    """
                        DELETE FROM applications
                        WHERE (name = :appname) AND (version = :appversion)
                    """
                )
        }
    }

    private suspend fun cleanupBeforeDelete(ctx: DBContext, appName: String, appVersion: String) {
        ctx.withSession { session ->
            //DELETE FROM FAVORITES
            session
                .sendPreparedStatement(
                    {
                        setParameter("appname", appName)
                        setParameter("appversion", appVersion)
                    },
                    """
                        DELETE FROM favorited_by
                        WHERE (application_name = :appname) AND (application_version = :appversion)
                    """
                )
                .rows
        }
    }

    suspend fun updateDescription(
        ctx: DBContext,
        user: SecurityPrincipal,
        appName: String,
        appVersion: String,
        newDescription: String?,
        newAuthors: List<String>?
    ) {
        ctx.withSession { session ->
            val existing = internalByNameAndVersion(session, appName, appVersion) ?: throw ApplicationException.NotFound()
            if (!canUserPerformWriteOperation(
                    existing.getField(ApplicationTable.owner),
                    user
                )
            ) throw ApplicationException.NotAllowed()

            val existingApplication = existing.toApplicationWithInvocation()
            session
                .sendPreparedStatement(
                    {
                        setParameter("newdesc", newDescription)
                        setParameter("newauthors", defaultMapper.writeValueAsString(newAuthors ?: existingApplication.metadata.authors))
                        setParameter("name", appName)
                        setParameter("version", appVersion)
                    },
                    """
                        UPDATE applications
                        SET description = COALESCE(:newdesc, description), authors = :newauthors
                        WHERE (name = :name) AND (version = :version)
                    """
                )
        }
        // We allow for this to be cached for some time. But this instance might as well clear the cache now.
        byNameAndVersionCache.remove(NameAndVersion(appName, appVersion))
    }

    suspend fun isOwnerOfApplication(ctx: DBContext, user: SecurityPrincipal, appName: String): Boolean =
        ctx.withSession {session -> findOwnerOfApplication(session, appName)!! == user.username}


    suspend fun preparePageForUser(
        ctx: DBContext,
        user: String?,
        page: Page<Application>
    ): Page<ApplicationWithFavoriteAndTags> {
        if (!user.isNullOrBlank()) {
            val allFavorites = ctx.withSession { session ->
                session
                    .sendPreparedStatement(
                        {
                            setParameter("user", user)
                        },
                        """
                            SELECT * 
                            FROM favorited_by
                            WHERE the_user = :user
                        """
                    )
                    .rows
            }

            val allApplicationsOnPage = page.items.map { it.metadata.name }.toSet()

            val allTagsForApplicationsOnPage = ctx.withSession { session ->
                session
                    .sendPreparedStatement(
                        {
                            setParameter("allapps", allApplicationsOnPage.toList())
                        },
                        """
                            SELECT *
                            FROM application_tags
                            WHERE application_name IN (select unnest(:allapps::text[]))
                        """
                    )
                    .rows
            }
            val preparedPageItems = page.items.map { item ->
                val isFavorite = allFavorites.any { fav ->
                    fav.getField(FavoriteApplicationTable.applicationName) == item.metadata.name &&
                            fav.getField(FavoriteApplicationTable.applicationVersion) == item.metadata.version
                }

                val allTagsForApplication = allTagsForApplicationsOnPage
                    .filter { item.metadata.name == it.getField(TagTable.applicationName) }
                    .map { it.getField(TagTable.tag) }
                    .toSet()
                    .toList()

                ApplicationWithFavoriteAndTags(item.metadata, item.invocation, isFavorite, allTagsForApplication)
            }

            return Page(page.itemsInTotal, page.itemsPerPage, page.pageNumber, preparedPageItems)
        } else {
            val preparedPageItems = page.items.map { item ->
                val allTagsForApplication = ctx.withSession { session ->
                    session
                        .sendPreparedStatement(
                            {
                                setParameter("appname", item.metadata.name)
                            },
                            """
                                SELECT * 
                                FROM application_tags
                                WHERE application_name = :appname
                            """
                        )
                        .rows
                        .map {
                            it.getField(TagTable.tag)
                        }
                        .toSet()
                        .toList()
                }

                ApplicationWithFavoriteAndTags(item.metadata, item.invocation, false, allTagsForApplication)
            }
            return Page(page.itemsInTotal, page.itemsPerPage, page.pageNumber, preparedPageItems)
        }
    }

    suspend fun findLatestByTool(
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

        val items = ctx.withSession { session ->
            session
                .sendPreparedStatement(
                    {
                        setParameter("toolName", tool)
                        setParameter("user", user.username)
                        setParameter("isAdmin", Roles.PRIVILEGED.contains(user.role))
                        setParameter("project", project)
                        setParameter("groups", groups)
                    },
                    """
                        SELECT A.*
                        FROM applications AS A 
                        WHERE 
                            (A.created_at) IN (
                                SELECT MAX(created_at)
                                FROM applications AS B
                                WHERE A.name = B.name
                                GROUP BY name
                            ) AND A.tool_name = :toolName AND (
                                (
                                    A.is_public = TRUE
                                ) OR (
                                    cast(:project as text) is null AND :user in (
                                        SELECT P1.username FROM permissions AS P1 WHERE P1.application_name = A.name
                                    )
                                ) OR (
                                    cast(:project as text) is not null AND exists (
                                        SELECT P2.project_group
                                        FROM permissions AS P2
                                        WHERE
                                            P2.application_name = A.name AND
                                            P2.project = cast(:project as text) AND
                                            P2.project_group in (:groups)
                                    )
                                ) or (
                                    :isAdmin
                                ) 
                            )
                        order by A.name
                    """
                )
                .rows
        }

        return Page(items.size, paging.itemsPerPage, paging.page, items.map { it.toApplicationWithInvocation() })
    }

    suspend fun findAllByID(
        ctx: DBContext,
        user: SecurityPrincipal,
        project: String?,
        projectGroups: List<String>,
        embeddedNameAndVersionList: List<EmbeddedNameAndVersion>,
        paging: NormalizedPaginationRequest
    ): List<Application> {
        if (embeddedNameAndVersionList.isEmpty()) {
            return emptyList()
        }
        val names = embeddedNameAndVersionList.map { it.name }
        val versions = embeddedNameAndVersionList.map { it.version }

        return ctx.withSession { session ->
            session
                .sendPreparedStatement(
                    {
                        setParameter("names", names)
                        setParameter("versions", versions)
                    },
                    """
                        SELECT *
                        FROM app_store.applications
                        WHERE (name, version) IN (select unnest(:names::text[]), unnest(:versions::text[]))
                    """
                )
                .rows
                .map { it.toApplicationWithInvocation() }
        }
    }
}

internal fun RowData.toApplicationMetadata(): ApplicationMetadata {
    val authors = defaultMapper.readValue<List<String>>(getField(ApplicationTable.authors))

    return ApplicationMetadata(
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
    val application = getField(ApplicationTable.application)
    val invocations = defaultMapper.readValue<ApplicationInvocationDescription>(application)
    return Application(
        toApplicationMetadata(),
        invocations
    )
}

sealed class ApplicationException(why: String, httpStatusCode: HttpStatusCode) : RPCException(why, httpStatusCode) {
    class NotFound : ApplicationException("Not found", HttpStatusCode.NotFound)
    class NotAllowed : ApplicationException("Not allowed", HttpStatusCode.Forbidden)
    class AlreadyExists : ApplicationException("Already exists", HttpStatusCode.Conflict)
    class BadToolReference : ApplicationException("Tool does not exist", HttpStatusCode.BadRequest)
    class BadApplication : ApplicationException("Application does not exists", HttpStatusCode.NotFound)
}
