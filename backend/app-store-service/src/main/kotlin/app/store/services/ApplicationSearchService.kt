package app.store.services

import com.fasterxml.jackson.module.kotlin.readValue
import com.github.jasync.sql.db.RowData
import dk.sdu.cloud.SecurityPrincipal
import dk.sdu.cloud.app.store.api.ApplicationSummaryWithFavorite
import dk.sdu.cloud.app.store.services.AppStoreAsyncDAO
import dk.sdu.cloud.app.store.services.ApplicationTable
import dk.sdu.cloud.app.store.services.ElasticDAO
import dk.sdu.cloud.app.store.services.ElasticIndexedApplication
import dk.sdu.cloud.app.store.services.EmbeddedNameAndVersion
import dk.sdu.cloud.app.store.services.toApplicationWithInvocation
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.db.async.AsyncDBSessionFactory
import dk.sdu.cloud.service.db.async.getField
import dk.sdu.cloud.service.db.async.withSession
import dk.sdu.cloud.service.mapItems
import dk.sdu.cloud.service.paginate
import org.elasticsearch.action.search.SearchResponse

class ApplicationSearchService (
    private val db: AsyncDBSessionFactory,
    private val searchDAO: ApplicationSearchAsyncDAO,
    private val elasticDAO: ElasticDAO,
    private val applicationDAO: AppStoreAsyncDAO,
    private val authenticatedClient: AuthenticatedClient
) {
    suspend fun searchByTags(
        securityPrincipal: SecurityPrincipal,
        project: String?,
        tags: List<String>,
        normalizedPaginationRequest: NormalizedPaginationRequest
    ): Page<ApplicationSummaryWithFavorite> {
        val projectGroups = if (project.isNullOrBlank()) {
            emptyList()
        } else {
            retrieveUserProjectGroups(securityPrincipal, project, authenticatedClient)
        }

        return db.withSession { session ->
            searchDAO.searchByTags(
                session,
                securityPrincipal,
                project,
                projectGroups,
                tags,
                normalizedPaginationRequest
            )
        }
    }

    suspend fun searchApps(
        securityPrincipal: SecurityPrincipal,
        project: String?,
        query: String,
        normalizedPaginationRequest: NormalizedPaginationRequest
    ): Page<ApplicationSummaryWithFavorite> {
        val projectGroups = if (project.isNullOrBlank()) {
            emptyList()
        } else {
            retrieveUserProjectGroups(securityPrincipal, project, authenticatedClient)
        }

        return db.withSession { session ->
            searchDAO.search(
                session,
                securityPrincipal,
                project,
                projectGroups as List<String>,
                query,
                normalizedPaginationRequest
            )
        }
    }

    suspend fun advancedSearch(
        user: SecurityPrincipal,
        project: String?,
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

        val normalizedQuery = query?.toLowerCase() ?: ""

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

        val results = elasticDAO.search(queryTerms, normalizedTags)

        if (results.hits.hits.isEmpty()) {
            return Page(
                0,
                paging.itemsPerPage,
                0,
                emptyList()
            )
        }

        val projectGroups = if (project.isNullOrBlank()) {
            emptyList()
        } else {
            retrieveUserProjectGroups(user, project, authenticatedClient)
        }

        if (showAllVersions) {
            val embeddedNameAndVersionList = results.hits.map {
                val result = defaultMapper.readValue<ElasticIndexedApplication>(it.sourceAsString)
                EmbeddedNameAndVersion(result.name, result.version)
            }

            val applications = db.withSession { session ->
                applicationDAO.findAllByID(session, user, project, projectGroups, embeddedNameAndVersionList, paging)
            }

            return sortAndCreatePageByScore(applications, results, user, paging)

        } else {
            val titles = results.hits.map {
                val result = defaultMapper.readValue<ElasticIndexedApplication>(it.sourceAsString)
                result.title
            }

            val applications = db.withSession { session ->
                searchDAO.multiKeywordsearch(session, user, project, projectGroups, titles.toList(), paging)
            }

            return sortAndCreatePageByScore(applications, results, user, paging)
        }
    }

    private suspend fun sortAndCreatePageByScore(
        applications: List<RowData>,
        results: SearchResponse,
        user: SecurityPrincipal,
        paging: NormalizedPaginationRequest
    ): Page<ApplicationSummaryWithFavorite> {
        val map = applications.associateBy(
            { EmbeddedNameAndVersion(
                it.getField(ApplicationTable.idName).toLowerCase(),
                it.getField(ApplicationTable.idVersion).toLowerCase()
            ) }, { it }
        )

        val sortedList = mutableListOf<RowData>()

        results.hits.hits.forEach {
            val foundEntity =
                map[EmbeddedNameAndVersion(it.sourceAsMap["name"].toString(), it.sourceAsMap["version"].toString())]
            if (foundEntity != null) {
                sortedList.add(foundEntity)
            }
        }

        val sortedResultsPage = sortedList.map { it.toApplicationWithInvocation() }.paginate(paging)

        return db.withSession { session ->
            applicationDAO.preparePageForUser(session, user.username, sortedResultsPage)
                .mapItems { it.withoutInvocation() }
        }
    }
}
