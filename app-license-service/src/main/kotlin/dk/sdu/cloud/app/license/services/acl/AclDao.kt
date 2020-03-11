package dk.sdu.cloud.app.license.services.acl
import dk.sdu.cloud.SecurityPrincipal

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
    val entity_name: String,
    val entity_type: EntityType,
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

    fun revokeAllServerPermissions(
        session: Session,
        serverId: String
    )

    fun listAcl(
        session: Session,
        serverId: String
    ): List<EntityWithPermission>
}
