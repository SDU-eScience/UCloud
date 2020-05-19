package dk.sdu.cloud.accounting.compute.services

import dk.sdu.cloud.accounting.compute.api.CreditsAccount
import dk.sdu.cloud.service.db.async.DBContext

class VisualizationService(
    private val balance: BalanceService
) {
    suspend fun dailyUsage(
        ctx: DBContext,
        requestedBy: String,
        account: CreditsAccount
    ) {

    }
}