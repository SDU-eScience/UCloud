package dk.sdu.cloud.app.store.services

import co.elastic.clients.elasticsearch.core.search.Hit
import com.github.jasync.sql.db.ResultSet
import dk.sdu.cloud.*
import dk.sdu.cloud.app.store.api.Application
import dk.sdu.cloud.app.store.api.ApplicationSummaryWithFavorite
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.db.async.AsyncDBSessionFactory
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.sendPreparedStatement
import dk.sdu.cloud.service.db.async.withSession
import java.io.Serializable
import java.util.*

data class EmbeddedNameAndVersion(
    var name: String = "",
    var version: String = ""
) : Serializable

class ApplicationSearchService (
    private val db: AsyncDBSessionFactory,
    private val elasticDao: ElasticDao?,
    private val appStoreService: AppStoreService,
    private val authenticatedClient: AuthenticatedClient
) {
    suspend fun searchByTags(
        actorAndProject: ActorAndProject,
        tags: List<String>,
        paging: NormalizedPaginationRequest,
        excludeTools: List<String>? = emptyList()
    ): Page<ApplicationSummaryWithFavorite> {
        val groups = if (actorAndProject.project.isNullOrBlank()) {
            emptyList()
        } else {
            retrieveUserProjectGroups(actorAndProject, authenticatedClient)
        }

        return db.withSession { session ->
            val applications = appStoreService.findAppNamesFromTags(actorAndProject, groups, tags, excludeTools)
            if (applications.isEmpty()) {
                appStoreService.preparePageForUser(
                    session,
                    actorAndProject.actor.username,
                    Page(
                        0,
                        paging.itemsPerPage,
                        paging.page,
                        emptyList()
                    )
                ).mapItems { it.withoutInvocation() }
            } else {
                val (apps, itemsInTotal) = appStoreService.findAppsFromAppNames(
                    actorAndProject,
                    groups,
                    applications,
                    excludeTools
                )
                val items = apps.paginate(paging).items
                appStoreService.preparePageForUser(
                    session,
                    actorAndProject.actor.username,
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

    suspend fun searchApps(
        actorAndProject: ActorAndProject,
        query: String,
        paging: NormalizedPaginationRequest
    ): Page<ApplicationSummaryWithFavorite> {
        val groups = if (actorAndProject.project.isNullOrBlank()) {
            emptyList()
        } else {
            retrieveUserProjectGroups(actorAndProject, authenticatedClient)
        }

        if (query.isBlank()) {
            return Page(0, paging.itemsPerPage, 0, emptyList())
        }
        val trimmedNormalizedQuery = normalizeQuery(query).trim()
        val keywords = trimmedNormalizedQuery.split(" ").filter { it.isNotBlank() }
        if (keywords.size == 1) {
            return db.withSession { session ->
                doSearch(session, actorAndProject, groups, trimmedNormalizedQuery, paging)
            }
        }
        val firstTenKeywords = keywords.filter { it.isNotBlank() }.take(10)
        return db.withSession { session ->
            doMultiKeywordSearch(session, actorAndProject, groups, firstTenKeywords, paging)
        }
    }

    private suspend fun multiKeywordsearch(
        ctx: DBContext,
        actorAndProject: ActorAndProject,
        projectGroups: List<String>,
        keywords: List<String>,
        paging: NormalizedPaginationRequest
    ): List<Application> {
        val keywordsQuery = createKeywordQuery(keywords)

        return ctx.withSession { session ->
            createMultiKeyWordApplicationEntityQuery(
                session,
                actorAndProject,
                projectGroups,
                keywords,
                keywordsQuery
            ).map { it.toApplicationWithInvocation() }
        }
    }

    suspend fun advancedSearch(
        actorAndProject: ActorAndProject,
        query: String?,
        tagFilter: List<String>?,
        showAllVersions: Boolean,
        paging: NormalizedPaginationRequest
    ): Page<ApplicationSummaryWithFavorite> {
        if (query.isNullOrBlank() && tagFilter == null) {
            return Page(
                0,
                paging.itemsPerPage,
                0,
                emptyList()
            )
        }

        val normalizedQuery = query?.lowercase(Locale.getDefault()) ?: ""

        val normalizedTags = mutableListOf<String>()
        tagFilter?.forEach { tag ->
            if (tag.contains(" ")) {
                val splittedTag = tag.split(" ")
                normalizedTags.addAll(splittedTag)
            } else {
                normalizedTags.add(tag)
            }
        }

        val queryTerms = normalizedQuery.split(" ").filter { it.isNotBlank() }

        val results = elasticDao?.search(queryTerms, normalizedTags)
            ?: throw RPCException.fromStatusCode(HttpStatusCode.InternalServerError, "Missing elasticDao")
        val hits = results
        if (hits.isEmpty()) {
            return Page(
                0,
                paging.itemsPerPage,
                0,
                emptyList()
            )
        }

        val projectGroups = if (actorAndProject.project.isNullOrBlank()) {
            emptyList()
        } else {
            retrieveUserProjectGroups(actorAndProject, authenticatedClient)
        }

        if (showAllVersions) {
            val embeddedNameAndVersionList = hits.map {
                val result = it.source()
                EmbeddedNameAndVersion(result!!.name, result!!.version)
            }

            val applications = appStoreService.findAllByID(actorAndProject, projectGroups, embeddedNameAndVersionList, paging)
            return sortAndCreatePageByScore(applications, results, actorAndProject, paging)

        } else {
            val titles = hits.map {
                val result = it.source()
                result!!.title
            }

            val applications = db.withSession { session ->
                multiKeywordsearch(session, actorAndProject, projectGroups, titles.toList(), paging)
            }

            return sortAndCreatePageByScore(applications, results, actorAndProject, paging)
        }
    }

    private suspend fun sortAndCreatePageByScore(
        applications: List<Application>,
        results: List<Hit<ElasticIndexedApplication>>?,
        actorAndProject: ActorAndProject,
        paging: NormalizedPaginationRequest
    ): Page<ApplicationSummaryWithFavorite> {
        val map = applications.associateBy(
            { EmbeddedNameAndVersion(
                it.metadata.name.toLowerCase(),
                it.metadata.version.toLowerCase()
            ) }, { it }
        )

        val sortedList = mutableListOf<Application>()

        results?.forEach {
            val foundEntity =
                map[EmbeddedNameAndVersion(it.source()?.name.toString(), it.source()?.version.toString())]
            if (foundEntity != null) {
                sortedList.add(foundEntity)
            }
        }

        val sortedResultsPage = sortedList.paginate(paging)

        return db.withSession { session ->
            appStoreService.preparePageForUser(session, actorAndProject.actor.username, sortedResultsPage)
                .mapItems { it.withoutInvocation() }
        }
    }

    private suspend fun doMultiKeywordSearch(
        ctx: DBContext,
        actorAndProject: ActorAndProject,
        projectGroups: List<String>,
        keywords: List<String>,
        paging: NormalizedPaginationRequest
    ): Page<ApplicationSummaryWithFavorite> {
        val groups = projectGroups.ifEmpty {
            listOf("")
        }

        val keywordsQuery = createKeywordQuery(keywords)

        val items = ctx.withSession { session ->
            createMultiKeyWordApplicationEntityQuery(
                session,
                actorAndProject,
                groups,
                keywords,
                keywordsQuery
            ).paginate(paging)
                .mapItems { it.toApplicationWithInvocation() }
        }

        return ctx.withSession { session ->
            appStoreService.preparePageForUser(
                session,
                actorAndProject.actor.username,
                items
            ).mapItems { it.withoutInvocation() }
        }
    }

    private suspend fun doSearch(
        ctx: DBContext,
        actorAndProject: ActorAndProject,
        memberGroups: List<String>,
        normalizedQuery: String,
        paging: NormalizedPaginationRequest
    ): Page<ApplicationSummaryWithFavorite> {
        val groups = memberGroups.ifEmpty {
            listOf("")
        }
        val isAdmin = Roles.PRIVILEGED.contains((actorAndProject.actor as? Actor.User)?.principal?.role)
        val items = ctx.withSession { session ->
            session
                .sendPreparedStatement(
                    {
                        setParameter("query", normalizedQuery)
                        setParameter("user", actorAndProject.actor.username)
                        setParameter("project", actorAndProject.project)
                        setParameter("groups", groups)
                        setParameter("isAdmin", isAdmin)
                    },
                    """
                        SELECT * 
                        FROM app_store.applications AS A
                        WHERE (A.created_at) in (
                            SELECT max(B.created_at)
                            FROM app_store.applications AS B
                            where A.title = B.title AND (
                                :isAdmin OR 
                                    (
                                        B.is_public OR (
                                            cast(:project as text) is null AND :user IN (
                                                SELECT P1.username FROM app_store.permissions AS P1 WHERE P1.application_name = A.name
                                            )
                                        ) OR (
                                            cast(:project as text) is not null AND exists (
                                                SELECT P2.project_group FROM app_store.permissions AS P2 WHERE
                                                    P2.application_name = A.name AND
                                                    P2.project = cast(:project as text) AND
                                                    P2.project_group IN (select unnest(:groups::text[]))
                                             )
                                        )
                                    )
                            )
                            GROUP BY title
                        ) AND LOWER(A.title) like '%' || :query || '%'
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
            appStoreService.preparePageForUser(
                session,
                actorAndProject.actor.username,
                items
            ).mapItems { it.withoutInvocation() }
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
        actorAndProject: ActorAndProject,
        projectGroups: List<String>,
        keywords: List<String>,
        keywordsQuery: String
    ): ResultSet {
        val groups = projectGroups.ifEmpty {
            listOf("")
        }

        return ctx.withSession { session ->
            val isAdmin = Roles.PRIVILEGED.contains((actorAndProject.actor as? Actor.User)?.principal?.role)
            session
                .sendPreparedStatement(
                    {
                        keywords.forEachIndexed { index, keyword ->
                            setParameter("query$index", keyword)
                        }
                        setParameter("user", actorAndProject.actor.safeUsername())
                        setParameter("project", actorAndProject.project)
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
}
