package dk.sdu.cloud.app.license.services

import dk.sdu.cloud.Roles
import dk.sdu.cloud.SecurityPrincipal
import dk.sdu.cloud.app.license.api.LicenseServer
import dk.sdu.cloud.app.license.api.LicenseServerId
import dk.sdu.cloud.app.license.api.LicenseServerWithId
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

    @get:Column(name = "name", unique = false, nullable = false)
    var name: String,

    @Column(name = "address", unique = false, nullable = false)
    var address: String,

    @Column(name = "port", unique = false, nullable = false)
    var port: String,

    @Column(name = "license", unique = false, nullable = true)
    var license: String?
) {
    fun toModel(): LicenseServerWithId {
        return LicenseServerWithId(
            id = id,
            name = name,
            address = address,
            port = port,
            license = if (license.isNullOrBlank()) { null } else { license }
        )
    }

    fun toIdentifiable(): LicenseServerId {
        return LicenseServerId(
            id = id,
            name = name
        )
    }

    companion object : HibernateEntity<LicenseServerEntity>, WithId<String>
}

class AppLicenseHibernateDao : AppLicenseDao<HibernateSession> {

    override fun create(session: HibernateSession, serverId: String, appLicenseServer: LicenseServer) {
        println("Adding license with key: ${appLicenseServer.license}")

        val licenseServer = LicenseServerEntity(
            serverId,
            appLicenseServer.name,
            appLicenseServer.address,
            appLicenseServer.port,
            appLicenseServer.license
        )

        session.save(licenseServer)
    }

    override fun getById(
        session: HibernateSession,
        id: String
    ): LicenseServerWithId? {
        return session.criteria<LicenseServerEntity> {
            allOf(
                entity[LicenseServerEntity::id] equal id
            )
        }.uniqueResult().toModel()
    }

    override fun list(
        session: HibernateSession,
        names: List<String>,
        userEntity: UserEntity
    ): List<LicenseServerId>? {

        return session.createNativeQuery<LicenseServerEntity>(
            """
            SELECT LS.id, LS.name, LS.address, LS.port, LS.license FROM {h-schema}license_servers AS LS
            INNER JOIN permissions
                ON LS.id = permissions.server_id
            WHERE
                LS.name in (:names)
                AND permissions.entity = :entityId
                AND permissions.entity_type = :entityType
                AND (permissions.permission = 'READ_WRITE'
    		    OR permissions.permission = 'READ')
        """.trimIndent(), LicenseServerEntity::class.java
        ).also {
            it.setParameter("names", names)
            it.setParameter("entityId", userEntity.id)
            it.setParameter("entityType", userEntity.type.toString())
        }.list().map { entity ->
            entity.toIdentifiable()
        }
    }

    override fun listAll(
        session: HibernateSession,
        user: SecurityPrincipal
    ): List<LicenseServerWithId>? {
        return session.createNativeQuery<LicenseServerEntity>(
            """
            SELECT LS.id, LS.name, LS.address, LS.port, LS.license FROM {h-schema}license_servers AS LS
            WHERE :role in (:privileged) 
        """.trimIndent(), LicenseServerEntity::class.java
        ).also {
            it.setParameter("role", user.role)
            it.setParameter("privileged", Roles.PRIVILEDGED)
        }.list().map { entity ->
            entity.toModel()
        }
    }

    override fun update(session: HibernateSession, appLicenseServer: LicenseServerWithId) {
        val existing = session.criteria<LicenseServerEntity> {
            (entity[LicenseServerEntity::id] equal appLicenseServer.id)
        }.uniqueResult()

        existing.address = appLicenseServer.address
        existing.port = appLicenseServer.port
        existing.license = appLicenseServer.license
        existing.name = appLicenseServer.name

        session.update(existing)
    }
}
