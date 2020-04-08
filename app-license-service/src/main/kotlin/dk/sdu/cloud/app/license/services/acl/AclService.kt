package dk.sdu.cloud.app.license.services.acl

import dk.sdu.cloud.Role
import dk.sdu.cloud.SecurityPrincipal
import dk.sdu.cloud.app.license.api.*
import dk.sdu.cloud.app.license.rpc.AppLicenseController
import dk.sdu.cloud.auth.api.LookupUsersRequest
import dk.sdu.cloud.auth.api.Principal
import dk.sdu.cloud.auth.api.UserDescriptions
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orRethrowAs
import dk.sdu.cloud.project.api.GroupExistsRequest
import dk.sdu.cloud.project.api.ProjectGroups
import dk.sdu.cloud.project.api.ProjectServiceDescription
import dk.sdu.cloud.project.api.Projects
import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.withTransaction
import io.ktor.http.HttpStatusCode

class AclService<Session>(
    private val db: DBSessionFactory<Session>,
    private val authenticatedClient: AuthenticatedClient,
    private val dao: AclDao<Session>
) {

    suspend fun hasPermission(serverId: String, accessEntity: AccessEntity, permission: ServerAccessRight): Boolean {
        return db.withTransaction { session ->
            dao.hasPermission(session, serverId, accessEntity, permission)
        }
    }

    suspend fun updatePermissions(serverId: String, changes: List<AclEntryRequest>, accessEntity: AccessEntity) {
        db.withTransaction { session ->
            if (dao.hasPermission(session, serverId, accessEntity, ServerAccessRight.READ_WRITE)) {
                changes.forEach { change ->
                    if (accessEntity.user == change.entity.user) {
                        throw RPCException.fromStatusCode(HttpStatusCode.Unauthorized)
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
        entity: AccessEntity,
        permissions: ServerAccessRight
    ) {
        if (!entity.user.isNullOrBlank()) {
            AppLicenseController.log.debug("Verifying that user exists")

            val lookup = UserDescriptions.lookupUsers.call(
                LookupUsersRequest(listOf(entity.user)),
                authenticatedClient
            ).orRethrowAs {
                throw RPCException.fromStatusCode(HttpStatusCode.InternalServerError)
            }

            if (lookup.results[entity.user] == null) throw RPCException.fromStatusCode(
                HttpStatusCode.BadRequest,
                "The user does not exist"
            )

            if (lookup.results[entity.user]?.role == Role.SERVICE) {
                throw RPCException.fromStatusCode(HttpStatusCode.BadRequest, "The user does not exist")
            }
            dao.updatePermissions(session, serverId, entity, permissions)
        } else if(!entity.project.isNullOrBlank() && !entity.group.isNullOrBlank()) {
            AppLicenseController.log.debug("Verifying that project and group exists")

            val lookup = ProjectGroups.groupExists.call(
                GroupExistsRequest(entity.project, entity.group),
                authenticatedClient
            ).orRethrowAs {
                throw RPCException.fromStatusCode(HttpStatusCode.InternalServerError)
            }

            if (!lookup.exists) throw RPCException.fromStatusCode(
                HttpStatusCode.BadRequest,
                "The project group does not exist"
            )

            dao.updatePermissions(session, serverId, entity, permissions)
        } else {
            throw RPCException.fromStatusCode(HttpStatusCode.BadRequest, "Neither user or project group defined")
        }
    }

    suspend fun listAcl(serverId: String): List<AccessEntityWithPermission> {
        return db.withTransaction {
            dao.listAcl(it, serverId)
        }
    }

    suspend fun revokePermission(serverId: String, entity: AccessEntity) {
        db.withTransaction {
            revokePermissionWithSession(it, serverId, entity)
        }
    }

    suspend fun revokeAllFromEntity(entity: AccessEntity) {
        db.withTransaction { session ->
            dao.revokePermissionsFromEntity(session, entity)
        }
    }

    private fun revokePermissionWithSession(session: Session, serverId: String, entity: AccessEntity) {
        dao.revokePermission(session, serverId, entity)
    }

    fun revokeAllServerPermissionsWithSession(session: Session, serverId: String) {
        dao.revokeAllServerPermissions(session, serverId)
    }
}
