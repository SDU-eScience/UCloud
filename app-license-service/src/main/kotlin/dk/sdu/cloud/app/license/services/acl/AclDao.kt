package dk.sdu.cloud.app.license.services.acl

enum class EntityType {
    USER,
    PROJECT_AND_GROUP
}

enum class ServerAccessRight {
    READ,
    READ_WRITE
}

data class UserEntity(
    val id: String,
    val type: EntityType
)

data class EntityWithPermission(
    val entity: UserEntity,
    val permission: ServerAccessRight
)

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

    fun listAcl(
        session: Session,
        serverId: String
    ) : List<EntityWithPermission>
}
