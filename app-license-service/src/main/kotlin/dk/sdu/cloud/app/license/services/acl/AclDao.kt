package dk.sdu.cloud.app.license.services.acl
import dk.sdu.cloud.AccessRight

enum class EntityType {
    USER,
    PROJECTANDGROUP
}

data class Entity(
    val id: String,
    val type: EntityType
)

interface AclDao<Session> {
    fun hasPermission(
        session: Session,
        entity: Entity,
        serverId: String,
        permission: AccessRight
    ): Boolean
}
