package dk.sdu.cloud.file.ucloud.services.acl

import dk.sdu.cloud.Actor
import dk.sdu.cloud.file.orchestrator.api.FilePermission
import dk.sdu.cloud.file.ucloud.services.FSException
import dk.sdu.cloud.file.ucloud.services.UCloudFile

suspend inline fun AclService.requirePermission(actor: Actor, file: UCloudFile, permission: FilePermission) {
    if (!fetchMyPermissions(actor, file).contains(permission)) throw FSException.PermissionException()
}
