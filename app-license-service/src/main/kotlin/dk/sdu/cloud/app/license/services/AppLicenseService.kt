package dk.sdu.cloud.app.license.services

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.app.license.api.ApplicationLicenseServer
import dk.sdu.cloud.app.license.api.SaveLicenseRequest
import dk.sdu.cloud.app.license.api.UpdateAclRequest
import dk.sdu.cloud.app.license.services.acl.AclService
import dk.sdu.cloud.app.license.services.acl.UserEntity
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.withTransaction
import io.ktor.http.HttpStatusCode
import dk.sdu.cloud.calls.server.CallHandler

class AppLicenseService<Session>(
    private val db: DBSessionFactory<Session>,
    private val aclService: AclService<*>,
    private val appLicenseDao: AppLicenseDao<Session>
) {
    fun getLicenseServer(licenseId: String, entity: UserEntity) : ApplicationLicenseServerEntity {
        if (aclService.hasPermission(licenseId, entity, AccessRight.READ)) {
            val licenseServer = db.withTransaction {
                appLicenseDao.getById(it, licenseId)
            }

            if (licenseServer == null) {
                throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
            } else {
                return licenseServer
            }
        }
        throw RPCException.fromStatusCode(HttpStatusCode.Unauthorized)
    }

    fun updateAcl(request: UpdateAclRequest, entity: UserEntity) {
        request.changes.forEach { change ->
            if(change.revoke) {
                aclService.revokePermission(request.licenseId, change.entity)
            } else {
                aclService.updatePermissions(request.licenseId, change.entity, change.rights)
            }
        }
    }

    fun saveLicenseServer(request: SaveLicenseRequest, entity: UserEntity): String {
        if (request.withId == null) {
            // Save new license server
            db.withTransaction {
                val licenseId = appLicenseDao.create(
                    it,
                    ApplicationLicenseServer(
                        request.name,
                        request.version,
                        request.address,
                        request.license
                    )
                )

                // Add rw permissions for the creator
                aclService.updatePermissions(licenseId, entity, AccessRight.READ_WRITE)

                return licenseId
            }
        } else {
            if (aclService.hasPermission(request.withId, entity, AccessRight.READ_WRITE)) {
                // Save information for existing license server
                db.withTransaction {
                    appLicenseDao.save(
                        it,
                        ApplicationLicenseServer(
                            request.name,
                            request.version,
                            request.address,
                            request.license
                        ),
                        request.withId
                    )
                }

                return request.withId
            } else {
                throw RPCException.fromStatusCode(HttpStatusCode.Unauthorized)
            }
        }
    }
}