package dk.sdu.cloud.app.license.services

import dk.sdu.cloud.Roles
import dk.sdu.cloud.SecurityPrincipal
import dk.sdu.cloud.app.license.api.*
import dk.sdu.cloud.app.license.services.acl.*
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.withTransaction
import io.ktor.http.HttpStatusCode
import java.util.*

class AppLicenseService<Session>(
    private val db: DBSessionFactory<Session>,
    private val aclService: AclService<Session>,
    private val appLicenseDao: AppLicenseDao<Session>
) {
    fun getLicenseServer(serverId: String, entity: UserEntity): LicenseServerWithId? {
        if (
            !aclService.hasPermission(serverId, entity, ServerAccessRight.READ) &&
            !Roles.PRIVILEDGED.contains(entity.principal.role)
        ) {
            throw RPCException("Unauthorized request to license server", HttpStatusCode.Unauthorized)
        }

        return db.withTransaction { session ->
            appLicenseDao.getById(session, serverId)
        } ?: throw RPCException("The requested license server was not found", HttpStatusCode.NotFound)
    }

    suspend fun updateAcl(request: UpdateAclRequest, entity: UserEntity) {
        aclService.updatePermissions(request.serverId, request.changes, entity)
    }

    fun listAcl(request: ListAclRequest, user: SecurityPrincipal): List<EntityWithPermission> {
        return if (Roles.PRIVILEDGED.contains(user.role)) {
            aclService.listAcl(request.serverId)
        } else {
            throw RPCException.fromStatusCode(HttpStatusCode.Unauthorized, "Not allowed")
        }
    }

    fun listServers(tags: List<String>, entity: UserEntity): List<LicenseServerId> {
        return db.withTransaction { session ->
            appLicenseDao.list(
                session,
                tags,
                entity
            )
        } ?: throw RPCException("No available license servers found", HttpStatusCode.NotFound)
    }

    fun listAllServers(user: SecurityPrincipal): List<LicenseServerWithId> {
        return db.withTransaction { session ->
            appLicenseDao.listAll(
                session,
                user
            )
        } ?: throw RPCException("No available license servers found", HttpStatusCode.NotFound)
    }

    suspend fun createLicenseServer(request: NewServerRequest, entity: UserEntity): String {
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

    fun updateLicenseServer(request: UpdateServerRequest, entity: UserEntity): String {
        if (
            aclService.hasPermission(request.withId, entity, ServerAccessRight.READ_WRITE) ||
            Roles.PRIVILEDGED.contains(entity.principal.role)
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

    fun deleteLicenseServer(request: DeleteServerRequest, entity: UserEntity) {
        if (
            aclService.hasPermission(request.id, entity, ServerAccessRight.READ_WRITE) ||
            Roles.PRIVILEDGED.contains(entity.principal.role)
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

    fun addTag(name: String, serverId: String) {
        db.withTransaction { session ->
            appLicenseDao.addTag(session, name, serverId)
        }
    }

    fun listTags(serverId: String): List<String> {
        return db.withTransaction { session ->
            appLicenseDao.listTags(session, serverId)
        }
    }

    fun deleteTag(name: String, serverId: String) {
        db.withTransaction { session ->
            appLicenseDao.deleteTag(session, name, serverId)
        }
    }
}
