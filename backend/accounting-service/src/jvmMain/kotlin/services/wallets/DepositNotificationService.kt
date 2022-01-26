package dk.sdu.cloud.accounting.services.wallets

import dk.sdu.cloud.ActorAndProject
import dk.sdu.cloud.FindByStringId
import dk.sdu.cloud.accounting.api.DepositNotification
import dk.sdu.cloud.accounting.api.DepositNotificationsMarkAsReadRequestItem
import dk.sdu.cloud.auth.api.AuthProviders
import dk.sdu.cloud.calls.BulkRequest
import dk.sdu.cloud.calls.BulkResponse
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.safeUsername
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.sendPreparedStatement
import dk.sdu.cloud.service.db.async.withSession
import kotlinx.serialization.decodeFromString

class DepositNotificationService(
    private val db: DBContext,
){
    suspend fun retrieveNotifications(actorAndProject: ActorAndProject): BulkResponse<DepositNotification> {
        val providerId = actorAndProject.actor.safeUsername().removePrefix(AuthProviders.PROVIDER_PREFIX)
        val notifications = db.withSession { session ->
            session.sendPreparedStatement(
                {
                    setParameter("provider_id", providerId)
                },
                """
                    select jsonb_build_object(
                        'id', notification.id::text,
                        'owner', jsonb_build_object(
                            'type', case
                                when notification.username is not null then 'user'
                                else 'project'
                            end,
                            'username', notification.username,
                            'projectId', notification.project_id
                        ),
                        'category', jsonb_build_object(
                            'name', pc.category,
                            'provider', pc.provider
                        ),
                        'balance', notification.balance
                    )
                    from
                        accounting.deposit_notifications notification join
                        accounting.product_categories pc on notification.category_id = pc.id
                    order by notification.id
                    limit 50
                """
            ).rows.map { defaultMapper.decodeFromString<DepositNotification>(it.getString(0)!!) }
        }

        return BulkResponse(notifications)
    }

    suspend fun markAsRead(
        actorAndProject: ActorAndProject,
        request: BulkRequest<DepositNotificationsMarkAsReadRequestItem>
    ) {
        val providerId = actorAndProject.actor.safeUsername().removePrefix(AuthProviders.PROVIDER_PREFIX)
        db.withSession { session ->
            session.sendPreparedStatement(
                {
                    setParameter("provider_id", providerId)
                    setParameter("ids", request.items.mapNotNull { it.id.toLongOrNull() })
                    setParameter("generated_ids", request.items.map {
                        if (it.providerGeneratedId != null) providerId + it.providerGeneratedId
                        else null
                    })
                },
                """
                    with
                        id_to_generated_id as (
                            select
                                unnest(:ids::bigint[]) id,
                                unnest(:generated_ids::text[]) generated_id
                        ),
                        cleared_notifications as (
                            delete from accounting.deposit_notifications notification
                            using accounting.product_categories pc
                            where
                                pc.id = notification.category_id and
                                pc.provider = :provider_id and
                                notification.id = some(:ids::bigint[])
                            returning
                                notification.id, 
                                notification.username, 
                                notification.project_id, 
                                notification.category_id
                        )
                    update accounting.wallet_allocations alloc
                    set provider_generated_id = lookup.generated_id
                    from
                        id_to_generated_id lookup join
                        cleared_notifications cleared on lookup.id = cleared.id join
                        accounting.wallet_owner wo on
                            cleared.username = wo.username or
                            cleared.project_id = wo.project_id join
                        accounting.wallets w on
                            w.owned_by = wo.id and
                            w.category = cleared.category_id
                    where
                        alloc.associated_wallet = w.id
                """
            )
        }
    }
}
