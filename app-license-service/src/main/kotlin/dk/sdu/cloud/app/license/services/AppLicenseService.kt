package dk.sdu.cloud.app.license.services

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
        if (aclService.hasPermission(serverId, entity, ServerAccessRight.READ)) {

            return db.withTransaction { session ->
                appLicenseDao.getById(session, serverId)
            } ?: throw RPCException("The requested license server was not found", HttpStatusCode.NotFound)
        }
        throw RPCException("Unauthorized request to license server", HttpStatusCode.Unauthorized)
    }

    fun updateAcl(request: UpdateAclRequest, entity: UserEntity) {
        aclService.updatePermissions(request.serverId, request.changes, entity)
    }

    fun listServers(names: List<String>, entity: UserEntity): List<LicenseServerId> {
        return db.withTransaction { session ->
            appLicenseDao.list(
                session,
                names,
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

    fun createLicenseServer(request: NewServerRequest, entity: UserEntity): String {
        val license = if (request.license.isNullOrBlank()) { null } else { request.license }
        val serverId = UUID.randomUUID().toString()
        val port = if (request.port.matches("^[0-9]{2,5}$".toRegex())) { request.port } else {
            throw RPCException.fromStatusCode(HttpStatusCode.BadRequest, "Invalid port")
        }

        db.withTransaction { session ->
            appLicenseDao.create(
                session,
                serverId,
                LicenseServer(
                    request.name,
                    request.address,
                    port,
                    license
                )
            )

            // Add rw permissions for the creator
            aclService.updatePermissionsWithSession(session, serverId, entity, ServerAccessRight.READ_WRITE)
        }
        return serverId
    }

    fun updateLicenseServer(request: UpdateServerRequest, entity: UserEntity): String {
        if (aclService.hasPermission(request.withId, entity, ServerAccessRight.READ_WRITE)) {
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
}
