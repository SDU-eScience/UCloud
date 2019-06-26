package dk.sdu.cloud.file.services.acl

enum class AclPermission {
    READ,
    WRITE
}

data class UserWithPermissions(val username: String, val permission: AclPermission)

interface AclDao<Session> {
    fun createOrUpdatePermission(session: Session, path: String, username: String, permission: AclPermission)
    fun hasPermission(session: Session, path: String, username: String, permission: AclPermission): Boolean
    fun listAcl(session: Session, path: String): List<UserWithPermissions>
    fun revokePermission(session: Session, path: String, username: String)
    fun handleFilesMoved(session: Session, path: String)
    fun handleFilesDeleted(session: Session, path: String)
}
