package dk.sdu.cloud.app.store.services.acl

import dk.sdu.cloud.SecurityPrincipal
import dk.sdu.cloud.app.store.api.AccessEntity
import dk.sdu.cloud.app.store.api.ApplicationAccessRight
import dk.sdu.cloud.app.store.api.EntityWithPermission
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
        @get:Column(name = "application_name") var applicationName: String,
        @get:Enumerated(EnumType.STRING) var permission: ApplicationAccessRight
    ) : Serializable
}


class AclHibernateDao : AclDao<HibernateSession> {
    override fun hasPermission(
        session: HibernateSession,
        user: SecurityPrincipal,
        project: String?,
        memberGroups: List<String>,
        applicationName: String,
        permissions: Set<ApplicationAccessRight>
    ): Boolean {
        val result = session.criteria<PermissionEntry> {
            (entity[PermissionEntry::key][PermissionEntry.Key::user] equal user.username) or (
                    (entity[PermissionEntry::key][PermissionEntry.Key::project] equal project) and
                            (entity[PermissionEntry::key][PermissionEntry.Key::group] isInCollection memberGroups)
                    ) and (entity[PermissionEntry::key][PermissionEntry.Key::applicationName] equal applicationName)
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
                accessEntity.user ?: "",
                accessEntity.project ?: "",
                accessEntity.group ?: "",
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
            (entity[PermissionEntry::key][PermissionEntry.Key::applicationName] equal applicationName) and
                    if (accessEntity.user.isNullOrBlank()) {
                        (entity[PermissionEntry::key][PermissionEntry.Key::project] equal accessEntity.project) and
                                (entity[PermissionEntry::key][PermissionEntry.Key::group] equal accessEntity.group)
                    } else {
                        (entity[PermissionEntry::key][PermissionEntry.Key::user] equal accessEntity.user)
                    }
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
                EntityWithPermission(AccessEntity(it.key.user, it.key.project, it.key.group), it.key.permission)
            }
    }
}
