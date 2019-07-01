package dk.sdu.cloud.file.services.acl

import dk.sdu.cloud.file.api.AccessRight

object AccessRights {
    val READ_WRITE = setOf(AccessRight.READ, AccessRight.WRITE)
    val READ_ONLY = setOf(AccessRight.READ)
}

data class UserWithPermissions(val username: String, val permissions: Set<AccessRight>)

interface AclDao<Session> {
    fun updatePermissions(session: Session, path: String, username: String, permissions: Set<AccessRight>)
    fun hasPermission(session: Session, path: String, username: String, permission: AccessRight): Boolean
    fun listAcl(session: Session, paths: List<String>): Map<String, List<UserWithPermissions>>
    fun revokePermission(session: Session, path: String, username: String)
    fun handleFilesMoved(session: Session, path: String)
    fun handleFilesDeleted(session: Session, path: String)
}
