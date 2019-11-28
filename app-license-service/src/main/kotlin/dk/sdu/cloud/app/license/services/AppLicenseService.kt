package dk.sdu.cloud.app.license.services

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
    fun getLicenseServer(serverId: String, entity: UserEntity): LicenseServerEntity {
        if (aclService.hasPermission(serverId, entity, ServerAccessRight.READ)) {
            val licenseServer = db.withTransaction { session ->
                appLicenseDao.getById(session, serverId)
            } ?: throw RPCException("The requested license server was not found", HttpStatusCode.NotFound)

            return licenseServer
        }
        throw RPCException("Unauthorized request to license server", HttpStatusCode.Unauthorized)
    }

    fun updateAcl(request: UpdateAclRequest, entity: UserEntity) {
        aclService.updatePermissions(request.serverId, request.changes, entity)
    }

    fun listServers(application: Application, entity: UserEntity): List<ApplicationLicenseServer> {
        return db.withTransaction { session ->
            appLicenseDao.list(
                session,
                application,
                entity
            )
        } ?: throw RPCException("No available license servers found", HttpStatusCode.NotFound)
    }

    fun createLicenseServer(request: NewServerRequest, entity: UserEntity): String {
        val serverId = UUID.randomUUID().toString()

        db.withTransaction { session ->
            appLicenseDao.create(
                session,
                serverId,
                ApplicationLicenseServer(
                    request.name,
                    request.address,
                    request.port,
                    request.license
                )
            )

            // Add rw permissions for the creator
            aclService.updatePermissionsWithSession(session, serverId, entity, ServerAccessRight.READ_WRITE)

            request.applications?.forEach { app ->
                // Add applications to the license server
                appLicenseDao.addApplicationToServer(session, app, serverId)
            }
        }
        return serverId
    }

    fun updateLicenseServer(request: UpdateServerRequest, entity: UserEntity): String {
        if (aclService.hasPermission(request.withId, entity, ServerAccessRight.READ_WRITE)) {
            // Save information for existing license server
            db.withTransaction { session ->
                appLicenseDao.save(
                    session,
                    ApplicationLicenseServer(
                        request.name,
                        request.address,
                        request.port,
                        request.license
                    ),
                    request.withId
                )
            }

            return request.withId
        } else {
            throw RPCException("Not authorized to change license server details", HttpStatusCode.Unauthorized)
        }
    }

    fun addApplicationsToServer(request: AddApplicationsToServerRequest, entity: UserEntity) {
        if (aclService.hasPermission(request.serverId, entity, ServerAccessRight.READ_WRITE)) {
            db.withTransaction { session ->
                request.applications.forEach { app ->
                    appLicenseDao.addApplicationToServer(session, app, request.serverId)
                }
            }
        } else {
            throw RPCException("Not authorized to change license server details", HttpStatusCode.Unauthorized)
        }
    }
}
