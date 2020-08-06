package dk.sdu.cloud.app.license.services

import com.github.jasync.sql.db.RowData
import dk.sdu.cloud.Roles
import dk.sdu.cloud.SecurityPrincipal
import dk.sdu.cloud.app.license.api.*
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.SQLTable
import dk.sdu.cloud.service.db.async.getField
import dk.sdu.cloud.service.db.async.insert
import dk.sdu.cloud.service.db.async.int
import dk.sdu.cloud.service.db.async.sendPreparedStatement
import dk.sdu.cloud.service.db.async.text
import dk.sdu.cloud.service.db.async.withSession
import io.ktor.http.HttpStatusCode

object TagLicenseTable : SQLTable ("tags") {
    val name = text("name", notNull = true)
    val serverId = text("license_server", notNull = true)
}

object LicenseServerTable : SQLTable("license_servers") {
    val id = text("id", notNull = true)
    val name = text("name", notNull = true)
    val address = text("address", notNull = true)
    val port = int("port", notNull = true)
    val license = text("license")
}

fun RowData.toLicenseServerWithId(): LicenseServerWithId {
    return LicenseServerWithId(
        id = getField(LicenseServerTable.id),
        name = getField(LicenseServerTable.name),
        address = getField(LicenseServerTable.address),
        port = getField(LicenseServerTable.port),
        license = if (getField(LicenseServerTable.license).isNullOrBlank()) {null}
                    else {getField(LicenseServerTable.license)}
    )
}

fun RowData.toIdentifiable(): LicenseServerId {
    return LicenseServerId(
        id = getField(LicenseServerTable.id),
        name = getField(LicenseServerTable.name)
    )
}


class AppLicenseAsyncDao {

    suspend fun create(db: DBContext, serverId: String, appLicenseServer: LicenseServer) {
        db.withSession { session ->
            session.insert(LicenseServerTable) {
                set(LicenseServerTable.id, serverId)
                set(LicenseServerTable.name, appLicenseServer.name)
                set(LicenseServerTable.address, appLicenseServer.address)
                set(LicenseServerTable.port, appLicenseServer.port)
                set(LicenseServerTable.license, appLicenseServer.license)
            }
        }
    }

    suspend fun getById(
        db: DBContext,
        id: String
    ): LicenseServerWithId? {
        return db.withSession { session ->
            session
                .sendPreparedStatement(
                    {
                        setParameter("serverID", id)
                    },
                    """
                        SELECT * 
                        FROM license_servers
                        WHERE id = :serverID
                    """
                ).rows
                .firstOrNull()
                ?.toLicenseServerWithId()
        }
    }

    suspend fun list(
        db: DBContext,
        tags: List<String>,
        securityPrincipal: SecurityPrincipal,
        projectGroups: List<ProjectAndGroup>
    ): List<LicenseServerId>? {
        val projects = projectGroups.map { it.project }
        val groups = projectGroups.map { it.group }

        return db.withSession { session ->
            session
                .sendPreparedStatement(
                    {
                        setParameter("tags", tags)
                        setParameter("user", securityPrincipal.username)
                        setParameter("projects", projects )
                        setParameter("groups", groups)
                    },
                    """
                        SELECT * 
                        FROM license_servers AS LS
                        INNER JOIN permissions as P
                            ON LS.id = P.server_id
                        WHERE LS.id IN (SELECT T.license_server FROM tags AS T where T.name IN (select unnest(:tags::text[])))
                            AND (
                                P.username = :user
                                OR ((P.project, P.project_group) IN (select unnest(:projects::text[]), unnest(:groups::text[]))) 
                                AND (P.permission = 'READ_WRITE' OR P.permission = 'READ')
                            )
                    """

                    //query
                ).rows
                .map {
                    it.toIdentifiable()
                }

        }
    }

    suspend fun listAll(
        db: DBContext,
        user: SecurityPrincipal
    ): List<LicenseServerWithId>? {
        if (user.role in Roles.PRIVILEGED) {
            return db.withSession { session ->
                session
                    .sendPreparedStatement(
                        """
                            SELECT * 
                            FROM license_servers
                        """
                    ).rows
                    .map {
                        it.toLicenseServerWithId()
                    }
            }
        } else {
            throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)
        }
    }

    suspend fun update(db: DBContext, appLicenseServer: LicenseServerWithId) {
        db.withSession { session ->
            val rowsAffectedByUpdate = session
                .sendPreparedStatement(
                    {
                        setParameter("address", appLicenseServer.address)
                        setParameter("port", appLicenseServer.port)
                        setParameter("license", appLicenseServer.license)
                        setParameter("name", appLicenseServer.name)
                        setParameter("licenseID", appLicenseServer.id)
                    },
                    """
                        UPDATE license_servers
                        SET address = :address, port = :port, license = :license, name = :name
                        WHERE id = :licenseID
                    """
                ).rowsAffected

            if ( rowsAffectedByUpdate == 0L ) {
                throw RPCException.fromStatusCode(HttpStatusCode.BadRequest, "License server does not exist.")
            }
        }
    }

    suspend fun delete(db: DBContext, serverId: String) {
        db.withSession{ session ->
            session
                .sendPreparedStatement(
                    {
                        setParameter("serverID", serverId)
                    },
                    """
                        DELETE FROM license_servers
                        WHERE id = :serverID
                    """
                )
        }
    }

    suspend fun addTag(db: DBContext, name: String, serverId: String) {
        db.withSession { session ->
            session
                .insert(TagLicenseTable) {
                    set(TagLicenseTable.name, name)
                    set(TagLicenseTable.serverId, serverId)
                }
        }
    }

    suspend fun listTags(db: DBContext, serverId: String): List<String> {
        return db.withSession { session ->
            session
                .sendPreparedStatement(
                    {
                        setParameter("serverId", serverId)
                    },
                    """
                        SELECT * 
                        FROM tags
                        WHERE license_server = :serverId
                    """
                ).rows
                .map {
                    it.getField(TagLicenseTable.name)
                }
        }
    }

    suspend fun deleteTag(db: DBContext, name: String, serverId: String) {
        db.withSession { session ->
            session
                .sendPreparedStatement(
                    {
                        setParameter("serverId", serverId)
                        setParameter("name", name)
                    },
                    """
                        DELETE FROM tags
                        WHERE license_server = :serverId AND name = :name
                    """
                )
        }
    }
}
