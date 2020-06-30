package dk.sdu.cloud.app.store.services

import com.fasterxml.jackson.module.kotlin.readValue
import dk.sdu.cloud.SecurityPrincipal
import dk.sdu.cloud.app.store.api.Application
import dk.sdu.cloud.app.store.api.ApplicationSummaryWithFavorite
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.db.async.AsyncDBSessionFactory
import dk.sdu.cloud.service.db.async.withSession
import dk.sdu.cloud.service.mapItems
import dk.sdu.cloud.service.paginate
import org.elasticsearch.action.search.SearchResponse

class ApplicationSearchService (
    private val db: AsyncDBSessionFactory,
    private val searchDao: ApplicationSearchAsyncDao,
    private val elasticDao: ElasticDao,
    private val applicationDao: AppStoreAsyncDao,
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
            searchDao.searchByTags(
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
            searchDao.search(
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

        val results = elasticDao.search(queryTerms, normalizedTags)

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
                applicationDao.findAllByID(session, user, project, projectGroups, embeddedNameAndVersionList, paging)
            }

            return sortAndCreatePageByScore(applications, results, user, paging)

        } else {
            val titles = results.hits.map {
                val result = defaultMapper.readValue<ElasticIndexedApplication>(it.sourceAsString)
                result.title
            }

            val applications = db.withSession { session ->
                searchDao.multiKeywordsearch(session, user, project, projectGroups, titles.toList(), paging)
            }

            return sortAndCreatePageByScore(applications, results, user, paging)
        }
    }

    private suspend fun sortAndCreatePageByScore(
        applications: List<Application>,
        results: SearchResponse,
        user: SecurityPrincipal,
        paging: NormalizedPaginationRequest
    ): Page<ApplicationSummaryWithFavorite> {
        val map = applications.associateBy(
            { EmbeddedNameAndVersion(
                it.metadata.name.toLowerCase(),
                it.metadata.version.toLowerCase()
            ) }, { it }
        )

        val sortedList = mutableListOf<Application>()

        results.hits.hits.forEach {
            val foundEntity =
                map[EmbeddedNameAndVersion(it.sourceAsMap["name"].toString(), it.sourceAsMap["version"].toString())]
            if (foundEntity != null) {
                sortedList.add(foundEntity)
            }
        }

        val sortedResultsPage = sortedList.paginate(paging)

        return db.withSession { session ->
            applicationDao.preparePageForUser(session, user.username, sortedResultsPage)
                .mapItems { it.withoutInvocation() }
        }
    }
}
