package dk.sdu.cloud.app.store.services

import dk.sdu.cloud.ActorAndProject
import dk.sdu.cloud.Role
import dk.sdu.cloud.SecurityPrincipal
import dk.sdu.cloud.app.store.api.*
import dk.sdu.cloud.app.store.api.Project
import dk.sdu.cloud.app.store.api.ProjectGroup
import dk.sdu.cloud.app.store.services.acl.*
import dk.sdu.cloud.auth.api.LookupUsersRequest
import dk.sdu.cloud.auth.api.UserDescriptions
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orRethrowAs
import dk.sdu.cloud.project.api.*
import dk.sdu.cloud.service.*
import dk.sdu.cloud.service.db.async.AsyncDBSessionFactory
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.withSession
import dk.sdu.cloud.service.db.withTransaction

class AppStoreService(
    private val db: AsyncDBSessionFactory,
    private val authenticatedClient: AuthenticatedClient,
    private val applicationDao: AppStoreAsyncDao,
    private val publicDAO: ApplicationPublicAsyncDao,
    private val toolDao: ToolAsyncDao,
    private val aclDao: AclAsyncDao,
    private val elasticDao: ElasticDao?,
    private val appEventProducer: AppEventProducer?
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
            retrieveUserProjectGroups(securityPrincipal, project, authenticatedClient)
        }

        return db.withSession { session ->
            val result = applicationDao.findByNameAndVersionForUser(
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
            retrieveUserProjectGroups(securityPrincipal, project, authenticatedClient)
        }

        return db.withSession { session ->
            publicDAO.isPublic(session, securityPrincipal, appName, appVersion) ||
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
    ): List<DetailedEntityWithPermission> {
        if (securityPrincipal.role != Role.ADMIN) throw RPCException(
            "Unable to access application permissions",
            HttpStatusCode.Unauthorized
        )
        return db.withTransaction { session ->
            aclDao.listAcl(
                session,
                applicationName
            ).map { accessEntity ->
                val projectAndGroupLookup =
                    if (!accessEntity.entity.project.isNullOrBlank() && !accessEntity.entity.group.isNullOrBlank()) {
                        ProjectGroups.lookupProjectAndGroup.call(
                            LookupProjectAndGroupRequest(accessEntity.entity.project!!, accessEntity.entity.group!!),
                            authenticatedClient
                        ).orRethrowAs {
                            throw RPCException.fromStatusCode(HttpStatusCode.InternalServerError)
                        }
                    } else {
                        null
                    }

                DetailedEntityWithPermission(
                    if (projectAndGroupLookup != null) {
                        DetailedAccessEntity(
                            null,
                            Project(
                                projectAndGroupLookup.project.id,
                                projectAndGroupLookup.project.title
                            ),
                            ProjectGroup(
                                projectAndGroupLookup.group.id,
                                projectAndGroupLookup.group.title
                            )
                        )
                    } else {
                        DetailedAccessEntity(
                            accessEntity.entity.user,
                            null,
                            null
                        )
                    },
                    accessEntity.permission
                )
            }
        }
    }

    suspend fun updatePermissions(
        actorAndProject: ActorAndProject,
        applicationName: String,
        changes: List<ACLEntryRequest>
    ) {
        return db.withTransaction { session ->
            verifyAppUpdatePermission(actorAndProject, session, applicationName)

            changes.forEach { change ->
                if (!change.revoke) {
                    updatePermissionsWithSession(session, applicationName, change.entity, change.rights)
                } else {
                    revokePermissionWithSession(session, applicationName, change.entity)
                }
            }
        }
    }

    private suspend fun updatePermissionsWithSession(
        session: DBContext,
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
        } else if (!entity.project.isNullOrBlank() && !entity.group.isNullOrBlank()) {
            log.debug("Verifying that project exists")

            val projectLookup = Projects.lookupByPath.call(
                LookupByTitleRequest(entity.project!!),
                authenticatedClient
            ).orRethrowAs {
                throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
            }

            log.debug("Verifying that project group exists")

            val groupLookup = ProjectGroups.lookupByTitle.call(
                LookupByGroupTitleRequest(projectLookup.id, entity.group!!),
                authenticatedClient
            ).orRethrowAs {
                throw RPCException.fromStatusCode(
                    HttpStatusCode.BadRequest,
                    "The project group does not exist"
                )
            }

            val entityWithProjectId = AccessEntity(
                null,
                projectLookup.id,
                groupLookup.groupId
            )

            aclDao.updatePermissions(session, entityWithProjectId, applicationName, permissions)
        } else {
            throw RPCException.fromStatusCode(HttpStatusCode.BadRequest, "Neither user or project group defined")
        }
    }

    private suspend fun revokePermissionWithSession(
        session: DBContext,
        applicationName: String,
        entity: AccessEntity
    ) {
        aclDao.revokePermission(session, entity, applicationName)
    }

    @OptIn(ExperimentalStdlibApi::class)
    suspend fun findBySupportedFileExtension(
        securityPrincipal: SecurityPrincipal,
        request: NormalizedPaginationRequestV2,
        project: String?,
        files: List<String>
    ): PageV2<ApplicationWithExtension> {
        val extensions = files.flatMap { file ->
            if (file.contains(".")) {
                listOf("." + file.substringAfterLast('.'))
            } else {
                buildList {
                    val name = file.substringAfterLast('/')
                    add(name.removeSuffix("/"))

                    if (file.endsWith("/")) {
                        add("$name/")
                        add("/")
                    }
                }
            }
        }.toSet()

        val projectGroups = if (project.isNullOrBlank()) {
            emptyList()
        } else {
            retrieveUserProjectGroups(securityPrincipal, project, authenticatedClient)
        }

        return applicationDao.findBySupportedFileExtension(
            db,
            securityPrincipal,
            project,
            projectGroups,
            request,
            extensions
        )
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
            retrieveUserProjectGroups(securityPrincipal, project, authenticatedClient)
        }

        return db.withTransaction {
            applicationDao.findAllByName(
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
            retrieveUserProjectGroups(securityPrincipal, project, authenticatedClient)
        }

        return db.withTransaction { session ->
            applicationDao.listLatestVersion(
                session,
                securityPrincipal,
                project,
                projectGroups,
                normalizedPaginationRequest
            )
        }
    }

    suspend fun findGroup(
        securityPrincipal: SecurityPrincipal,
        project: String?,
        applicationName: String
    ): FindGroupResponse {
        return db.withTransaction { session ->
            applicationDao.findGroup(session, project, applicationName)
        }
    }

    suspend fun overview(securityPrincipal: SecurityPrincipal, project: String?): AppStoreOverviewResponse {
        val projectGroups = if (project.isNullOrBlank()) {
            emptyList()
        } else {
            retrieveUserProjectGroups(securityPrincipal, project, authenticatedClient)
        }

        return AppStoreOverviewResponse(
            /*db.withTransaction {
                applicationDao.overview(db, securityPrincipal, project, projectGroups)
            }*/
            emptyList()
        )
    }

    suspend fun browseSections(securityPrincipal: SecurityPrincipal, project: String?, pageType: AppStorePageType): AppStoreOverviewResponse {
        val projectGroups = if (project.isNullOrBlank()) {
            emptyList()
        } else {
            retrieveUserProjectGroups(securityPrincipal, project, authenticatedClient)
        }

        return AppStoreOverviewResponse(
            db.withTransaction {
                applicationDao.browseSections(db, securityPrincipal, project, projectGroups, pageType)
            }
        )
    }

    suspend fun create(actorAndProject: ActorAndProject, application: Application, content: String) {
        db.withTransaction { session ->
            applicationDao.create(session, actorAndProject, application, content)
        }
        elasticDao?.createApplicationInElastic(
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
            retrieveUserProjectGroups(securityPrincipal, project, authenticatedClient)
        }

        db.withTransaction { session ->
            applicationDao.delete(session, securityPrincipal, project, projectGroups, appName, appVersion)
        }

        appEventProducer?.produce(
            AppEvent.Deleted(
                appName,
                appVersion
            )
        )

        elasticDao?.deleteApplicationInElastic(appName, appVersion)
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
            retrieveUserProjectGroups(user, project, authenticatedClient)
        }

        return db.withTransaction { session ->
            applicationDao.findLatestByTool(session, user, project, projectGroups, tool, paging)
        }
    }

    companion object : Loggable {
        override val log = logger()
    }

}
