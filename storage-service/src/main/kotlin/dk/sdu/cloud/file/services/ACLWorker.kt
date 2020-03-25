package dk.sdu.cloud.file.services

import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.file.api.UpdateAclRequest
import dk.sdu.cloud.file.services.acl.AclService
import dk.sdu.cloud.file.services.acl.UserWithPermissions
import dk.sdu.cloud.service.Loggable
import io.ktor.http.HttpStatusCode

class ACLWorker(private val aclService: AclService) {
    suspend fun updateAcl(request: UpdateAclRequest, user: String) {
        log.debug("Executing ACL update request: $request")

        if (!aclService.isOwner(request.path, user)) {
            throw RPCException("Only the owner can update the ACL", HttpStatusCode.Forbidden)
        }

        val bulkChanges = ArrayList<UserWithPermissions>()
        request.changes.forEach { change ->
            if (change.revoke) {
                aclService.revokePermission(request.path, change.entity)
            } else {
                bulkChanges.add(UserWithPermissions(change.entity, change.rights))
            }
        }

        aclService.updatePermissions(request.path, bulkChanges)
    }

    companion object : Loggable {
        override val log = logger()
    }
}
