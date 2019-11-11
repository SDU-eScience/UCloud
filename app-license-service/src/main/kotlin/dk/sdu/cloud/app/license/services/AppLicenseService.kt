package dk.sdu.cloud.app.license.services

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.app.license.api.UpdateAclRequest
import dk.sdu.cloud.app.license.services.acl.AclService
import dk.sdu.cloud.app.license.services.acl.UserEntity
import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.withTransaction

class AppLicenseService<Session>(
    private val db: DBSessionFactory<Session>,
    private val aclService: AclService<*>,
    private val appLicenseDao: AppLicenseDao<Session>
) {
    fun getLicenseServer(licenseId: String, entity: UserEntity) : ApplicationLicenseServerEntity? {
        if (aclService.hasPermission(licenseId, entity, AccessRight.READ)) {
            return db.withTransaction {
                appLicenseDao.getById(it, licenseId)
            }
        }
        return null
    }

    fun updateAcl(request: UpdateAclRequest, entity: String) {
        request.changes.forEach { change ->
            if(change.revoke) {
                aclService.revokePermission(request.licenseId, change.entity)
            } else {
                aclService.updatePermissions(request.licenseId, change.entity, change.rights)
            }
        }
    }
}