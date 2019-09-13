package dk.sdu.cloud.file.services

import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.file.api.UpdateAclRequest
import dk.sdu.cloud.file.services.acl.AclService
import dk.sdu.cloud.service.Loggable
import io.ktor.http.HttpStatusCode

class ACLWorker(private val aclService: AclService<*>) {
    suspend fun updateAcl(request: UpdateAclRequest, user: String) {
        log.debug("Executing ACL update request: $request")

        if (!aclService.isOwner(request.path, user)) {
            throw RPCException("Only the owner can update the ACL", HttpStatusCode.Forbidden)
        }

        request.changes.forEach { change ->
            val entity = FSACLEntity(change.entity)

            if (change.revoke) {
                aclService.revokePermission(request.path, entity.user)
            } else {
                aclService.updatePermissions(request.path, entity.user, change.rights)
            }
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}
