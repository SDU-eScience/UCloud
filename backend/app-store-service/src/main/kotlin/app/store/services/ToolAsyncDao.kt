package dk.sdu.cloud.app.store.services

import com.fasterxml.jackson.module.kotlin.readValue
import com.github.jasync.sql.db.RowData
import dk.sdu.cloud.Role
import dk.sdu.cloud.SecurityPrincipal
import dk.sdu.cloud.app.store.api.NameAndVersion
import dk.sdu.cloud.app.store.api.NormalizedToolDescription
import dk.sdu.cloud.app.store.api.Tool
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.service.*
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.getField
import dk.sdu.cloud.service.db.async.insert
import dk.sdu.cloud.service.db.async.paginatedQuery
import dk.sdu.cloud.service.db.async.sendPreparedStatement
import dk.sdu.cloud.service.db.async.withSession
import io.ktor.http.HttpStatusCode
import org.joda.time.DateTimeZone
import org.joda.time.LocalDateTime
import java.util.*

class ToolAsyncDao {
    private val byNameAndVersionCache = Collections.synchronizedMap(HashMap<NameAndVersion, Pair<Tool, Long>>())

    suspend fun findAllByName(
        ctx: DBContext,
        user: SecurityPrincipal?,
        name: String,
        paging: NormalizedPaginationRequest
    ): Page<Tool> {
        return ctx.withSession { session ->
            session.paginatedQuery(
                paging,
                {
                    setParameter("name", name)
                },
                """
                    FROM tools
                    WHERE name = ?name
                """.trimIndent()
            ).mapItems { it.toTool() }
        }
    }

    suspend fun findByNameAndVersion(
        ctx: DBContext,
        user: SecurityPrincipal?,
        name: String,
        version: String
    ): Tool {
        val cacheKey = NameAndVersion(name, version)
        val (cached, expiry) = byNameAndVersionCache[cacheKey] ?: Pair(null, 0L)
        if (cached != null && expiry > Time.now()) return cached
        val result = ctx.withSession { session ->
            (internalByNameAndVersion(session, name, version)?.toTool() ?: throw ToolException.NotFound())
        }
        byNameAndVersionCache[cacheKey] = Pair(result, Time.now() + (1000L * 60 * 60))
        return result
    }

    suspend fun listLatestVersion(
        ctx: DBContext,
        user: SecurityPrincipal?,
        paging: NormalizedPaginationRequest
    ): Page<Tool> {
        val count = ctx.withSession { session ->
            session.sendPreparedStatement(
                {

                },
                """
                    SELECT COUNT(*)
                    FROM tools AS A
                    WHERE (A.created_at) in (
                        SELECT MAX(created_at)
                        FROM tools as B
                        WHERE A.name = B.name
                        GROUP BY name
                    )
                """.trimIndent()
            ).rows.singleOrNull()?.getLong(0)?.toInt() ?: 0
        }
        val items = ctx.withSession { session ->
            session.sendPreparedStatement(
                {
                    setParameter("limit", paging.itemsPerPage)
                    setParameter("offset", paging.offset)
                },
                """
                    SELECT *
                    FROM tools AS A
                    WHERE (A.created_at) in (
                        SELECT MAX(created_at)
                        FROM tools as B
                        WHERE A.name = B.name
                        GROUP BY name
                    )
                    ORDER BY A.name 
                    LIMIT ?limit
                    OFFSET ?offset
                """.trimIndent()
            ).rows.map { it.toTool() }
        }

        return Page(
            count,
            paging.itemsPerPage,
            paging.page,
            items
        )
    }

    suspend fun create(
        ctx: DBContext,
        user: SecurityPrincipal,
        description: NormalizedToolDescription,
        originalDocument: String
    ) {
        val existingOwner =
            ctx.withSession { session ->
                findOwner(session, description.info.name)
            }
        if (existingOwner != null && !canUserPerformWrite(existingOwner, user)) {
            throw ToolException.NotAllowed()
        }

        val existing =
            ctx.withSession { session ->
                internalByNameAndVersion(session, description.info.name, description.info.version)
            }
        if (existing != null) throw ToolException.AlreadyExists()

        ctx.withSession { session ->
            session.insert(ToolTable) {
                set(ToolTable.owner, user.username)
                set(ToolTable.createdAt, LocalDateTime(Time.now(), DateTimeZone.UTC))
                set(ToolTable.modifiedAt, LocalDateTime(Time.now(), DateTimeZone.UTC))
                set(ToolTable.tool, defaultMapper.writeValueAsString(description))
                set(ToolTable.originalDocument, originalDocument)
                set(ToolTable.idName, description.info.name)
                set(ToolTable.idVersion, description.info.version)
            }
        }
    }

    suspend fun updateDescription(
        ctx: DBContext,
        user: SecurityPrincipal,
        name: String,
        version: String,
        newDescription: String?,
        newAuthors: List<String>?
    ) {
        val existing =
            ctx.withSession { session ->
                internalByNameAndVersion(session, name, version) ?: throw ToolException.NotFound()
            }
        if (!canUserPerformWrite(existing.getField(ToolTable.owner), user)) throw ToolException.NotAllowed()

        if (newDescription != null) {
            ctx.withSession { session ->
                session.sendPreparedStatement(
                    {
                        setParameter("description", defaultMapper.writeValueAsString(newDescription))
                        setParameter("name", name)
                        setParameter("version", version)
                    },
                    """
                        UPDATE tools
                        SET tool = jsonb_set(tool, '{description}', ?description)
                        WHERE (name = ?name) AND (version = ?version) 
                    """.trimIndent()
                )
            }
        }
        if (!newAuthors.isNullOrEmpty()) {
            ctx.withSession { session ->
                session.sendPreparedStatement(
                    {
                        setParameter("authors", defaultMapper.writeValueAsString(newAuthors))
                        setParameter("name", name)
                        setParameter("version", version)
                    },
                    """
                        UPDATE tools
                        SET tool = jsonb_set(tool, '{authors}', ?authors)
                        WHERE (name = ?name) AND (version = ?version) 
                    """.trimIndent()
                )
            }
        }
        // We allow for this to be cached for some time. But this instance might as well clear the cache now.
        byNameAndVersionCache.remove(NameAndVersion(name, version))
    }

    suspend fun createLogo(ctx: DBContext, user: SecurityPrincipal, name: String, imageBytes: ByteArray) {
        val tool =
            ctx.withSession { session ->
                findOwner(session, name) ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
            }

        if (tool != user.username && user.role != Role.ADMIN) {
            throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)
        }

        val logo = ctx.withSession { session ->
            fetchLogo(session, name)
        }
        if (logo != null) {
            ctx.withSession{ session ->
                session.sendPreparedStatement(
                    {
                        setParameter("appname", name)
                        setParameter("bytes", imageBytes)
                    },
                    """
                        UPDATE tool_logos
                        SET data = ?bytes
                        WHERE (application = ?appname)
                    """.trimIndent()
                )
            }
        } else {
            ctx.withSession { session ->
                session.insert(ToolLogoTable) {
                    set(ToolLogoTable.application, name)
                    set(ToolLogoTable.data, imageBytes)
                }
            }
        }
    }

    suspend fun clearLogo(ctx: DBContext, user: SecurityPrincipal, name: String) {
        val application =
            ctx.withSession { session ->
                findOwner(session, name) ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
            }
        if (application != user.username && user.role != Role.ADMIN) {
            throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)
        }

        ctx.withSession { session ->
            session.sendPreparedStatement(
                {
                    setParameter("appname", name)
                },
                """
                    DELETE FROM tool_logos
                    WHERE application ?= ?appname
                """.trimIndent()
            )
        }
    }

    suspend fun fetchLogo(ctx: DBContext, name: String): ByteArray? {
        return ctx.withSession { session ->
            session.sendPreparedStatement(
                {
                    setParameter("appname", name)
                },
                """
                    SELECT data
                    FROM tool_logos
                    WHERE (application = ?appname)
                """.trimIndent()
            )
        }.rows.singleOrNull()?.getField(ToolLogoTable.data)

    }

    internal suspend fun internalByNameAndVersion(ctx: DBContext, name: String, version: String): RowData? {
        return ctx.withSession { session ->
            session.sendPreparedStatement(
                {
                    setParameter("name", name)
                    setParameter("version", version)
                },
                """
                    SELECT *
                    FROM tools
                    WHERE 
                        (name = ?name) and
                        (version = ?version)
                """.trimIndent()
            ).rows.singleOrNull()
        }
    }

    private fun canUserPerformWrite(owner: String, user: SecurityPrincipal): Boolean {
        if (user.role == Role.ADMIN) return true
        return owner == user.username
    }

    private suspend fun findOwner(ctx: DBContext, name: String): String? {
        return ctx.withSession { session ->
            session.sendPreparedStatement(
                {
                    setParameter("name", name)
                },
                """
                    SELECT * 
                    FROM tools
                    WHERE name = ?name
                """.trimIndent()
            ).rows.singleOrNull()?.getField(ToolTable.owner)
        }
    }
}

internal fun RowData.toTool(): Tool {
    val normalizedToolDesc = defaultMapper.readValue<NormalizedToolDescription>(getField(ToolTable.tool))

    return Tool(
        getField(ToolTable.owner),
        getField(ToolTable.createdAt).toDateTime(DateTimeZone.UTC).millis,
        getField(ToolTable.modifiedAt).toDateTime(DateTimeZone.UTC).millis,
        normalizedToolDesc
    )
}

sealed class ToolException(why: String, httpStatusCode: HttpStatusCode) : RPCException(why, httpStatusCode) {
    class NotFound : ToolException("Not found", HttpStatusCode.NotFound)
    class NotAllowed : ToolException("Not allowed", HttpStatusCode.Forbidden)
    class AlreadyExists : ToolException("Already exists", HttpStatusCode.Conflict)
}
