package dk.sdu.cloud.app.license.services.acl

import dk.sdu.cloud.app.license.api.*
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
        @get:Column(name = "username") var user: String,
        @get:Column(name = "project") var project: String,
        @get:Column(name = "project_group") var group: String,
        @get:Column(name = "server_id") var serverId: String,
        @get:Enumerated(EnumType.STRING) var permission: ServerAccessRight
    ) : Serializable
}


class AclHibernateDao : AclDao<HibernateSession> {
    override fun hasPermission(
        session: HibernateSession,
        serverId: String,
        accessEntity: AccessEntity,
        permission: ServerAccessRight
    ): Boolean {
        return session.criteria<PermissionEntry> {
            (
                (entity[PermissionEntry::key][PermissionEntry.Key::user] equal accessEntity.user) or (
                    (entity[PermissionEntry::key][PermissionEntry.Key::project] equal accessEntity.project) and
                        (entity[PermissionEntry::key][PermissionEntry.Key::group] equal accessEntity.group)
                )
            ) and (
                if (permission == ServerAccessRight.READ_WRITE) {
                    (entity[PermissionEntry::key][PermissionEntry.Key::permission] equal ServerAccessRight.READ_WRITE)
                } else {
                    ((entity[PermissionEntry::key][PermissionEntry.Key::permission] equal ServerAccessRight.READ) or
                        (entity[PermissionEntry::key][PermissionEntry.Key::permission] equal ServerAccessRight.READ_WRITE))
                }
            )
        }.list().isNotEmpty()
    }


    override fun updatePermissions(
        session: HibernateSession,
        serverId: String,
        accessEntity: AccessEntity,
        permissions: ServerAccessRight
    ) {
        val permissionEntry = PermissionEntry(
            PermissionEntry.Key(
                accessEntity.user ?: "",
                accessEntity.project ?: "",
                accessEntity.group ?: "",
                serverId,
                permissions
            )
        )

        session.saveOrUpdate(permissionEntry)
    }

    override fun revokePermission(
        session: HibernateSession,
        serverId: String,
        accessEntity: AccessEntity
    ) {
        session.deleteCriteria<PermissionEntry> {
            (entity[PermissionEntry::key][PermissionEntry.Key::serverId] equal serverId) and
                    if (accessEntity.user.isNullOrBlank()) {
                        (entity[PermissionEntry::key][PermissionEntry.Key::project] equal accessEntity.project) and
                                (entity[PermissionEntry::key][PermissionEntry.Key::group] equal accessEntity.group)
                    } else {
                        (entity[PermissionEntry::key][PermissionEntry.Key::user] equal accessEntity.user)
                    }
        }.executeUpdate()
    }

    override fun revokeAllServerPermissions(
        session: HibernateSession,
        serverId: String
    ) {
        session.deleteCriteria<PermissionEntry> {
            (entity[PermissionEntry::key][PermissionEntry.Key::serverId] equal serverId)
        }.executeUpdate()
    }

    override fun listAcl(
        session: HibernateSession,
        serverId: String
    ): List<AccessEntityWithPermission> {
        return session
            .criteria<PermissionEntry> {
                entity[PermissionEntry::key][PermissionEntry.Key::serverId] equal serverId
            }
            .list()
            .map {
                AccessEntityWithPermission(AccessEntity(it.key.user, it.key.project, it.key.group), it.key.permission)
            }
    }

    override fun revokePermissionsFromEntity(session: HibernateSession, accessEntity: AccessEntity) {
        session.deleteCriteria<PermissionEntry> {
            if (accessEntity.user.isNullOrBlank()) {
                (entity[PermissionEntry::key][PermissionEntry.Key::project] equal accessEntity.project) and
                        (entity[PermissionEntry::key][PermissionEntry.Key::group] equal accessEntity.group)
            } else {
                (entity[PermissionEntry::key][PermissionEntry.Key::user] equal accessEntity.user)
            }
        }.executeUpdate()
    }
}
