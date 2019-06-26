package dk.sdu.cloud.file.services.acl

import dk.sdu.cloud.file.api.normalize
import dk.sdu.cloud.file.api.parents
import dk.sdu.cloud.service.db.HibernateEntity
import dk.sdu.cloud.service.db.HibernateSession
import dk.sdu.cloud.service.db.WithId
import dk.sdu.cloud.service.db.criteria
import dk.sdu.cloud.service.db.deleteCriteria
import dk.sdu.cloud.service.db.get
import java.io.Serializable
import javax.persistence.Column
import javax.persistence.Embeddable
import javax.persistence.EmbeddedId
import javax.persistence.Entity
import javax.persistence.EnumType
import javax.persistence.Enumerated
import javax.persistence.Table

@Entity
@Table(name = "permissions")
data class PermissionEntry(
    @get:EmbeddedId
    var key: Key,

    @get:Enumerated(EnumType.STRING)
    var permission: AclPermission
) {
    companion object : HibernateEntity<PermissionEntry>, WithId<Key>

    @Embeddable
    data class Key(
        @get:Column(length = 2048) var path: String,
        @get:Column(length = 2048) var username: String
    ) : Serializable
}

class AclHibernateDao : AclDao<HibernateSession> {
    override fun createOrUpdatePermission(
        session: HibernateSession,
        path: String,
        username: String,
        permission: AclPermission
    ) {
        val entry = PermissionEntry(PermissionEntry.Key(path.normalize(), username), permission)
        session.saveOrUpdate(entry)
    }

    override fun hasPermission(
        session: HibernateSession,
        path: String,
        username: String,
        permission: AclPermission
    ): Boolean {
        val normalizedPath = path.normalize()
        val parents = normalizedPath.parents().map { it.normalize() } + listOf(normalizedPath)

        return session.criteria<PermissionEntry> {
            anyOf(
                *(parents.map { parent ->
                    val permissionPredicate = when (permission) {
                        AclPermission.READ -> {
                            (entity[PermissionEntry::permission] equal AclPermission.READ) or
                                    (entity[PermissionEntry::permission] equal AclPermission.WRITE)
                        }

                        AclPermission.WRITE -> {
                            (entity[PermissionEntry::permission] equal AclPermission.WRITE)
                        }
                    }

                    (entity[PermissionEntry::key][PermissionEntry.Key::username] equal username) and
                            (entity[PermissionEntry::key][PermissionEntry.Key::path] equal parent) and
                            permissionPredicate
                }.toTypedArray())
            )
        }.list().isNotEmpty()
    }

    override fun listAcl(session: HibernateSession, path: String): List<UserWithPermissions> {
        val normalizedPath = path.normalize()
        return session.criteria<PermissionEntry> {
            entity[PermissionEntry::key][PermissionEntry.Key::path] equal normalizedPath
        }.list().map {
            UserWithPermissions(it.key.username, it.permission)
        }
    }

    override fun revokePermission(session: HibernateSession, path: String, username: String) {
        val normalizedPath = path.normalize()
        session.deleteCriteria<PermissionEntry> {
            (entity[PermissionEntry::key][PermissionEntry.Key::path] equal normalizedPath) and
                    (entity[PermissionEntry::key][PermissionEntry.Key::username] equal username)
        }.executeUpdate()
    }

    override fun handleFilesMoved(session: HibernateSession, path: String) {
        TODO("not implemented")
    }

    override fun handleFilesDeleted(session: HibernateSession, path: String) {
        val normalizedPath = path.normalize()
        session.deleteCriteria<PermissionEntry> {
            (entity[PermissionEntry::key][PermissionEntry.Key::path] equal normalizedPath) or
                    (entity[PermissionEntry::key][PermissionEntry.Key::path] like "$normalizedPath/%")
        }.executeUpdate()
    }
}
