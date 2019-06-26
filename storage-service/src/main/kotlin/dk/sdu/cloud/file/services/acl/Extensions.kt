package dk.sdu.cloud.file.services.acl

import dk.sdu.cloud.file.util.FSException

suspend inline fun AclService<*>.requirePermission(path: String, username: String, permission: AclPermission) {
    if (!hasPermission(path, username, permission)) throw FSException.PermissionException()
}
