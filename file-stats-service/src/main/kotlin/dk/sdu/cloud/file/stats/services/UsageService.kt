package dk.sdu.cloud.file.stats.services

import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.indexing.api.AllOf
import dk.sdu.cloud.indexing.api.FileQuery
import dk.sdu.cloud.indexing.api.NumericStatisticsRequest
import dk.sdu.cloud.indexing.api.QueryDescriptions
import dk.sdu.cloud.indexing.api.StatisticsRequest
import dk.sdu.cloud.service.Loggable
import io.ktor.http.HttpStatusCode
import org.slf4j.Logger
import kotlin.math.roundToLong

class UsageService(
    private val serviceCloud: AuthenticatedClient
) {
    suspend fun calculateUsage(directory: String, owner: String, causedById: String? = null): Long {
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
            serviceCloud//.optionallyCausedBy(causedById)
        ).orThrow()

        return result.size?.sum?.roundToLong() ?: run {
            log.warn("Could not retrieve sum from file index!")
            throw RPCException.fromStatusCode(HttpStatusCode.InternalServerError)
        }
    }

    companion object : Loggable {
        override val log: Logger = logger()
    }
}
