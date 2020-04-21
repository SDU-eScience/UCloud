package dk.sdu.cloud.app.store.services.acl

import dk.sdu.cloud.app.store.api.ApplicationAccessRight
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
        @get:Column(name = "entity") var accessEntity: String,
        @get:Enumerated(EnumType.STRING) @Column(name = "entity_type") var entityType: EntityType,
        @get:Column(name = "application_name") var applicationName: String,
        @get:Enumerated(EnumType.STRING) var permission: ApplicationAccessRight
    ) : Serializable
}


class AclHibernateDao : AclDao<HibernateSession> {
    override fun hasPermission(
        session: HibernateSession,
        accessEntity: AccessEntity,
        applicationName: String,
        permissions: Set<ApplicationAccessRight>
    ): Boolean {
        val result = session.criteria<PermissionEntry> {
            allOf(
                (entity[PermissionEntry::key][PermissionEntry.Key::accessEntity] equal accessEntity.id),
                (entity[PermissionEntry::key][PermissionEntry.Key::entityType] equal accessEntity.type),
                (entity[PermissionEntry::key][PermissionEntry.Key::applicationName] equal applicationName)
            )
        }.uniqueResultOptional()

        if (result.isPresent) {
            return permissions.contains(result.get().key.permission)
        }
        return false
    }


    override fun updatePermissions(
        session: HibernateSession,
        accessEntity: AccessEntity,
        applicationName: String,
        permissions: ApplicationAccessRight
    ) {
        val permissionEntry = PermissionEntry(
            PermissionEntry.Key(
                accessEntity.id,
                accessEntity.type,
                applicationName,
                permissions
            )
        )

        session.saveOrUpdate(permissionEntry)
    }

    override fun revokePermission(
        session: HibernateSession,
        accessEntity: AccessEntity,
        applicationName: String
    ) {
        session.deleteCriteria<PermissionEntry> {
            allOf(
                (entity[PermissionEntry::key][PermissionEntry.Key::applicationName] equal applicationName),
                (entity[PermissionEntry::key][PermissionEntry.Key::accessEntity] equal accessEntity.id),
                (entity[PermissionEntry::key][PermissionEntry.Key::entityType] equal accessEntity.type)
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
                EntityWithPermission(AccessEntity(it.key.accessEntity, it.key.entityType), it.key.permission)
            }
    }
}
