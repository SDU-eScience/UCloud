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

class AppStoreService(
    private val db: DBSessionFactory,
    private val authenticatedClient: AuthenticatedClient,
    private val applicationDAO: ApplicationDAO,
    private val toolDao: ToolDAO,
    private val aclDao: AclDao,
    private val elasticDAO: ElasticDAO,
    private val appEventProducer : AppEventProducer
) {

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

        return db.withTransaction { session ->
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

            result.copy(
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
        project: String?,
        appName: String,
        appVersion: String,
        permissions: Set<ApplicationAccessRight>
    ): Boolean {
        val projectGroups = if (project.isNullOrBlank()) {
            emptyList()
        } else {
            retrieveUserProjectGroups(securityPrincipal, project)
        }

        return db.withTransaction { session ->
            applicationDAO.isPublic(session, securityPrincipal, appName, appVersion) ||
                    aclDao.hasPermission(
                        session,
                        securityPrincipal,
                        project,
                        projectGroups,
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


        println(projectGroups)

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

    suspend fun delete(securityPrincipal: SecurityPrincipal, project: String?, appName: String, appVersion: String) {
        val projectGroups = if (project.isNullOrBlank()) {
            emptyList()
        } else {
            retrieveUserProjectGroups(securityPrincipal, project)
        }

        db.withTransaction { session ->
            applicationDAO.delete(session, securityPrincipal, project, projectGroups, appName, appVersion)
        }

        appEventProducer.produce(AppEvent.Deleted(
            appName,
            appVersion
        ))

        elasticDAO.deleteApplicationInElastic(appName, appVersion)
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

    companion object : Loggable {
        override val log = logger()
    }

}
