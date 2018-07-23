package dk.sdu.cloud.zenodo.controller

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import dk.sdu.cloud.auth.api.JWTProtection
import dk.sdu.cloud.auth.api.Role
import dk.sdu.cloud.auth.api.protect
import dk.sdu.cloud.metadata.utils.withAuthMock
import dk.sdu.cloud.service.*
import dk.sdu.cloud.zenodo.api.*
import dk.sdu.cloud.zenodo.http.Controller
import dk.sdu.cloud.zenodo.http.ZenodoController
import dk.sdu.cloud.zenodo.services.*
import io.ktor.application.Application
import io.ktor.application.install
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.routing.route
import io.ktor.routing.routing
import io.ktor.server.testing.TestApplicationRequest
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.setBody
import io.ktor.server.testing.withTestApplication
import io.mockk.*
import org.junit.Test
import java.net.URL
import java.util.*
import kotlin.test.assertEquals

fun TestApplicationRequest.setUser(username: String = "user", role: Role = dk.sdu.cloud.auth.api.Role.USER) {
    addHeader(io.ktor.http.HttpHeaders.Authorization, "Bearer $username/$role")
}

fun Application.configureBaseServer(vararg controllers: Controller) {
    installDefaultFeatures(
        mockk(relaxed = true),
        mockk(relaxed = true),
        mockk(relaxed = true),
        requireJobId = true
    )

    install(JWTProtection)

    routing {
        protect()
        for (controller in controllers) {
            route(controller.baseContext) {
                controller.configure(this)
            }
        }
    }
}

private fun Application.configureZenodoServer(
    kafka: KafkaServices = mockk(relaxed = true),
    zenodoRpcService: ZenodoRPCService = ZenodoRPCService(mockk(relaxed = true)),
    publicationService: PublicationService =
        PublicationService(mockk(relaxed = true), zenodoRpcService),
    eventEmitter: MappedEventProducer<String, ZenodoPublishCommand> = mockk(relaxed = true)
) {
    configureBaseServer(ZenodoController(kafka, publicationService, zenodoRpcService, eventEmitter))
}

class ZenodoTest {

    @Test
    fun `Is connected test`() {
        withAuthMock {
            withTestApplication(
                moduleFunction = {
                    val zenodoRpcService = mockk<ZenodoRPCService>()
                    configureZenodoServer(
                        zenodoRpcService = zenodoRpcService
                    )
                    every { zenodoRpcService.isConnected(any()) } returns true
                },

                test = {
                    val response =
                        handleRequest(HttpMethod.Get, "/api/zenodo/status") {
                            addHeader("Job-Id", UUID.randomUUID().toString())
                            setUser()
                        }.response

                    assertEquals(HttpStatusCode.OK, response.status())

                    val mapper = jacksonObjectMapper()
                    val obj = mapper.readTree(response.content)

                    assertEquals("true", obj["connected"].toString())
                }
            )
        }
    }

    @Test
    fun `Is connected - false - test`() {
        withAuthMock {
            withTestApplication(
                moduleFunction = {
                    val zenodoRpcService = mockk<ZenodoRPCService>()
                    configureZenodoServer(
                        zenodoRpcService = zenodoRpcService
                    )
                    every { zenodoRpcService.isConnected(any()) } returns false
                },

                test = {
                    val response =
                        handleRequest(HttpMethod.Get, "/api/zenodo/status") {
                            addHeader("Job-Id", UUID.randomUUID().toString())
                            setUser()
                        }.response

                    assertEquals(HttpStatusCode.OK, response.status())

                    val mapper = jacksonObjectMapper()
                    val obj = mapper.readTree(response.content)

                    assertEquals("false", obj["connected"].toString())
                }
            )
        }
    }

    @Test
    fun `List publications test`() {
        withAuthMock {
            withTestApplication(
                moduleFunction = {
                    val zenodoRpcService = mockk<ZenodoRPCService>()
                    val publicationService = mockk<PublicationService>()

                    configureZenodoServer(
                        zenodoRpcService = zenodoRpcService,
                        publicationService = publicationService
                    )
                    coEvery { zenodoRpcService.validateToken(any()) } just Runs
                    every { zenodoRpcService.isConnected(any()) } returns true
                    every { publicationService.findForUser(any(), any()) } answers {
                        val zenodoList = listOf<ZenodoPublication>(
                            ZenodoPublication(
                                1,
                                "title",
                                ZenodoPublicationStatus.COMPLETE,
                                null,
                                System.currentTimeMillis(),
                                System.currentTimeMillis()
                            ),
                            ZenodoPublication(
                                2,
                                "title2",
                                ZenodoPublicationStatus.PENDING,
                                null,
                                System.currentTimeMillis(),
                                System.currentTimeMillis()
                            )
                        )
                        val page = Page<ZenodoPublication>(
                            2,
                            10,
                            0,
                            zenodoList
                        )
                        val publications = ZenodoPublicationList(
                            page
                        )
                        publications

                    }
                },

                test = {
                    val response =
                        handleRequest(HttpMethod.Get, "/api/zenodo/publications") {
                            addHeader("Job-Id", UUID.randomUUID().toString())
                            setUser()
                        }.response

                    assertEquals(HttpStatusCode.OK, response.status())
                    //TODO Should be better way to to do this
                    assert(
                        response.content.toString().contains(
                            """"id":1,"name":"title","status":"COMPLETE""""
                        ) && response.content.toString().contains(
                            """"id":2,"name":"title2","status":"PENDING""""
                        )
                    )
                }
            )
        }
    }

    @Test
    fun `List publications - missing authentication - test`() {
        withAuthMock {
            withTestApplication(
                moduleFunction = {
                    val zenodoRpcService = mockk<ZenodoRPCService>()
                    val publicationService = mockk<PublicationService>()

                    configureZenodoServer(
                        zenodoRpcService = zenodoRpcService,
                        publicationService = publicationService
                    )
                    coEvery { zenodoRpcService.validateToken(any()) } answers {
                        throw MissingOAuthToken()
                    }
                },

                test = {
                    val response =
                        handleRequest(HttpMethod.Get, "/api/zenodo/publications") {
                            addHeader("Job-Id", UUID.randomUUID().toString())
                            setUser()
                        }.response

                    assertEquals(HttpStatusCode.BadRequest, response.status())
                }
            )
        }
    }

    @Test
    fun `List publications - Too many retries - test`() {
        withAuthMock {
            withTestApplication(
                moduleFunction = {
                    val zenodoRpcService = mockk<ZenodoRPCService>()
                    val publicationService = mockk<PublicationService>()

                    configureZenodoServer(
                        zenodoRpcService = zenodoRpcService,
                        publicationService = publicationService
                    )
                    coEvery { zenodoRpcService.validateToken(any()) } answers {
                        throw TooManyRetries()
                    }
                },

                test = {
                    val response =
                        handleRequest(HttpMethod.Get, "/api/zenodo/publications") {
                            addHeader("Job-Id", UUID.randomUUID().toString())
                            setUser()
                        }.response

                    assertEquals(HttpStatusCode.BadGateway, response.status())
                }
            )
        }
    }

    @Test
    fun `Publish test`() {
        withAuthMock {
            withTestApplication(
                moduleFunction = {
                    val zenodoRpcService = mockk<ZenodoRPCService>()
                    val publicationService = mockk<PublicationService>()
                    val kafka = mockk<KafkaServices>()
                    configureZenodoServer(
                        kafka = kafka,
                        zenodoRpcService = zenodoRpcService,
                        publicationService = publicationService
                    )

                    every { publicationService.createUploadForFiles(
                        any(),
                        any(),
                        any()
                    ) } returns 1

                },

                test = {
                    val response =
                        handleRequest(HttpMethod.Post, "/api/zenodo/publish") {
                            addHeader("Job-Id", UUID.randomUUID().toString())
                            setUser()
                            setBody("""
                                {
                                "name":"Publish1",
                                "filePaths":
                                    [
                                        "home/sdu",
                                        "home/my/file"
                                    ]
                                }
                                """.trimIndent())
                        }.response

                    assertEquals(HttpStatusCode.OK, response.status())

                    val mapper = jacksonObjectMapper()
                    val obj = mapper.readTree(response.content)

                    assertEquals("1", obj["publicationId"].toString())
                }
            )
        }
    }

    @Test
    fun `Publish - not connected - test`() {
        withAuthMock {
            withTestApplication(
                moduleFunction = {
                    val zenodoRpcService = mockk<ZenodoRPCService>()
                    val publicationService = mockk<PublicationService>()
                    val kafka = mockk<KafkaServices>()
                    configureZenodoServer(
                        kafka = kafka,
                        zenodoRpcService = zenodoRpcService,
                        publicationService = publicationService
                    )

                    every { publicationService.createUploadForFiles(
                        any(),
                        any(),
                        any()
                    ) } answers {
                        throw PublicationException.NotConnected()
                    }

                },

                test = {
                    val response =
                        handleRequest(HttpMethod.Post, "/api/zenodo/publish") {
                            addHeader("Job-Id", UUID.randomUUID().toString())
                            setUser()
                            setBody("""
                                {
                                "name":"Publish1",
                                "filePaths":
                                    [
                                        "home/sdu",
                                        "home/my/file"
                                    ]
                                }
                                """.trimIndent())
                        }.response

                    assertEquals(HttpStatusCode.Unauthorized, response.status())
                }
            )
        }
    }

    @Test
    fun `Request access test`() {
        withAuthMock {
            withTestApplication(
                moduleFunction = {
                    val zenodoRpcService = mockk<ZenodoRPCService>()
                    val publicationService = mockk<PublicationService>()
                    val kafka = mockk<KafkaServices>()
                    configureZenodoServer(
                        kafka = kafka,
                        zenodoRpcService = zenodoRpcService,
                        publicationService = publicationService
                    )

                    every { zenodoRpcService.createAuthorizationUrl(any(), any()) } returns URL("http://cloud.sdu.dk")
                },

                test = {
                    val response =
                        handleRequest(HttpMethod.Post, "/api/zenodo/request?returnTo=http://cloud.sdu.dk") {
                            addHeader("Job-Id", UUID.randomUUID().toString())
                            setUser()
                        }.response

                    assertEquals(HttpStatusCode.OK, response.status())

                    val mapper = jacksonObjectMapper()
                    val obj = mapper.readTree(response.content)

                    assertEquals("\"http://cloud.sdu.dk\"", obj["redirectTo"].toString())

                }
            )
        }
    }

    @Test
    fun `Find by ID test`() {
        withAuthMock {
            withTestApplication(
                moduleFunction = {
                    val zenodoRpcService = mockk<ZenodoRPCService>()
                    val publicationService = mockk<PublicationService>()
                    val kafka = mockk<KafkaServices>()
                    configureZenodoServer(
                        kafka = kafka,
                        zenodoRpcService = zenodoRpcService,
                        publicationService = publicationService
                    )

                    every { publicationService.findById(any(), any()) } answers {
                        val zenodoUploadList = listOf<ZenodoUpload>(
                            ZenodoUpload("data",
                                true,
                                83901284901283)
                        )
                        val result = ZenodoPublicationWithFiles(
                            ZenodoPublication(
                                1,
                                "publication result",
                                ZenodoPublicationStatus.COMPLETE,
                                null,
                                99049028139,
                                8921048192301
                            ), zenodoUploadList
                        )
                        result
                    }
                },

                test = {
                    val response =
                        handleRequest(HttpMethod.Get, "/api/zenodo/publications/1") {
                            addHeader("Job-Id", UUID.randomUUID().toString())
                            setUser()
                        }.response

                    assertEquals(HttpStatusCode.OK, response.status())
                    println(response.content)
                    assert(response.content.toString().contains(""""id":1,"name":"publication result""""))
                }
            )
        }
    }

    @Test
    fun `Find by ID - not connected - test`() {
        withAuthMock {
            withTestApplication(
                moduleFunction = {
                    val zenodoRpcService = mockk<ZenodoRPCService>()
                    val publicationService = mockk<PublicationService>()
                    val kafka = mockk<KafkaServices>()
                    configureZenodoServer(
                        kafka = kafka,
                        zenodoRpcService = zenodoRpcService,
                        publicationService = publicationService
                    )

                    every { publicationService.findById(any(), any()) } answers {
                        throw PublicationException.NotConnected()
                    }

                },

                test = {
                    val response =
                        handleRequest(HttpMethod.Get, "/api/zenodo/publications/1") {
                            addHeader("Job-Id", UUID.randomUUID().toString())
                            setUser()
                        }.response

                    assertEquals(HttpStatusCode.Unauthorized, response.status())

                }
            )
        }
    }

    @Test
    fun `Find by ID - not Found - test`() {
        withAuthMock {
            withTestApplication(
                moduleFunction = {
                    val zenodoRpcService = mockk<ZenodoRPCService>()
                    val publicationService = mockk<PublicationService>()
                    val kafka = mockk<KafkaServices>()
                    configureZenodoServer(
                        kafka = kafka,
                        zenodoRpcService = zenodoRpcService,
                        publicationService = publicationService
                    )

                    every { publicationService.findById(any(), any()) } answers {
                        throw PublicationException.NotFound()
                    }

                },

                test = {
                    val response =
                        handleRequest(HttpMethod.Get, "/api/zenodo/publications/1") {
                            addHeader("Job-Id", UUID.randomUUID().toString())
                            setUser()
                        }.response

                    assertEquals(HttpStatusCode.NotFound, response.status())

                }
            )
        }
    }
}
