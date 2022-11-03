package dk.sdu.cloud.accounting.services.grants

import dk.sdu.cloud.Actor
import dk.sdu.cloud.ActorAndProject
import dk.sdu.cloud.FindByLongId
import dk.sdu.cloud.accounting.api.DepositToWalletRequestItem
import dk.sdu.cloud.accounting.api.ProductCategoryId
import dk.sdu.cloud.accounting.api.WalletOwner
import dk.sdu.cloud.accounting.services.wallets.AccountingService
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.bulkRequestOf
import dk.sdu.cloud.grant.api.*
import dk.sdu.cloud.safeUsername
import dk.sdu.cloud.service.db.async.*
import java.util.*

class GiftService(
    private val db: DBContext,
    private val accountingService: AccountingService,
) {
    suspend fun claimGift(
        actorAndProject: ActorAndProject,
        giftId: Long,
    ) {
        db.withSession(remapExceptions = true) { session ->
            val row = session.sendPreparedStatement(
                {
                    setParameter("username", actorAndProject.actor.safeUsername())
                    setParameter("gift_id", giftId)
                },
                """
                    with
                        resources_to_be_gifted as (
                            select
                                g.id gift_id,
                                :username recipient,
                                coalesce(res.credits, res.quota) as balance,
                                pc.category,
                                pc.provider,
                                g.resources_owned_by
                            from
                                -- NOTE(Dan): Fetch data about the gift
                                "grant".gifts g join
                                "grant".gifts_user_criteria uc on g.id = uc.gift_id join
                                "grant".gift_resources res on g.id = res.gift_id join

                                accounting.product_categories pc on
                                    res.product_category = pc.id join

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
                    select res.balance, res.category, res.provider, res.resources_owned_by
                    from
                        resources_to_be_gifted res join
                        gifts_claimed on
                            res.gift_id = gifts_claimed.gift_id;
                """,
            ).rows.singleOrNull() ?: throw RPCException("Unable to claim this gift", HttpStatusCode.BadRequest)

            val balance = row.getLong(0)!!
            val category = ProductCategoryId(row.getString(1)!!, row.getString(2)!!)
            val sourceProject = row.getString(3)!!

            val allocations = accountingService.retrieveAllocationsInternal(
                ActorAndProject(Actor.System, null),
                WalletOwner.Project(sourceProject),
                category
            )
            val sourceAllocation = allocations.find { it.balance >= balance } ?: allocations.firstOrNull() ?:
                throw RPCException("Unable to claim this gift", HttpStatusCode.BadRequest)

            accountingService.deposit(
                ActorAndProject(Actor.System, null),
                bulkRequestOf(DepositToWalletRequestItem(
                    WalletOwner.User(actorAndProject.actor.safeUsername()),
                    sourceAllocation.id,
                    balance,
                    "Gift for ${category.name} / ${category.provider}"
                ))
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
