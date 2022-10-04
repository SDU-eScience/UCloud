package dk.sdu.cloud.accounting.services.grants

import dk.sdu.cloud.ActorAndProject
import dk.sdu.cloud.FindByLongId
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.grant.api.*
import dk.sdu.cloud.safeUsername
import dk.sdu.cloud.service.db.async.*
import java.util.*

class GiftService(
    private val db: DBContext,
) {
    suspend fun claimGift(
        actorAndProject: ActorAndProject,
        giftId: Long,
    ) {
        db.withSession(remapExceptions = true) { session ->
            val giftsClaimed = session.sendPreparedStatement(
                {
                    setParameter("username", actorAndProject.actor.safeUsername())
                    setParameter("gift_id", giftId)
                    setParameter("transaction_id", UUID.randomUUID().toString())
                },
                """
                    with
                        resources_to_be_gifted as (
                            select
                                g.id gift_id,
                                gift_pi.username initiated_by,
                                :username recipient,
                                alloc.id allocation_id,
                                res.credits,
                                res.quota,
                                alloc.start_date,
                                alloc.end_date,
                                project.title project_title,
                                row_number() over (partition by pc.id order by alloc.end_date nulls last, alloc.id)
                                    as alloc_idx
                            from
                                -- NOTE(Dan): Fetch data about the gift
                                "grant".gifts g join
                                "grant".gifts_user_criteria uc on g.id = uc.gift_id join
                                "grant".gift_resources res on g.id = res.gift_id join
                                
                                -- NOTE(Dan): Lookup the relevant allocations for every resource
                                accounting.product_categories pc on res.product_category = pc.id join
                                accounting.wallet_owner wo on wo.project_id = g.resources_owned_by join
                                accounting.wallets w on
                                    w.category = pc.id and
                                    w.owned_by = wo.id join
                                accounting.wallet_allocations alloc on
                                    alloc.associated_wallet = w.id and
                                    now() >= alloc.start_date and
                                    (alloc.end_date is null or now() <= alloc.end_date) join
                                    
                                -- NOTE(Dan): Find information about the gifting project (for transactions)
                                project.projects project on
                                    project.id = g.resources_owned_by join
                                project.project_members gift_pi on
                                    gift_pi.project_id = g.resources_owned_by and
                                    gift_pi.role = 'PI' join
                                    
                                -- NOTE(Dan): Find the user
                                auth.principals user_info on
                                    user_info.id = :username
                            where
                                g.id = :gift_id and

                                -- User must not have claimed this gift already
                                not exists(
                                    select gc.user_id
                                    from "grant".gifts_claimed gc
                                    where gc.user_id = :username and gc.gift_id = g.id
                                ) and

                                -- User must match at least one criteria
                                (
                                    (uc.type = 'anyone') or
                                    (uc.type = 'wayf' and uc.applicant_id is not distinct from user_info.org_id) or
                                    (
                                        uc.type = 'email' and
                                        user_info.email like '%@' || uc.applicant_id
                                    )
                                )
                        ),
                        gifts_claimed as (
                            insert into "grant".gifts_claimed (gift_id, user_id) 
                            select distinct gift_id, recipient
                            from resources_to_be_gifted
                            returning gift_id
                        )
                    select accounting.deposit(array_agg((
                        initiated_by,
                        recipient,
                        false,
                        allocation_id,
                        coalesce(credits, quota),
                        start_date,
                        end_date,
                        'Gift from ' || project_title,
                        :transaction_id || (allocation_id::text),
                        null
                    )::accounting.deposit_request)), count(distinct gifts_claimed.gift_id)
                    from resources_to_be_gifted join gifts_claimed on
                        resources_to_be_gifted.gift_id = gifts_claimed.gift_id
                    where alloc_idx = 1
                """,
            ).rows.singleOrNull()?.getLong(1)

            if (giftsClaimed != 1L) {
                throw RPCException("Unable to claim this gift", HttpStatusCode.BadRequest)
            }
        }
    }

    suspend fun findAvailableGifts(
        actorAndProject: ActorAndProject,
        giftId: Long? = null
    ): AvailableGiftsResponse {
        val actor = actorAndProject.actor
        return AvailableGiftsResponse(db.withSession(remapExceptions = true) { session ->
            session.sendPreparedStatement(
                {
                    setParameter("userId", actor.safeUsername())
                    setParameter("giftId", giftId)
                },

                """
                    select distinct g.id
                    from
                        "grant".gifts g join
                        "grant".gifts_user_criteria uc on g.id = uc.gift_id join
                        auth.principals user_info on user_info.id = :userId
                    where
                        -- If giftId is specified it must match the gift we are looking for
                        (g.id = :giftId::bigint or :giftId::bigint is null) and

                        -- User must not have claimed this gift already
                        not exists(
                            select gc.user_id
                            from "grant".gifts_claimed gc
                            where gc.user_id = :userId and gc.gift_id = g.id
                        ) and

                        -- User must match at least one criteria
                        (
                            (uc.type = 'anyone') or
                            (uc.type = 'wayf' and uc.applicant_id is not distinct from user_info.org_id) or
                            (
                                uc.type = 'email' and
                                user_info.email like '%@' || uc.applicant_id
                            )
                        )
                """
            ).rows.map { FindByLongId(it.getLong(0)!!) }
        })
    }

    suspend fun createGift(
        actorAndProject: ActorAndProject,
        gift: GiftWithCriteria
    ): Long {
        return db.withSession(remapExceptions = true) { session ->
            session.sendPreparedStatement(
                {
                    setParameter("username", actorAndProject.actor.safeUsername())
                    setParameter("title", gift.title)
                    setParameter("description", gift.description)
                    setParameter("resources_owned_by", gift.resourcesOwnedBy)

                    gift.criteria.split {
                        into("criteria_type") { it.type }
                        into("criteria_id") { it.id }
                    }

                    gift.resources.split {
                        into("resource_cat_name") { it.category }
                        into("resource_provider") { it.provider }
                        into("credits") { it.balanceRequested }
                        into("quota") { 0 }
                    }
                },
                """
                    select "grant".create_gift(
                        :username, :resources_owned_by, :title, :description,
                        :criteria_type, :criteria_id,
                        :resource_cat_name, :resource_provider, :credits, :quota
                    )
                """
            ).rows.singleOrNull()?.getLong(0)
                ?: throw RPCException("unable to create gift", HttpStatusCode.InternalServerError)
        }
    }

    suspend fun deleteGift(
        actorAndProject: ActorAndProject,
        giftId: Long
    ) {
        db.withSession(remapExceptions = true) { session ->
            session.sendPreparedStatement(
                {
                    setParameter("username", actorAndProject.actor.safeUsername())
                    setParameter("id", giftId)
                },
                "select \"grant\".delete_gift(:username, :id)"
            )
        }
    }
}
