package dk.sdu.cloud.app.store.services.acl

import dk.sdu.cloud.app.store.api.AccessEntity
import dk.sdu.cloud.app.store.api.ApplicationAccessRight
import dk.sdu.cloud.app.store.api.EntityWithPermission


interface AclDao<Session> {
    fun hasPermission(
        session: Session,
        entity: AccessEntity,
        appName: String,
        permission: Set<ApplicationAccessRight>
    ): Boolean

    fun updatePermissions(
        session: Session,
        entity: AccessEntity,
        applicationName: String,
        permissions: ApplicationAccessRight
    )

    fun revokePermission(
        session: Session,
        entity: AccessEntity,
        applicationName: String
    )

    fun listAcl(
        session: Session,
        applicationName: String
    ): List<EntityWithPermission>
}
