package dk.sdu.cloud.app.store.services

import dk.sdu.cloud.Actor
import dk.sdu.cloud.ActorAndProject
import dk.sdu.cloud.PaginationRequest
import dk.sdu.cloud.SecurityPrincipal
import dk.sdu.cloud.service.NormalizedPaginationRequestV2
import dk.sdu.cloud.service.PageV2
import dk.sdu.cloud.service.db.async.*

class ApplicationLogoAsyncDao(
    private val appStoreService: AppStoreService
) {

    suspend fun createLogo(ctx: DBContext, actorAndProject: ActorAndProject, name: String, imageBytes: ByteArray) {
        val exists = fetchLogo(ctx, actorAndProject, name)
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
                session.sendPreparedStatement(
                    {
                        setParameter("application", name)
                        setParameter("data", imageBytes)
                    },
                    """
                        insert into app_store.application_logos (application, data) VALUES (:application, :data)
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
                    DELETE FROM application_logos
                    WHERE application = :appname
                """
            )
        }
    }

    suspend fun fetchLogo(ctx: DBContext, actorAndProject: ActorAndProject, name: String): ByteArray? {
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
            ).rows.singleOrNull()?.getAs<ByteArray>("data")
        }
        if (logoFromApp != null) return logoFromApp
        val app = appStoreService.findByName(
            actorAndProject,
            name,
            PaginationRequest().normalize(),
        ).items.firstOrNull() ?: return null

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
            mapper = { _, rows ->
                rows.map {
                    Pair(it.getString("application")!!, it.getAs("data"))
                }
            }
        )
    }
}
