package dk.sdu.cloud.app.license.services

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.app.license.services.acl.AclService
import dk.sdu.cloud.app.license.services.acl.Entity
import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.withTransaction

class AppLicenseService<Session>(
    private val db: DBSessionFactory<Session>,
    private val aclService: AclService<*>,
    private val appLicenseDao: AppLicenseDao<Session>
) {
    fun getLicenseServer(entity: Entity, serverId: String) : ApplicationLicenseServerEntity? {
        if (aclService.hasPermission(entity, serverId, AccessRight.READ)) {
            return db.withTransaction {
                appLicenseDao.getById(it, serverId)
            }
        }
        return null
    }
}