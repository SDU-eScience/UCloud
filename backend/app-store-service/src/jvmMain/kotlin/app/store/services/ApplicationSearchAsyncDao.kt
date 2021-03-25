package dk.sdu.cloud.app.store.services

import com.github.jasync.sql.db.ResultSet
import dk.sdu.cloud.Roles
import dk.sdu.cloud.SecurityPrincipal
import dk.sdu.cloud.app.store.api.Application
import dk.sdu.cloud.app.store.api.ApplicationSummaryWithFavorite
import dk.sdu.cloud.mapItems
import dk.sdu.cloud.paginate
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.sendPreparedStatement
import dk.sdu.cloud.service.db.async.withSession

class ApplicationSearchAsyncDao(
    val appStoreAsyncDao: AppStoreAsyncDao
) {
    suspend fun searchByTags(
        ctx: DBContext,
        user: SecurityPrincipal,
        project: String?,
        memberGroups: List<String>,
        tags: List<String>,
        paging: NormalizedPaginationRequest,
        excludeTools: List<String>? = emptyList()
    ): Page<ApplicationSummaryWithFavorite> {
        val groups = if (memberGroups.isEmpty()) {
            listOf("")
        } else {
            memberGroups
        }
        return ctx.withSession { session ->
            val applications = appStoreAsyncDao.findAppNamesFromTags(session, user, project, groups, tags, excludeTools)
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
                    applications,
                    excludeTools
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
        val firstTenKeywords = keywords.filter { !it.isBlank() }.take(10)
        return ctx.withSession { session ->
            doMultiKeywordSearch(session, user, currentProject, projectGroups, firstTenKeywords, paging)
        }
    }

    suspend fun multiKeywordSearch(
        ctx: DBContext,
        user: SecurityPrincipal,
        currentProject: String?,
        projectGroups: List<String>,
        keywords: List<String>,
        paging: NormalizedPaginationRequest
    ): List<Application> {
        val keywordsQuery = createKeywordQuery(keywords)

        return ctx.withSession { session ->
            advancedSearch(
                session,
                user,
                currentProject,
                projectGroups,
                keywords,
                keywordsQuery
            )
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

    private suspend fun advancedSearch(
        ctx: DBContext,
        user: SecurityPrincipal,
        project: String?,
        projectGroups: List<String>,
        keywords: List<String>,
        keywordsQuery: String
    ): List<Application> {
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
                        FROM applications as A 
                        WHERE $keywordsQuery AND (
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
                        ORDER BY A.title ASC , A.created_at DESC 
                    """
                )
        }.rows
            .map {
                it.toApplicationWithInvocation()
            }
            .distinctBy { it.metadata.title }
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
            advancedSearch(
                session,
                user,
                project,
                groups,
                keywords,
                keywordsQuery
            ).paginate(paging)
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
