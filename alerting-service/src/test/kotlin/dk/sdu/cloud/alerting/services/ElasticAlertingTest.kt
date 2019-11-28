package dk.sdu.cloud.alerting.services

import dk.sdu.cloud.alerting.Configuration
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlinx.coroutines.runBlocking
import org.elasticsearch.action.admin.cluster.health.ClusterHealthResponse
import org.elasticsearch.client.ClusterClient
import org.elasticsearch.client.Response
import org.elasticsearch.client.RestClient
import org.elasticsearch.client.RestHighLevelClient
import org.elasticsearch.cluster.health.ClusterHealthStatus
import org.junit.Test
import java.net.ConnectException
import kotlin.test.assertEquals

class ElasticAlertingTest {

    @Test
    fun `test status green`() {
        val rest = mockk<RestHighLevelClient>()
        val alerting = mockk<AlertingService>()

        every { rest.cluster() } answers {
            val clusterClient = mockk<ClusterClient>()
            every { clusterClient.health(any(), any()) } answers {
                val response = mockk<ClusterHealthResponse>()
                every { response.status } returns ClusterHealthStatus.GREEN
                response
            }
            clusterClient
        }

        val ea = ElasticAlerting(rest, alerting, true)
        runBlocking {
            ea.alertOnClusterHealth()
        }
    }

    @Test
    fun `test status green - alert active`() {
        val rest = mockk<RestHighLevelClient>()
        val alerting = mockk<AlertingService>()

        every { rest.cluster() } answers {
            val clusterClient = mockk<ClusterClient>()
            every { clusterClient.health(any(), any()) } answers {
                val response = mockk<ClusterHealthResponse>()
                every { response.status } returns ClusterHealthStatus.GREEN
                response
            }
            clusterClient
        }
        coEvery { alerting.createAlert(any())} just runs

        val ea = ElasticAlerting(rest, alerting, true)
        ea.setAlertSent(true)
        runBlocking {
            ea.alertOnClusterHealth()
        }
    }

    @Test (expected = ConnectException::class)
    fun `test lost connection`() {
        val rest = mockk<RestHighLevelClient>()
        val alerting = mockk<AlertingService>()

        every { rest.cluster() } answers {
            val clusterClient = mockk<ClusterClient>()
            every { clusterClient.health(any(), any()) } answers {
                throw ConnectException()
            }
            clusterClient
        }

        val ea = ElasticAlerting(rest, alerting, true)

        runBlocking {
            ea.alertOnClusterHealth()
        }
    }

    @Test
    fun `test status yellow - zero error count and raise until alert`() {
        val rest = mockk<RestHighLevelClient>()
        val alerting = mockk<AlertingService>()

        every { rest.cluster() } answers {
            val clusterClient = mockk<ClusterClient>()
            every { clusterClient.health(any(), any()) } answers {
                val response = mockk<ClusterHealthResponse>()
                every { response.status } returns ClusterHealthStatus.YELLOW
                response
            }
            clusterClient
        }
        coEvery { alerting.createAlert(any())} just runs

        val ea = ElasticAlerting(rest, alerting, true)

        assertEquals(Status.GREEN, ea.getStatus())
        assertEquals(0, ea.getErrorCount())

        runBlocking {
            ea.alertOnClusterHealth()
        }

        assertEquals(Status.YELLOW, ea.getStatus())
        assertEquals(0, ea.getErrorCount())

        runBlocking {
            ea.alertOnClusterHealth()
        }
        assertEquals(Status.YELLOW, ea.getStatus())
        assertEquals(1, ea.getErrorCount())

        ea.setErrorCount(10)
        runBlocking {
            ea.alertOnClusterHealth()
        }
        assertEquals(Status.YELLOW, ea.getStatus())
        assertEquals(10, ea.getErrorCount())

        runBlocking {
            ea.alertOnClusterHealth()
        }

        assertEquals(Status.YELLOW, ea.getStatus())
        assertEquals(10, ea.getErrorCount())
    }

    @Test
    fun `test status red - zero error count`() {
        val rest = mockk<RestHighLevelClient>()
        val alerting = mockk<AlertingService>()

        every { rest.cluster() } answers {
            val clusterClient = mockk<ClusterClient>()
            every { clusterClient.health(any(), any()) } answers {
                val response = mockk<ClusterHealthResponse>()
                every { response.status } returns ClusterHealthStatus.RED
                response
            }
            clusterClient
        }

        coEvery { alerting.createAlert(any())} just runs

        val ea = ElasticAlerting(rest, alerting, true)

        assertEquals(Status.GREEN, ea.getStatus())
        assertEquals(0, ea.getErrorCount())

        runBlocking {
            ea.alertOnClusterHealth()
        }

        ea.setErrorCount(1)
        runBlocking {
            ea.alertOnClusterHealth()
        }

        assertEquals(2, ea.getErrorCount())

        runBlocking {
            ea.alertOnClusterHealth()
        }

        assertEquals(2, ea.getErrorCount())
        assertEquals(Status.RED, ea.getStatus())
    }

    @Test
    fun `alert on indices count - not 200 response on health request`() {
        val rest = mockk<RestHighLevelClient>()
        val alerting = mockk<AlertingService>()
        val ea = ElasticAlerting(rest, alerting, true)

        val lowRest = mockk<RestClient>()

        every { lowRest.performRequest(any()) } answers {
            val response = mockk<Response>()
            every { response.statusLine.statusCode } returns 400
            response
        }

        runBlocking {ea.alertOnIndicesCount(lowRest, Configuration())}

    }

}
