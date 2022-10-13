package dk.sdu.cloud.app.store.services

import com.github.jasync.sql.db.RowData
import dk.sdu.cloud.SecurityPrincipal
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.allocateId
import dk.sdu.cloud.service.db.async.getField
import dk.sdu.cloud.service.db.async.insert
import dk.sdu.cloud.service.db.async.sendPreparedStatement
import dk.sdu.cloud.service.db.async.withSession

class ApplicationTagsAsyncDao() {
    suspend fun createTags(
        ctx: DBContext,
        user: SecurityPrincipal,
        applicationName: String,
        tags: List<String>
    ) {
        ctx.withSession { session ->
            findOwnerOfApplication(session, applicationName) ?: throw RPCException.fromStatusCode(
                HttpStatusCode.NotFound
            )

            session.sendPreparedStatement(
                {
                    setParameter("tags", tags)
                },
                """
                    insert into app_store.tags (tag)
                        select unnest(:tags::text[])
                """
            )

            // TODO(Brian): Do we even need this ID?
            val id = session.allocateId()
            session.sendPreparedStatement(
                {
                    setParameter("id", id)
                    setParameter("application_name", applicationName)
                    setParameter("tags", tags)
                },
                """
                    insert into app_store.application_tags (id, application_name, tag_id)
                        select :id, :application_name, id
                        from app_store.tags where
                            lower(tag) in (select lower(unnest(:tags::text[])))
                        on conflict do nothing 
                """
            )
        }
    }

    suspend fun deleteTags(
        ctx: DBContext,
        user: SecurityPrincipal,
        applicationName: String,
        tags: List<String>
    ) {
        ctx.withSession { session ->
            findOwnerOfApplication(session, applicationName) ?: throw RPCException.fromStatusCode(
                HttpStatusCode.NotFound
            )

            session.sendPreparedStatement(
                {
                    setParameter("app_name", applicationName)
                    setParameter("tags", tags)
                },
                """
                    delete from app_store.application_tags
                        where application_name = :app_name and
                        tag_id in (
                            select id from app_store.tags where tag in (select lower(unnest(:tags::text[])))
                        )
                """
            )
        }
    }

    suspend fun findTagsForApp(
        ctx: DBContext,
        applicationName: String
    ): List<String> {
        return ctx.withSession { session ->
            session.sendPreparedStatement(
                {
                    setParameter("app_name", applicationName)
                },
                """
                    select t.tag
                    from app_store.application_tags
                    inner join tags t on t.id = application_tags.tag_id
                    where application_name = :app_name and tag_id 
                    
                """
            ).rows.mapNotNull {
                it.getString(0)
            }
        }
    }
}
