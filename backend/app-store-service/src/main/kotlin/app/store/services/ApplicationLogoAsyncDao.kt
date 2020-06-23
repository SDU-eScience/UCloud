package dk.sdu.cloud.app.store.services

import dk.sdu.cloud.Role
import dk.sdu.cloud.SecurityPrincipal
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.service.PaginationRequest
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.getField
import dk.sdu.cloud.service.db.async.insert
import dk.sdu.cloud.service.db.async.sendPreparedStatement
import dk.sdu.cloud.service.db.async.withSession
import io.ktor.http.HttpStatusCode

class ApplicationLogoAsyncDao(
    private val appStoreAsyncDao: AppStoreAsyncDao
) {

    suspend fun createLogo(ctx: DBContext, user: SecurityPrincipal, name: String, imageBytes: ByteArray) {
        val applicationOwner = ctx.withSession { session ->
            findOwnerOfApplication(session, name) ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
        }

        if (applicationOwner != user.username && user.role != Role.ADMIN) {
            throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)
        }

        val exists = fetchLogo(ctx, name)
        if (exists != null) {
            ctx.withSession { session ->
                session.sendPreparedStatement(
                    {
                        setParameter("bytes", imageBytes)
                        setParameter("appname", name)
                    },
                    """
                        UPDATE application_logos
                        SET data = ?bytes
                        WHERE application = ?appname
                    """.trimIndent()
                )
            }
        } else {
            ctx.withSession { session ->
                session.insert(ApplicationLogosTable) {
                    set(ApplicationLogosTable.application, name)
                    set(ApplicationLogosTable.data, imageBytes)
                }
            }
        }
    }

    suspend fun clearLogo(ctx: DBContext, user: SecurityPrincipal, name: String) {
        val application =
            ctx.withSession { session ->
                findOwnerOfApplication(session, name) ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
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
                    DELETE FROM application_logos
                    WHERE application = ?appname
                """.trimIndent()
            )
        }
    }

    suspend fun fetchLogo(ctx: DBContext, name: String): ByteArray? {
        val logoFromApp = ctx.withSession { session ->
            session.sendPreparedStatement(
                {
                    setParameter("appname", name)
                },
                """
                    SELECT *
                    FROM application_logos
                    WHERE application = ?appname
                """.trimIndent()
            ).rows.singleOrNull()?.getField(ApplicationLogosTable.data)
        }
        if (logoFromApp != null) return logoFromApp
        val app = ctx.withSession { session ->
            internalFindAllByName(
                session,
                null,
                null,
                emptyList(),
                name,
                PaginationRequest().normalize(),
                appStoreAsyncDao
            ).items.firstOrNull()
        } ?: return null
        val toolName = app.invocation.tool.name
        return ctx.withSession { session ->
            session.sendPreparedStatement(
                {
                    setParameter("toolname", toolName)
                },
                """
                    SELECT *
                    FROM tool_logos
                    WHERE application = ?toolname
                """.trimIndent()
            )
        }.rows.singleOrNull()?.getField(ToolLogoTable.data)
    }
}
