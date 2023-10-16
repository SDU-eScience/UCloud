package dk.sdu.cloud.app.store.services

import com.github.jasync.sql.db.RowData
import dk.sdu.cloud.*
import dk.sdu.cloud.app.store.api.NameAndVersion
import dk.sdu.cloud.app.store.api.NormalizedToolDescription
import dk.sdu.cloud.app.store.api.Tool
import dk.sdu.cloud.app.store.api.ToolBackend
import dk.sdu.cloud.auth.api.AuthProviders
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.service.*
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.NormalizedPaginationRequestV2
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.PageV2
import dk.sdu.cloud.service.db.async.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.time.LocalDateTime
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
                    WHERE name = :name
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
                    LIMIT :limit
                    OFFSET :offset
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
        user: ActorAndProject,
        description: NormalizedToolDescription,
        originalDocument: String
    ) {
        val username = user.actor.safeUsername()
        val isProvider = username.startsWith(AuthProviders.PROVIDER_PREFIX)
        val providerName = username.removePrefix(AuthProviders.PROVIDER_PREFIX)

        if (isProvider) {
            if (description.backend != ToolBackend.NATIVE) throw ToolException.NotAllowed()
            if (description.supportedProviders != listOf(providerName)) throw ToolException.NotAllowed()
        }

        val existing =
            ctx.withSession { session ->
                internalByNameAndVersion(session, description.info.name, description.info.version)
            }
        if (existing != null) throw ToolException.AlreadyExists()

        ctx.withSession { session ->
            session.sendPreparedStatement(
                {
                    setParameter("owner", username)
                    setParameter("created_at", LocalDateTime.now())
                    setParameter("modified_at", LocalDateTime.now())
                    setParameter("modifiedAt", LocalDateTime.now())
                    setParameter("tool", defaultMapper.encodeToString(description))
                    setParameter("original_document", originalDocument)
                    setParameter("name", description.info.name)
                    setParameter("version", description.info.version)
                },
                """
                    insert into app_store.tools
                        (name, version, created_at, modified_at, original_document, owner, tool) 
                    values 
                        (:name, :version, :created_at, :modified_at, :original_document, :owner, :tool)
                """
            )
        }
    }

    suspend fun createLogo(ctx: DBContext, actorAndProject: ActorAndProject, name: String, imageBytes: ByteArray) {
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
                        SET data = :bytes
                        WHERE (application = :appname)
                    """.trimIndent()
                )
            }
        } else {
            ctx.withSession { session ->
                session.sendPreparedStatement(
                    {
                        setParameter("application", name)
                        setParameter("data", imageBytes)
                    },
                    """
                        insert into app_store.tool_logos (application, data) values (:application, :data)
                    """
                )
            }
        }
    }

    suspend fun clearLogo(ctx: DBContext, actorAndProject: ActorAndProject, name: String) {
        ctx.withSession { session ->
            session.sendPreparedStatement(
                {
                    setParameter("appname", name)
                },
                """
                    DELETE FROM tool_logos
                    WHERE application = :appname
                """
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
                    WHERE (application = :appname)
                """.trimIndent()
            )
        }.rows.singleOrNull()?.getAs<ByteArray>("data")

    }

    suspend fun browseAllLogos(ctx: DBContext, request: NormalizedPaginationRequestV2): PageV2<Pair<String, ByteArray>> {
        return ctx.paginateV2(
            Actor.System,
            request,
            create = { session ->
                session.sendPreparedStatement(
                    {},

                    """
                        declare c cursor for
                            select * from tool_logos
                    """
                )
            },
            mapper = { session, rows ->
                rows.map {
                    Pair(it.getString("application")!!, it.getAs("data"))
                }
            }
        )
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
                        (name = :name) and
                        (version = :version)
                """.trimIndent()
            ).rows.singleOrNull()
        }
    }
}

internal fun RowData.toTool(): Tool {
    val normalizedToolDesc = defaultMapper.decodeFromString<NormalizedToolDescription>(this.getString("tool")!!)

    return Tool(
        this.getString("owner")!!,
        this.getDate("created_at")!!.toTimestamp(),
        this.getDate("modified_at")!!.toTimestamp(),
        normalizedToolDesc
    )
}

sealed class ToolException(why: String, httpStatusCode: HttpStatusCode) : RPCException(why, httpStatusCode) {
    class NotFound : ToolException("Not found", HttpStatusCode.NotFound)
    class NotAllowed : ToolException("Not allowed", HttpStatusCode.Forbidden)
    class AlreadyExists : ToolException("Already exists", HttpStatusCode.Conflict)
}
