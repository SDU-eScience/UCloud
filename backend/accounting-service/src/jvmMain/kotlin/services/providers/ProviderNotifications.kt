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
    val event: ProviderNotificationEvent,
    val user: String,
    val project: String? = null,
    val group: String? = null,
    val role: String? = null
)


class ProviderNotifications(
    private val db: DBContext
) {
    suspend fun create(
        event: ProviderNotificationEvent,
        user: String,
        project: String? = null,
        group: String? = null,
        role: ProjectRole? = null
    ) {
        // TODO(brian): How do we find the correct providers?
        val providers = listOf("ucloud")

        db.withSession { session ->
            session.sendPreparedStatement(
                {
                    setParameter("time", LocalDateTime.now())
                    setParameter("event", event.name)
                    setParameter("user", user)
                    setParameter("providers", providers)
                    setParameter("project", project)
                    setParameter("group", group)
                    setParameter("role", role?.name)
                },
                """
                    insert into provider.notifications
                        (time, event, user_id, provider, project_id, group_id, user_role)
                    select :time, :event, :user, unnest(:providers::text[]), :project, :group, :role
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
                    select id, time, event, user_id, project_id, group_id, user_role
                    from provider.notifications
                """
            ).rows.map {
                ProviderNotification(
                    it.getLong("id")!!,
                    it.getDate("time")!!,
                    ProviderNotificationEvent.valueOf(it.getString("event")!!),
                    it.getString("user")!!,
                    it.getString("project"),
                    it.getString("group"),
                    it.getString("role")
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

