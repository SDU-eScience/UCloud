package dk.sdu.cloud.app.license.services

import dk.sdu.cloud.Roles
import dk.sdu.cloud.SecurityPrincipal
import dk.sdu.cloud.app.license.api.*
import dk.sdu.cloud.app.license.services.acl.*
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orRethrowAs
import dk.sdu.cloud.project.api.ProjectMembers
import dk.sdu.cloud.project.api.UserStatusRequest
import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.withTransaction
import io.ktor.http.HttpStatusCode
import java.util.*

class AppLicenseService<Session>(
    private val db: DBSessionFactory<Session>,
    private val aclService: AclService<Session>,
    private val appLicenseDao: AppLicenseDao<Session>,
    private val authenticatedClient: AuthenticatedClient
) {
    suspend fun getLicenseServer(securityPrincipal: SecurityPrincipal, serverId: String, accessEntity: AccessEntity): LicenseServerWithId? {
        if (
            !aclService.hasPermission(serverId, accessEntity, ServerAccessRight.READ) &&
            securityPrincipal.role !in Roles.PRIVILEDGED
        ) {
            throw RPCException("Unauthorized request to license server", HttpStatusCode.Unauthorized)
        }

        return db.withTransaction { session ->
            appLicenseDao.getById(session, serverId)
        } ?: throw RPCException("The requested license server was not found", HttpStatusCode.NotFound)
    }

    suspend fun updateAcl(serverId: String, changes: List<AclEntryRequest>, principal: SecurityPrincipal) {
        val accessEntity = AccessEntity(principal.username, null, null)
        aclService.updatePermissions(serverId, changes, accessEntity)
    }

    suspend fun deleteProjectGroupAclEntries(project: String, group: String)  {
        val accessEntity = AccessEntity(null, project, group)
        aclService.revokeAllFromEntity(accessEntity)
    }

    suspend fun listAcl(request: ListAclRequest, user: SecurityPrincipal): List<AccessEntityWithPermission> {
        return if (Roles.PRIVILEDGED.contains(user.role)) {
            aclService.listAcl(request.serverId)
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
            ProjectAndGroup(it.projectId, it.group)
        }

        return db.withTransaction { session ->
            appLicenseDao.list(
                session,
                tags,
                principal,
                projectGroups
            )
        } ?: throw RPCException("No available license servers found", HttpStatusCode.NotFound)
    }

    suspend fun listAllServers(user: SecurityPrincipal): List<LicenseServerWithId> {
        return db.withTransaction { session ->
            appLicenseDao.listAll(
                session,
                user
            )
        } ?: throw RPCException("No available license servers found", HttpStatusCode.NotFound)
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

        db.withTransaction { session ->
            appLicenseDao.create(
                session,
                serverId,
                LicenseServer(
                    request.name,
                    request.address,
                    request.port,
                    license
                )
            )

            // Add rw permissions for the creator
            aclService.updatePermissionsWithSession(session, serverId, entity, ServerAccessRight.READ_WRITE)
        }
        return serverId
    }

    suspend fun updateLicenseServer(securityPrincipal: SecurityPrincipal, request: UpdateServerRequest, accessEntity: AccessEntity): String {
        if (
            aclService.hasPermission(request.withId, accessEntity, ServerAccessRight.READ_WRITE) ||
            securityPrincipal.role in Roles.PRIVILEDGED
        ) {
            // Save information for existing license server
            db.withTransaction { session ->
                appLicenseDao.update(
                    session,
                    LicenseServerWithId(
                        request.withId,
                        request.name,
                        request.address,
                        request.port,
                        request.license
                    )
                )
            }

            return request.withId
        } else {
            throw RPCException("Not authorized to change license server details", HttpStatusCode.Unauthorized)
        }
    }

    suspend fun deleteLicenseServer(securityPrincipal: SecurityPrincipal, request: DeleteServerRequest) {
        if (
            aclService.hasPermission(request.id, AccessEntity(securityPrincipal.username, null, null), ServerAccessRight.READ_WRITE) ||
            securityPrincipal.role in Roles.PRIVILEDGED
        ) {
            db.withTransaction { session ->
                // Delete Acl entries for the license server
                aclService.revokeAllServerPermissionsWithSession(session, request.id)

                // Delete license server
                appLicenseDao.delete(session, request.id)
            }
        } else {
            throw RPCException("Not authorized to delete license server", HttpStatusCode.Unauthorized)
        }
    }

    suspend fun addTag(name: String, serverId: String) {
        db.withTransaction { session ->
            appLicenseDao.addTag(session, name, serverId)
        }
    }

    suspend fun listTags(serverId: String): List<String> {
        return db.withTransaction { session ->
            appLicenseDao.listTags(session, serverId)
        }
    }

    suspend fun deleteTag(name: String, serverId: String) {
        db.withTransaction { session ->
            appLicenseDao.deleteTag(session, name, serverId)
        }
    }
}
