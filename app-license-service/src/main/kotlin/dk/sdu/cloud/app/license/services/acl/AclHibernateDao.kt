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
        @get:Column(length = 2048) var application_name: String,
        @get:Column(length = 2048) var application_version: String,
        @get:Column(length = 2048) var entity: String,
        @get:Enumerated(EnumType.STRING) var permission: AccessRight
    ) : Serializable
}

class AclHibernateDao : AclDao<HibernateSession> {
    /*override fun updatePermissions(
        session: HibernateSession,
        path: String,
        username: String,
        permissions: Set<AccessRight>
    ) {
        val entries =
            permissions.map { PermissionEntry(path.parent().normalize(), PermissionEntry.Key(path, username, it)) }
        entries.forEach { session.saveOrUpdate(it) }

        session.deleteCriteria<PermissionEntry> {
            val key = entity[PermissionEntry::key]

            (key[PermissionEntry.Key::username] equal username) and
                    (key[PermissionEntry.Key::path] equal path) and
                    (not(key[PermissionEntry.Key::permission] isInCollection permissions))
        }.executeUpdate()
    }*/

    override fun hasPermission(
        session: HibernateSession,
        licenseServer: ApplicationLicenseServer<HibernateSession>,
        username: String,
        permission: AccessRight
    ): Boolean {
        return session
            .criteria<PermissionEntry> {
                anyOf(licenseServer.address)
            }
            .list()
            .isNotEmpty()
    }
}
