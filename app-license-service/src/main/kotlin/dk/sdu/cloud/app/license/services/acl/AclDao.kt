package dk.sdu.cloud.app.license.services.acl

import dk.sdu.cloud.AccessRight

interface AclDao<Session> {
    fun hasPermission(
        session: Session,
        serverId: String,
        entity: String,
        permission: AccessRight
    ): Boolean
}
