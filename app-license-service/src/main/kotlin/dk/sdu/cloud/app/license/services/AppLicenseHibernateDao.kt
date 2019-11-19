package dk.sdu.cloud.app.license.services

import dk.sdu.cloud.SecurityPrincipal
import dk.sdu.cloud.app.license.api.ApplicationLicenseServer
import dk.sdu.cloud.service.db.*
import io.ktor.http.ContentType
import java.util.*
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.Table

@Entity
@Table(name = "application_license_servers")
class ApplicationLicenseServerEntity(
    @Id
    @Column(name = "id", unique = true, nullable = false)
    var id: String,

    @Column(name = "name", unique = false, nullable = true)
    var name: String?,

    @Column(name = "version", unique = false, nullable = true)
    var version: String?,

    @Column(name = "address", unique = false, nullable = true)
    var address: String?,

    @Column(name = "license", unique = false, nullable = true)
    var license: String?
) {
    companion object : HibernateEntity<ApplicationLicenseServerEntity>, WithId<String>
}


class AppLicenseHibernateDao : AppLicenseDao<HibernateSession> {
    override fun create(session: HibernateSession, appLicenseServer: ApplicationLicenseServer): String {
        val newId = UUID.randomUUID().toString()
        val licenseServer = ApplicationLicenseServerEntity(
            newId,
            appLicenseServer.name,
            appLicenseServer.version,
            appLicenseServer.address,
            appLicenseServer.license
        )

        session.save(licenseServer)
        session.transaction.commit()

        return newId
    }

    override fun getById(
        session: HibernateSession,
        id: String
    ): ApplicationLicenseServerEntity? {
        return session.criteria<ApplicationLicenseServerEntity> {
            allOf(
                entity[ApplicationLicenseServerEntity::id] equal id
            )
        }.uniqueResult()
    }

    override fun save(session: HibernateSession, appLicenseServer: ApplicationLicenseServer, withId: String) {
        val existing = session.criteria<ApplicationLicenseServerEntity> {
            (entity[ApplicationLicenseServerEntity::id] equal withId)
        }.uniqueResult()

        existing.address = appLicenseServer.address
        existing.license = appLicenseServer.license
        existing.name = appLicenseServer.name
        existing.version = appLicenseServer.version

        session.update(existing)
    }
}