package dk.sdu.cloud.accounting.services.wallets

import dk.sdu.cloud.Actor
import dk.sdu.cloud.ActorAndProject
import dk.sdu.cloud.Role
import dk.sdu.cloud.accounting.api.*
import dk.sdu.cloud.calls.BulkRequest
import dk.sdu.cloud.calls.BulkResponse
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.safeUsername
import dk.sdu.cloud.service.db.async.*
import io.ktor.http.*

class AccountingService(
    val db: DBContext,
) {
    private val transactionMode = TransactionMode.Serializable(readWrite = true)

    suspend fun charge(
        actorAndProject: ActorAndProject,
        request: BulkRequest<ChargeWalletRequestItem>,
    ) {
        val actor = actorAndProject.actor
        if (actor != Actor.System || (actor is Actor.User && actor.principal.role != Role.SERVICE)) {
            throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)
        }

        db.withSession(remapExceptions = true, transactionMode) { session ->
            session.sendPreparedStatement(
                packChargeRequests(request),
                """
                    with requests as (
                        select (
                            unnest(:payer_ids::text[]),
                            unnest(:payer_is_project::boolean[]),
                            unnest(:units::bigint[]),
                            unnest(:number_of_products::bigint[]),
                            unnest(:product_ids::text[]),
                            unnest(:product_categories::text[]),
                            unnest(:product_provider::text[]),
                            unnest(:performed_by::text[]),
                            unnest(:descriptions::text[]) 
                        )::accounting.charge_request req
                    )
                    select accounting.charge(array_agg(req))
                    from requests;
                """
            )
        }
    }

    suspend fun check(
        actorAndProject: ActorAndProject,
        request: BulkRequest<ChargeWalletRequestItem>,
    ): BulkResponse<Boolean> {
        val actor = actorAndProject.actor
        if (actor != Actor.System || (actor is Actor.User && actor.principal.role != Role.SERVICE)) {
            throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)
        }

        return BulkResponse(
            db.withSession(remapExceptions = true, transactionMode) { session ->
                session.sendPreparedStatement(
                    packChargeRequests(request),
                    """
                        with requests as (
                            select (
                                unnest(:payer_ids::text[]),
                                unnest(:payer_is_project::boolean[]),
                                unnest(:units::bigint[]),
                                unnest(:number_of_products::bigint[]),
                                unnest(:product_ids::text[]),
                                unnest(:product_categories::text[]),
                                unnest(:product_provider::text[]),
                                unnest(:performed_by::text[]),
                                unnest(:descriptions::text[]) 
                            )::accounting.charge_request req
                        )
                        select accounting.credit_check(array_agg(req))
                        from requests;
                    """
                ).rows.map { it.getBoolean(0)!! }
            }
        )
    }

    private fun packChargeRequests(request: BulkRequest<ChargeWalletRequestItem>): EnhancedPreparedStatement.() -> Unit =
        {
            val payerIds by parameterList<String>()
            val payerIsProject by parameterList<Boolean>()
            val units by parameterList<Long>()
            val numberOfProducts by parameterList<Long>()
            val productIds by parameterList<String>()
            val productCategories by parameterList<String>()
            val productProvider by parameterList<String>()
            val performedBy by parameterList<String>()
            val descriptions by parameterList<String>()
            for (req in request.items) {
                when (val payer = req.payer) {
                    is WalletOwner.Project -> {
                        payerIds.add(payer.projectId)
                        payerIsProject.add(true)
                    }
                    is WalletOwner.User -> {
                        payerIds.add(payer.username)
                        payerIsProject.add(false)
                    }
                }
                units.add(req.units)
                numberOfProducts.add(req.numberOfProducts)
                productIds.add(req.product.id)
                productCategories.add(req.product.category)
                productProvider.add(req.product.provider)
                performedBy.add(req.performedBy)
                descriptions.add(req.description)
            }
        }

    suspend fun deposit(
        actorAndProject: ActorAndProject,
        request: BulkRequest<DepositToWalletRequestItem>,
    ) {
        db.withSession(remapExceptions = true, transactionMode) { session ->
            session.sendPreparedStatement(
                {
                    val initiatedBy by parameterList<String>()
                    val recipients by parameterList<String>()
                    val recipientIsProject by parameterList<Boolean>()
                    val sourceAllocation by parameterList<Long?>()
                    val desiredBalance by parameterList<Long>()
                    val startDates by parameterList<Long?>()
                    val endDates by parameterList<Long?>()
                    for (req in request.items) {
                        initiatedBy.add(actorAndProject.actor.safeUsername())
                        when (val recipient = req.recipient) {
                            is WalletOwner.Project -> {
                                recipients.add(recipient.projectId)
                                recipientIsProject.add(true)
                            }
                            is WalletOwner.User -> {
                                recipients.add(recipient.username)
                                recipientIsProject.add(false)
                            }
                        }
                        sourceAllocation.add(req.sourceAllocation.toLongOrNull())
                        desiredBalance.add(req.amount)
                        startDates.add(req.startDate?.let { it / 1000 })
                        endDates.add(req.endDate?.let { it / 1000 })
                    }
                },
                """
                    with requests as (
                        select (
                            unnest(:initiated_by::text[]),
                            unnest(:recipients::text[]),
                            unnest(:recipient_is_project::boolean[]),
                            unnest(:source_allocation::bigint[]),
                            unnest(:desired_balance::bigint[]),
                            to_timestamp(unnest(:start_dates::bigint[])),
                            to_timestamp(unnest(:end_dates::bigint[])),
                            unnest(:descriptions::text[])
                        )::accounting.deposit_request req
                    )
                    select accounting.deposit(array_agg(req))
                    from requests
                """
            )
        }
    }

    suspend fun transfer(
        actorAndProject: ActorAndProject,
        request: BulkRequest<TransferToWalletRequestItem>,
    ) {
        TODO()
    }

    suspend fun updateAllocation(
        actorAndProject: ActorAndProject,
        request: BulkRequest<UpdateAllocationRequestItem>,
    ) {
        TODO()
    }
}
