package dk.sdu.cloud.accounting.services.providers

import dk.sdu.cloud.ActorAndProject
import dk.sdu.cloud.FindByStringId
import dk.sdu.cloud.accounting.api.ResourceNotification
import dk.sdu.cloud.auth.api.AuthProviders
import dk.sdu.cloud.calls.BulkRequest
import dk.sdu.cloud.calls.BulkResponse
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.project.api.ProjectRole
import dk.sdu.cloud.safeUsername
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.sendPreparedStatement
import dk.sdu.cloud.service.db.async.withSession
import kotlinx.serialization.decodeFromString
import org.joda.time.LocalDateTime

enum class ResourceNotificationEvent{
    MEMBER_LEFT_PROJECT,
    MEMBER_LEFT_GROUP,
    MEMBER_ROLE_CHANGED
}

class ResourceNotificationService(
    private val db: DBContext
) {
    private val resourceTypes = listOf("sync_folder")

    suspend fun create(
        event: ResourceNotificationEvent,
        user: String,
        project: String? = null,
        group: String? = null,
        role: ProjectRole? = null
    ) {
        db.withSession { session ->
            // TODO Fetch resources
            val resources: List<Long> = if (event == ResourceNotificationEvent.MEMBER_LEFT_PROJECT) {
                session.sendPreparedStatement(
                    {
                        setParameter("user", user)
                        setParameter("project", project)
                        setParameter("types", resourceTypes)
                    },
                    """
                        select provider, id
                        from provider.resource
                        where
                            project = :project and
                            created_by = :user and
                            type in (select unnest(:types::text[]))
                    """
                ).rows.map {
                    it.getLong("id")!!
                }
            } else {
                emptyList()
            }

            session.sendPreparedStatement(
                {
                    setParameter("created_at", LocalDateTime.now())
                    setParameter("user", user)
                    setParameter("resources", resources)
                },
                """
                    insert into provider.notifications
                        (created_at, username, resource)
                    select :created_at, :user, unnest(:resources::bigint[])
                """
            )
        }
    }

    suspend fun retrieve(actorAndProject: ActorAndProject): BulkResponse<ResourceNotification> {
        val providerId = actorAndProject.actor.safeUsername().removePrefix(AuthProviders.PROVIDER_PREFIX)
        val notifications = db.withSession { session ->
            session.sendPreparedStatement(
                {
                    setParameter("provider", providerId)
                },
                """
                    select jsonb_build_object(
                        'id', n.id,
                        'createdAt', n.created_at,
                        'user', n.username,
                        'resource', n.resource
                    )
                    from provider.notifications n
                    join provider.resource r on r.id = n.resource
                    where r.provider = :provider
                    limit 50
                """
            ).rows.map { defaultMapper.decodeFromString<ResourceNotification>(it.getString(0)!!) }
        }

        return BulkResponse(notifications)
    }

    suspend fun markAsRead(actorAndProject: ActorAndProject, ids: BulkRequest<FindByStringId>) {
        db.withSession { session ->
            session.sendPreparedStatement(
                {
                    setParameter("ids", ids.items)
                },
                """
                    delete from provider.notifications where id in (select unnest(:ids::bigserial[]))
                """
            )
        }
    }
}

