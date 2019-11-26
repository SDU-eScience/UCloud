package dk.sdu.cloud.app.license.services.acl

import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.withTransaction
import dk.sdu.cloud.AccessRight

class AclService<Session>(
    private val db: DBSessionFactory<Session>,
    private val dao: AclDao<Session>) {

    fun hasPermission(serverId: String, entity: UserEntity, permission: AccessRight): Boolean {
        return db.withTransaction {
            dao.hasPermission(it, serverId, entity, permission)
        }
    }

    fun updatePermissions(serverId: String, entity: UserEntity, permissions: AccessRight) {
        db.withTransaction {
            dao.updatePermissions(it, serverId, entity, permissions)
        }
    }

    fun listAcl(serverId: String): List<EntityWithPermission> {
        return db.withTransaction {
            dao.listAcl(it, serverId)
        }
    }

    fun revokePermission(serverId: String, entity: UserEntity) {
        db.withTransaction {
            dao.revokePermission(it, serverId, entity)
        }
    }
}
