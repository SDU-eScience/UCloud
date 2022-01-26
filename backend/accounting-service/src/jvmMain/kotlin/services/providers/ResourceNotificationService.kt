package dk.sdu.cloud.accounting.services.providers

import dk.sdu.cloud.ActorAndProject
import dk.sdu.cloud.FindByStringId
import dk.sdu.cloud.accounting.api.ResourceNotification
import dk.sdu.cloud.accounting.api.ResourceNotificationsProvider
import dk.sdu.cloud.accounting.util.Providers
import dk.sdu.cloud.accounting.util.SimpleProviderCommunication
import dk.sdu.cloud.auth.api.AuthProviders
import dk.sdu.cloud.calls.BulkRequest
import dk.sdu.cloud.calls.BulkResponse
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.project.api.Project
import dk.sdu.cloud.project.api.ProjectRole
import dk.sdu.cloud.safeUsername
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.sendPreparedStatement
import dk.sdu.cloud.service.db.async.withSession
import kotlinx.serialization.decodeFromString
import org.joda.time.LocalDateTime
import kotlin.reflect.typeOf

sealed class ResourceNotificationEvent {
    abstract val user: String
    abstract val project: String

    data class LeftProject(
        override val user: String,
        override val project: String
    ): ResourceNotificationEvent()

    data class LeftGroup(
        override val user: String,
        override val project: String,
        val group: String
    ): ResourceNotificationEvent()

    data class ChangedRole(
        override val user: String,
        override val project: String,
        val role: ProjectRole
    ): ResourceNotificationEvent()
}




class ResourceNotificationService(
    private val db: DBContext,
    private val providers: Providers<SimpleProviderCommunication>
) {
    private val resourceTypes = listOf("sync_folder")

    suspend fun create(
        event: ResourceNotificationEvent
    ) {
        val resources: List<Pair<Long, String>> = db.withSession { session ->
            // TODO Fetch resources
            val resources = when(event) {
                is ResourceNotificationEvent.LeftProject -> {
                    session.sendPreparedStatement(
                        {
                            setParameter("user", event.user)
                            setParameter("project", event.project)
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
                        Pair(it.getLong("id")!!, it.getString("provider")!!)
                    }
                }
                is ResourceNotificationEvent.ChangedRole -> {
                    // TODO
                    emptyList()
                }
                is ResourceNotificationEvent.LeftGroup -> {
                    // TODO
                    emptyList()
                }
            }

            session.sendPreparedStatement(
                {
                    setParameter("created_at", LocalDateTime.now())
                    setParameter("user", event.user)
                    setParameter("resources", resources.map { it.first })
                },
                """
                    insert into provider.notifications
                        (created_at, username, resource)
                    select :created_at, :user, unnest(:resources::bigint[])
                """
            )

            resources
        }

        resources.map { it.second }.toSet().forEach { provider ->
            val comms = providers.prepareCommunication(provider)
            ResourceNotificationsProvider(provider).pullRequest.call(Unit, comms.client)
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
                        'username', n.username,
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
                    setParameter("ids", ids.items.map { it.id.toLong() })
                },
                """
                    delete from provider.notifications where id in (select unnest(:ids::bigint[]))
                """
            )
        }
    }
}

