package dk.sdu.cloud.file.services.acl

import dk.sdu.cloud.file.api.ACLEntity
import dk.sdu.cloud.file.api.AccessRight

object AccessRights {
    val READ_WRITE = setOf(AccessRight.READ, AccessRight.WRITE)
    val READ_ONLY = setOf(AccessRight.READ)
}

data class UserWithPermissions(val entity: ACLEntity, val permissions: Set<AccessRight>)
