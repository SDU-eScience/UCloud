package dk.sdu.cloud.app.store.services

import com.github.jasync.sql.db.ResultSet
import dk.sdu.cloud.Roles
import dk.sdu.cloud.SecurityPrincipal
import dk.sdu.cloud.app.store.api.Application
import dk.sdu.cloud.app.store.api.ApplicationSummaryWithFavorite
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.sendPreparedStatement
import dk.sdu.cloud.service.db.async.withSession
import dk.sdu.cloud.service.mapItems
import dk.sdu.cloud.service.paginate

class ApplicationSearchAsyncDao(
    val appStoreAsyncDao: AppStoreAsyncDao
) {
    suspend fun searchByTags(
        ctx: DBContext,
        user: SecurityPrincipal,
        project: String?,
        memberGroups: List<String>,
        tags: List<String>,
        paging: NormalizedPaginationRequest
    ): Page<ApplicationSummaryWithFavorite> {
        val groups = if (memberGroups.isEmpty()) {
            listOf("")
        } else {
            memberGroups
        }

        return ctx.withSession { session ->
            val applications = appStoreAsyncDao.findAppNamesFromTags(session, user, project, groups, tags)

            if (applications.isEmpty()) {
                appStoreAsyncDao.preparePageForUser(
                    session,
                    user.username,
                    Page(
                        0,
                        paging.itemsPerPage,
                        paging.page,
                        emptyList()
                    )
                ).mapItems { it.withoutInvocation() }
            } else {
                val (apps, itemsInTotal) = appStoreAsyncDao.findAppsFromAppNames(
                    session,
                    user,
                    project,
                    groups,
                    applications
                )
                val items = apps.paginate(paging).items
                appStoreAsyncDao.preparePageForUser(
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
        }
    }

    suspend fun search(
        ctx: DBContext,
        user: SecurityPrincipal,
        currentProject: String?,
        projectGroups: List<String>,
        query: String,
        paging: NormalizedPaginationRequest
    ): Page<ApplicationSummaryWithFavorite> {
        if (query.isBlank()) {
            return Page(0, paging.itemsPerPage, 0, emptyList())
        }
        val trimmedNormalizedQuery = normalizeQuery(query).trim()
        val keywords = trimmedNormalizedQuery.split(" ").filter { it.isNotBlank() }
        if (keywords.size == 1) {
            return ctx.withSession { session ->
                doSearch(session, user, currentProject, projectGroups, trimmedNormalizedQuery, paging)
            }
        }
        val firstTenKeywords = keywords.filter { !it.isBlank() }.take(10)
        return ctx.withSession { session ->
            doMultiKeywordSearch(session, user, currentProject, projectGroups, firstTenKeywords, paging)
        }
    }

    suspend fun multiKeywordsearch(
        ctx: DBContext,
        user: SecurityPrincipal,
        currentProject: String?,
        projectGroups: List<String>,
        keywords: List<String>,
        paging: NormalizedPaginationRequest
    ): List<Application> {
        val keywordsQuery = createKeywordQuery(keywords)

        return ctx.withSession { session ->
            createMultiKeyWordApplicationEntityQuery(
                session,
                user,
                currentProject,
                projectGroups,
                keywords,
                keywordsQuery
            ).map { it.toApplicationWithInvocation() }
        }
    }

    private fun createKeywordQuery(keywords: List<String>): String {
        var keywordsQuery = "("
        for (i in keywords.indices) {
            if (i == keywords.lastIndex) {
                keywordsQuery += "lower(A.title) like '%' || ?query$i || '%'"
                continue
            }
            keywordsQuery += "lower(A.title) like '%'|| ?query$i ||'%' or "
        }
        keywordsQuery += ")"

        return keywordsQuery
    }

    private suspend fun createMultiKeyWordApplicationEntityQuery(
        ctx: DBContext,
        user: SecurityPrincipal,
        project: String?,
        projectGroups: List<String>,
        keywords: List<String>,
        keywordsQuery: String
    ): ResultSet {
        val groups = if (projectGroups.isEmpty()) {
            listOf("")
        } else {
            projectGroups
        }

        return ctx.withSession { session ->
            val isAdmin = Roles.PRIVILEGED.contains(user.role)
            session
                .sendPreparedStatement(
                    {
                        keywords.forEachIndexed { index, keyword ->
                            setParameter("query$index", keyword)
                        }
                        setParameter("user", user.username)
                        setParameter("project", project)
                        setParameter("groups", groups)
                        setParameter("isAdmin", isAdmin)
                    },
                    """
                    SELECT *
                    FROM applications AS A
                    WHERE (A.created_at) IN (
                        SELECT MAX(created_at)
                        FROM applications as B
                        WHERE A.title = B.title
                        GROUP BY title
                    ) AND $keywordsQuery AND (
                        (A.is_public = TRUE OR (
                            cast(:project as text) is null AND ?user IN (
                                SELECT P.username FROM permissions AS P WHERE P.application_name = A.name
                            )
                        ) OR (
                            cast(:project as text) is not null AND exists (
                                SELECT P2.project_group FROM permissions as P2 WHERE
                                    P2.application_name = A.name AND
                                    P2.project = cast(:project as text) AND
                                    P2.project_group IN (select unnest(:groups::text[]))
                            ) or :isAdmin
                        ))
                    )
                    ORDER BY A.title
                    """
                )
        }.rows
    }

    private suspend fun doMultiKeywordSearch(
        ctx: DBContext,
        user: SecurityPrincipal,
        project: String?,
        projectGroups: List<String>,
        keywords: List<String>,
        paging: NormalizedPaginationRequest
    ): Page<ApplicationSummaryWithFavorite> {
        val groups = if (projectGroups.isEmpty()) {
            listOf("")
        } else {
            projectGroups
        }

        val keywordsQuery = createKeywordQuery(keywords)

        val items = ctx.withSession { session ->
            createMultiKeyWordApplicationEntityQuery(
                session,
                user,
                project,
                groups,
                keywords,
                keywordsQuery
            ).paginate(paging)
                .mapItems { it.toApplicationWithInvocation() }
        }

        return ctx.withSession { session ->
            appStoreAsyncDao.preparePageForUser(
                session,
                user.username,
                items
            ).mapItems { it.withoutInvocation() }
        }
    }

    private suspend fun doSearch(
        ctx: DBContext,
        user: SecurityPrincipal,
        currentProject: String?,
        memberGroups: List<String>,
        normalizedQuery: String,
        paging: NormalizedPaginationRequest
    ): Page<ApplicationSummaryWithFavorite> {
        val groups = if (memberGroups.isEmpty()) {
            listOf("")
        } else {
            memberGroups
        }
        val isAdmin = Roles.PRIVILEGED.contains(user.role)
        val items = ctx.withSession { session ->
            session
                .sendPreparedStatement(
                    {
                        setParameter("query", normalizedQuery)
                        setParameter("user", user.username)
                        setParameter("project", currentProject)
                        setParameter("groups", groups)
                        setParameter("isAdmin", isAdmin)
                    },
                    """
                        SELECT * 
                        FROM applications AS A
                        WHERE (A.created_at) in (
                            SELECT max(B.created_at)
                            FROM applications AS B
                            WHERE A.title = B.title
                            GROUP BY title
                        ) AND LOWER(A.title) LIKE '%' || :query || '%' AND (
                            (
                                A.is_public = TRUE
                            ) OR (
                                cast(:project as text) is null AND :user IN (
                                    SELECT P1.username FROM permissions AS P1 WHERE P1.application_name = A.name
                                )
                            ) OR (
                                cast(:project as text) is not null AND exists (
                                    SELECT P2.project_group FROM permissions AS P2 WHERE
                                        P2.application_name = A.name AND
                                        P2.project = cast(:project as text) AND
                                        P2.project_group IN (select unnest(:groups::text[]))
                                 )
                            ) OR (
                                :isAdmin
                            ) 
                        )
                        ORDER BY A.title 
                    """
                )
            }
            .rows
            .paginate(paging)
            .mapItems {
                it.toApplicationWithInvocation()
            }

        return ctx.withSession { session ->
            appStoreAsyncDao.preparePageForUser(
                session,
                user.username,
                items
            ).mapItems { it.withoutInvocation() }
        }
    }
}
