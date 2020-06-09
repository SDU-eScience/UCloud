package app.store.services

import com.github.jasync.sql.db.RowData
import dk.sdu.cloud.SecurityPrincipal
import dk.sdu.cloud.app.store.services.TagTable
import dk.sdu.cloud.app.store.services.TagsDAO
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.allocateId
import dk.sdu.cloud.service.db.async.getField
import dk.sdu.cloud.service.db.async.insert
import dk.sdu.cloud.service.db.async.sendPreparedStatement
import dk.sdu.cloud.service.db.async.withSession
import io.ktor.http.HttpStatusCode

class ApplicationTagsAsyncDAO() : TagsDAO {

    override suspend fun createTags(
        ctx: DBContext,
        user: SecurityPrincipal,
        applicationName: String,
        tags: List<String>
    ) {
        val owner = ctx.withSession { session ->
            findOwnerOfApplication(session, applicationName)
        } ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound)

        if (!canUserPerformWriteOperation(owner, user)) {
            throw RPCException.fromStatusCode(HttpStatusCode.Forbidden, "Not owner of application")
        }
        tags.forEach { tag ->
            val existing = ctx.withSession { session -> findTag(session, applicationName, tag) }

            if (existing != null) {
                return@forEach
            }

            ctx.withSession { session ->
                val id = session.allocateId()
                session.insert(TagTable) {
                    set(TagTable.id, id)
                    set(TagTable.applicationName, applicationName)
                    set(TagTable.tag, tag)
                }
            }
        }
    }

    override suspend fun deleteTags(
        ctx: DBContext,
        user: SecurityPrincipal,
        applicationName: String,
        tags: List<String>
    ) {
        val owner = ctx.withSession { session ->
            findOwnerOfApplication(session, applicationName)
        } ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound)

        if (!canUserPerformWriteOperation(owner, user)) {
            throw RPCException.fromStatusCode(HttpStatusCode.Forbidden, "Not owner of application")
        }

        ctx.withSession { session ->
            tags.forEach { tag ->
                val existing = findTag(
                    session,
                    applicationName,
                    tag
                ) ?: return@forEach

                val id = existing.getField(TagTable.id)
                session.sendPreparedStatement(
                    {
                        setParameter("id", id)
                    },
                    """
                        DELETE FROM tags
                        WHERE id = ?id
                    """.trimIndent()
                )
            }
        }
    }

    suspend fun findTag(
        ctx: DBContext,
        appName: String,
        tag: String
    ): RowData? {
        return ctx.withSession { session ->
            session.sendPreparedStatement(
                {
                    setParameter("tag", tag)
                    setParameter("appname", appName)
                },
                """
                    SELECT * 
                    FROM tags
                    WHERE (tag = ?tag) AND (application_name = ?appname)
                """.trimIndent()
            ).rows.singleOrNull()
        }
    }

    override suspend fun findTagsForApp(
        ctx: DBContext,
        applicationName: String
    ): List<RowData> {
        return ctx.withSession { session ->
            session.sendPreparedStatement(
                {
                    setParameter("appname", applicationName)
                },
                """
                    SELECT * 
                    FROM tags
                    WHERE application_name = ?appname
                """.trimIndent()
            ).rows.toList()
        }
    }
}
