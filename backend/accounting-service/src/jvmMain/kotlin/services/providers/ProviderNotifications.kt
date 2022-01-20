package dk.sdu.cloud.accounting.services.providers

import dk.sdu.cloud.project.api.ProjectRole
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.sendPreparedStatement
import dk.sdu.cloud.service.db.async.withSession
import org.joda.time.LocalDateTime

enum class ProviderNotificationEvent {
    MEMBER_LEFT_PROJECT,
    MEMBER_LEFT_GROUP,
    MEMBER_ROLE_CHANGED
}

data class ProviderNotification(
    val id: Long,
    val time: LocalDateTime,
    val user: String,
    val resource: Long,
    val permission: NewResourcePermission
)

enum class NewResourcePermission {
    NONE,
    READ,
    READ_WRITE
}

data class ResourceWithProviderAndPermission(
    val resource: Long,
    val provider: String,
    val permission: NewResourcePermission
)


class ProviderNotifications(
    private val db: DBContext
) {
    private val resourceTypes = listOf("sync_folder")

    suspend fun create(
        event: ProviderNotificationEvent,
        user: String,
        project: String? = null,
        group: String? = null,
        role: ProjectRole? = null
    ) {
        db.withSession { session ->
            // TODO Fetch resources
            val resources: List<ResourceWithProviderAndPermission> = if (event == ProviderNotificationEvent.MEMBER_LEFT_PROJECT) {
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
                    ResourceWithProviderAndPermission(
                        it.getLong("id")!!,
                        it.getString("provider")!!,
                        NewResourcePermission.NONE
                    )
                }
            } else {
                emptyList()
            }


            session.sendPreparedStatement(
                {
                    setParameter("time", LocalDateTime.now())
                    setParameter("user", user)
                    setParameter("providers", resources.map { it.provider })
                    setParameter("resources", resources.map { it.resource })
                    setParameter("permission", resources.map { it.permission.name } )
                },
                """
                    insert into provider.notifications
                        (time, user_id, provider, resource, permission)
                    select :time, :event, :user, unnest(:providers::text[]), unnest(:resources::bigint[]), unnest(:permission::text[])
                """
            )
        }
    }

    suspend fun browse(provider: String): List<ProviderNotification> {
        return db.withSession { session ->
            session.sendPreparedStatement(
                {
                    setParameter("provider", provider)
                },
                """
                    select id, time, user_id, resource, permission
                    from provider.notifications
                    where provider = :provider
                """
            ).rows.map {
                ProviderNotification(
                    it.getLong("id")!!,
                    it.getDate("time")!!,
                    it.getString("user_id")!!,
                    it.getLong("resource")!!,
                    NewResourcePermission.valueOf(it.getString("permission")!!)
                )
            }
        }
    }

    suspend fun delete(ids: List<Long>) {
        db.withSession { session ->
            session.sendPreparedStatement(
                {
                    setParameter("ids", ids)
                },
                """
                    delete from provider.notifications where id in (select unnest(:ids::bigserial[]))
                """
            )
        }
    }
}

