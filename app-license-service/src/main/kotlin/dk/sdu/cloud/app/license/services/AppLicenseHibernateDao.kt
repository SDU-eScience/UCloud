package dk.sdu.cloud.app.license.services

import dk.sdu.cloud.app.license.api.Application
import dk.sdu.cloud.app.license.api.ApplicationLicenseServer
import dk.sdu.cloud.app.license.services.acl.AclService
import dk.sdu.cloud.app.license.services.acl.PermissionEntry
import dk.sdu.cloud.app.license.services.acl.UserEntity
import dk.sdu.cloud.service.db.*
import java.io.Serializable
import java.util.*
import javax.persistence.*

@Entity
@Table(name = "license_servers")
class LicenseServerEntity(
    @Id
    @Column(name = "id", unique = true, nullable = false)
    var id: String,

    @get:Column(name = "name", unique = false, nullable = true)
    var name: String,

    @Column(name = "version", unique = false, nullable = true)
    var version: String,

    @Column(name = "address", unique = false, nullable = true)
    var address: String,

    @Column(name = "port", unique = false, nullable = true)
    var port: String,

    @Column(name = "license", unique = false, nullable = true)
    var license: String?
) {
    fun toModel(): ApplicationLicenseServer {
        return ApplicationLicenseServer(
            name = name,
            version = version,
            address = address,
            port = port,
            license = license
        )
    }

    companion object : HibernateEntity<LicenseServerEntity>, WithId<String>
}

@Entity
@Table(name = "application_license_servers")
class ApplicationLicenseServerEntity(
    @EmbeddedId
    var key: Key
) {
    companion object : HibernateEntity<ApplicationLicenseServerEntity>, WithId<Key>

    @Embeddable
    data class Key(
        @get:Column(name = "app_name", length = 255) var appName: String,
        @get:Column(name = "app_version", length = 255) var appVersion: String,
        @get:Column(name = "license_server", length = 255) var licenseServer: String
    ) : Serializable
}

class AppLicenseHibernateDao : AppLicenseDao<HibernateSession> {

    override fun create(session: HibernateSession, serverId: String, appLicenseServer: ApplicationLicenseServer) {
        val licenseServer = LicenseServerEntity(
            serverId,
            appLicenseServer.name,
            appLicenseServer.version,
            appLicenseServer.address,
            appLicenseServer.port,
            appLicenseServer.license
        )

        session.save(licenseServer)
    }

    override fun addApplicationToServer(
        session: HibernateSession,
        application: Application,
        serverId: String
    ) {
        val applicationLicenseServer = ApplicationLicenseServerEntity(
            ApplicationLicenseServerEntity.Key(application.name, application.version, serverId)
        )

        session.save(applicationLicenseServer)
    }

    override fun removeApplicationFromServer(
        session: HibernateSession,
        application: Application,
        serverId: String
    ) {
        session.deleteCriteria<ApplicationLicenseServerEntity> {
            (entity[ApplicationLicenseServerEntity::key][ApplicationLicenseServerEntity.Key::licenseServer] equal serverId) and
                    (entity[ApplicationLicenseServerEntity::key][ApplicationLicenseServerEntity.Key::appName] equal application.name) and
                    (entity[ApplicationLicenseServerEntity::key][ApplicationLicenseServerEntity.Key::appVersion] equal application.version)
        }.executeUpdate()
    }

    override fun getById(
        session: HibernateSession,
        id: String
    ): LicenseServerEntity? {
        return session.criteria<LicenseServerEntity> {
            allOf(
                entity[LicenseServerEntity::id] equal id
            )
        }.uniqueResult()
    }

    override fun list(
        session: HibernateSession,
        application: Application,
        userEntity: UserEntity
    ): List<LicenseServerEntity>? {

        val query = session.createNativeQuery<LicenseServerEntity>(
            """
            SELECT LS.id, LS.name, LS.version, LS.address, LS.port, LS.license FROM {h-schema}license_servers AS LS
            INNER JOIN application_license_servers
              ON LS.id = application_license_servers.license_server       
            INNER JOIN permissions
              ON LS.id = permissions.server_id
            WHERE
              application_license_servers.app_name = :appName
    	      AND application_license_servers.app_version = :appVersion
              AND permissions.entity = :entityId
              AND permissions.entity_type = :entityType
              AND (permissions.permission = 'READ_WRITE'
    		    OR permissions.permission = 'READ')
        """.trimIndent(), LicenseServerEntity::class.java
        ).also {
            it.setParameter("appName", application.name)
            it.setParameter("appVersion", application.version)
            it.setParameter("entityId", userEntity.id)
            it.setParameter("entityType", userEntity.type.toString())
        }

        return query.list()
    }

    override fun save(session: HibernateSession, appLicenseServer: ApplicationLicenseServer, withId: String) {
        val existing = session.criteria<LicenseServerEntity> {
            (entity[LicenseServerEntity::id] equal withId)
        }.uniqueResult()

        existing.address = appLicenseServer.address
        existing.port = appLicenseServer.port
        existing.license = appLicenseServer.license
        existing.name = appLicenseServer.name
        existing.version = appLicenseServer.version

        session.update(existing)
    }
}