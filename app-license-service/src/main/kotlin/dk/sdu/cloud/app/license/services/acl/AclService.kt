package dk.sdu.cloud.app.license.services.acl

import dk.sdu.cloud.app.license.api.ACLEntryRequest
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.withTransaction
import io.ktor.http.HttpStatusCode

class AclService<Session>(
    private val db: DBSessionFactory<Session>,
    private val dao: AclDao<Session>
) {

    fun hasPermission(serverId: String, entity: UserEntity, permission: ServerAccessRight): Boolean {
        return db.withTransaction { session ->
            dao.hasPermission(session, serverId, entity, permission)
        }
    }

    fun updatePermissions(serverId: String, changes: List<ACLEntryRequest>, entity: UserEntity) {
        db.withTransaction { session ->
            if (dao.hasPermission(session, serverId, entity, ServerAccessRight.READ_WRITE)) {
                changes.forEach { change ->
                    if (!change.revoke) {
                        updatePermissionsWithSession(session, serverId, change.entity, change.rights)
                    } else {
                        revokePermissionWithSession(session, serverId, change.entity)
                    }
                }
            } else {
                RPCException("Request to update permissions unauthorized", HttpStatusCode.Unauthorized)
            }
        }
    }

    fun updatePermissionsWithSession(
        session: Session,
        serverId: String,
        entity: UserEntity,
        permissions: ServerAccessRight
    ) {
        dao.updatePermissions(session, serverId, entity, permissions)
    }

    fun listAcl(serverId: String): List<EntityWithPermission> {
        return db.withTransaction {
            dao.listAcl(it, serverId)
        }
    }

    fun revokePermission(serverId: String, entity: UserEntity) {
        db.withTransaction {
            revokePermissionWithSession(it, serverId, entity)
        }
    }

    private fun revokePermissionWithSession(session: Session, serverId: String, entity: UserEntity) {
        dao.revokePermission(session, serverId, entity)
    }
}
