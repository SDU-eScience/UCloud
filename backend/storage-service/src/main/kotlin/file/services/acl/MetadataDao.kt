package dk.sdu.cloud.file.services.acl

import com.fasterxml.jackson.databind.JsonNode
import com.github.jasync.sql.db.RowData
import com.github.jasync.sql.db.postgresql.exceptions.GenericDatabaseException
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.file.api.normalize
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.db.async.AsyncDBConnection
import dk.sdu.cloud.service.db.async.SQLTable
import dk.sdu.cloud.service.db.async.getField
import dk.sdu.cloud.service.db.async.jsonb
import dk.sdu.cloud.service.db.async.sendPreparedStatement
import dk.sdu.cloud.service.db.async.text
import dk.sdu.cloud.service.db.async.timestamp
import io.ktor.http.HttpStatusCode

data class FileInMovement(val path: String, val pathMovingTo: String)

data class Metadata(
    val path: String,
    val type: String,
    val username: String?,
    val payload: JsonNode,
    val movingTo: String? = null
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
    val username = text("username", notNull = true)

    /**
     * The type of metadata
     */
    val type = text("type", notNull = true)

    /**
     * The data for this file
     */
    val data = jsonb("data", notNull = true)
}

class MetadataDao {
    suspend fun findMetadata(
        session: AsyncDBConnection,
        path: String,
        username: String?,
        type: String?
    ): Metadata? {
        return session
            .sendPreparedStatement(
                {
                    setParameter("path", path.normalize())
                    setParameter("username", username)
                    setParameter("type", type)
                },
                """
                    select *
                    from metadata
                    where
                        path = ?path and
                        (?username::text is null or username = ?username) and
                        (?type::text is null or type = ?type)
                """
            )
            .rows
            .map { it.toMetadata() }
            .firstOrNull()
    }

    suspend fun createMetadata(
        session: AsyncDBConnection,
        metadata: Metadata
    ) {
        try {
            session
                .sendPreparedStatement(
                    {
                        setParameter("path", metadata.path.normalize())
                        setParameter("path_moving_to", metadata.movingTo)
                        setParameter("username", metadata.username ?: "")
                        setParameter("type", metadata.type)
                        setParameter("data", defaultMapper.writeValueAsString(metadata.payload))
                    },
                    """
                    insert into metadata
                        (path, path_moving_to, last_modified, username, type, data)
                    values 
                        (?path, ?path_moving_to, now(), ?username, ?type, ?data)
                """
                )
        } catch (ex: GenericDatabaseException) {
            if (ex.errorMessage.fields['C'] == "23505") {
                throw RPCException("Already exists", HttpStatusCode.Conflict)
            } else {
                throw ex
            }
        }
    }

    suspend fun updateMetadata(
        session: AsyncDBConnection,
        metadata: Metadata
    ) {
        session
            .sendPreparedStatement(
                {
                    setParameter("path", metadata.path.normalize())
                    setParameter("path_moving_to", metadata.movingTo)
                    setParameter("username", metadata.username ?: "")
                    setParameter("type", metadata.type)
                    setParameter("data", defaultMapper.writeValueAsString(metadata.payload))
                },
                """
                    insert into metadata
                        (path, path_moving_to, last_modified, username, type, data)
                    values 
                        (?path, ?path_moving_to, now(), ?username, ?type, ?data)
                    
                    on conflict (path, type, username) do update set 
                        (data, last_modified, path_moving_to) = 
                        (excluded.data, excluded.last_modified, excluded.path_moving_to);
                """
            )
    }

    suspend fun listMetadata(
        session: AsyncDBConnection,
        paths: List<String>?,
        username: String?,
        type: String?
    ): Map<String, List<Metadata>> {
        return session
            .sendPreparedStatement(
                {
                    setParameter("paths", paths?.map { it.normalize() })
                    setParameter("username", username)
                    setParameter("type", type)
                },
                """
                    select *
                    from metadata
                    where
                        (?paths::text[] is null or path in (select unnest(?paths::text[]))) and
                        (?username::text is null or username = ?username) and
                        (?type::text is null or type = ?type)
                """
            )
            .rows
            .map { it.toMetadata() }
            .groupBy { it.path }
    }

    /**
     * Removes one or more entries for a [username]/[path]
     *
     * If [type] == null then this will remove all entries for this file.
     *
     * If [username] == null then this metadata belongs to the file rather than a specific username.
     */
    suspend fun removeEntry(
        session: AsyncDBConnection,
        path: String,
        username: String?,
        type: String?
    ) {
        log.debug("removeEntry $path $username $type")
        session
            .sendPreparedStatement(
                {
                    setParameter("path", path.normalize())
                    setParameter("username", username)
                    setParameter("type", type)
                },
                """
                    delete from metadata  
                    where
                        path = ?path and
                        (?username::text is null or username = ?username) and
                        (?type::text is null or type = ?type)
                """
            )
    }

    suspend fun writeFilesAreDeleting(
        session: AsyncDBConnection,
        paths: List<String>
    ) {
        val updatedRows = session
            .sendPreparedStatement(
                {
                    setParameter("paths", paths.map { it.normalize() })
                },
                """
                    update metadata
                    set
                        path_moving_to = '/deleted',
                        last_modified = now()
                    where
                        path in (select unnest(?paths::text[]))
                """
            )
            .rowsAffected

        if (updatedRows == 0L) {
            log.debug("No metadata found. Inserting special entry.")

            val payload = defaultMapper.readTree("{}")
            for (path in paths) {
                updateMetadata(session, Metadata(path, "moving", null, payload, "/deleted"))
            }
        } else {
            log.debug("Existing metadata was updated ($updatedRows)")
        }
    }

    suspend fun writeFileIsMoving(
        session: AsyncDBConnection,
        oldPath: String,
        newPath: String
    ) {
        val updatedRows = session
            .sendPreparedStatement(
                {
                    setParameter("newPath", newPath.normalize())
                    setParameter("oldPath", oldPath.normalize())
                },
                """
                    update metadata
                    set
                        path_moving_to = ?newPath,
                        last_modified = now()
                    where
                        path = ?oldPath
                """
            )
            .rowsAffected

        if (updatedRows == 0L) {
            log.debug("No metadata found. Inserting special entry.")
            updateMetadata(session, Metadata(oldPath, "moving", null, defaultMapper.readTree("{}"), newPath))
        } else {
            log.debug("Existing metadata was updated ($updatedRows)")
        }
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
                    path = concat(?newPath::text, substr(path, ?startIdx)),
                    path_moving_to = null,
                    last_modified = now()
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
                    path = ?newPath,
                    path_moving_to = null,
                    last_modified = now()
                where
                    path = ?oldPath
            """
        )
    }

    suspend fun cancelMovement(
        session: AsyncDBConnection,
        paths: List<String>
    ) {
        session.sendPreparedStatement(
            {
                setParameter("paths", paths)
            },
            """
                update metadata
                set 
                    path_moving_to = null,
                    last_modified = now()
                where
                    path in (select unnest(?paths::text[])) and
                    path_moving_to is not null
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
                        from (select unnest(?paths::text[]) as path) as t
                        where 
                            p.path = t.path or
                            p.path like (t.path || '/%')
                    )
                """
            )
    }

    suspend fun findUnmanagedMetadata(
        session: AsyncDBConnection
    ): List<FileInMovement> {
        return session
            .sendQuery(
                """
                    select distinct path, path_moving_to
                    from metadata
                    where
                        path_moving_to is not null and
                        last_modified <= (now() - interval '5 minutes')
                    limit 500
                """
            )
            .rows
            .map {
                FileInMovement(it["path"] as String, it["path_moving_to"] as String)
            }
    }

    suspend fun moveMetadata(session: AsyncDBConnection, oldPath: String, newPath: String) {
        session
            .sendPreparedStatement(
                {
                    setParameter("oldPath", oldPath.normalize())
                    setParameter("newPath", newPath.normalize())
                },
                """
                    update metadata m
                    set
                        path = path_moving_to,
                        path_moving_to = null,
                        last_modified = now()
                    where
                        path = ?oldPath and
                        path_moving_to = ?newPath and
                        (path_moving_to, type, username) not in 
                            (select path, type, username from metadata m2 where m.path_moving_to = m2.path)
                """
            )

        session
            .sendPreparedStatement(
                {
                    setParameter("oldPath", oldPath.normalize())
                },
                """
                    delete from metadata where path = ?oldPath
                """
            )
    }

    suspend fun deleteStaleMovingAttributes(session: AsyncDBConnection) {
        session.sendQuery(
            """
                delete from metadata
                where
                    type = 'moving' and
                    path_moving_to is null
            """
        )
    }

    suspend fun deleteByPrefix(
        session: AsyncDBConnection,
        path: String,
        includeFile: Boolean = true,
        type: String? = null
    ) {
        session.sendPreparedStatement(
            {
                setParameter("path", path.normalize())
                setParameter("includeFile", includeFile)
                setParameter("type", type)
            },
            """
                delete from metadata
                where
                    (
                        path like (?path || '/%') or
                        (?includeFile and path = ?path)
                    ) and
                    (
                        ?type::text is null or
                        type = ?type
                    )
            """
        )
    }

    suspend fun findByPrefix(
        session: AsyncDBConnection,
        pathPrefix: String,
        type: String?,
        username: String?
    ): List<Metadata> {
        return session
            .sendPreparedStatement(
                {
                    setParameter("path", pathPrefix.normalize())
                    setParameter("type", type)
                    setParameter("username", username)
                },
                """
                    select *
                    from metadata
                    where
                        (path like (?path || '/%') or path = ?path) and
                        (?type::text is null or type = ?type) and
                        (?username::text is null or username = ?username)
                """
            )
            .rows
            .map { it.toMetadata() }
    }

    private fun RowData.toMetadata(): Metadata {
        return Metadata(
            getField(MetadataTable.path),
            getField(MetadataTable.type),
            getField(MetadataTable.username).takeIf { it.isNotEmpty() },
            runCatching {
                defaultMapper.readTree(getField(MetadataTable.data))
            }.getOrNull() ?: run {
                log.warn(
                    "Unable to parse metadata for: " +
                            "${getField(MetadataTable.path)}, " +
                            "${getField(MetadataTable.type)}, " +
                            getField(MetadataTable.username)
                )

                defaultMapper.readTree("{}")
            }
        )
    }

    companion object : Loggable {
        override val log = logger()
    }
}
