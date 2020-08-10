package dk.sdu.cloud.grant.services

import com.github.jasync.sql.db.RowData
import dk.sdu.cloud.accounting.api.WalletOwnerType
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.grant.api.*
import dk.sdu.cloud.service.Actor
import dk.sdu.cloud.service.db.async.*
import dk.sdu.cloud.service.safeUsername
import io.ktor.http.HttpStatusCode

object GiftTable : SQLTable("gifts") {
    val id = long("id", notNull = true)
    val title = text("title", notNull = true)
    val description = text("description", notNull = true)
    val resourcesOwnedBy = text("resources_owned_by", notNull = true)
}

object GiftUserCriteriaTable : SQLTable("gifts_user_criteria") {
    val giftId = long("gift_id", notNull = true)
    val type = text("type", notNull = true)
    val applicantId = text("applicant_id", notNull = false)
}

object GiftResourceTable : SQLTable("gift_resources") {
    val giftId = long("gift_id", notNull = true)
    val productCategory = text("product_category", notNull = true)
    val productProvider = text("product_provider", notNull = true)
    val credits = long("credits", notNull = false)
    val quota = long("quota", notNull = false)
}

object GiftClaimedTable : SQLTable("gifts_claimed") {
    val giftId = long("gift_id", notNull = true)
    val userId = text("user_id", notNull = true)
    val claimedAt = timestamp("claimed_at", notNull = true)
}

class GiftService(
    private val projects: ProjectCache,
    private val serviceClient: AuthenticatedClient
) {
    suspend fun claimGift(
        ctx: DBContext,
        actor: Actor,
        giftId: Long
    ) {
        ctx.withSession { session ->
            val hasClaimedGift = session
                .sendPreparedStatement(
                    {
                        setParameter("username", actor.safeUsername())
                        setParameter("giftId", giftId)
                    },
                    "select * from gifts_claimed where user_id = :username and gift_id = :giftId"
                )
                .rows
                .size > 0L

            if (hasClaimedGift) throw RPCException("Gift has already been claimed", HttpStatusCode.BadRequest)

            val gift = findAvailableGifts(session, actor, giftId).singleOrNull()
                ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound)

            session.insert(GiftClaimedTable) {
                set(GiftClaimedTable.giftId, giftId)
                set(GiftClaimedTable.userId, actor.safeUsername())
            }

            grantResourcesToProject(
                gift.resourcesOwnedBy,
                gift.resources,
                actor.safeUsername(),
                WalletOwnerType.USER,
                serviceClient
            )
        }
    }

    suspend fun findAvailableGifts(
        ctx: DBContext,
        actor: Actor,
        giftId: Long? = null
    ): List<GiftWithId> {
        return ctx.withSession { session ->
            val rows = session
                .sendPreparedStatement(
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
                        with unclaimed_gifts as (
                            select g.* 
                            from gifts g, gifts_user_criteria uc
                            where 
                                (g.id = :giftId::bigint or :giftId::bigint is null) and
                                g.id = uc.gift_id and
                                not exists(
                                    select gc.user_id 
                                    from gifts_claimed gc 
                                    where gc.user_id = :userId and gc.gift_id = g.id
                                ) and (
                                    (uc.type = 'anyone') or
                                    (uc.type = 'wayf' and uc.applicant_id = :wayfId::text and :wayfId::text is not null) or
                                    (uc.type = 'email' and uc.applicant_id = :emailDomain::text and :emailDomain::text is not null)
                                )
                        )
                        
                        select 
                            ug.id, ug.title, ug.description, ug.resources_owned_by,
                            gr.product_category, gr.product_provider, gr.credits, gr.quota
                        from unclaimed_gifts ug, gift_resources gr
                        where ug.id = gr.gift_id
                    """
                )
                .rows

            if (rows.isEmpty()) return@withSession emptyList<GiftWithId>()

            rows
                .map { row ->
                    GiftWithId(
                        row.getField(GiftTable.id),
                        row.getField(GiftTable.resourcesOwnedBy),
                        row.getField(GiftTable.title),
                        row.getField(GiftTable.description),
                        listOf(
                            ResourceRequest(
                                row.getField(GiftResourceTable.productCategory),
                                row.getField(GiftResourceTable.productProvider),
                                row.getFieldNullable(GiftResourceTable.credits),
                                row.getFieldNullable(GiftResourceTable.quota)
                            )
                        )
                    )
                }
                .groupBy { it.id }
                .values
                .map { rowsForGift ->
                    rowsForGift.reduce { acc, giftWithId -> acc.copy(resources = acc.resources + giftWithId.resources) }
                }
        }
    }

    suspend fun createGift(
        ctx: DBContext,
        actor: Actor,
        gift: GiftWithCriteria
    ): Long {
        if (!projects.isAdminOfProject(gift.resourcesOwnedBy, actor)) {
            throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
        }

        return ctx.withSession { session ->
            val id = session.allocateId("gift_id_sequence")
            session.insert(GiftTable) {
                set(GiftTable.id, id)
                set(GiftTable.title, gift.title)
                set(GiftTable.description, gift.description)
                set(GiftTable.resourcesOwnedBy, gift.resourcesOwnedBy)
            }

            gift.criteria.forEach { criteria ->
                session.insert(GiftUserCriteriaTable) {
                    set(GiftUserCriteriaTable.giftId, id)
                    set(GiftUserCriteriaTable.type, criteria.toSqlType())
                    set(GiftUserCriteriaTable.applicantId, criteria.toSqlApplicantId())
                }
            }

            gift.resources.forEach { resource ->
                session.insert(GiftResourceTable) {
                    set(GiftResourceTable.giftId, id)
                    set(GiftResourceTable.productCategory, resource.productCategory)
                    set(GiftResourceTable.productProvider, resource.productProvider)
                    set(GiftResourceTable.credits, resource.creditsRequested)
                    set(GiftResourceTable.quota, resource.quotaRequested)
                }
            }
            id
        }
    }

    suspend fun deleteGift(
        ctx: DBContext,
        actor: Actor,
        giftId: Long
    ) {
        ctx.withSession { session ->
            session.sendPreparedStatement(
                { setParameter("giftId", giftId )},
                "delete from gifts_claimed where gift_id = :giftId"
            )
            session.sendPreparedStatement(
                { setParameter("giftId", giftId )},
                "delete from gift_resources where gift_id = :giftId"
            )
            session.sendPreparedStatement(
                { setParameter("giftId", giftId )},
                "delete from gifts_user_criteria where gift_id = :giftId"
            )

            val projectId = session
                .sendPreparedStatement(
                    { setParameter("giftId", giftId) },
                    "delete from gifts where id = :giftId returning resources_owned_by "
                )
                .rows
                .singleOrNull()
                ?.getField(GiftTable.resourcesOwnedBy)
                ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound)

            if (!projects.isAdminOfProject(projectId, actor)) {
                throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
            }
        }
    }

    suspend fun listGifts(
        ctx: DBContext,
        actor: Actor,
        projectId: String
    ): List<GiftWithCriteria> {
        data class GiftRow(val id: Long, val title: String, val description: String)

        fun RowData.toGift(): GiftRow {
            return GiftRow(
                getField(GiftTable.id),
                getField(GiftTable.title),
                getField(GiftTable.description)
            )
        }

        if (!projects.isAdminOfProject(projectId, actor)) {
            throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
        }

        return ctx.withSession { session ->
            val resources = session
                .sendPreparedStatement(
                    { setParameter("projectId", projectId) },
                    """
                        with project_gifts as (
                            select * from gifts where resources_owned_by = :projectId
                        )
                        
                        select g.*, gr.* 
                        from project_gifts g inner join gift_resources gr on (g.id = gr.gift_id)
                    """
                )
                .rows
                .map {
                    it.toGift() to ResourceRequest(
                        it.getField(GiftResourceTable.productCategory),
                        it.getField(GiftResourceTable.productProvider),
                        it.getFieldNullable(GiftResourceTable.credits),
                        it.getFieldNullable(GiftResourceTable.quota)
                    )
                }
                .groupBy { it.first }

            val criteria = session
                .sendPreparedStatement(
                    { setParameter("projectId", projectId) },
                    """
                        with project_gifts as (
                            select * from gifts where resources_owned_by = :projectId
                        )
                        
                        select g.*, gr.* 
                        from project_gifts g inner join gifts_user_criteria gr on (g.id = gr.gift_id)
                    """
                )
                .rows
                .map { it.toGift() to it.toUserCriteria() }
                .groupBy { it.first }

            // Note: We are guaranteed that all gifts will appear in both collections
            resources.keys.map { gift ->
                val localResources = resources.getValue(gift)
                val localCriteria = criteria.getValue(gift)
                GiftWithCriteria(
                    gift.id,
                    projectId,
                    gift.title,
                    gift.description,
                    localResources.map { it.second },
                    localCriteria.map { it.second }
                )
            }
        }
    }

    private fun RowData.toUserCriteria(): UserCriteria {
        val id = getField(GiftUserCriteriaTable.applicantId)
        return when (getField(GiftUserCriteriaTable.type)) {
            UserCriteria.ANYONE_TYPE -> UserCriteria.Anyone()
            UserCriteria.EMAIL_TYPE -> UserCriteria.EmailDomain(id)
            UserCriteria.WAYF_TYPE -> UserCriteria.WayfOrganization(id)
            else -> throw IllegalArgumentException("Unknown type")
        }
    }
}
