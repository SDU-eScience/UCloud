package dk.sdu.cloud.app.store.services

import dk.sdu.cloud.Actor
import dk.sdu.cloud.ActorAndProject
import dk.sdu.cloud.PaginationRequest
import dk.sdu.cloud.SecurityPrincipal
import dk.sdu.cloud.service.NormalizedPaginationRequestV2
import dk.sdu.cloud.service.PageV2
import dk.sdu.cloud.service.db.async.*

class ApplicationLogoAsyncDao(
    private val appStoreAsyncDao: AppStoreAsyncDao
) {

    suspend fun createLogo(ctx: DBContext, actorAndProject: ActorAndProject, name: String, imageBytes: ByteArray) {
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
                        SET data = :bytes
                        WHERE application = :appname
                    """
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
        ctx.withSession { session ->
            session.sendPreparedStatement(
                {
                    setParameter("appname", name)
                },
                """
                    DELETE FROM application_logos
                    WHERE application = :appname
                """
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
                    WHERE application = :appname
                """
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
                    WHERE application = :toolname
                """
            )
        }.rows.singleOrNull()?.getAs<ByteArray>("data")
    }

    suspend fun browseAll(ctx: DBContext, request: NormalizedPaginationRequestV2): PageV2<Pair<String, ByteArray>> {
        return ctx.paginateV2(
            Actor.System,
            request,
            create = { session ->
                session.sendPreparedStatement(
                    {},

                    """
                        declare c cursor for
                            select * from application_logos
                    """
                )
            },
            mapper = { session, rows ->
                rows.map {
                    Pair(it.getField(ApplicationLogosTable.application), it.getField(ApplicationLogosTable.data))
                }
            }
        )
    }
}
