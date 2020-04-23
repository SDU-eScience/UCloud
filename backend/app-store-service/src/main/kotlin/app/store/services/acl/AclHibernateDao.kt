package dk.sdu.cloud.app.store.services.acl

import dk.sdu.cloud.app.store.api.ApplicationAccessRight
import dk.sdu.cloud.app.store.api.EntityType
import dk.sdu.cloud.app.store.api.EntityWithPermission
import dk.sdu.cloud.app.store.api.UserEntity
import dk.sdu.cloud.service.db.*
import java.io.Serializable
import javax.persistence.*

@javax.persistence.Entity
@Table(name = "permissions")
data class PermissionEntry(
    @get:EmbeddedId
    var key: Key
) {
    companion object : HibernateEntity<PermissionEntry>, WithId<Key>

    @Embeddable
    data class Key(
        @get:Column(name = "entity") var userEntity: String,
        @get:Enumerated(EnumType.STRING) @Column(name = "entity_type") var entityType: EntityType,
        @get:Column(name = "application_name") var applicationName: String,
        @get:Enumerated(EnumType.STRING) var permission: ApplicationAccessRight
    ) : Serializable
}


class AclHibernateDao : AclDao<HibernateSession> {
    override fun hasPermission(
        session: HibernateSession,
        entity: UserEntity,
        applicationName: String,
        permissions: Set<ApplicationAccessRight>
    ): Boolean {
        val result = session.criteria<PermissionEntry> {
            allOf(
                (this.entity[PermissionEntry::key][PermissionEntry.Key::userEntity] equal entity.id),
                (this.entity[PermissionEntry::key][PermissionEntry.Key::entityType] equal entity.type),
                (this.entity[PermissionEntry::key][PermissionEntry.Key::applicationName] equal applicationName)
            )
        }.uniqueResultOptional()

        if (result.isPresent) {
            return permissions.contains(result.get().key.permission)
        }
        return false
    }


    override fun updatePermissions(
        session: HibernateSession,
        userEntity: UserEntity,
        applicationName: String,
        permissions: ApplicationAccessRight
    ) {
        val permissionEntry = PermissionEntry(
            PermissionEntry.Key(
                userEntity.id,
                userEntity.type,
                applicationName,
                permissions
            )
        )

        session.saveOrUpdate(permissionEntry)
    }

    override fun revokePermission(
        session: HibernateSession,
        entity: UserEntity,
        applicationName: String
    ) {
        session.deleteCriteria<PermissionEntry> {
            allOf(
                (this.entity[PermissionEntry::key][PermissionEntry.Key::applicationName] equal applicationName),
                (this.entity[PermissionEntry::key][PermissionEntry.Key::userEntity] equal entity.id),
                (this.entity[PermissionEntry::key][PermissionEntry.Key::entityType] equal entity.type)
            )
        }.executeUpdate()
    }

    override fun listAcl(
        session: HibernateSession,
        applicationName: String
    ): List<EntityWithPermission> {
        return session
            .criteria<PermissionEntry> {
                entity[PermissionEntry::key][PermissionEntry.Key::applicationName] equal applicationName
            }
            .list()
            .map {
                EntityWithPermission(UserEntity(it.key.userEntity, it.key.entityType), it.key.permission)
            }
    }
}
