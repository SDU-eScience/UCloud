package dk.sdu.cloud.app.services

import dk.sdu.cloud.app.api.NewNormalizedToolDecription
import dk.sdu.cloud.app.api.NewTool
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.db.*
import dk.sdu.cloud.service.mapItems
import java.util.*

class ToolHibernateDAO : ToolDAO2<HibernateSession> {
    override fun findAllByName(
        session: HibernateSession,
        user: String?,
        name: String,
        paging: NormalizedPaginationRequest
    ): Page<NewTool> {
        return session.paginatedCriteria<ToolEntity>(paging) {
            entity[ToolEntity::id][EmbeddedNameAndVersion::name] equal name
        }.mapItems { it.toModel() }
    }

    override fun findByNameAndVersion(
        session: HibernateSession,
        user: String?,
        name: String,
        version: String
    ): NewTool {
        return internalByNameAndVersion(session, name, version)?.toModel() ?: throw ToolException.NotFound()
    }

    override fun listLatestVersion(
        session: HibernateSession,
        user: String?,
        paging: NormalizedPaginationRequest
    ): Page<NewTool> {
        //language=HQL
        val count = session.typedQuery<Long>(
            """
            select count (id.name)
            from ToolEntity where (id.name, createdAt) in (
                select id.name, max(createdAt)
                from ToolEntity
                group by id.name
            )
        """.trimIndent()
        ).uniqueResult().toInt()

        //language=HQL
        val items = session.typedQuery<ToolEntity>(
            """
            from ToolEntity where (id.name, createdAt) in (
                select id.name, max(createdAt)
                from ToolEntity
                group by id.name
            )
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
        user: String,
        description: NewNormalizedToolDecription,
        originalDocument: String
    ) {
        val existingOwner = findOwner(session, description.info.name)
        if (existingOwner != null && existingOwner != user) {
            throw ToolException.NotAllowed()
        }

        val existing = internalByNameAndVersion(session, description.info.name, description.info.version)
        if (existing != null) throw ToolException.AlreadyExists()

        session.save(
            ToolEntity(
                user,
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
        user: String,
        name: String,
        version: String,
        newDescription: String?,
        newAuthors: List<String>?
    ) {
        val existing = internalByNameAndVersion(session, name, version) ?: throw ToolException.NotFound()
        if (existing.owner != user) throw ToolException.NotAllowed()

        val newTool = existing.tool.let {
            if (newDescription != null) it.copy(description = newDescription)
            else it
        }.let {
            if (newAuthors != null) it.copy(authors = newAuthors)
            else it
        }

        existing.tool = newTool
        session.update(existing)
    }

    internal fun internalByNameAndVersion(session: HibernateSession, name: String, version: String): ToolEntity? {
        return session
            .criteria<ToolEntity> {
                (entity[ToolEntity::id][EmbeddedNameAndVersion::name] equal name) and
                        (entity[ToolEntity::id][EmbeddedNameAndVersion::version] equal version)
            }
            .uniqueResult()
    }

    private fun findOwner(session: HibernateSession, name: String): String? {
        return session.criteria<ToolEntity> {
            entity[ToolEntity::id][EmbeddedNameAndVersion::name] equal name
        }.apply {
            maxResults = 1
        }.uniqueResult()?.owner
    }
}

internal fun ToolEntity.toModel(): NewTool = NewTool(owner, createdAt.time, modifiedAt.time, tool)

sealed class ToolException(why: String) : RuntimeException(why) {
    class NotFound : ToolException("Not found")
    class NotAllowed : ToolException("Not allowed")
    class AlreadyExists : ToolException("Already exists")
}
