package app.store.services

import com.fasterxml.jackson.module.kotlin.readValue
import dk.sdu.cloud.SecurityPrincipal
import dk.sdu.cloud.app.store.api.ApplicationSummaryWithFavorite
import dk.sdu.cloud.app.store.services.ElasticIndexedApplication
import dk.sdu.cloud.app.store.services.EmbeddedNameAndVersion
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.db.withTransaction
import org.elasticsearch.action.search.SearchResponse

class ApplicationSearchService () {
    suspend fun searchByTags(
        securityPrincipal: SecurityPrincipal,
        project: String?,
        tags: List<String>,
        normalizedPaginationRequest: NormalizedPaginationRequest
    ): Page<ApplicationSummaryWithFavorite> {
        val projectGroups = if (project.isNullOrBlank()) {
            emptyList()
        } else {
            retrieveUserProjectGroups(securityPrincipal, project)
        }

        return db.withTransaction { session ->
            applicationDAO.searchTags(
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
            retrieveUserProjectGroups(securityPrincipal, project)
        }

        return db.withTransaction { session ->
            applicationDAO.search(
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
            retrieveUserProjectGroups(user, project)
        }

        if (showAllVersions) {
            val embeddedNameAndVersionList = results.hits.map {
                val result = defaultMapper.readValue<ElasticIndexedApplication>(it.sourceAsString)
                EmbeddedNameAndVersion(result.name, result.version)
            }

            val applications = db.withTransaction { session ->
                applicationDAO.findAllByID(session, user, project, projectGroups, embeddedNameAndVersionList, paging)
            }

            return sortAndCreatePageByScore(applications, results, user, paging)

        } else {
            val titles = results.hits.map {
                val result = defaultMapper.readValue<ElasticIndexedApplication>(it.sourceAsString)
                result.title
            }

            val applications = db.withTransaction { session ->
                applicationDAO.multiKeywordsearch(session, user, project, projectGroups, titles.toList(), paging)
            }

            return sortAndCreatePageByScore(applications, results, user, paging)
        }
    }

    private suspend fun sortAndCreatePageByScore(
        applications: List<ApplicationEntity>,
        results: SearchResponse,
        user: SecurityPrincipal,
        paging: NormalizedPaginationRequest
    ): Page<ApplicationSummaryWithFavorite> {
        val map = applications.associateBy(
            { EmbeddedNameAndVersion(it.id.name.toLowerCase(), it.id.version.toLowerCase()) }, { it }
        )

        val sortedList = mutableListOf<ApplicationEntity>()

        results.hits.hits.forEach {
            val foundEntity =
                map[EmbeddedNameAndVersion(it.sourceAsMap["name"].toString(), it.sourceAsMap["version"].toString())]
            if (foundEntity != null) {
                sortedList.add(foundEntity)
            }
        }

        val sortedResultsPage = sortedList.map { it.toModelWithInvocation() }.paginate(paging)

        return db.withTransaction { session ->
            applicationDAO.preparePageForUser(session, user.username, sortedResultsPage)
                .mapItems { it.withoutInvocation() }
        }
    }
}
