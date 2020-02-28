package dk.sdu.cloud.file.services.acl

import com.fasterxml.jackson.databind.JsonNode
import com.github.jasync.sql.db.RowData
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.file.api.normalize
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.db.async.AsyncDBConnection
import dk.sdu.cloud.service.db.async.SQLTable
import dk.sdu.cloud.service.db.async.getField
import dk.sdu.cloud.service.db.async.sendPreparedStatement
import dk.sdu.cloud.service.db.async.text
import dk.sdu.cloud.service.db.async.timestamp

data class Metadata(
    val path: String,
    val type: String,
    val user: String?,
    val payload: JsonNode
)

object MetadataTable : SQLTable("metadata") {
    /**
     * The path to the file containing this metadata
     */
    val path = text("path", notNull = true)

    /**
     * The path that we are about to move to
     *
     * Before a file is moved this property will be updated with the new [path]. At start up services will check
     * all files which have this property set to a non-null value. It will then determine if the move had completed
     * or not.
     */
    val pathMovingTo = text("path_moving_to", notNull = false)

    /**
     * Timestamp containing the last modification of this row
     *
     * Note: This is not related to the last modification of the file.
     */
    val lastModified = timestamp("last_modified", notNull = true)

    /**
     * The username of the user owning this metadata
     *
     * This will be an empty string for file specific metadata.
     */
    val user = text("user", notNull = true)

    /**
     * The type of metadata
     */
    val type = text("type", notNull = true)

    /**
     * The data for this file
     */
    val data = text("data", notNull = true) // TODO JSONB
}

class MetadataDao {
    suspend fun findMetadata(
        session: AsyncDBConnection,
        path: String,
        user: String?,
        type: String?
    ): Metadata? {
        return session
            .sendPreparedStatement(
                {
                    setParameter("path", path.normalize())
                    setParameter("user", user)
                    setParameter("type", type)
                },
                """
                    select *
                    from metadata
                    where
                        path = ?path and
                        (?user is null or user = ?user) and
                        (?type is null or type = ?type)
                """
            )
            .rows
            .map { it.toMetadata() }
            .firstOrNull()
    }

    suspend fun updateMetadata(
        session: AsyncDBConnection,
        metadata: Metadata
    ) {
        session
            .sendPreparedStatement(
                {
                    setParameter("path", metadata.path.normalize())
                    setParameter("path_moving_to", null as String?)
                    setParameter("user", metadata.user ?: "")
                    setParameter("type", metadata.type)
                    setParameter("data", defaultMapper.writeValueAsString(metadata.payload))
                },
                """
                    insert into metadata
                    (path, path_moving_to, last_modified, user, type, data)
                    values
                    (?path, ?path_moving_to, now(), ?user, ?type, ?data)
                    on conflict update
                """
            )
        TODO()
    }

    suspend fun listMetadata(
        session: AsyncDBConnection,
        paths: List<String>,
        user: String?,
        type: String?
    ): Map<String, List<Metadata>> {
        return session
            .sendPreparedStatement(
                {
                    setParameter("paths", paths.map { it.normalize() })
                    setParameter("user", user)
                    setParameter("type", type)
                },
                """
                    select *
                    from metadata
                    where
                        path in ?paths and
                        (?user is null or user = ?user) and
                        (?type is null or type = ?type)
                """
            )
            .rows
            .map { it.toMetadata() }
            .groupBy { it.path }
    }

    /**
     * Removes one or more entries for a [user]/[path]
     *
     * If [type] == null then this will remove all entries for this file.
     *
     * If [user] == null then this metadata belongs to the file rather than a specific user.
     */
    suspend fun removeEntry(
        session: AsyncDBConnection,
        path: String,
        user: String?,
        type: String?
    ) {
        session
            .sendPreparedStatement(
                {
                    setParameter("path", path.normalize())
                    setParameter("user", user)
                    setParameter("type", type)
                },
                """
                    delete from metadata  
                    where
                        path = ?path and
                        (?user is null or user = ?user) and
                        (?type is null or type = ?type)
                """
            )
    }

    suspend fun handleFilesMoved(
        session: AsyncDBConnection,
        oldPath: String,
        newPath: String
    ) {
        // The first query will update all children
        session.sendPreparedStatement(
            {
                setParameter("newPath", newPath.normalize())
                setParameter("oldPathLike", "${newPath.normalize()}/%")

                // add one for the forward-slash and another for offsetting substr
                setParameter("startIdx", newPath.length + 2)
            },
            """
                update metadata
                set 
                    path = concat(?newPath, substr(path, ?startIdx))
                where path like ?oldPathLike 
            """
        )

        // The second will update just the root
        session.sendPreparedStatement(
            {
                setParameter("newPath", newPath.normalize())
                setParameter("oldPath", oldPath.normalize())
            },
            """
                update metadata 
                set
                    path = ?newPath
                where
                    path = ?oldPath
            """
        )
    }

    suspend fun handleFilesDeleted(
        session: AsyncDBConnection,
        paths: List<String>
    ) {
        session
            .sendPreparedStatement(
                {
                    setParameter("paths", paths.map { it.normalize() })
                },
                """
                    delete
                    from metadata p
                    where p.path in (
                        select p.path
                        from (select unnest(?paths as path) as t
                        where 
                            p.path = t.path or
                            p.path like (t.path || '/%')
                    )
                """
            )
    }

    private fun RowData.toMetadata(): Metadata {
        return Metadata(
            getField(MetadataTable.path),
            getField(MetadataTable.type),
            getField(MetadataTable.user).takeIf { it.isNotEmpty() },
            runCatching {
                defaultMapper.readTree(getField(MetadataTable.data))
            }.getOrNull() ?: run {
                log.warn(
                    "Unable to parse metadata for: " +
                            "${getField(MetadataTable.path)}, " +
                            "${getField(MetadataTable.type)}, " +
                            getField(MetadataTable.user)
                )

                defaultMapper.readTree("{}")
            }
        )
    }

    companion object : Loggable {
        override val log = logger()
    }
}
