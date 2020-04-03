package dk.sdu.cloud.app.license.services.acl

import dk.sdu.cloud.app.license.api.EntityWithPermission
import dk.sdu.cloud.app.license.api.UserEntity
import dk.sdu.cloud.app.license.api.ServerAccessRight



interface AclDao<Session> {
    fun hasPermission(
        session: Session,
        serverId: String,
        entity: UserEntity,
        permission: ServerAccessRight
    ): Boolean

    fun updatePermissions(
        session: Session,
        serverId: String,
        entity: UserEntity,
        permissions: ServerAccessRight
    )

    fun revokePermission(
        session: Session,
        serverId: String,
        entity: UserEntity
    )

    fun revokeAllServerPermissions(
        session: Session,
        serverId: String
    )

    fun listAcl(
        session: Session,
        serverId: String
    ): List<EntityWithPermission>
}
