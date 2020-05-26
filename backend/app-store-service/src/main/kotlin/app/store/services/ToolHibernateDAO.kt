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
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.db.HibernateSession
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.getField
import dk.sdu.cloud.service.db.async.paginatedQuery
import dk.sdu.cloud.service.db.async.sendPreparedStatement
import dk.sdu.cloud.service.db.async.withSession
import dk.sdu.cloud.service.db.criteria
import dk.sdu.cloud.service.db.get
import dk.sdu.cloud.service.db.paginatedCriteria
import dk.sdu.cloud.service.db.paginatedList
import dk.sdu.cloud.service.db.typedQuery
import dk.sdu.cloud.service.mapItems
import dk.sdu.cloud.service.offset
import io.ktor.http.HttpStatusCode
import org.joda.time.DateTimeZone
import java.util.*

class ToolHibernateDAO : ToolDAO {
    private val byNameAndVersionCache = Collections.synchronizedMap(HashMap<NameAndVersion, Pair<Tool, Long>>())

    override suspend fun findAllByName(
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
                    SELECT * 
                    FROM tools
                    WHERE name = ?name
                """.trimIndent()
            ).mapItems { it.toTool() }
        }
    }

    override suspend fun findByNameAndVersion(
        ctx: DBContext,
        user: SecurityPrincipal?,
        name: String,
        version: String
    ): Tool {
        val cacheKey = NameAndVersion(name, version)
        val (cached, expiry) = byNameAndVersionCache[cacheKey] ?: Pair(null, 0L)
        if (cached != null && expiry > System.currentTimeMillis()) return cached
        val result = ctx.withSession { session ->
            (internalByNameAndVersion(session, name, version)?.toTool() ?: throw ToolException.NotFound())
        }
        byNameAndVersionCache[cacheKey] = Pair(result, System.currentTimeMillis() + (1000L * 60 * 60))
        return result
    }

    override suspend fun listLatestVersion(
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

    override suspend fun create(
        ctx: DBContext,
        user: SecurityPrincipal,
        description: NormalizedToolDescription,
        originalDocument: String
    ) {
        val existingOwner = findOwner(session, description.info.name)
        if (existingOwner != null && !canUserPerformWrite(existingOwner, user)) {
            throw ToolException.NotAllowed()
        }

        val existing = internalByNameAndVersion(session, description.info.name, description.info.version)
        if (existing != null) throw ToolException.AlreadyExists()

        session.save(
            ToolEntity(
                user.username,
                Date(),
                Date(),
                description,
                originalDocument,
                EmbeddedNameAndVersion(description.info.name, description.info.version)
            )
        )
    }

    override suspend fun updateDescription(
        ctx: DBContext,
        user: SecurityPrincipal,
        name: String,
        version: String,
        newDescription: String?,
        newAuthors: List<String>?
    ) {
        val existing = internalByNameAndVersion(session, name, version) ?: throw ToolException.NotFound()
        if (!canUserPerformWrite(existing.owner, user)) throw ToolException.NotAllowed()

        val newTool = existing.tool.let {
            if (newDescription != null) {
                it.copy(description = newDescription)
            } else {
                it
            }
        }.let {
            if (newAuthors != null) {
                it.copy(authors = newAuthors)
            } else {
                it
            }
        }

        existing.tool = newTool

        // We allow for this to be cached for some time. But this instance might as well clear the cache now.
        byNameAndVersionCache.remove(NameAndVersion(name, version))

        session.update(existing)
    }

    override suspend fun createLogo(ctx: DBContext, user: SecurityPrincipal, name: String, imageBytes: ByteArray) {
        val tool =
            findOwner(session, name) ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound)

        if (tool != user.username && user.role != Role.ADMIN) {
            throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)
        }

        session.saveOrUpdate(
            ToolLogoEntity(name, imageBytes)
        )
    }

    override suspend fun clearLogo(ctx: DBContext, user: SecurityPrincipal, name: String) {
        val application =
            findOwner(session, name) ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound)

        if (application != user.username && user.role != Role.ADMIN) {
            throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)
        }

        session.delete(ToolLogoEntity[session, name] ?: return)
    }

    override suspend fun fetchLogo(ctx: DBContext, name: String): ByteArray? {
        return ToolLogoEntity[session, name]?.data
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

    private fun findOwner(ctx: DBContext, name: String): String? {
        return ctx.withSession { session ->
            session.sendPreparedStatement(
                {
                },
                """
                    SELECT * 
                    FROM 
                """.trimIndent()
            )
        }
                entity[ToolEntity::id][EmbeddedNameAndVersion::name] equal name
            }.apply {
                maxResults = 1
            }.uniqueResult()?.owner
        }
    }
}

internal fun RowData.toTool(): Tool {
    val normalizedToolDesc = defaultMapper.readValue<NormalizedToolDescription>(getField(ToolTable.tool))

    return Tool(
        getField(ToolTable.owner),
        getField(ToolTable.createdAt).toDateTime(DateTimeZone.UTC).millis,
        getField(ToolTable.modifiedAt).toDateTime(DateTimeZone.UTC).millis, ,
        normalizedToolDesc
    )
}

sealed class ToolException(why: String, httpStatusCode: HttpStatusCode) : RPCException(why, httpStatusCode) {
    class NotFound : ToolException("Not found", HttpStatusCode.NotFound)
    class NotAllowed : ToolException("Not allowed", HttpStatusCode.Forbidden)
    class AlreadyExists : ToolException("Already exists", HttpStatusCode.Conflict)
}
