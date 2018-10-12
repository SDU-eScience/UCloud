package dk.sdu.cloud.indexing.services

import dk.sdu.cloud.file.api.EventMaterializedStorageFile
import dk.sdu.cloud.file.api.FileType
import dk.sdu.cloud.indexing.api.AllOf
import dk.sdu.cloud.indexing.api.AnyOf
import dk.sdu.cloud.indexing.api.Comparison
import dk.sdu.cloud.indexing.api.ComparisonOperator
import dk.sdu.cloud.indexing.api.FileQuery
import dk.sdu.cloud.indexing.api.NumericStatisticsRequest
import dk.sdu.cloud.indexing.api.StatisticsRequest
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page
import org.apache.http.HttpHost
import org.elasticsearch.client.RestClient
import org.elasticsearch.client.RestHighLevelClient
import org.junit.Ignore
import org.junit.Test

@Ignore
class RealElasticTest {
    // A collection of not really tests that are supposed to be run against a real Elasticsearch instance with real
    // data.
    //
    // Just used to check if we will outright crash on real data. Is meant to be refactored into real tests later.
    private val service = ElasticQueryService(
        RestHighLevelClient(
            RestClient.builder(HttpHost("localhost", 9200, "http"))
        )
    )

    @Test
    fun `test size of home`() {
        val result = service.newStatistics(
            StatisticsRequest(
                FileQuery(
                    roots = listOf("/home/jonas@hinchely.dk")
                ),

                size = NumericStatisticsRequest(
                    calculateSum = true
                )
            )
        )

        println(result)
    }

    @Test
    fun `test number of directories`() {
        val numberOfDirs = service.newStatistics(
            StatisticsRequest(
                FileQuery(
                    roots = listOf("/"),
                    fileTypes = AnyOf.with(FileType.FILE)
                )
            )
        )

        println(numberOfDirs)
    }

    @Test
    fun `test stats`() {
        val result = service.newStatistics(
            StatisticsRequest(
                FileQuery(
                    roots = listOf("/"),
                    fileTypes = AnyOf.with(FileType.FILE)
                ),

                size = NumericStatisticsRequest(
                    calculateMean = true,
                    calculateSum = true,
                    calculateMaximum = true,
                    calculateMinimum = true,
                    percentiles = listOf(5.0, 25.0, 50.0, 75.0, 95.0, 99.999)
                ),

                fileDepth = NumericStatisticsRequest(
                    calculateMean = true,
                    calculateSum = true,
                    calculateMaximum = true,
                    calculateMinimum = true,
                    percentiles = listOf(5.0, 25.0, 50.0, 75.0, 95.0, 99.999)
                )
            )
        )

        println(result)
    }

    @Test
    fun `test listing a directory`() {
        fun listDirectory(
            dir: String,
            page: NormalizedPaginationRequest = NormalizedPaginationRequest(null, null)
        ): Page<EventMaterializedStorageFile> {
            return service.newQuery(
                FileQuery(
                    roots = listOf(dir),
                    fileDepth = AllOf.with(
                        Comparison(dir.removeSuffix("/").count { it == '/' } + 1, ComparisonOperator.EQUALS)
                    )
                ),
                page
            )
        }

        println(listDirectory("/home/jonas@hinchely.dk"))
    }

    @Test
    fun `tree at directory`() {
        fun tree(
            dir: String,
            page: NormalizedPaginationRequest = NormalizedPaginationRequest(null, null)
        ): Page<EventMaterializedStorageFile> {
            return service.newQuery(
                FileQuery(
                    roots = listOf(dir),
                    fileDepth = AllOf.with(
                        Comparison(dir.removeSuffix("/").count { it == '/' } + 1,
                            ComparisonOperator.GREATER_THAN_EQUALS)
                    )
                ),
                page
            )
        }

        println(tree("/home/jonas@hinchely.dk/"))
    }

    @Test
    fun `search for a file`() {
        val result = service.newQuery(
            FileQuery(
                roots = listOf("/home/jonas@hinchely.dk/"),
                fileNameQuery = listOf("stdout", "stderr")
            ),
            NormalizedPaginationRequest(null, null)
        )

        println(result)
    }

    @Test
    fun `files created in the last week`() {
        val result = service.newQuery(
            FileQuery(
                roots = listOf("/"),
                createdAt = AnyOf.with(
                    Comparison(
                        System.currentTimeMillis() - (1000 * 60 * 60 * 24 * 7L),
                        ComparisonOperator.GREATER_THAN_EQUALS
                    )
                )
            ),
            NormalizedPaginationRequest(null, null)
        )

        println(result)
    }
}
