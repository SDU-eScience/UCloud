package dk.sdu.cloud.app.license.services.acl

import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.withTransaction
import dk.sdu.cloud.AccessRight

class AclService<Session>(
    private val db: DBSessionFactory<Session>,
    private val dao: AclDao<Session>) {

    fun hasPermission(licenseId: String, entity: UserEntity, permission: AccessRight): Boolean {
        return db.withTransaction {
            dao.hasPermission(it, licenseId, entity, permission)
        }
    }

    fun updatePermissions(licenseId: String, entity: UserEntity, permissions: Set<AccessRight>) {
        db.withTransaction {
            dao.updatePermissions(it, licenseId, entity, permissions)
        }
    }

    fun revokePermission(licenseId: String, entity: UserEntity) {
        db.withTransaction {
            dao.revokePermission(it, licenseId, entity)
        }
    }
}
