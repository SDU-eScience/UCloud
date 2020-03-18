import dk.sdu.cloud.indexing.api.NumericStatistics
import dk.sdu.cloud.indexing.api.NumericStatisticsRequest
import dk.sdu.cloud.indexing.api.StatisticsRequest
import dk.sdu.cloud.indexing.api.StatisticsResponse
import dk.sdu.cloud.indexing.http.QueryController
import dk.sdu.cloud.indexing.services.ElasticQueryService
import dk.sdu.cloud.indexing.utils.eventMatStorFile
import dk.sdu.cloud.indexing.utils.fileQuery
import dk.sdu.cloud.indexing.utils.queryRequest
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.test.TestUsers
import dk.sdu.cloud.service.test.sendJson
import dk.sdu.cloud.service.test.withKtorTest
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.mockk.every
import io.mockk.mockk
import org.junit.Test
import kotlin.test.assertEquals

private fun configureQueryServer(indexQueryService: ElasticQueryService): List<Controller> {
    return listOf(QueryController(indexQueryService))
}

class QueryTest {

    @Test
    fun `query test`() {
        withKtorTest(
            setup = {
                val indexQueryService = mockk<ElasticQueryService>()
                every { indexQueryService.query(any(), any()) } answers {
                    val returnpage = Page(1, 25, 1, listOf(eventMatStorFile))
                    returnpage
                }

                configureQueryServer(indexQueryService)
            },

            test = {
                with(engine) {
                    val response = sendJson(
                        HttpMethod.Post,
                        "/api/indexing/query",
                        queryRequest,
                        TestUsers.admin
                    ).response

                    assertEquals(HttpStatusCode.OK, response.status())
                }
            }

        )
    }

    @Test
    fun `query test - invalid json`() {
        withKtorTest(
            setup = {
                val indexQueryService = mockk<ElasticQueryService>()
                every { indexQueryService.query(any(), any()) } answers {
                    val returnpage = Page(1, 25, 1, listOf(eventMatStorFile))
                    returnpage
                }
                configureQueryServer(indexQueryService)
            },

            test = {
                with(engine) {
                    val response = sendJson(
                        HttpMethod.Post,
                        "/api/indexing/query",
                        fileQuery,
                        TestUsers.admin
                    ).response

                    assertEquals(HttpStatusCode.BadRequest, response.status())
                }
            }
        )
    }

    private val statisticsRequest = StatisticsRequest(
        fileQuery,
        NumericStatisticsRequest(),
        NumericStatisticsRequest()
    )

    @Test
    fun `statistics test`() {
        withKtorTest(
            setup = {
                val indexQueryService = mockk<ElasticQueryService>()
                every { indexQueryService.statisticsQuery(any()) } answers {
                    val response = StatisticsResponse(
                        22,
                        NumericStatistics(2.0, 1.0, 3.0, 5.0, listOf(22.0)),
                        NumericStatistics(2.0, 1.0, 3.0, 5.0, listOf(22.0))
                    )
                    response
                }
                configureQueryServer(indexQueryService)
            },

            test = {
                with(engine) {
                    val response = sendJson(
                        HttpMethod.Post,
                        "/api/indexing/query/statistics",
                        statisticsRequest,
                        TestUsers.admin
                    ).response

                    assertEquals(HttpStatusCode.OK, response.status())
                }
            }

        )
    }

    @Test
    fun `statistics test - json invalid`() {
        withKtorTest(
            setup = {
                val indexQueryService = mockk<ElasticQueryService>()
                every { indexQueryService.statisticsQuery(any()) } answers {
                    val response = StatisticsResponse(
                        22,
                        NumericStatistics(2.0, 1.0, 3.0, 5.0, listOf(22.0)),
                        NumericStatistics(2.0, 1.0, 3.0, 5.0, listOf(22.0))
                    )
                    response
                }
                configureQueryServer(indexQueryService)
            },

            test = {
                with(engine) {
                    val response = sendJson(
                        HttpMethod.Post,
                        "/api/indexing/query/statistics",
                        fileQuery,
                        TestUsers.admin
                    ).response

                    assertEquals(HttpStatusCode.BadRequest, response.status())
                }
            }
        )
    }
}
