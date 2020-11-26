package dk.sdu.cloud.alerting.services

import dk.sdu.cloud.service.test.ClientMock
import dk.sdu.cloud.slack.api.SlackDescriptions
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.apache.http.Header
import org.apache.http.HttpEntity
import org.elasticsearch.action.admin.cluster.health.ClusterHealthResponse
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
        val client = ClientMock.authenticatedClient
        every { rest.cluster() } answers {
            val clusterClient = mockk<ClusterClient>()
            every { clusterClient.health(any(), any()) } answers {
                val response = mockk<ClusterHealthResponse>()
                every { response.status } returns ClusterHealthStatus.GREEN
                response
            }
            clusterClient
        }

        val ea = ElasticAlerting(rest, client, true)
        runBlocking {
            ea.alertOnClusterHealth()
        }
    }

    @Test
    fun `test status green - alert active`() {
        val rest = mockk<RestHighLevelClient>()
        val client = ClientMock.authenticatedClient

        every { rest.cluster() } answers {
            val clusterClient = mockk<ClusterClient>()
            every { clusterClient.health(any(), any()) } answers {
                val response = mockk<ClusterHealthResponse>()
                every { response.status } returns ClusterHealthStatus.GREEN
                response
            }
            clusterClient
        }
        ClientMock.mockCallSuccess(
            SlackDescriptions.sendAlert,
            Unit
        )
        val ea = ElasticAlerting(rest, client, true)
        ea.setAlertSent(true)
        runBlocking {
            ea.alertOnClusterHealth()
        }
    }

    @Test (expected = ConnectException::class)
    fun `test lost connection`() {
        val rest = mockk<RestHighLevelClient>()
        val client = ClientMock.authenticatedClient

        every { rest.cluster() } answers {
            val clusterClient = mockk<ClusterClient>()
            every { clusterClient.health(any(), any()) } answers {
                throw ConnectException()
            }
            clusterClient
        }

        val ea = ElasticAlerting(rest, client, true)

        runBlocking {
            ea.alertOnClusterHealth()
        }
    }

    @Test
    fun `test status yellow - zero error count and raise until alert`() {
        val rest = mockk<RestHighLevelClient>()
        val client = ClientMock.authenticatedClient

        every { rest.cluster() } answers {
            val clusterClient = mockk<ClusterClient>()
            every { clusterClient.health(any(), any()) } answers {
                val response = mockk<ClusterHealthResponse>()
                every { response.status } returns ClusterHealthStatus.YELLOW
                response
            }
            clusterClient
        }
        ClientMock.mockCallSuccess(
            SlackDescriptions.sendAlert,
            Unit
        )
        val ea = ElasticAlerting(rest, client, true)

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
        val client = ClientMock.authenticatedClient

        every { rest.cluster() } answers {
            val clusterClient = mockk<ClusterClient>()
            every { clusterClient.health(any(), any()) } answers {
                val response = mockk<ClusterHealthResponse>()
                every { response.status } returns ClusterHealthStatus.RED
                response
            }
            clusterClient
        }

        ClientMock.mockCallSuccess(
            SlackDescriptions.sendAlert,
            Unit
        )
        val ea = ElasticAlerting(rest, client, true)

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
        val client = ClientMock.authenticatedClient

        val ea = ElasticAlerting(rest, client, true)

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
        val client = ClientMock.authenticatedClient

        val ea = ElasticAlerting(rest, client, true)

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
                    ByteArrayInputStream( "index 1600000000".toByteArray())
                }
                entity
            }
            response
        }

        ClientMock.mockCallSuccess(
            SlackDescriptions.sendAlert,
            Unit
        )
        runBlocking {
            ea.alertOnNumberOfDocs(lowRest)
        }
    }

    @Test
    fun `alert on doc count - high limit`() {
        val rest = mockk<RestHighLevelClient>()
        val client = ClientMock.authenticatedClient

        val ea = ElasticAlerting(rest, client, true)

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
                    ByteArrayInputStream( "index 2000000000".toByteArray())
                }
                entity
            }
            response
        }

        ClientMock.mockCallSuccess(
            SlackDescriptions.sendAlert,
            Unit
        )
        runBlocking {
            ea.alertOnNumberOfDocs(lowRest)
        }
    }

    @Test
    fun `alert on doc count`() {
        val rest = mockk<RestHighLevelClient>()
        val client = ClientMock.authenticatedClient

        val ea = ElasticAlerting(rest, client, true)

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
                    ByteArrayInputStream( "index 84".toByteArray())
                }
                entity
            }
            response
        }

        ClientMock.mockCallSuccess(
            SlackDescriptions.sendAlert,
            Unit
        )
        runBlocking {
            ea.alertOnNumberOfDocs(lowRest)
        }
    }

}
