package dk.sdu.cloud.app.license.services.acl
import dk.sdu.cloud.AccessRight

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
    val permission: AccessRight
)

interface AclDao<Session> {
    fun hasPermission(
        session: Session,
        licenseId: String,
        entity: UserEntity,
        permission: AccessRight
    ): Boolean

    fun updatePermissions(
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

    fun listAcl(
        session: Session,
        licenseId: String
    ) : List<EntityWithPermission>
}
