package dk.sdu.cloud.app.license.services.acl

import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.withTransaction

class AclService<Session>(
    private val db: DBSessionFactory<Session>,
    private val dao: AclDao<Session>) {

    fun hasPermission(serverId: String, entity: UserEntity, permission: ServerAccessRight): Boolean {
        return db.withTransaction {
            dao.hasPermission(it, serverId, entity, permission)
        }
    }

    fun updatePermissions(serverId: String, entity: UserEntity, permissions: ServerAccessRight) {
        db.withTransaction { session ->
            updatePermissionsWithSession(session, serverId, entity, permissions)
        }
    }

    fun updatePermissionsWithSession(session: Session, serverId: String, entity: UserEntity, permissions: ServerAccessRight) {
        dao.updatePermissions(session, serverId, entity, permissions)
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
