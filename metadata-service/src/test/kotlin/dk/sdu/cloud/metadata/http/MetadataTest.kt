package dk.sdu.cloud.metadata.http

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import dk.sdu.cloud.client.RESTResponse
import dk.sdu.cloud.metadata.services.*
import dk.sdu.cloud.metadata.utils.withAuthMock
import dk.sdu.cloud.storage.api.FileDescriptions
import dk.sdu.cloud.storage.api.FileType
import io.ktor.application.Application
import io.ktor.cio.toByteReadChannel
import io.ktor.client.response.HttpResponse
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.setBody
import io.ktor.server.testing.withTestApplication
import io.mockk.*
import org.elasticsearch.client.RestHighLevelClient
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.Test
import java.util.*
import kotlin.test.assertEquals

private fun Application.configureMetadataServer(
    elasticMetadataService: ElasticMetadataService,
    projectService: ProjectService = ProjectService(ProjectSQLDao())
) {
    configureMetadataServer(elasticMetadataService, elasticMetadataService, elasticMetadataService, projectService)
}

private fun Application.configureMetadataServer(
    metadataCommandService: ElasticMetadataService,
    metadataQueryService: MetadataQueryService,
    metadataAdvancedQueryService: MetadataAdvancedQueryService,
    projectService: ProjectService = ProjectService(ProjectSQLDao())
) {
    Database.connect(
        url = "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1",
        driver = "org.h2.Driver"
    )

    transaction {
        SchemaUtils.create(Projects)
    }

    configureBaseServer(
        MetadataController(
            metadataCommandService,
            metadataQueryService,
            metadataAdvancedQueryService,
            projectService
        )
    )
}

class MetadataTest {
    @Test
    fun Test() {
        objectMockk(FileDescriptions).use {
            withAuthMock {
                val elasticClient = mockk<RestHighLevelClient>(relaxed = true)
                val elasticService = ElasticMetadataService(
                    elasticClient = elasticClient,
                    projectService = ProjectService(ProjectSQLDao())
                )

                withTestApplication(
                    moduleFunction = { configureMetadataServer(elasticService) },
                    test = {
                        val response =
                            handleRequest(HttpMethod.Put, "/api/projects") {
                                addHeader("Job-Id", UUID.randomUUID().toString())
                                setUser("user1")
                                setBody(
                                    """
                                {
                                "fsRoot" : "/home/user1/folder/test1"
                                }
                                """.trimIndent()
                                )
                            }.response

                        assertEquals(HttpStatusCode.OK, response.status())

                        val response2 =
                            handleRequest(HttpMethod.Get, "/api/projects?path=/home/user1/folder/test1") {
                                addHeader("Job-Id", UUID.randomUUID().toString())
                                setUser("user1")
                            }.response

                        assertEquals(HttpStatusCode.OK, response2.status())

                        val mapper = jacksonObjectMapper()
                        val obj = mapper.readTree(response2.content)
                        assertEquals("\"/home/user1/folder/test1\"", obj["fsRoot"].toString())
                        assertEquals("\"user1\"", obj["owner"].toString())

                        verify {
                            elasticClient.index(
                                match {
                                    it.index() == "foobar" && it.sourceAsMap().containsKey("qwe")
                                },
                                any()
                            )
                        }
                    }
                )
            }
        }
    }
}