package dk.sdu.cloud.app.store.services.acl

import dk.sdu.cloud.app.store.api.ApplicationAccessRight
import dk.sdu.cloud.app.store.api.EntityWithPermission
import dk.sdu.cloud.app.store.api.UserEntity

interface AclDao<Session> {
    fun hasPermission(
        session: Session,
        entity: UserEntity,
        appName: String,
        permission: Set<ApplicationAccessRight>
    ): Boolean

    fun updatePermissions(
        session: Session,
        entity: UserEntity,
        applicationName: String,
        permissions: ApplicationAccessRight
    )

    fun revokePermission(
        session: Session,
        entity: UserEntity,
        applicationName: String
    )

    fun listAcl(
        session: Session,
        applicationName: String
    ): List<EntityWithPermission>
}
