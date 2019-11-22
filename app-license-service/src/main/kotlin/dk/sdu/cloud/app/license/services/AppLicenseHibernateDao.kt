package dk.sdu.cloud.app.license.services

import dk.sdu.cloud.app.license.api.Application
import dk.sdu.cloud.app.license.api.ApplicationLicenseServer
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

    override fun create(session: HibernateSession, appLicenseServer: ApplicationLicenseServer): String {
        val newId = UUID.randomUUID().toString()
        val licenseServer = LicenseServerEntity(
            newId,
            appLicenseServer.name,
            appLicenseServer.version,
            appLicenseServer.address,
            appLicenseServer.port,
            appLicenseServer.license
        )


        session.save(licenseServer)

        println("Saving license server: $newId, ${appLicenseServer.name}, ${appLicenseServer.version}")

        return newId
    }

    override fun addApplicationToServer(
        session: HibernateSession,
        application: Application,
        serverId: String
    ) {
        val applicationLicenseServer = ApplicationLicenseServerEntity(
            ApplicationLicenseServerEntity.Key(application.name, application.version, serverId)
        )

        println("Adding relationship ${application.name}, ${application.version}, $serverId")

        session.save(applicationLicenseServer)

        println(session.createNativeQuery("""SELECT * FROM application_license_servers""").list().size)
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

        val serverSize = session.createNativeQuery("SELECT * FROM license_servers").list().size
        println("Found ${serverSize} servers")
        val relSize = session.createNativeQuery("SELECT * FROM application_license_servers").list().size
        println("Found ${relSize} relationships")

        val query = session.createNativeQuery<LicenseServerEntity>(
            """
            SELECT * FROM license_servers
            INNER JOIN application_license_servers
            ON license_servers.id = application_license_servers.license_server
            WHERE
              app_name = :appName AND app_version = :appVersion 
        """.trimIndent(), LicenseServerEntity::class.java
        ).also {
            it.setParameter("appName", application.name)
            it.setParameter("appVersion", application.version)
        }

        println("Found ${query.list().size} license servers")

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