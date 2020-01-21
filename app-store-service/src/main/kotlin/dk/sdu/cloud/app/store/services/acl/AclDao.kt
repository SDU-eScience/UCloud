package dk.sdu.cloud.app.store.services.acl

import dk.sdu.cloud.app.store.api.ApplicationAccessRight

enum class EntityType {
    USER,
    PROJECT_AND_GROUP
}

data class UserEntity(
    val id: String,
    val type: EntityType
)

data class EntityWithPermission(
    val entity: UserEntity,
    val permission: ApplicationAccessRight
)

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
