package dk.sdu.cloud.accounting.services.grants

import dk.sdu.cloud.*
import dk.sdu.cloud.accounting.api.*
import dk.sdu.cloud.accounting.services.projects.ProjectService
import dk.sdu.cloud.accounting.services.wallets.AccountingService
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.bulkRequestOf
import dk.sdu.cloud.grant.api.*
import dk.sdu.cloud.service.Time
import dk.sdu.cloud.service.db.async.*

class GiftService(
    private val db: DBContext,
    private val accountingService: AccountingService,
    private val projectService: ProjectService
) {
    suspend fun claimGift(
        actorAndProject: ActorAndProject,
        giftId: Long,
    ) {
        db.withSession(remapExceptions = true) { session ->
            val rows = session.sendPreparedStatement(
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
                """
            ).rows

            if (rows.isEmpty()) {
                throw RPCException("Unable to claim this gift", HttpStatusCode.BadRequest)
            }

            rows.forEach { row ->
                val balance = row.getLong(0)!!
                val category = ProductCategoryIdV2(row.getString(1)!!, row.getString(2)!!)
                val sourceProject = row.getString(3)!!

                val allocations = accountingService.retrieveAllocationsInternal(
                    ActorAndProject(Actor.System, null),
                    WalletOwner.Project(sourceProject),
                    category
                )
                val sourceAllocation = allocations.find { (it.quota - (it.treeUsage ?: it.localUsage)) >= balance }
                    ?: allocations.firstOrNull()
                    ?: throw RPCException("Unable to claim this gift", HttpStatusCode.BadRequest)

                accountingService.subAllocate(
                    ActorAndProject(Actor.System, null),
                    bulkRequestOf(
                        SubAllocationRequestItem(
                            owner = WalletOwner.User(actorAndProject.actor.safeUsername()),
                            parentAllocation = sourceAllocation.id,
                            quota = balance,
                            grantedIn = null,
                            start = Time.now(),
                            //gifts ends after 1 year
                            end = Time.now() + (1000L * 3600 * 24 * 365),
                        )
                    )
                )
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
        val project = actorAndProject.project
            ?: throw RPCException("Only projects are allowed to create gifts", HttpStatusCode.BadRequest)

        if (gift.resourcesOwnedBy != project) {
            throw RPCException("Cannot create gift on behalf of other project", HttpStatusCode.BadRequest)
        }

        val projectRole = projectService.findRoleOfMember(db, project, actorAndProject.actor.safeUsername())
            ?: throw RPCException("Unknown Project", HttpStatusCode.BadRequest)

        if (!projectRole.isAdmin()) {
            throw RPCException("Only admins and PIs are allowed to create gifts", HttpStatusCode.Forbidden)
        }

        




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
                        into("h") { it.period }
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

    suspend fun browse(
        actorAndProject: ActorAndProject,
        pagination: NormalizedPaginationRequestV2
    ): PageV2<GiftWithCriteria> {
        val itemsPerPage = pagination.itemsPerPage
        if (actorAndProject.project == null) return PageV2(itemsPerPage, emptyList(), null)

        val items = db.withSession { session ->
            session.sendPreparedStatement(
                {
                    setParameter("project", actorAndProject.project)
                    setParameter("username", actorAndProject.actor.safeUsername())
                    setParameter("next", pagination.next?.toLongOrNull())
                },
                """
                    with
                        gifts as (
                            select g.id
                            from
                                project.project_members pm
                                join "grant".gifts g on pm.project_id = g.resources_owned_by
                            where
                                pm.project_id = :project
                                and pm.username = :username
                                and (
                                    pm.role = 'ADMIN'
                                    or pm.role = 'PI'
                                )
                                and (
                                    :next::int8 is null
                                    or g.id > :next::int8
                                )
                            limit $itemsPerPage
                        ),
                        criteria_by_gift as (
                            select
                                g.id,
                                 'criteria', jsonb_agg(jsonb_build_object(
                                    'type', c.type,
                                    case
                                        when c.type = 'email' then 'domain'
                                        when c.type = 'wayf' then 'org'
                                        else 'ignored'
                                    end,
                                    c.applicant_id
                                )) as criteria_arr
                            from
                                gifts g
                                join "grant".gifts_user_criteria c on g.id = c.gift_id
                            group by g.id
                        ),
                        resources_by_gift as (
                            select
                                g.id,
                                jsonb_agg(jsonb_build_object(
                                    'category', pc.category,
                                    'provider', pc.provider,
                                    'grantGiver', :project::text,
                                    'balanceRequested', r.credits,
                                    'period', jsonb_build_object('start', null::int8, 'end', null::int8)
                                )) as resources_arr
                            from
                                gifts g
                                join "grant".gift_resources r on g.id = r.gift_id
                                join accounting.product_categories pc on r.product_category = pc.id
                            group by g.id
                        )
                    select jsonb_build_object(
                        'id', g.id,
                        'title', g.title,
                        'description', g.description,
                        'resourcesOwnedBy', g.resources_owned_by,
                        'resources', coalesce(r.resources_arr, '[]'::jsonb),
                        'criteria', coalesce(c.criteria_arr, '[]'::jsonb)
                    )
                    from
                        gifts rg
                        join "grant".gifts g on rg.id = g.id
                        left join criteria_by_gift c on c.id = g.id
                        left join resources_by_gift r on r.id = g.id
                    order by
                        g.id
                    limit $itemsPerPage
                """
            ).rows.map { defaultMapper.decodeFromString(GiftWithCriteria.serializer(), it.getString(0)!!) }
        }

        val next = if (items.size < itemsPerPage) {
            null
        } else {
            items.last().id.toString()
        }

        return PageV2(itemsPerPage, items, next)
    }
}
