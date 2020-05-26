package app.store.services

import dk.sdu.cloud.Roles
import dk.sdu.cloud.SecurityPrincipal
import dk.sdu.cloud.app.store.api.ApplicationSummaryWithFavorite
import dk.sdu.cloud.app.store.services.SearchDAO
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.paginatedList
import org.hibernate.query.Query

class ApplicationSearchAsyncDAO(): SearchDAO {

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

        val applications = findAppNamesFromTags(session, user, project, groups, tags)

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

        val (apps, itemsInTotal) = findAppsFromAppNames(session, user, project, groups, applications)

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
            return doSearch(session, user, currentProject, projectGroups, trimmedNormalizedQuery, paging)
        }
        val firstTenKeywords = keywords.filter { !it.isBlank() }.take(10)
        return doMultiKeywordSearch(session, user, currentProject, projectGroups, firstTenKeywords, paging)
    }

    override suspend fun multiKeywordsearch(
        ctx: DBContext,
        user: SecurityPrincipal,
        currentProject: String?,
        projectGroups: List<String>,
        keywords: List<String>,
        paging: NormalizedPaginationRequest
    ): List<ApplicationEntity> {
        val keywordsQuery = createKeywordQuery(keywords)

        return createMultiKeyWordApplicationEntityQuery(
            session,
            user,
            currentProject,
            projectGroups,
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

    private suspend fun createMultiKeyWordApplicationEntityQuery(
        ctx: DBContext,
        user: SecurityPrincipal,
        project: String?,
        projectGroups: List<String>,
        keywords: List<String>,
        keywordsQuery: String
    ): Query<ApplicationEntity> {
        val groups = if (projectGroups.isEmpty()) {
            listOf("")
        } else {
            projectGroups
        }

        return session.typedQuery<ApplicationEntity>(
            """
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
            order by A.title
            """.trimIndent()
        ).setParameter("user", user.username)
            .setParameter("project", project)
            .setParameter("groups", groups)
            .setParameter("role", user.role)
            .setParameterList("privileged", Roles.PRIVILEDGED)
            .also {
                for ((i, item) in keywords.withIndex()) {
                    it.setParameter("query$i", item)
                }
            }
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
        val count = session.typedQuery<Long>(
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

        val items = createMultiKeyWordApplicationEntityQuery(
            session,
            user,
            project,
            groups,
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

        val count = session.createNativeQuery<Long>(
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

        println(count)

        val items = session.createNativeQuery<ApplicationEntity>(
            """
               select * from {h-schema}applications as A
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
               
               order by A.title 
            """.trimIndent(), ApplicationEntity::class.java
        )
            .setParameter("query", normalizedQuery)
            .setParameter("user", user.username)
            .setParameter("project", currentProject)
            .setParameter("groups", groups)
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

}
