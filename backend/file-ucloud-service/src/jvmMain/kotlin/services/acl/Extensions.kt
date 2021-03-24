package dk.sdu.cloud.file.ucloud.services.acl

import dk.sdu.cloud.Actor
import dk.sdu.cloud.file.orchestrator.api.FilePermission

suspend inline fun AclService.requirePermission(path: String, actor: Actor, permission: FilePermission) {
    TODO()
    // if (!hasPermission(path, username, permission)) throw FSException.PermissionException()
}
