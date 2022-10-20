package dk.sdu.cloud.app.store.services

import com.github.jasync.sql.db.RowData
import dk.sdu.cloud.*
import dk.sdu.cloud.NormalizedPaginationRequestV2
import dk.sdu.cloud.PageV2
import dk.sdu.cloud.app.store.api.*
import dk.sdu.cloud.app.store.services.acl.AclAsyncDao
import dk.sdu.cloud.auth.api.AuthProviders
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.service.*
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.db.async.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.joda.time.DateTimeZone
import org.joda.time.LocalDateTime
import java.util.*
import kotlin.collections.ArrayList

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
        tags: List<String>,
        excludeTools: List<String>?
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
                        setParameter("exclude", excludeTools?.map { it.toLowerCase() } ?: emptyList())
                    },
                    """
                        select distinct at.application_name as app_name
                        from application_tags as at, applications as a
                        where at.application_name = a.name and at.tag_id in (
                            select id from tags where lower(tag) in (select lower(unnest(:tags::text[])))
                        ) and (
                            (
                                a.is_public = true
                            ) or (
                                cast(:project as text) is null and :user in (
                                    select p1.username from permissions as p1 where p1.application_name = a.name
                                )
                            ) or (
                                cast(:project as text) is not null and exists (
                                    select p2.project_group from permissions as p2 where
                                        p2.application_name = a.name and
                                        p2.project = cast(:project as text) and
                                        p2.project_group in (select unnest(:groups::text[]))
                                )
                            ) or (
                                :isAdmin
                            )
                        ) and (a.tool_name not in (select unnest(:exclude::text[])))
                        order by at.application_name;
                    """
                )
                .rows
                .toList()
                .mapNotNull {
                    it.getString("app_name")
                }
        }
    }

    internal suspend fun findAppsFromAppNames(
        ctx: DBContext,
        user: SecurityPrincipal,
        project: String?,
        memberGroups: List<String>,
        applicationNames: List<String>,
        excludeTools: List<String>?
    ): Pair<List<Application>, Int> {
        val items = ctx.withSession { session ->
            val excludeNormalized = excludeTools?.map {it.toLowerCase()} ?: emptyList()
            session
                .sendPreparedStatement(
                    {
                        setParameter("applications", applicationNames)
                        setParameter("user", user.username)
                        setParameter("project", project)
                        setParameter("groups", memberGroups)
                        setParameter("isAdmin", Roles.PRIVILEGED.contains(user.role))
                        setParameter("exclude", excludeNormalized)
                    },
                    """
                        SELECT DISTINCT ON (A.name) A.*
                        FROM applications as A
                        WHERE (A.name) IN (select unnest(:applications::text[])) AND (
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
                        ) AND (A.tool_name NOT IN (select unnest(:exclude::text[])))
                        ORDER BY A.name, A.created_at DESC ;
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
        db: DBContext,
        user: SecurityPrincipal,
        currentProject: String?,
        projectGroups: List<String>,
        request: NormalizedPaginationRequestV2,
        fileExtensions: Set<String>
    ): PageV2<ApplicationWithExtension> {
        return db.paginateV2(
            Actor.User(user),
            request,
            create = { session ->
                session
                    .sendPreparedStatement(
                        {
                            setParameter("user", user.username)
                            setParameter("ext", fileExtensions.toList())
                            setParameter("current_project", currentProject)
                            setParameter("project_groups", projectGroups)
                        },
                        """
                            declare c cursor for
                            with
                                project_info as (
                                    select :current_project current_project,
                                           unnest(:project_groups::text[]) as project_group
                                ),
                                most_recent_applications as (
                                    select apps_with_rno.*
                                    from (
                                        select
                                            a.*,
                                            row_number() over (
                                                partition by name
                                                order by created_at desc
                                            ) as rno
                                        from
                                            applications a left join
                                            permissions p on a.name = p.application_name left join
                                            project_info pinfo
                                                on p.project = pinfo.current_project and p.project_group = pinfo.project_group
                                        where
                                            a.is_public = true or
                                            p.permission is distinct from null
                                    ) apps_with_rno
                                    where rno <= 1
                                )
                            select a.*
                            from
                                most_recent_applications a
                            where
                                (a.application -> 'fileExtensions' ??| :ext::text[])

                            order by a.title
                        """,
                    )
            },
            mapper = { _, rows ->
                rows.filter { rowData ->
                    defaultMapper.decodeFromString<ApplicationInvocationDescription>(
                        rowData.getField(ApplicationTable.application)
                    ).parameters.all { it.optional }
                }
                .map {
                    ApplicationWithExtension(
                        it.toApplicationMetadata(),
                        defaultMapper.decodeFromString<ApplicationInvocationDescription>(
                            it.getField(ApplicationTable.application)
                        ).fileExtensions
                    )
                }
            }
        )
    }

    suspend fun findByNameAndVersionForUser(
        ctx: DBContext,
        user: SecurityPrincipal,
        currentProject: String?,
        projectGroups: List<String>,
        appName: String,
        appVersion: String
    ): ApplicationWithFavoriteAndTags {
        return ctx.withSession { session ->
            val hasPermission = internalHasPermission(
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

            if (!hasPermission) throw ApplicationException.NotFound()

            val application = internalByNameAndVersion(session, appName, appVersion)?.toApplicationWithInvocation()
                ?: throw ApplicationException.NotFound()

            preparePageForUser(session, user.username, Page(1, 1, 0, listOf(application))).items.first()
        }
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
        actorAndProject: ActorAndProject,
        description: Application,
        originalDocument: String
    ) {
        val username = actorAndProject.actor.safeUsername()
        val isProvider = username.startsWith(AuthProviders.PROVIDER_PREFIX)
        val providerName = username.removePrefix(AuthProviders.PROVIDER_PREFIX)

        val existing = ctx.withSession { session -> internalByNameAndVersion(session, description.metadata.name, description.metadata.version) }
        if (existing != null) throw ApplicationException.AlreadyExists()

        val existingTool = ctx.withSession { session ->
            toolDAO.internalByNameAndVersion(
                session,
                description.invocation.tool.name,
                description.invocation.tool.version
            )
        } ?: throw ApplicationException.BadToolReference()

        if (isProvider) {
            val tool = existingTool.toTool()
            if (tool.description.backend != ToolBackend.NATIVE) throw ApplicationException.NotAllowed()
            if (tool.description.supportedProviders != listOf(providerName)) throw ApplicationException.NotAllowed()
        }

        ctx.withSession { session ->
            session.insert(ApplicationTable) {
                set(ApplicationTable.owner, username)
                set(ApplicationTable.createdAt, LocalDateTime(Time.now(), DateTimeZone.UTC))
                set(ApplicationTable.modifiedAt, LocalDateTime(Time.now(), DateTimeZone.UTC))
                set(ApplicationTable.authors, defaultMapper.encodeToString(description.metadata.authors))
                set(ApplicationTable.title, description.metadata.title)
                set(ApplicationTable.description, description.metadata.description)
                set(ApplicationTable.website, description.metadata.website)
                set(ApplicationTable.toolName, existingTool.getField(ToolTable.idName))
                set(ApplicationTable.toolVersion, existingTool.getField(ToolTable.idVersion))
                set(ApplicationTable.isPublic, description.metadata.isPublic)
                set(ApplicationTable.idName, description.metadata.name)
                set(ApplicationTable.idVersion, description.metadata.version)
                set(ApplicationTable.application, defaultMapper.encodeToString(description.invocation))
            }

            if (isProvider) {
                // TODO(Dan): Need to rework how this app-store works. There isn't even a unique constraint on
                //  (application, tag)
                // UPDATE(Brian): There is now
                val tagId = session.sendPreparedStatement(
                    {
                        setParameter("provider_tag", providerName)
                    },
                    """
                        insert into tags (tag) values (:providerTag) returning id
                    """
                ).rows.first().getInt(0)

                session.sendPreparedStatement(
                    {
                        setParameter("name", description.metadata.name)
                        setParameter("tag_id", tagId)
                    },
                    """
                        insert into application_tags (application_name, tag_id)
                        values (:name, :tag_id)
                    """
                )
            }
        }
    }

    suspend fun overview(
        ctx: DBContext,
        user: SecurityPrincipal,
        project: String?,
        memberGroups: List<String>
    ): List<AppStoreSection> {
        val groups = if (memberGroups.isEmpty()) {
            listOf("")
        } else {
            memberGroups
        }

        val sections = ArrayList<AppStoreSection>()
        ctx.withSession { session ->
            session.sendPreparedStatement(
                {
                    setParameter("user", user.username)
                    setParameter("is_admin", Roles.PRIVILEGED.contains(user.role))
                    setParameter("project", project)
                    setParameter("groups", groups)
                },
                """
                    select reference_id,
                        (select tag from tags where reference_type = 'TAG' and id = cast(reference_id as int) limit 1) tag_name,
                        reference_type,
                        application_to_json(a, t),
                        exists(select * from favorited_by where the_user = :user and application_name = a.name) favorite,
                        (select json_agg(tag) from tags where id in (select tag_id from application_tags where application_name = a.name)) tags,
                        columns,
                        rows 
                    from
                        overview,
                        (select * from applications a1 where created_at in (
                            select max(created_at)
                            from applications a2 where a1.name = a2.name and (
                                :is_admin or (
                                    a2.is_public or (
                                        cast(:project as text) is null and :user in (
                                            select p.username from app_store.permissions p where p.application_name = a1.name
                                        )
                                    ) or (
                                        cast(:project as text) is not null and exists (
                                            select p.project_group from app_store.permissions p where 
                                                p.application_name = a1.name and 
                                                p.project = cast(:project as text) and 
                                                p.project_group in (select unnest(:groups::text[]))
                                         )
                                    )
                                )
                            )
                        ) order by name) a
                    join tools t on
                        a.tool_name = t.name and a.tool_version = t.version
                    where (reference_type = 'TOOL' and lower(tool_name) = lower(reference_id))
                        or (
                            reference_type = 'TAG' and
                            lower(a.name) in (select lower(application_name)
                                from application_tags
                                where tag_id in (select id from tags where id = cast(reference_id as int))
                            )
                        )
                    order by order_id, a.title
                """
            ).rows.forEach { row ->
                val refId = row.getString("reference_id")!!
                val tagName = row.getString("tag_name")
                val type = AppStoreSectionType.valueOf(row.getString("reference_type")!!)
                val app = defaultMapper.decodeFromString<Application>(row.getString("application_to_json")!!)
                val favorite = row.getBoolean("favorite")!!
                val columns = row.getInt("columns")!!
                val rows = row.getInt("rows")!!
                val tags = try {
                    defaultMapper.decodeFromString<List<String>>(row.getString("tags")!!)
                } catch(e: NullPointerException) {
                    emptyList()
                }
                val appWithFavorite = ApplicationSummaryWithFavorite(app.metadata, favorite, tags)

                val section = sections.find { it.name == tagName || it.name == refId }
                if (section != null) {
                    when (section) {
                        is AppStoreSection.Tag -> {
                            section.applications.add(appWithFavorite)
                        }
                        is AppStoreSection.Tool -> {
                            section.applications.add(appWithFavorite)
                        }
                    }
                } else {
                    val newSection = when (type) {
                        AppStoreSectionType.TAG -> {
                            AppStoreSection.Tag(
                                tagName ?: "",
                                arrayListOf(appWithFavorite),
                                columns,
                                rows
                            )
                        }
                        AppStoreSectionType.TOOL -> {
                            AppStoreSection.Tool(
                                refId,
                                arrayListOf(appWithFavorite),
                                columns,
                                rows
                            )
                        }
                    }

                    sections.add(newSection)
                }
            }
        }

        return sections
    }

    suspend fun delete(
        ctx: DBContext,
        user: SecurityPrincipal,
        project: String?,
        projectGroups: List<String>,
        appName: String,
        appVersion: String
    ) {
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
                            select application_name, tag
                            from application_tags, tags
                            where application_name in (select unnest(:allapps::text[])) and tag_id = tags.id
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
                    .filter { item.metadata.name == it.getString("application_name") }
                    .mapNotNull { it.getString("tag") }
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
                                select distinct tag
                                from application_tags, tags
                                where application_name = :appname and tag_id = tags.id
                            """
                        )
                        .rows
                        .mapNotNull {
                            it.getString("tag")
                        }
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
                        FROM applications
                        WHERE (name, version) IN (select unnest(:names::text[]), unnest(:versions::text[]))
                    """
                )
                .rows
                .map { it.toApplicationWithInvocation() }
        }
    }
}

internal fun RowData.toApplicationMetadata(): ApplicationMetadata {
    val authors = defaultMapper.decodeFromString<List<String>>(getField(ApplicationTable.authors))

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
    val invocations = defaultMapper.decodeFromString<ApplicationInvocationDescription>(application)
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
