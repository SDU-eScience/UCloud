package dk.sdu.cloud.app.license.services.acl

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.app.license.api.ApplicationLicenseServer
import dk.sdu.cloud.service.db.*
import java.io.Serializable
import javax.persistence.*

data class PermissionEntry(
    var parent: String,

    @get:EmbeddedId
    var key: Key
) {
    companion object : HibernateEntity<PermissionEntry>, WithId<Key>

    @Embeddable
    data class Key(
        @get:Column(length = 255) var serverId: String,
        @get:Column(length = 255) var entity: String,
        @get:Column var entityType: EntityType,
        @get:Enumerated(EnumType.STRING) var permission: AccessRight
    ) : Serializable
}

class AclHibernateDao : AclDao<HibernateSession> {
    override fun hasPermission(
        session: HibernateSession,
        accessEntity: Entity,
        serverId: String,
        permission: AccessRight
    ): Boolean {
        return session
            .criteria<PermissionEntry> {
                (entity[PermissionEntry::key][PermissionEntry.Key::entity] equal accessEntity.id) and
                        (entity[PermissionEntry::key][PermissionEntry.Key::entityType] equal accessEntity.type) and
                        (entity[PermissionEntry::key][PermissionEntry.Key::serverId] equal serverId) and
                        (entity[PermissionEntry::key][PermissionEntry.Key::permission] equal permission)
            }
            .list()
            .isNotEmpty()
    }
}
