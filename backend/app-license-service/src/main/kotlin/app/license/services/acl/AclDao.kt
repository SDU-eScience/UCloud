package dk.sdu.cloud.app.license.services.acl

import dk.sdu.cloud.SecurityPrincipal
import dk.sdu.cloud.app.license.api.AccessEntity
import dk.sdu.cloud.app.license.api.AccessEntityWithPermission
import dk.sdu.cloud.app.license.api.ServerAccessRight



interface AclDao<Session> {
    fun hasPermission(
        session: Session,
        serverId: String,
        entity: AccessEntity,
        permission: ServerAccessRight
    ): Boolean

    fun updatePermissions(
        session: Session,
        serverId: String,
        entity: AccessEntity,
        permissions: ServerAccessRight
    )

    fun revokePermission(
        session: Session,
        serverId: String,
        accessEntity: AccessEntity
    )

    fun revokePermissionsFromEntity(
        session: Session,
        accessEntity: AccessEntity
    )

    fun revokeAllServerPermissions(
        session: Session,
        serverId: String
    )

    fun listAcl(
        session: Session,
        serverId: String
    ): List<AccessEntityWithPermission>
}
