package dk.sdu.cloud.app.store.services

import com.fasterxml.jackson.module.kotlin.readValue
import dk.sdu.cloud.Role
import dk.sdu.cloud.SecurityPrincipal
import dk.sdu.cloud.app.store.api.*
import dk.sdu.cloud.app.store.services.acl.*
import dk.sdu.cloud.auth.api.LookupUsersRequest
import dk.sdu.cloud.auth.api.UserDescriptions
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orRethrowAs
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.project.api.GroupExistsRequest
import dk.sdu.cloud.project.api.ProjectGroups
import dk.sdu.cloud.project.api.ProjectMembers
import dk.sdu.cloud.project.api.UserStatusRequest
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.PaginationRequest
import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.withTransaction
import dk.sdu.cloud.service.mapItems
import dk.sdu.cloud.service.paginate
import io.ktor.http.HttpStatusCode
import org.elasticsearch.action.search.SearchResponse

class AppStoreService<DBSession>(
    private val db: DBSessionFactory<DBSession>,
    private val authenticatedClient: AuthenticatedClient,
    private val applicationDAO: ApplicationDAO<DBSession>,
    private val toolDao: ToolDAO<DBSession>,
    private val aclDao: AclDao<DBSession>,
    private val elasticDAO: ElasticDAO
) {
    suspend fun toggleFavorite(securityPrincipal: SecurityPrincipal, appName: String, appVersion: String) {
        db.withTransaction { session ->
            applicationDAO.toggleFavorite(
                session,
                securityPrincipal,
                appName,
                appVersion
            )
        }
    }

    private suspend fun retrieveUserProjectGroups(user: SecurityPrincipal, project: String): List<String> =
        ProjectMembers.userStatus.call(
            UserStatusRequest(user.username),
            authenticatedClient
        ).orRethrowAs {
            throw RPCException.fromStatusCode(HttpStatusCode.InternalServerError)
        }.groups.map {
            if (it.projectId == project) {
                it.group
            }
        } as List<String>

    suspend fun retrieveFavorites(
        securityPrincipal: SecurityPrincipal,
        project: String?,
        request: PaginationRequest
    ): Page<ApplicationSummaryWithFavorite> {
        val projectGroups = if (project.isNullOrBlank()) {
            emptyList()
        } else {
            retrieveUserProjectGroups(securityPrincipal, project)
        }

        return db.withTransaction { session ->
            applicationDAO.retrieveFavorites(
                session,
                securityPrincipal,
                project,
                projectGroups as List<String>,
                request.normalize()
            )
        }
    }

    suspend fun searchTags(
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

    suspend fun findByNameAndVersion(
        securityPrincipal: SecurityPrincipal,
        project: String?,
        appName: String,
        appVersion: String
    ): ApplicationWithFavoriteAndTags {
        val projectGroups = if (project.isNullOrBlank()) {
            emptyList()
        } else {
            retrieveUserProjectGroups(securityPrincipal, project)
        }

        db.withTransaction { session ->
            val result = applicationDAO.findByNameAndVersionForUser(
                session,
                securityPrincipal,
                project,
                projectGroups,
                appName,
                appVersion
            )

            val toolRef = result.invocation.tool
            val tool = toolDao.findByNameAndVersion(session, securityPrincipal, toolRef.name, toolRef.version)

            return result.copy(
                invocation = result.invocation.copy(
                    tool = ToolReference(
                        toolRef.name,
                        toolRef.version,
                        tool
                    )
                )
            )
        }
    }

    suspend fun hasPermission(
        securityPrincipal: SecurityPrincipal,
        appName: String,
        appVersion: String,
        permissions: Set<ApplicationAccessRight>
    ): Boolean {
        return db.withTransaction { session ->
            applicationDAO.isPublic(session, securityPrincipal, appName, appVersion) ||
                    aclDao.hasPermission(
                        session,
                        AccessEntity(securityPrincipal.username, null, null),
                        appName,
                        permissions
                    )
        }
    }

    suspend fun listAcl(
        securityPrincipal: SecurityPrincipal,
        applicationName: String
    ): List<EntityWithPermission> {
        if (securityPrincipal.role != Role.ADMIN) throw RPCException(
            "Unable to access application permissions",
            HttpStatusCode.Unauthorized
        )
        return db.withTransaction { session ->
            aclDao.listAcl(
                session,
                applicationName
            )
        }
    }

    suspend fun updatePermissions(
        securityPrincipal: SecurityPrincipal,
        applicationName: String,
        changes: List<ACLEntryRequest>
    ) {
        return db.withTransaction { session ->
            if (securityPrincipal.role == Role.ADMIN) {
                changes.forEach { change ->
                    if (!change.revoke) {
                        updatePermissionsWithSession(session, applicationName, change.entity, change.rights)
                    } else {
                        revokePermissionWithSession(session, applicationName, change.entity)
                    }
                }
            } else {
                throw RPCException("Request to update permissions unauthorized", HttpStatusCode.Unauthorized)
            }
        }
    }

    private suspend fun updatePermissionsWithSession(
        session: DBSession,
        applicationName: String,
        entity: AccessEntity,
        permissions: ApplicationAccessRight
    ) {
        if (!entity.user.isNullOrBlank()) {
            log.debug("Verifying that user exists")

            val lookup = UserDescriptions.lookupUsers.call(
                LookupUsersRequest(listOf(entity.user!!)),
                authenticatedClient
            ).orRethrowAs {
                throw RPCException.fromStatusCode(HttpStatusCode.InternalServerError)
            }

            if (lookup.results[entity.user!!] == null) throw RPCException.fromStatusCode(
                HttpStatusCode.BadRequest,
                "The user does not exist"
            )

            if (lookup.results[entity.user!!]?.role == Role.SERVICE) {
                throw RPCException.fromStatusCode(HttpStatusCode.BadRequest, "The user does not exist")
            }
            aclDao.updatePermissions(session, entity, applicationName, permissions)
        } else if(!entity.project.isNullOrBlank() && !entity.group.isNullOrBlank()) {
            log.debug("Verifying that project group exists")

            val lookup = ProjectGroups.groupExists.call(
                GroupExistsRequest(entity.project!!, entity.group!!),
                authenticatedClient
            ).orRethrowAs {
                throw RPCException.fromStatusCode(HttpStatusCode.InternalServerError)
            }

            if (!lookup.exists) throw RPCException.fromStatusCode(
                HttpStatusCode.BadRequest,
                "The project group does not exist"
            )
            aclDao.updatePermissions(session, entity, applicationName, permissions)
        } else {
            throw RPCException.fromStatusCode(HttpStatusCode.BadRequest, "Neither user or project group defined")
        }
    }

    private fun revokePermissionWithSession(
        session: DBSession,
        applicationName: String,
        entity: AccessEntity
    ) {
        aclDao.revokePermission(session, entity, applicationName)
    }

    suspend fun findBySupportedFileExtension(
        securityPrincipal: SecurityPrincipal,
        project: String?,
        files: List<String>
    ): List<ApplicationWithExtension> {
        val extensions = files.map { file ->
            if (file.contains(".")) {
                "." + file.substringAfterLast('.')
            } else {
                file.substringAfterLast('/')
            }
        }.toSet()

        val projectGroups = if (project.isNullOrBlank()) {
            emptyList()
        } else {
            retrieveUserProjectGroups(securityPrincipal, project)
        }

        return db.withTransaction {
            applicationDAO.findBySupportedFileExtension(
                it,
                securityPrincipal,
                project,
                projectGroups,
                extensions
            )
        }
    }

    suspend fun findByName(
        securityPrincipal: SecurityPrincipal,
        project: String?,
        appName: String,
        normalizedPaginationRequest: NormalizedPaginationRequest
    ): Page<ApplicationSummaryWithFavorite> {
        val projectGroups = if (project.isNullOrBlank()) {
            emptyList()
        } else {
            retrieveUserProjectGroups(securityPrincipal, project)
        }

        return db.withTransaction {
            applicationDAO.findAllByName(
                it,
                securityPrincipal,
                project,
                projectGroups,
                appName,
                normalizedPaginationRequest
            )
        }
    }

    suspend fun isPublic(
        securityPrincipal: SecurityPrincipal,
        applications: List<NameAndVersion>
    ): Map<NameAndVersion, Boolean> {
        return db.withTransaction {session ->
            applications.map { app ->
                Pair<NameAndVersion, Boolean>(
                    NameAndVersion(app.name, app.version),
                    applicationDAO.isPublic(
                        session,
                        securityPrincipal,
                        app.name,
                        app.version
                    )
                )
            }.toMap()
        }
    }

    suspend fun setPublic(
        securityPrincipal: SecurityPrincipal,
        appName: String,
        appVersion: String,
        public: Boolean
    ) {
        db.withTransaction {
            applicationDAO.setPublic(
                it,
                securityPrincipal,
                appName,
                appVersion,
                public
            )
        }
    }

    suspend fun listAll(
        securityPrincipal: SecurityPrincipal,
        project: String?,
        normalizedPaginationRequest: NormalizedPaginationRequest
    ): Page<ApplicationSummaryWithFavorite> {
        val projectGroups = if (project.isNullOrBlank()) {
            emptyList()
        } else {
            retrieveUserProjectGroups(securityPrincipal, project)
        }

        return db.withTransaction { session ->
            applicationDAO.listLatestVersion(
                session,
                securityPrincipal,
                project,
                projectGroups,
                normalizedPaginationRequest
            )
        }
    }

    suspend fun create(securityPrincipal: SecurityPrincipal, application: Application, content: String) {
        db.withTransaction { session ->
            applicationDAO.create(session, securityPrincipal, application, content)
        }
        elasticDAO.createApplicationInElastic(
            application.metadata.name,
            application.metadata.version,
            application.metadata.description,
            application.metadata.title
        )
    }

    suspend fun delete(securityPrincipal: SecurityPrincipal, appName: String, appVersion: String) {
        db.withTransaction { session ->
            applicationDAO.delete(session, securityPrincipal, appName, appVersion)
        }

        elasticDAO.deleteApplicationInElastic(appName, appVersion)
    }

    suspend fun createTags(tags: List<String>, applicationName: String, user: SecurityPrincipal) {
        db.withTransaction { session ->
            applicationDAO.createTags(session, user, applicationName, tags)
        }
        elasticDAO.addTagToElastic(applicationName, tags)

    }

    suspend fun deleteTags(tags: List<String>, applicationName: String, user: SecurityPrincipal) {
        db.withTransaction { session ->
            applicationDAO.deleteTags(session, user, applicationName, tags)
        }
        elasticDAO.removeTagFromElastic(applicationName, tags)
    }

    suspend fun findLatestByTool(
        user: SecurityPrincipal,
        project: String?,
        tool: String,
        paging: NormalizedPaginationRequest
    ): Page<Application> {
        val projectGroups = if (project.isNullOrBlank()) {
            emptyList()
        } else {
            retrieveUserProjectGroups(user, project)
        }

        return db.withTransaction { session ->
            applicationDAO.findLatestByTool(session, user, project, projectGroups, tool, paging)
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

    companion object : Loggable {
        override val log = logger()
    }

}
