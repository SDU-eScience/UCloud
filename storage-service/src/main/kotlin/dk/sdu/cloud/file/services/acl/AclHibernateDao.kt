package dk.sdu.cloud.file.services.acl

import dk.sdu.cloud.file.api.AccessRight
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
    var key: Key
) {
    companion object : HibernateEntity<PermissionEntry>, WithId<Key>

    @Embeddable
    data class Key(
        @get:Column(length = 2048) var path: String,
        @get:Column(length = 2048) var username: String,
        @get:Enumerated(EnumType.STRING) var permission: AccessRight
    ) : Serializable
}

class AclHibernateDao : AclDao<HibernateSession> {
    override fun updatePermissions(
        session: HibernateSession,
        path: String,
        username: String,
        permissions: Set<AccessRight>
    ) {
        val entries = permissions.map { PermissionEntry(PermissionEntry.Key(path, username, it)) }
        entries.forEach { session.saveOrUpdate(it) }

        session.deleteCriteria<PermissionEntry> {
            val key = entity[PermissionEntry::key]

            (key[PermissionEntry.Key::username] equal username) and
                    (key[PermissionEntry.Key::path] equal path) and
                    (not (key[PermissionEntry.Key::permission] isInCollection permissions))
        }.executeUpdate()
    }

    override fun hasPermission(
        session: HibernateSession,
        path: String,
        username: String,
        permission: AccessRight
    ): Boolean {
        val normalizedPath = path.normalize()
        val parents = normalizedPath.parents().map { it.normalize() } + listOf(normalizedPath)

        return session.criteria<PermissionEntry> {
            anyOf(
                *(parents.map { parent ->
                    (entity[PermissionEntry::key][PermissionEntry.Key::username] equal username) and
                            (entity[PermissionEntry::key][PermissionEntry.Key::path] equal parent) and
                            (entity[PermissionEntry::key][PermissionEntry.Key::permission] equal permission)
                }.toTypedArray())
            )
        }.list().isNotEmpty()
    }

    override fun listAcl(session: HibernateSession, paths: List<String>): Map<String, List<UserWithPermissions>> {
        return session
            .criteria<PermissionEntry> {
                anyOf(
                    *paths.map { normalizedPath ->
                        entity[PermissionEntry::key][PermissionEntry.Key::path] equal normalizedPath
                    }.toTypedArray()
                )
            }
            .list()
            .groupBy { it.key.path }
            .mapValues { (_, entries) ->
                entries
                    .groupBy { it.key.username }
                    .map { (username, permissions) ->
                        UserWithPermissions(username, permissions.map { it.key.permission }.toSet())
                    }
            }
            .toMap()
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
