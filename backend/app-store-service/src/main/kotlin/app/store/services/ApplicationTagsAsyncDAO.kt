package app.store.services

import dk.sdu.cloud.SecurityPrincipal
import dk.sdu.cloud.app.store.services.TagsDAO
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.service.db.async.DBContext
import io.ktor.http.HttpStatusCode

class ApplicationTagsAsyncDAO() : TagsDAO {

    override suspend fun createTags(
        ctx: DBContext,
        user: SecurityPrincipal,
        applicationName: String,
        tags: List<String>
    ) {
        val owner = findOwnerOfApplication(session, applicationName)
            ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound)

        if (!canUserPerformWriteOperation(owner, user)) {
            throw RPCException.fromStatusCode(HttpStatusCode.Forbidden, "Not owner of application")
        }
        tags.forEach { tag ->
            val existing = findTag(session, applicationName, tag)

            if (existing != null) {
                return@forEach
            }
            val entity = TagEntity(
                applicationName,
                tag
            )
            session.save(entity)
        }
    }

    override suspend fun deleteTags(
        ctx: DBContext,
        user: SecurityPrincipal,
        applicationName: String,
        tags: List<String>
    ) {
        val owner = findOwnerOfApplication(session, applicationName)
            ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound)

        if (!canUserPerformWriteOperation(owner, user)) {
            throw RPCException.fromStatusCode(HttpStatusCode.Forbidden, "Not owner of application")
        }

        tags.forEach { tag ->
            val existing = findTag(
                session,
                applicationName,
                tag
            ) ?: return@forEach

            session.delete(existing)
        }
    }

    suspend fun findTag(
        ctx: DBContext,
        appName: String,
        tag: String
    ): TagEntity? {
        return session
            .criteria<TagEntity> {
                allOf(
                    entity[TagEntity::tag] equal tag,
                    entity[TagEntity::applicationName] equal appName
                )
            }.uniqueResult()
    }

    override suspend fun findTagsForApp(
        ctx: DBContext,
        applicationName: String
    ): List<TagEntity> {
        return session.criteria<TagEntity> {
            allOf(
                entity[TagEntity::applicationName] equal applicationName
            )
        }.resultList
    }
}
