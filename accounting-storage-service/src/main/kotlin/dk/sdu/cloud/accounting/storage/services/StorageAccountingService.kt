package dk.sdu.cloud.accounting.storage.services

import dk.sdu.cloud.accounting.api.BillableItem
import dk.sdu.cloud.accounting.api.Currencies
import dk.sdu.cloud.accounting.api.SerializedMoney
import dk.sdu.cloud.client.AuthenticatedCloud
import dk.sdu.cloud.indexing.api.AllOf
import dk.sdu.cloud.indexing.api.FileQuery
import dk.sdu.cloud.indexing.api.NumericStatisticsRequest
import dk.sdu.cloud.indexing.api.QueryDescriptions
import dk.sdu.cloud.indexing.api.StatisticsRequest
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.RPCException
import dk.sdu.cloud.service.optionallyCausedBy
import dk.sdu.cloud.service.orThrow
import io.ktor.http.HttpStatusCode
import org.slf4j.Logger
import java.math.BigDecimal
import kotlin.math.roundToLong

class StorageAccountingService<DBSession>(
    private val serviceCloud: AuthenticatedCloud
) {

    suspend fun calculateUsage(
        directory: String,
        owner: String,
        causedById: String? = null
    ): List<BillableItem> {
        val result = QueryDescriptions.statistics.call(
            StatisticsRequest(
                query = FileQuery(
                    listOf(directory),
                    owner = AllOf.with(owner)
                ),
                size = NumericStatisticsRequest(
                    calculateSum = true
                )
            ),
            serviceCloud.optionallyCausedBy(causedById)
        ).orThrow()

        val usedStorage = result.size?.sum?.roundToLong() ?: run {
            log.warn("Could not retrieve sum from file index!")
            throw RPCException.fromStatusCode(HttpStatusCode.InternalServerError)
        }
        val pricePerUnit = BigDecimal("0.0001")
        val currencyName = Currencies.DKK

        return listOf(
            BillableItem("Storage Used", usedStorage, SerializedMoney(pricePerUnit,currencyName))
        )
    }

    companion object : Loggable {
        override val log: Logger = logger()
    }
}
