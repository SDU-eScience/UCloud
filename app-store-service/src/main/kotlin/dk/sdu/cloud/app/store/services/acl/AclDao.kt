package dk.sdu.cloud.app.store.services.acl

import dk.sdu.cloud.app.store.api.ApplicationAccessRight

enum class EntityType {
    USER,
    PROJECT_AND_GROUP
}

data class AccessEntity(
    val id: String,
    val type: EntityType
)

data class EntityWithPermission(
    val entity: AccessEntity,
    val permission: ApplicationAccessRight
)

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
