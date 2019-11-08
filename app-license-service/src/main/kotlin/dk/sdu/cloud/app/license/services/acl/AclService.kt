package dk.sdu.cloud.app.license.services.acl

import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.withTransaction
import dk.sdu.cloud.AccessRight

class AclService<Session>(
    private val db: DBSessionFactory<Session>,
    private val dao: AclDao<Session>) {

    fun hasPermission(entity: Entity, serverId: String, permission: AccessRight): Boolean {
        return db.withTransaction {
            dao.hasPermission(it, entity, serverId, permission)
        }
    }
}
