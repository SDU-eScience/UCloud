package dk.sdu.cloud.file.ucloud.services.acl

import dk.sdu.cloud.file.orchestrator.api.FilePermission
import dk.sdu.cloud.file.ucloud.services.linuxfs.FSException

suspend inline fun AclService.requirePermission(path: String, username: String, permission: FilePermission) {
    TODO()
    // if (!hasPermission(path, username, permission)) throw FSException.PermissionException()
}
