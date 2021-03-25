package dk.sdu.cloud.app.store.services

import com.github.jasync.sql.db.RowData
import dk.sdu.cloud.SecurityPrincipal
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.allocateId
import dk.sdu.cloud.service.db.async.getField
import dk.sdu.cloud.service.db.async.insert
import dk.sdu.cloud.service.db.async.sendPreparedStatement
import dk.sdu.cloud.service.db.async.withSession
import io.ktor.http.HttpStatusCode

class ApplicationTagsAsyncDao() {

    suspend fun createTags(
        ctx: DBContext,
        user: SecurityPrincipal,
        applicationName: String,
        tags: List<String>
    ) {
        ctx.withSession { session ->
            val owner = findOwnerOfApplication(session, applicationName) ?: throw RPCException.fromStatusCode(
                HttpStatusCode.NotFound
            )

            if (!canUserPerformWriteOperation(owner, user)) {
                throw RPCException.fromStatusCode(HttpStatusCode.Forbidden, "Not owner of application")
            }
            tags.forEach { tag ->
                val existing = ctx.withSession { session -> findTag(session, applicationName, tag) }

                if (existing != null) {
                    return@forEach
                }
                val id = session.allocateId()
                session.insert(TagTable) {
                    set(TagTable.id, id)
                    set(TagTable.applicationName, applicationName)
                    set(TagTable.tag, tag)
                }
            }
        }
    }

    suspend fun deleteTags(
        ctx: DBContext,
        user: SecurityPrincipal,
        applicationName: String,
        tags: List<String>
    ) {
        ctx.withSession { session ->
            val owner = findOwnerOfApplication(session, applicationName) ?: throw RPCException.fromStatusCode(
                HttpStatusCode.NotFound
            )

            if (!canUserPerformWriteOperation(owner, user)) {
                throw RPCException.fromStatusCode(HttpStatusCode.Forbidden, "Not owner of application")
            }

            tags.forEach { tag ->
                val existing = findTag(
                    session,
                    applicationName,
                    tag
                ) ?: return@forEach

                val id = existing.getField(TagTable.id)
                session
                    .sendPreparedStatement(
                        {
                            setParameter("id", id)
                        },
                        """
                            DELETE FROM application_tags
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
            session
                .sendPreparedStatement(
                    {
                        setParameter("tag", tag)
                        setParameter("appname", appName)
                    },
                    """
                        SELECT * 
                        FROM application_tags
                        WHERE (tag = ?tag) AND (application_name = ?appname)
                    """.trimIndent()
                )
                .rows
                .singleOrNull()
        }
    }

    suspend fun findTagsForApp(
        ctx: DBContext,
        applicationName: String
    ): List<RowData> {
        return ctx.withSession { session ->
            session
                .sendPreparedStatement(
                    {
                        setParameter("appname", applicationName)
                    },
                    """
                        SELECT * 
                        FROM application_tags
                        WHERE application_name = ?appname
                    """.trimIndent()
                )
                .rows
        }
    }
}
