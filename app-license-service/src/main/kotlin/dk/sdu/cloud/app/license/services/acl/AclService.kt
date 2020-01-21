package dk.sdu.cloud.app.license.services.acl

import dk.sdu.cloud.Role
import dk.sdu.cloud.app.license.api.ACLEntryRequest
import dk.sdu.cloud.app.license.rpc.AppLicenseController
import dk.sdu.cloud.auth.api.LookupUsersRequest
import dk.sdu.cloud.auth.api.UserDescriptions
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orRethrowAs
import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.withTransaction
import io.ktor.http.HttpStatusCode

class AclService<Session>(
    private val db: DBSessionFactory<Session>,
    private val authenticatedClient: AuthenticatedClient,
    private val dao: AclDao<Session>
) {

    fun hasPermission(serverId: String, entity: UserEntity, permission: ServerAccessRight): Boolean {
        return db.withTransaction { session ->
            dao.hasPermission(session, serverId, entity, permission)
        }
    }

    suspend fun updatePermissions(serverId: String, changes: List<ACLEntryRequest>, entity: UserEntity) {
        db.withTransaction { session ->
            if (dao.hasPermission(session, serverId, entity, ServerAccessRight.READ_WRITE)) {
                changes.forEach { change ->
                    if (entity == change.entity) {
                        throw RPCException.fromStatusCode(HttpStatusCode.Unauthorized, "Not allowed")
                    }
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

    suspend fun updatePermissionsWithSession(
        session: Session,
        serverId: String,
        entity: UserEntity,
        permissions: ServerAccessRight
    ) {
        if (entity.type == EntityType.USER) {
            AppLicenseController.log.debug("Verifying that user exists")

            val lookup = UserDescriptions.lookupUsers.call(
                LookupUsersRequest(listOf(entity.id)),
                authenticatedClient
            ).orRethrowAs {
                throw RPCException.fromStatusCode(HttpStatusCode.InternalServerError)
            }

            if (lookup.results[entity.id] == null) throw RPCException.fromStatusCode(
                HttpStatusCode.BadRequest,
                "The user does not exist"
            )

            if (lookup.results[entity.id]?.role == Role.SERVICE) {
                throw RPCException.fromStatusCode(HttpStatusCode.BadRequest, "The user does not exist")
            }
            dao.updatePermissions(session, serverId, entity, permissions)
        }
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

    fun revokeAllServerPermissionsWithSession(session: Session, serverId: String) {
        dao.revokeAllServerPermissions(session, serverId)
    };
}
