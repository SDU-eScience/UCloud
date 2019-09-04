package dk.sdu.cloud.app.store.services

import dk.sdu.cloud.Role
import dk.sdu.cloud.SecurityPrincipal
import dk.sdu.cloud.app.store.api.NameAndVersion
import dk.sdu.cloud.app.store.api.NormalizedToolDescription
import dk.sdu.cloud.app.store.api.Tool
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.db.HibernateSession
import dk.sdu.cloud.service.db.criteria
import dk.sdu.cloud.service.db.get
import dk.sdu.cloud.service.db.paginatedCriteria
import dk.sdu.cloud.service.db.paginatedList
import dk.sdu.cloud.service.db.typedQuery
import dk.sdu.cloud.service.mapItems
import io.ktor.http.HttpStatusCode
import java.util.*

class ToolHibernateDAO : ToolDAO<HibernateSession> {
    private val byNameAndVersionCache = Collections.synchronizedMap(HashMap<NameAndVersion, Pair<Tool, Long>>())

    override fun findAllByName(
        session: HibernateSession,
        user: SecurityPrincipal?,
        name: String,
        paging: NormalizedPaginationRequest
    ): Page<Tool> {
        return session.paginatedCriteria<ToolEntity>(paging) {
            entity[ToolEntity::id][EmbeddedNameAndVersion::name] equal name
        }.mapItems { it.toModel() }
    }

    override fun findByNameAndVersion(
        session: HibernateSession,
        user: SecurityPrincipal?,
        name: String,
        version: String
    ): Tool {
        val cacheKey = NameAndVersion(name, version)
        val (cached, expiry) = byNameAndVersionCache[cacheKey] ?: Pair(null, 0L)
        if (cached != null && expiry > System.currentTimeMillis()) return cached

        val result = (internalByNameAndVersion(session, name, version)?.toModel() ?: throw ToolException.NotFound())
        byNameAndVersionCache[cacheKey] = Pair(result, System.currentTimeMillis() + (1000L * 60 * 60))
        return result
    }

    override fun listLatestVersion(
        session: HibernateSession,
        user: SecurityPrincipal?,
        paging: NormalizedPaginationRequest
    ): Page<Tool> {
        val count = session.typedQuery<Long>(
            """
            select count (A.id.name)
            from ToolEntity as A where (A.createdAt) in (
                select max(createdAt)
                from ToolEntity as B
                where A.id.name = B.id.name
                group by id.name
            )
        """.trimIndent()
        ).uniqueResult().toInt()

        val items = session.typedQuery<ToolEntity>(
            """
            from ToolEntity as A where (A.createdAt) in (
                select max(createdAt)
                from ToolEntity as B
                where A.id.name = B.id.name
                group by id.name
            )
            order by A.id.name
        """.trimIndent()
        ).paginatedList(paging).map { it.toModel() }

        return Page(
            count,
            paging.itemsPerPage,
            paging.page,
            items
        )
    }

    override fun create(
        session: HibernateSession,
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

    override fun updateDescription(
        session: HibernateSession,
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

    override fun createLogo(session: HibernateSession, user: SecurityPrincipal, name: String, imageBytes: ByteArray) {
        val tool =
            findOwner(session, name) ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound)

        if (tool != user.username && user.role != Role.ADMIN) {
            throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)
        }

        session.saveOrUpdate(
            ToolLogoEntity(name, imageBytes)
        )
    }

    override fun fetchLogo(session: HibernateSession, name: String): ByteArray? {
        return ToolLogoEntity[session, name]?.data
    }

    internal fun internalByNameAndVersion(session: HibernateSession, name: String, version: String): ToolEntity? {
        return session
            .criteria<ToolEntity> {
                (entity[ToolEntity::id][EmbeddedNameAndVersion::name] equal name) and
                        (entity[ToolEntity::id][EmbeddedNameAndVersion::version] equal version)
            }
            .uniqueResult()
    }

    private fun canUserPerformWrite(owner: String, user: SecurityPrincipal): Boolean {
        if (user.role == Role.ADMIN) return true
        return owner == user.username
    }

    private fun findOwner(session: HibernateSession, name: String): String? {
        return session.criteria<ToolEntity> {
            entity[ToolEntity::id][EmbeddedNameAndVersion::name] equal name
        }.apply {
            maxResults = 1
        }.uniqueResult()?.owner
    }
}

internal fun ToolEntity.toModel(): Tool = Tool(owner, createdAt.time, modifiedAt.time, tool)

sealed class ToolException(why: String, httpStatusCode: HttpStatusCode) : RPCException(why, httpStatusCode) {
    class NotFound : ToolException("Not found", HttpStatusCode.NotFound)
    class NotAllowed : ToolException("Not allowed", HttpStatusCode.Forbidden)
    class AlreadyExists : ToolException("Already exists", HttpStatusCode.Conflict)
}
