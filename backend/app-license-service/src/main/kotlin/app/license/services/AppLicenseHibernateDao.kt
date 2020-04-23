package dk.sdu.cloud.app.license.services

import dk.sdu.cloud.Roles
import dk.sdu.cloud.SecurityPrincipal
import dk.sdu.cloud.app.license.api.*
import dk.sdu.cloud.service.db.*
import java.io.Serializable
import javax.persistence.*

@Entity
@Table(name = "tags")
class TagEntity(
    @get:EmbeddedId
    var key: Key
) {
    companion object : HibernateEntity<TagEntity>, WithId<Key>

    @Embeddable
    data class Key(
        @get:Column(name = "name") var name: String,
        @get:Column(name = "license_server") var serverId: String
    ) : Serializable
}

@Entity
@Table(name = "license_servers")
class LicenseServerEntity(
    @Id
    @Column(name = "id", unique = true, nullable = false)
    var id: String,

    @Column(name = "name", unique = false, nullable = false)
    var name: String,

    @Column(name = "address", unique = false, nullable = false)
    var address: String,

    @Column(name = "port", unique = false, nullable = false)
    var port: Int,

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
        tags: List<String>,
        securityPrincipal: SecurityPrincipal,
        projectGroups: List<ProjectAndGroup>
    ): List<LicenseServerId>? {
        var query = """
            SELECT LS.id, LS.name, LS.address, LS.port, LS.license FROM {h-schema}license_servers AS LS
            INNER JOIN {h-schema}permissions as P
                ON LS.id = P.server_id
            WHERE LS.id IN (SELECT T.license_server FROM {h-schema}tags AS T where T.name IN :tags)
                AND (
                    P.username = :user
        """

        if (projectGroups.size > 0) {
            query += " OR ("
            var i = 0
            for(index in projectGroups.indices) {
                query += "(P.project = :project$index AND P.project_group = :group$index)"
                if (i < projectGroups.size - 1) {
                    query += " OR "
                }
                i++
            }
            query += ")"
        }

        query += " AND (P.permission = 'READ_WRITE' OR P.permission = 'READ'))"

        return session.createNativeQuery<LicenseServerEntity>(
            query.trimIndent(), LicenseServerEntity::class.java
        ).also {
            it.setParameter("tags", tags)
            it.setParameter("user", securityPrincipal.username)
        }.apply {
            projectGroups.forEachIndexed { index, projectAndGroup ->
                setParameter("project$index", projectAndGroup.project)
                setParameter("group$index", projectAndGroup.group)
            }
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

    override fun delete(session: HibernateSession, serverId: String) {
        session.deleteCriteria<LicenseServerEntity> {
            (entity[LicenseServerEntity::id] equal serverId)
        }.executeUpdate()
    }

    override fun addTag(session: HibernateSession, name: String, serverId: String) {
        val tag = TagEntity(
            TagEntity.Key(name, serverId)
        )
        session.save(tag)
    }

    override fun listTags(session: HibernateSession, serverId: String): List<String> {
        return session.criteria<TagEntity> {
            entity[TagEntity::key][TagEntity.Key::serverId] equal serverId
        }.list().map { it.key.name }
    }

    override fun deleteTag(session: HibernateSession, name: String, serverId: String) {
        session.deleteCriteria<TagEntity> {
            allOf(
                (entity[TagEntity::key][TagEntity.Key::serverId] equal serverId),
                (entity[TagEntity::key][TagEntity.Key::name] equal name)
            )
        }.executeUpdate()
    }
}
