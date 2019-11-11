package dk.sdu.cloud.app.license.services.acl
import dk.sdu.cloud.AccessRight

enum class EntityType {
    USER,
    PROJECTANDGROUP
}

data class UserEntity(
    val id: String,
    val type: EntityType
)

interface AclDao<Session> {
    fun hasPermission(
        session: Session,
        licenseId: String,
        entity: UserEntity,
        permission: AccessRight
    ): Boolean

    fun updatePermission(
        session: Session,
        licenseId: String,
        entity: UserEntity,
        permissions: Set<AccessRight>
    )

    fun revokePermission(
        session: Session,
        licenseId: String,
        entity: UserEntity
    )
}
