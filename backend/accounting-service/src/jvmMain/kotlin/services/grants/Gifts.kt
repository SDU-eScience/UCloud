package dk.sdu.cloud.accounting.services.grants

import dk.sdu.cloud.Actor
import dk.sdu.cloud.ActorAndProject
import dk.sdu.cloud.FindByLongId
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.grant.api.*
import dk.sdu.cloud.safeUsername
import dk.sdu.cloud.service.db.async.*
import io.ktor.http.HttpStatusCode

class GiftService(
    private val db: DBContext,
) {
    suspend fun claimGift(
        actorAndProject: ActorAndProject,
        giftId: Long,
    ) {
        db.withSession(remapExceptions = true) { session ->
            session.sendPreparedStatement(
                {
                    setParameter("username", actorAndProject.actor.safeUsername())
                    setParameter("gift_id", giftId)
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
                                    gift_pi.role = 'PI'
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
                                    (uc.type = 'wayf' and uc.applicant_id = :wayfId::text and :wayfId::text is not null) or
                                    (
                                        uc.type = 'email' and
                                        uc.applicant_id = :emailDomain::text and
                                        :emailDomain::text is not null
                                    )
                                )
                        ),
                        gifts_claimed as (
                            insert into "grant".gifts_claimed (gift_id, user_id) 
                            select distinct gift_id, recipient
                            from resources_to_be_gifted
                        )
                    select accounting.deposit(array_agg(
                        initiated_by,
                        recipient,
                        false,
                        allocation_id,
                        coalesce(credits, quota),
                        start_date,
                        end_date,
                        'Gift from ' || project_title
                    )::accounting.deposit_request)
                    from resources_to_be_gifted
                    where alloc_idx = 1
                """
            )
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

                    if (actor is Actor.User) {
                        setParameter("wayfId", actor.principal.organization)
                        setParameter("emailDomain", actor.principal.email?.substringAfter('@'))
                    } else {
                        setParameter("wayfId", null as String?)
                        setParameter("emailDomain", null as String?)
                    }
                },

                """
                    select distinct g.id
                    from
                        "grant".gifts g join
                        "grant".gifts_user_criteria uc on g.id = uc.gift_id
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
                            (uc.type = 'wayf' and uc.applicant_id = :wayfId::text and :wayfId::text is not null) or
                            (uc.type = 'email' and uc.applicant_id = :emailDomain::text and :emailDomain::text is not null)
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
                        into("resource_cat_name") { it.productCategory }
                        into("resource_provider") { it.productProvider }
                        into("credits") { it.creditsRequested }
                        into("quota") { it.quotaRequested }
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
