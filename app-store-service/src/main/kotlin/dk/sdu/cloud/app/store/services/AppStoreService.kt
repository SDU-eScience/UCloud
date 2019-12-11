package dk.sdu.cloud.app.store.services

import com.fasterxml.jackson.module.kotlin.readValue
import dk.sdu.cloud.SecurityPrincipal
import dk.sdu.cloud.app.store.api.*
import dk.sdu.cloud.app.store.services.acl.*
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.defaultMapper
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
    private val applicationDAO: ApplicationDAO<DBSession>,
    private val toolDao: ToolDAO<DBSession>,
    private val aclDao: AclDao<DBSession>,
    private val elasticDAO: ElasticDAO
) {
    fun toggleFavorite(securityPrincipal: SecurityPrincipal, name: String, version: String) {
        db.withTransaction { session ->
            applicationDAO.toggleFavorite(
                session,
                securityPrincipal,
                name,
                version
            )
        }
    }

    fun retrieveFavorites(
        securityPrincipal: SecurityPrincipal,
        request: PaginationRequest
    ): Page<ApplicationSummaryWithFavorite> = db.withTransaction { session ->
        applicationDAO.retrieveFavorites(
            session,
            securityPrincipal,
            request.normalize()
        )
    }

    fun searchTags(
        securityPrincipal: SecurityPrincipal,
        tags: List<String>,
        normalizedPaginationRequest: NormalizedPaginationRequest
    ): Page<ApplicationSummaryWithFavorite> =
        db.withTransaction { session ->
            applicationDAO.searchTags(
                session,
                securityPrincipal,
                tags,
                normalizedPaginationRequest
            )
        }

    fun searchApps(
        securityPrincipal: SecurityPrincipal,
        query: String,
        normalizedPaginationRequest: NormalizedPaginationRequest
    ): Page<ApplicationSummaryWithFavorite> =
        db.withTransaction { session ->
            applicationDAO.search(
                session,
                securityPrincipal,
                query,
                normalizedPaginationRequest
            )
        }

    fun findByNameAndVersion(
        securityPrincipal: SecurityPrincipal,
        name: String,
        version: String
    ): ApplicationWithFavoriteAndTags {
        db.withTransaction { session ->
            val result = applicationDAO.findByNameAndVersionForUser(
                session,
                securityPrincipal,
                name,
                version
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

    fun hasPermission(
        securityPrincipal: SecurityPrincipal,
        name: String,
        permissionSet: Set<ApplicationAccessRight>
    ): Boolean {
        return db.withTransaction { session ->
            applicationDAO.isPublic(session, securityPrincipal, name) ||
            aclDao.hasPermission(
                session,
                UserEntity(securityPrincipal.username, EntityType.USER),
                name,
                permissionSet
            )
        }
    }

    fun listAcl(
        securityPrincipal: SecurityPrincipal,
        applicationName: String
    ): List<EntityWithPermission> {

        return db.withTransaction { session ->
            return if (aclDao.hasPermission(
                    session,
                    UserEntity(securityPrincipal.username, EntityType.USER),
                    applicationName,
                    ApplicationPermission.CHANGE
                ) || applicationDAO.isOwnerOfApplication(session, securityPrincipal, applicationName)
            ) {
                aclDao.listAcl(
                    session,
                    applicationName
                )
            } else {
                throw RPCException("Unable to access application permissions", HttpStatusCode.Unauthorized)
            }
        }
    }

    fun updatePermissions(
        securityPrincipal: SecurityPrincipal,
        applicationName: String,
        changes: List<ACLEntryRequest>
    ) {
        val userEntity = UserEntity(securityPrincipal.username, EntityType.USER)

        println("Reached updatePermissions")

        return db.withTransaction { session ->
            if (aclDao.hasPermission(
                    session,
                    userEntity,
                    applicationName,
                    ApplicationPermission.CHANGE
                ) || applicationDAO.isOwnerOfApplication(session, securityPrincipal, applicationName)
            ) {
                println("permission granted")

                changes.forEach { change ->
                    if (!change.revoke) {
                        println("Calling updatePermissionsWithSession")
                        updatePermissionsWithSession(session, applicationName, change.entity, change.rights)
                    } else {
                        println("Calling revokePermissionsWithSession")
                        revokePermissionWithSession(session, applicationName, change.entity)
                    }
                }
            } else {
                throw RPCException("Request to update permissions unauthorized", HttpStatusCode.Unauthorized)
            }
        }
    }

    fun updatePermissionsWithSession(
        session: DBSession,
        applicationName: String,
        entity: UserEntity,
        permissions: ApplicationAccessRight
    ) {
        println("Updating permission")
        aclDao.updatePermissions(session, entity, applicationName, permissions)
    }

    fun revokePermissionWithSession(
        session: DBSession,
        applicationName: String,
        entity: UserEntity
    ) {
        aclDao.revokePermission(session, entity, applicationName)
    }

    fun findBySupportedFileExtension(
        securityPrincipal: SecurityPrincipal,
        files: List<String>
    ): List<ApplicationWithExtension> {
        val extensions = files.map { file ->
            if (file.contains(".")) {
                "." + file.substringAfterLast('.')
            } else {
                file.substringAfterLast('/')
            }
        }.toSet()

        return db.withTransaction {
            applicationDAO.findBySupportedFileExtension(
                it,
                securityPrincipal,
                extensions
            )
        }
    }

    fun findByName(
        securityPrincipal: SecurityPrincipal,
        name: String,
        normalizedPaginationRequest: NormalizedPaginationRequest
    ): Page<ApplicationSummaryWithFavorite> =
        db.withTransaction {
            applicationDAO.findAllByName(
                it,
                securityPrincipal,
                name,
                normalizedPaginationRequest
            )
        }

    fun isPublic(
        securityPrincipal: SecurityPrincipal,
        name: String
    ): Boolean =
        db.withTransaction {
            applicationDAO.isPublic(
                it,
                securityPrincipal,
                name
            )
        }

    fun listAll(
        securityPrincipal: SecurityPrincipal,
        normalizedPaginationRequest: NormalizedPaginationRequest
    ): Page<ApplicationSummaryWithFavorite> =
        db.withTransaction { session ->
            applicationDAO.listLatestVersion(
                session,
                securityPrincipal,
                normalizedPaginationRequest
            )

        }

    fun create(securityPrincipal: SecurityPrincipal, application: Application, content: String) {
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

    fun createTags(tags: List<String>, applicationName: String, user: SecurityPrincipal) {
        db.withTransaction { session ->
            applicationDAO.createTags(session, user, applicationName, tags)
        }
        elasticDAO.addTagToElastic(applicationName, tags)

    }

    fun deleteTags(tags: List<String>, applicationName: String, user: SecurityPrincipal) {
        db.withTransaction { session ->
            applicationDAO.deleteTags(session, user, applicationName, tags)
        }
        elasticDAO.removeTagFromElastic(applicationName, tags)
    }

    fun findLatestByTool(
        user: SecurityPrincipal,
        tool: String,
        paging: NormalizedPaginationRequest
    ): Page<Application> {
        return db.withTransaction { session ->
            applicationDAO.findLatestByTool(session, user, tool, paging)
        }
    }

    fun advancedSearch(
        user: SecurityPrincipal,
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

        if (showAllVersions) {
            val embeddedNameAndVersionList = results.hits.map {
                val result = defaultMapper.readValue<ElasticIndexedApplication>(it.sourceAsString)
                EmbeddedNameAndVersion(result.name, result.version)
            }

            val applications = db.withTransaction { session ->
                applicationDAO.findAllByID(session, user, embeddedNameAndVersionList, paging)
            }

            return sortAndCreatePageByScore(applications, results, user, paging)

        } else {
            val titles = results.hits.map {
                val result = defaultMapper.readValue<ElasticIndexedApplication>(it.sourceAsString)
                result.title
            }

            val applications = db.withTransaction { session ->
                applicationDAO.multiKeywordsearch(session, titles.toList(), paging)
            }

            return sortAndCreatePageByScore(applications, results, user, paging)
        }
    }


    private fun sortAndCreatePageByScore(
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
