package dk.sdu.cloud.alerting.services

import dk.sdu.cloud.alerting.Configuration
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.toUtf8Bytes
import org.apache.http.Header
import org.apache.http.HttpEntity
import org.elasticsearch.ElasticsearchTimeoutException
import org.elasticsearch.action.admin.cluster.health.ClusterHealthResponse
import org.elasticsearch.action.admin.cluster.settings.ClusterGetSettingsResponse
import org.elasticsearch.client.ClusterClient
import org.elasticsearch.client.Response
import org.elasticsearch.client.RestClient
import org.elasticsearch.client.RestHighLevelClient
import org.elasticsearch.cluster.health.ClusterHealthStatus
import org.junit.Test
import java.io.ByteArrayInputStream
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

        ea.setErrorCount(40)
        runBlocking {
            ea.alertOnClusterHealth()
        }
        assertEquals(Status.YELLOW, ea.getStatus())
        assertEquals(40, ea.getErrorCount())

        runBlocking {
            ea.alertOnClusterHealth()
        }

        assertEquals(Status.YELLOW, ea.getStatus())
        assertEquals(40, ea.getErrorCount())
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
    fun `alert on doc count - not 200 response`() {
        val rest = mockk<RestHighLevelClient>()
        val alerting = mockk<AlertingService>()
        val ea = ElasticAlerting(rest, alerting, true)

        val lowRest = mockk<RestClient>()

        every { lowRest.performRequest(any()) } answers {
            val response = mockk<Response>()
            every { response.statusLine.statusCode} returns 404
            response
        }

        runBlocking {
            ea.alertOnNumberOfDocs(lowRest)
        }
    }

    @Test
    fun `alert on doc count - low limit`() {
        val rest = mockk<RestHighLevelClient>()
        val alerting = mockk<AlertingService>()
        val ea = ElasticAlerting(rest, alerting, true)

        val lowRest = mockk<RestClient>()

        every { lowRest.performRequest(any()) } answers {
            val response = mockk<Response>()
            every { response.statusLine.statusCode} returns 200
            every { response.entity} answers {
                val entity = mockk<HttpEntity>()
                every {entity.contentType} answers {
                    mockk<Header>(relaxed = true)
                }
                every { entity.contentLength } returns "index 1600000000".length.toLong()
                every { entity.content } answers {
                    ByteArrayInputStream( "index 1600000000".toUtf8Bytes())
                }
                entity
            }
            response
        }

        coEvery { alerting.createAlert(any()) } just runs

        runBlocking {
            ea.alertOnNumberOfDocs(lowRest)
        }
    }

    @Test
    fun `alert on doc count - high limit`() {
        val rest = mockk<RestHighLevelClient>()
        val alerting = mockk<AlertingService>()
        val ea = ElasticAlerting(rest, alerting, true)

        val lowRest = mockk<RestClient>()

        every { lowRest.performRequest(any()) } answers {
            val response = mockk<Response>()
            every { response.statusLine.statusCode} returns 200
            every { response.entity} answers {
                val entity = mockk<HttpEntity>()
                every {entity.contentType} answers {
                    mockk<Header>(relaxed = true)
                }
                every { entity.contentLength } returns "index 2000000000".length.toLong()
                every { entity.content } answers {
                    ByteArrayInputStream( "index 2000000000".toUtf8Bytes())
                }
                entity
            }
            response
        }

        coEvery { alerting.createAlert(any()) } just runs

        runBlocking {
            ea.alertOnNumberOfDocs(lowRest)
        }
    }

    @Test
    fun `alert on doc count`() {
        val rest = mockk<RestHighLevelClient>()
        val alerting = mockk<AlertingService>()
        val ea = ElasticAlerting(rest, alerting, true)

        val lowRest = mockk<RestClient>()

        every { lowRest.performRequest(any()) } answers {
            val response = mockk<Response>()
            every { response.statusLine.statusCode} returns 200
            every { response.entity} answers {
                val entity = mockk<HttpEntity>()
                every {entity.contentType} answers {
                    mockk<Header>(relaxed = true)
                }
                every { entity.contentLength } returns "index 84".length.toLong()
                every { entity.content } answers {
                    ByteArrayInputStream( "index 84".toUtf8Bytes())
                }
                entity
            }
            response
        }

        coEvery { alerting.createAlert(any()) } just runs

        runBlocking {
            ea.alertOnNumberOfDocs(lowRest)
        }
    }

}
