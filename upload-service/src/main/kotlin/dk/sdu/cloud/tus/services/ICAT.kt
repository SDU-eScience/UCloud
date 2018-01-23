package dk.sdu.cloud.tus.services

import dk.sdu.cloud.tus.ICatDatabaseConfig
import org.slf4j.LoggerFactory
import java.sql.Connection
import java.sql.DriverManager

class ICAT(private val config: ICatDatabaseConfig) {
    // TODO This is all a really quick hack. We really shouldn't be using plain JDBC

    fun <T> useConnection(body: ICATConnection.() -> T): T {
        Class.forName("org.postgresql.Driver")
        return ICATConnection(DriverManager.getConnection(config.jdbcUrl, config.user, config.password)).use {
            it.body()
        }
    }
}

class ICATConnection(connection: Connection) : Connection by connection {
    fun findAccessRightForUserInCollection(user: String, zone: String, path: String): ICATAccessEntry? {
        val actualPath = path.removeSuffix("/")
        log.debug("findAccessRightForUserInCollection($user, $zone, $actualPath)")
        val query = """
            SELECT object_id, user_id, access_type_id, create_ts, modify_ts
            FROM r_objt_access
            WHERE
             object_id = (SELECT r_coll_main.coll_id FROM r_coll_main WHERE r_coll_main.coll_name = ?) AND
             user_id = (SELECT user_id FROM r_user_main WHERE user_name = ? AND zone_name = ?);
            """

        val results = ArrayList<ICATAccessEntry>()
        val rs = prepareStatement(query).apply {
            setString(1, actualPath)
            setString(2, user)
            setString(3, zone)
        }.executeQuery()

        while (rs.next()) {
            results.add(
                ICATAccessEntry(
                    rs.getLong("object_id"),
                    rs.getLong("user_id"),
                    rs.getLong("access_type_id"),
                    rs.getString("create_ts").toLong(10) * 1000,
                    rs.getString("modify_ts").toLong(10) * 1000
                )
            )
        }

        return results.singleOrNull()
            .also { log.debug("findAccessRightForUserInCollection($user, $zone, $actualPath) = $it") }
    }

    fun registerAccessEntry(entry: ICATAccessEntry) {
        val query = """
            INSERT INTO r_objt_access (object_id, user_id, access_type_id, create_ts, modify_ts) VALUES (
                ?, ?, ?, ?, ?
            );
            """
        val statement = prepareStatement(query).apply {
            setLong(1, entry.objectId)
            setLong(2, entry.userId)
            setLong(3, entry.accessType)
            setString(4, convertTimestampToICAT(entry.createdAtUnixMs))
            setString(5, convertTimestampToICAT(entry.modifiedAtUnixMs))
        }

        statement.executeUpdate()
    }

    fun userHasWriteAccess(user: String, zone: String, collection: String): Pair<Boolean, ICATAccessEntry?> {
        val entry = findAccessRightForUserInCollection(user, zone, collection)
        val canWrite = entry != null && (entry.accessType == 1200L || entry.accessType == 1120L)
        val nulledEntryIfNoAccess = if (canWrite) entry else null
        return Pair(canWrite, nulledEntryIfNoAccess)
    }

    fun registerDataObject(
        collectionId: Long, cephId: String, objectSize: Long,
        irodsName: String, irodsOwner: String, irodsOwnerZone: String, irodsResourceId: Long
    ): Long? {
        val query = """
            INSERT INTO r_data_main (
              data_id,
              coll_id,
              data_name,
              data_repl_num,
              data_version,
              data_type_name,
              data_size,
              resc_group_name,
              resc_name,
              data_path,
              data_owner_name,
              data_owner_zone,
              data_is_dirty,
              data_status,
              data_checksum,
              data_expiry_ts,
              data_map_id,
              data_mode,
              r_comment,
              create_ts,
              modify_ts,
              resc_hier,
              resc_id
            ) VALUES (
              NEXTVAL('r_objectid'),
              ?,
              ?,
              0,
              '',
              'generic',
              ?,
              NULL,
              'EMPTY_RESC_NAME',
              ?,
              ?,
              ?,
              1,
              NULL,
              '',
              '00000000000',
              0,
              '',
              '',
              ?,
              ?,
              NULL,
              ?
            );
        """

        val now = convertTimestampToICAT(System.currentTimeMillis())
        prepareStatement(query).apply {
            setLong(1, collectionId)
            setString(2, irodsName)
            setLong(3, objectSize)
            setString(4, cephId)
            setString(5, irodsOwner)
            setString(6, irodsOwnerZone)
            setString(7, now)
            setString(8, now)
            setLong(9, irodsResourceId)
        }.executeUpdate()

        return prepareStatement("SELECT currval('r_objectid')").executeQuery().run {
            if (next()) getLong(1) else null
        }
    }

    fun findResourceByNameAndZone(name: String, zone: String): Long? {
        val query = """
            SELECT resc_id FROM r_resc_main WHERE resc_name = ? AND zone_name = ?
            """
        val rs = prepareStatement(query).apply {
            setString(1, name)
            setString(2, zone)
        }.executeQuery()
        val results = ArrayList<Long>()
        while (rs.next()) {
            results.add(rs.getLong(1))
        }

        return results.singleOrNull()
    }

    fun convertTimestampToICAT(unixMs: Long) = (unixMs / 1000).toString().padStart(11, '0')

    companion object {
        private val log = LoggerFactory.getLogger(ICAT::class.java)
    }
}

data class ICATAccessEntry(
    val objectId: Long, val userId: Long, val accessType: Long,
    val createdAtUnixMs: Long, val modifiedAtUnixMs: Long
)