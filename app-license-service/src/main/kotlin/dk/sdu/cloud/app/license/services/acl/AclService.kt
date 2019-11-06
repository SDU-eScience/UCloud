package dk.sdu.cloud.app.license.services.acl

import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.withTransaction
import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.app.license.api.ApplicationLicenseServer

class AclService<Session>(
    private val db: DBSessionFactory<Session>,
    private val dao: AclDao<Session>) {

    fun hasPermission(serverId: String, entity: String, permission: AccessRight): Boolean {
        return db.withTransaction {
            dao.hasPermission(it, serverId, entity, permission)
        }
    }
}
