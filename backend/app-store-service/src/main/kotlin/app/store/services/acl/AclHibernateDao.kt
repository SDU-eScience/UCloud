package dk.sdu.cloud.app.store.services.acl

import dk.sdu.cloud.SecurityPrincipal
import dk.sdu.cloud.app.store.api.AccessEntity
import dk.sdu.cloud.app.store.api.ApplicationAccessRight
import dk.sdu.cloud.app.store.api.EntityWithPermission
import dk.sdu.cloud.service.db.*
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.SQLTable
import dk.sdu.cloud.service.db.async.text
import java.io.Serializable
import javax.persistence.*

object PermissionTable : SQLTable("permissions") {
    val user = text("username", notNull = true)
    val project = text("project", notNull = true)
    val group = text("project_group", notNull = true)
    val applicationName = text("application_name", notNull = true)
    val permission = text()
}
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


class AclHibernateDao : AclDao {
    override suspend fun hasPermission(
        ctx: DBContext,
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

    override suspend fun updatePermissions(
        ctx: DBContext,
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

    override suspend fun revokePermission(
        ctx: DBContext,
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

    override suspend fun listAcl(
        ctx: DBContext,
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
