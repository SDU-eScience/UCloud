package dk.sdu.cloud.file.services.acl

import dk.sdu.cloud.file.api.AccessRight

object AccessRights {
    val READ_WRITE = setOf(AccessRight.READ, AccessRight.WRITE)
    val READ_ONLY = setOf(AccessRight.READ)
}

data class UserWithPermissions(val username: String, val permissions: Set<AccessRight>)

interface AclDao<Session> {
    suspend fun updatePermissions(session: Session, path: String, username: String, permissions: Set<AccessRight>)
    suspend fun hasPermission(session: Session, path: String, username: String, permission: AccessRight): Boolean
    suspend fun listAcl(session: Session, paths: List<String>): Map<String, List<UserWithPermissions>>
    suspend fun revokePermission(session: Session, path: String, username: String)
    suspend fun handleFilesMoved(session: Session, oldPath: String, newPath: String)
    suspend fun handleFilesDeleted(session: Session, paths: List<String>)
    suspend fun dumpAllForDebugging(session: Session): Map<String, List<UserWithPermissions>>
}
