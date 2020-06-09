package app.store.services

import com.github.jasync.sql.db.ResultSet
import com.github.jasync.sql.db.RowData
import dk.sdu.cloud.Roles
import dk.sdu.cloud.SecurityPrincipal
import dk.sdu.cloud.app.store.api.ApplicationSummaryWithFavorite
import dk.sdu.cloud.app.store.services.AppStoreAsyncDAO
import dk.sdu.cloud.app.store.services.SearchDAO
import dk.sdu.cloud.app.store.services.toApplicationWithInvocation
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.sendPreparedStatement
import dk.sdu.cloud.service.db.async.withSession
import dk.sdu.cloud.service.db.paginatedList
import dk.sdu.cloud.service.mapItems
import dk.sdu.cloud.service.paginate
import org.hibernate.query.Query

class ApplicationSearchAsyncDAO(
    val appStoreAsyncDAO: AppStoreAsyncDAO
): SearchDAO {

    override suspend fun searchByTags(
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

        val applications = ctx.withSession { session ->
            appStoreAsyncDAO.findAppNamesFromTags(session, user, project, groups, tags)
        }

        if (applications.isEmpty()) {
            return ctx.withSession { session ->
                appStoreAsyncDAO.preparePageForUser(
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
        }

        val (apps, itemsInTotal) = ctx.withSession { session ->
            appStoreAsyncDAO.findAppsFromAppNames(session, user, project, groups, applications)
        }

        val items = apps.paginate(paging).mapItems { it.toApplicationWithInvocation() }.items

        return ctx.withSession { session ->
            appStoreAsyncDAO.preparePageForUser(
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


    override suspend fun search(
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

    override suspend fun multiKeywordsearch(
        ctx: DBContext,
        user: SecurityPrincipal,
        currentProject: String?,
        projectGroups: List<String>,
        keywords: List<String>,
        paging: NormalizedPaginationRequest
    ): List<RowData> {
        val keywordsQuery = createKeywordQuery(keywords)

        return ctx.withSession { session ->
            createMultiKeyWordApplicationEntityQuery(
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
                keywordsQuery += "lower(A.title) like '%' || :query$i || '%'"
                continue
            }
            keywordsQuery += "lower(A.title) like '%'|| :query$i ||'%' or "
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
            session.sendPreparedStatement(
                {
                    setParameter("user", user.username)
                    setParameter("project", project)
                    setParameter("groups", groups)
                    setParameter("role", user.role.toString())
                    setParameter("privileged", Roles.PRIVILEDGED.toList())
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
                        cast(?project as text) is null AND ?user IN (
                            SELECT P.username from permissions AS P WHERE P.application_name = A.id.name
                        )
                    ) OR (
                        cast(?project as text) is not null AND exists (
                            SELECT P2.project_group from permissions as P2 WHERE
                                P2.application_name = A.id.name AND
                                P2.project = cast(?project as text) AND
                                P2.project_groups IN ?groups
                        ) or ?role IN (?privileged)
                    )
                )
            )
            ORDER BY A.title
            """.trimIndent()
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
        /*val count = session.typedQuery<Long>(
            """
            select count (A.title)
            from ApplicationEntity as A
            where (A.createdAt) in (
                select max(createdAt)
                from ApplicationEntity as B
                where A.title = B.title
                group by title
            ) and $keywordsQuery and (
                (A.isPublic = true or (
                    cast(:project as text) is null and :user in (
                        select P.key.user from PermissionEntry as P where P.key.applicationName = A.id.name
                    )
                ) or (
                    cast(:project as text) is not null and exists (
                        select P2.key.group from PermissionEntry as P2 where
                            P2.key.applicationName = A.id.name and
                            P2.key.project = cast(:project as text) and
                            P2.key.group in :groups
                        ) or :role in (:privileged)
                    )
                )
            )
            """.trimIndent()
        ).setParameter("user", user.username)
            .setParameter("project", project)
            .setParameter("groups", groups)
            .setParameter("role", user.role)
            .setParameterList("privileged", Roles.PRIVILEDGED).also {
                for ((i, item) in keywords.withIndex()) {
                    it.setParameter("query$i", item)
                }
            }.uniqueResult().toInt()
*/
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
            appStoreAsyncDAO.preparePageForUser(
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

        /*val count = session.createNativeQuery<Long>(
            """
                select count(A.title) from {h-schema}applications as A
                where (A.created_at) in (
                    select max(B.created_at)
                    from {h-schema}applications as B
                    where A.title = B.title
                    group by title
                ) and lower(A.title) like '%' || :query || '%' and (
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
            """.trimIndent(), Long::class.java
        )
            .setParameter("query", normalizedQuery)
            .setParameter("user", user.username)
            .setParameter("project", currentProject)
            .setParameter("groups", groups)
            .setParameter("role", user.role)
            .setParameterList("privileged", Roles.PRIVILEDGED)
            .singleResult.toInt()

        println(count)*/

        val items = ctx.withSession { session ->
            session.sendPreparedStatement(
                {
                    setParameter("query", normalizedQuery)
                    setParameter("user", user.username)
                    setParameter("project", currentProject)
                    setParameter("groups", groups)
                    setParameter("role", user.role.toString())
                    setParameter("privileged", Roles.PRIVILEDGED.toList())
                },
                """
                    SELECT * 
                    FROM applications AS A
                    WHERE (A.created_at) in (
                        SELECT max(B.created_at)
                        FROM applications AS B
                        WHERE A.title = B.title
                        GROUP BY title
                    ) AND LOWER(A.title) LIKE '%' || ?query || '%' AND (
                        (
                            A.is_public = TRUE
                        ) OR (
                            cast(?project as text) is null AND ?user IN (
                                SELECT P1.username FROM permissions AS P1 WHERE P1.application_name = A.name
                            )
                        ) OR (
                            cast(?project as text) is not null AND exists (
                                SELECT P2.project_group FROM permissions AS P2 WHERE
                                    P2.application_name = A.name AND
                                    P2.project = cast(?project as text) AND
                                    P2.project_group IN (?groups)
                             )
                        ) OR (
                            ?role IN (?privileged)
                        ) 
                    )
                   
                    ORDER BY A.title 
                """.trimIndent()
            )
        }.rows.paginate(paging).mapItems { it.toApplicationWithInvocation() }

        return ctx.withSession { session ->
            appStoreAsyncDAO.preparePageForUser(
                session,
                user.username,
                items
            ).mapItems { it.withoutInvocation() }
        }
    }
}
