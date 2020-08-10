package dk.sdu.cloud.app.license.services

import dk.sdu.cloud.Roles
import dk.sdu.cloud.SecurityPrincipal
import dk.sdu.cloud.app.license.api.*
import dk.sdu.cloud.app.license.api.Project
import dk.sdu.cloud.app.license.api.ProjectAndGroup
import dk.sdu.cloud.app.license.api.ProjectGroup
import dk.sdu.cloud.app.license.services.acl.*
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orRethrowAs
import dk.sdu.cloud.project.api.*
import dk.sdu.cloud.service.db.async.DBContext
import io.ktor.http.HttpStatusCode
import java.util.*

class AppLicenseService(
    private val db: DBContext,
    private val aclService: AclService,
    private val appLicenseDao: AppLicenseAsyncDao,
    private val authenticatedClient: AuthenticatedClient
) {
    suspend fun getLicenseServer(securityPrincipal: SecurityPrincipal, serverId: String, accessEntity: AccessEntity): LicenseServerWithId? {
        if (
            !aclService.hasPermission(serverId, accessEntity, ServerAccessRight.READ) &&
            securityPrincipal.role !in Roles.PRIVILEGED
        ) {
            throw RPCException("Unauthorized request to license server", HttpStatusCode.Unauthorized)
        }

        return appLicenseDao.getById(db, serverId)
            ?: throw RPCException("The requested license server was not found", HttpStatusCode.NotFound)
    }

    suspend fun updateAcl(serverId: String, changes: List<AclEntryRequest>, principal: SecurityPrincipal) {
        val accessEntity = AccessEntity(principal.username, null, null)
        aclService.updatePermissions(serverId, changes, accessEntity)
    }

    suspend fun deleteProjectGroupAclEntries(project: String, group: String)  {
        val accessEntity = AccessEntity(null, project, group)
        aclService.revokeAllFromEntity(accessEntity)
    }

    suspend fun listAcl(request: ListAclRequest, user: SecurityPrincipal): List<DetailedAccessEntityWithPermission> {
        return if (Roles.PRIVILEGED.contains(user.role)) {
            aclService.listAcl(request.serverId).map {
                if (!it.entity.project.isNullOrBlank() && !it.entity.group.isNullOrBlank()) {
                    val lookup = ProjectGroups.lookupProjectAndGroup.call(
                        LookupProjectAndGroupRequest(it.entity.project!!, it.entity.group!!),
                        authenticatedClient
                    ).orRethrowAs {
                        throw RPCException.fromStatusCode(HttpStatusCode.InternalServerError, "Failed to fetch project entities")
                    }

                    DetailedAccessEntityWithPermission(
                        DetailedAccessEntity(
                            null,
                            Project(lookup.project.id, lookup.project.title),
                            ProjectGroup(lookup.group.id, lookup.group.title)
                        ),
                        it.permission
                    )
                } else {
                    DetailedAccessEntityWithPermission(
                        DetailedAccessEntity(
                            it.entity.user,
                            null,
                            null
                        ),
                        it.permission
                    )
                }
            }
        } else {
            throw RPCException.fromStatusCode(HttpStatusCode.Unauthorized, "Not allowed")
        }
    }

    suspend fun listServers(tags: List<String>, principal: SecurityPrincipal): List<LicenseServerId> {
        val projectGroups = ProjectMembers.userStatus.call(
            UserStatusRequest(principal.username),
            authenticatedClient
        ).orRethrowAs {
            throw RPCException.fromStatusCode(HttpStatusCode.InternalServerError)
        }.groups.map {
            ProjectAndGroup(it.project, it.group)
        }

        return appLicenseDao.list(
                db,
                tags,
                principal,
                projectGroups
            ) ?: throw RPCException("No available license servers found", HttpStatusCode.NotFound)
    }

    suspend fun listAllServers(user: SecurityPrincipal): List<LicenseServerWithId> {
        return appLicenseDao.listAll( db, user )
            ?: throw RPCException("No available license servers found", HttpStatusCode.NotFound)
    }

    suspend fun createLicenseServer(request: NewServerRequest, entity: AccessEntity): String {


        val license = if (request.license.isNullOrBlank()) {
            null
        } else {
            request.license
        }
        val serverId = UUID.randomUUID().toString()

        if (0 > request.port || 65535 < request.port) {
            throw RPCException("Invalid port number", HttpStatusCode.BadRequest)
        }

        appLicenseDao.create(
                db,
                serverId,
                LicenseServer(
                    request.name,
                    request.address,
                    request.port,
                    license
                )
            )

        // Add rw permissions for the creator
        aclService.updatePermissionsWithSession(serverId, entity, ServerAccessRight.READ_WRITE)

        return serverId
    }

    suspend fun updateLicenseServer(securityPrincipal: SecurityPrincipal, request: UpdateServerRequest, accessEntity: AccessEntity): String {
        if (
            aclService.hasPermission(request.withId, accessEntity, ServerAccessRight.READ_WRITE) ||
            securityPrincipal.role in Roles.PRIVILEGED
        ) {
            // Save information for existing license server
            appLicenseDao.update(
                db,
                LicenseServerWithId(
                    request.withId,
                    request.name,
                    request.address,
                    request.port,
                    request.license
                )
            )


            return request.withId
        } else {
            throw RPCException("Not authorized to change license server details", HttpStatusCode.Unauthorized)
        }
    }

    suspend fun deleteLicenseServer(securityPrincipal: SecurityPrincipal, request: DeleteServerRequest) {
        if (
            aclService.hasPermission(
                request.id,
                AccessEntity(securityPrincipal.username, null, null),
                ServerAccessRight.READ_WRITE
            ) || securityPrincipal.role in Roles.PRIVILEGED
        ) {
            // Delete Acl entries for the license server
            aclService.revokeAllServerPermissionsWithSession(request.id)

            // Delete tags
            val tagsToServer = listTags(request.id)
            tagsToServer.forEach { deleteTag(it, request.id) }
            // Delete license server
            appLicenseDao.delete(db, request.id)
        } else {
            throw RPCException("Not authorized to delete license server", HttpStatusCode.Unauthorized)
        }
    }

    suspend fun addTag(name: String, serverId: String) {
        appLicenseDao.addTag(db, name, serverId)
    }

    suspend fun listTags(serverId: String): List<String> {
        return appLicenseDao.listTags(db, serverId)
    }

    suspend fun deleteTag(name: String, serverId: String) {
        appLicenseDao.deleteTag(db, name, serverId)
    }
}
