package dk.sdu.cloud.app.license.services.acl

import dk.sdu.cloud.Role
import dk.sdu.cloud.app.license.api.AccessEntity
import dk.sdu.cloud.app.license.api.AccessEntityWithPermission
import dk.sdu.cloud.app.license.api.AclEntryRequest
import dk.sdu.cloud.app.license.api.ServerAccessRight
import dk.sdu.cloud.app.license.rpc.AppLicenseController
import dk.sdu.cloud.auth.api.LookupUsersRequest
import dk.sdu.cloud.auth.api.UserDescriptions
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orRethrowAs
import dk.sdu.cloud.project.api.*
import dk.sdu.cloud.service.db.async.DBContext
import io.ktor.http.HttpStatusCode

class AclService(
    private val db: DBContext,
    private val authenticatedClient: AuthenticatedClient,
    private val dao: AclAsyncDao
) {

    suspend fun hasPermission(serverId: String, accessEntity: AccessEntity, permission: ServerAccessRight): Boolean {
        return dao.hasPermission(db, serverId, accessEntity, permission)
    }

    suspend fun updatePermissions(serverId: String, changes: List<AclEntryRequest>, accessEntity: AccessEntity) {
        if (dao.hasPermission(db, serverId, accessEntity, ServerAccessRight.READ_WRITE)) {
            changes.forEach { change ->
                if (accessEntity.user == change.entity.user) {
                    throw RPCException.fromStatusCode(HttpStatusCode.Unauthorized)
                }
                if (!change.revoke) {
                    updatePermissionsWithSession(serverId, change.entity, change.rights)
                } else {
                    revokePermissionWithSession(serverId, change.entity)
                }
            }
        } else {
            throw RPCException("Request to update permissions unauthorized", HttpStatusCode.Unauthorized)
        }

    }

    suspend fun updatePermissionsWithSession(
        serverId: String,
        entity: AccessEntity,
        permissions: ServerAccessRight
    ) {
        val user = entity.user
        if (!user.isNullOrBlank()) {
            AppLicenseController.log.debug("Verifying that user exists")

            val lookup = UserDescriptions.lookupUsers.call(
                LookupUsersRequest(listOf(user)),
                authenticatedClient
            ).orRethrowAs {
                throw RPCException.fromStatusCode(HttpStatusCode.InternalServerError)
            }

            if (lookup.results[user] == null) throw RPCException.fromStatusCode(
                HttpStatusCode.BadRequest,
                "The user does not exist"
            )

            if (lookup.results[user]?.role == Role.SERVICE) {
                throw RPCException.fromStatusCode(HttpStatusCode.BadRequest, "The user does not exist")
            }
            dao.updatePermissions(db, serverId, entity, permissions)
        } else {
            val group = entity.group
            val project = entity.project
            if(!project.isNullOrBlank() && !group.isNullOrBlank()) {
                AppLicenseController.log.debug("Verifying that project and group exists")

                val projectInfo = Projects.lookupByTitle.call(
                        LookupByTitleRequest(project),
                        authenticatedClient
                ).orRethrowAs {
                    throw RPCException("No project exists with that name", HttpStatusCode.BadRequest)
                }

                val groupInfo = ProjectGroups.lookupByTitle.call(
                    LookupByGroupTitleRequest(projectInfo.id, group),
                    authenticatedClient
                ).orRethrowAs {
                    throw RPCException("No group exists with that name", HttpStatusCode.BadRequest)
                }

                val entityWithId = AccessEntity(entity.user, projectInfo.id, groupInfo.groupId)

                dao.updatePermissions(db, serverId, entityWithId, permissions)
            } else {
                throw RPCException.fromStatusCode(HttpStatusCode.BadRequest, "Neither user or project group defined")
            }
        }
    }

    suspend fun listAcl(serverId: String): List<AccessEntityWithPermission> {
        return dao.listAcl(db, serverId)
    }

    suspend fun revokePermission(serverId: String, entity: AccessEntity) {
        revokePermissionWithSession(serverId, entity)
    }

    suspend fun revokeAllFromEntity(entity: AccessEntity) {
        dao.revokePermissionsFromEntity(db, entity)
    }

    private suspend fun revokePermissionWithSession(serverId: String, entity: AccessEntity) {
        dao.revokePermission(db, serverId, entity)
    }

    suspend fun revokeAllServerPermissionsWithSession(serverId: String) {
        dao.revokeAllServerPermissions(db, serverId)
    }
}
