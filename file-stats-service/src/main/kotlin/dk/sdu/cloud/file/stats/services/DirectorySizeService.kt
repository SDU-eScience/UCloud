package dk.sdu.cloud.file.stats.services

import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.indexing.api.*
import java.io.File

class DirectorySizeService(
    private val serviceCloud: AuthenticatedClient
) {
    suspend fun fetchDirectorySizes(
        paths: List<String>,
        username: String,
        causedById: String? = null
    ): Long {
        val parentPaths = paths.map { File(it).parent }
        val result= QueryDescriptions.statistics.call(
            StatisticsRequest(
                query = FileQuery(
                    roots = parentPaths,
                    owner = AllOf.with(username)
                ),
                size = NumericStatisticsRequest(
                    calculateSum = true
                )
            ),
            serviceCloud
        ).orThrow()

        return result.size?.sum?.toLong() ?: 0
    }
}