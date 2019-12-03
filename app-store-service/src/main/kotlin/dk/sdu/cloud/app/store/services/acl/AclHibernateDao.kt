package dk.sdu.cloud.app.store.services.acl

import dk.sdu.cloud.app.store.services.acl.ApplicationAccessRight
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
        accessEntity: UserEntity,
        applicationName: String,
        permission: ApplicationAccessRight
    ): Boolean {
        return when (permission) {
            ApplicationAccessRight.READ -> {
                session.criteria<PermissionEntry> {
                    allOf(
                        (entity[PermissionEntry::key][PermissionEntry.Key::userEntity] equal accessEntity.id),
                        (entity[PermissionEntry::key][PermissionEntry.Key::entityType] equal accessEntity.type),
                        ((entity[PermissionEntry::key][PermissionEntry.Key::permission] equal ApplicationAccessRight.READ) or
                                (entity[PermissionEntry::key][PermissionEntry.Key::permission] equal ApplicationAccessRight.READ_WRITE))
                    )
                }.list().isNotEmpty()
            }
            ApplicationAccessRight.READ_WRITE -> {
                session.criteria<PermissionEntry> {
                    allOf(
                        (entity[PermissionEntry::key][PermissionEntry.Key::userEntity] equal accessEntity.id),
                        (entity[PermissionEntry::key][PermissionEntry.Key::entityType] equal accessEntity.type),
                        (entity[PermissionEntry::key][PermissionEntry.Key::permission] equal ApplicationAccessRight.READ_WRITE)
                    )
                }.list().isNotEmpty()
            }
        }
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
        userEntity: UserEntity,
        applicationName: String
    ) {
        session.deleteCriteria<PermissionEntry> {
            allOf(
                (entity[PermissionEntry::key][PermissionEntry.Key::applicationName] equal applicationName),
                (entity[PermissionEntry::key][PermissionEntry.Key::userEntity] equal userEntity.id),
                (entity[PermissionEntry::key][PermissionEntry.Key::entityType] equal userEntity.type)
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
