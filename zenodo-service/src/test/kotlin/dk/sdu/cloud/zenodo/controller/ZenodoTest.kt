package dk.sdu.cloud.zenodo.controller

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import dk.sdu.cloud.Role
import dk.sdu.cloud.auth.api.AuthDescriptions
import dk.sdu.cloud.auth.api.TokenExtensionResponse
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.MappedEventProducer
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.db.FakeDBSessionFactory
import dk.sdu.cloud.service.test.CloudMock
import dk.sdu.cloud.service.test.TestCallResult
import dk.sdu.cloud.service.test.TokenValidationMock
import dk.sdu.cloud.service.test.createTokenForUser
import dk.sdu.cloud.service.test.withKtorTest
import dk.sdu.cloud.zenodo.api.ZenodoPublication
import dk.sdu.cloud.zenodo.api.ZenodoPublicationStatus
import dk.sdu.cloud.zenodo.api.ZenodoPublishCommand
import dk.sdu.cloud.zenodo.api.ZenodoUpload
import dk.sdu.cloud.zenodo.http.ZenodoController
import dk.sdu.cloud.zenodo.services.PublicationException
import dk.sdu.cloud.zenodo.services.PublicationService
import dk.sdu.cloud.zenodo.services.ZenodoRPCService
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.TestApplicationRequest
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.setBody
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import org.junit.Test
import java.net.URL
import java.util.*
import kotlin.test.assertEquals

fun TestApplicationRequest.setUser(username: String = "user", role: Role = Role.USER) {
    addHeader(
        io.ktor.http.HttpHeaders.Authorization,
        "Bearer ${TokenValidationMock.createTokenForUser(username, role)}"
    )
}

private fun configureZenodoServer(
    zenodoRpcService: ZenodoRPCService = ZenodoRPCService(true, mockk(relaxed = true)),
    publicationService: PublicationService<*> = mockk(relaxed = true),
    eventEmitter: MappedEventProducer<String, ZenodoPublishCommand> = mockk(
        relaxed = true
    )
): List<Controller> {
    return listOf(
        @Suppress("UNCHECKED_CAST")
        ZenodoController(
            FakeDBSessionFactory,
            publicationService as PublicationService<Unit>,
            zenodoRpcService,
            eventEmitter
        )
    )
}

class ZenodoTest {
    @Test
    fun `Is connected test`() {
        withKtorTest(
            setup = {
                val zenodoRpcService = mockk<ZenodoRPCService>()
                every { zenodoRpcService.isConnected(any()) } returns true
                configureZenodoServer(
                    zenodoRpcService = zenodoRpcService
                )
            },

            test = {
                with(engine) {
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
            }
        )
    }

    @Test
    fun `Is connected - false - test`() {
        withKtorTest(
            setup = {
                val zenodoRpcService = mockk<ZenodoRPCService>()
                every { zenodoRpcService.isConnected(any()) } returns false
                configureZenodoServer(
                    zenodoRpcService = zenodoRpcService
                )
            },

            test = {
                with(engine) {
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
            }
        )
    }

    @Test
    fun `List publications test`() {
        withKtorTest(
            setup = {
                val zenodoRpcService = mockk<ZenodoRPCService>()
                val publicationService = mockk<PublicationService<Unit>>()

                coEvery { zenodoRpcService.validateUser(any()) } just Runs
                every { zenodoRpcService.isConnected(any()) } returns true
                every { publicationService.findForUser(Unit, any(), any()) } answers {
                    val zenodoList = listOf(
                        ZenodoPublication(
                            1,
                            "title",
                            ZenodoPublicationStatus.COMPLETE,
                            null,
                            System.currentTimeMillis(),
                            System.currentTimeMillis(),
                            emptyList()
                        ),
                        ZenodoPublication(
                            2,
                            "title2",
                            ZenodoPublicationStatus.PENDING,
                            null,
                            System.currentTimeMillis(),
                            System.currentTimeMillis(),
                            emptyList()
                        )
                    )

                    Page(
                        itemsInTotal = 2,
                        itemsPerPage = 10,
                        pageNumber = 0,
                        items = zenodoList
                    )
                }

                configureZenodoServer(
                    zenodoRpcService = zenodoRpcService,
                    publicationService = publicationService
                )
            },

            test = {
                with(engine) {
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
            }
        )
    }

    @Test
    fun `Publish test`() {
        withKtorTest(
            setup = {
                val zenodoRpcService = mockk<ZenodoRPCService>()
                val publicationService = mockk<PublicationService<Unit>>()

                every {
                    publicationService.createUploadForFiles(
                        Unit,
                        any(),
                        any(),
                        any()
                    )
                } returns 1

                CloudMock.mockCall(
                    AuthDescriptions,
                    { AuthDescriptions.tokenExtension },
                    {
                        TestCallResult.Ok(
                            TokenExtensionResponse(
                                TokenValidationMock.createTokenForUser(
                                    "user",
                                    Role.USER
                                )
                            )
                        )
                    }
                )

                configureZenodoServer(
                    zenodoRpcService = zenodoRpcService,
                    publicationService = publicationService
                )
            },

            test = {
                with(engine) {
                    val response =
                        handleRequest(HttpMethod.Post, "/api/zenodo/publish") {
                            addHeader("Job-Id", UUID.randomUUID().toString())
                            setUser()
                            setBody(
                                """
                                {
                                "name":"Publish1",
                                "filePaths":
                                    [
                                        "home/sdu",
                                        "home/my/file"
                                    ]
                                }
                                """.trimIndent()
                            )
                        }.response

                    assertEquals(HttpStatusCode.OK, response.status())

                    val mapper = jacksonObjectMapper()
                    val obj = mapper.readTree(response.content)

                    assertEquals("1", obj["publicationId"].toString())
                }
            }
        )
    }

    @Test
    fun `Publish - not connected - test`() {
        withKtorTest(
            setup = {
                val zenodoRpcService = mockk<ZenodoRPCService>()
                val publicationService = mockk<PublicationService<Unit>>()

                every {
                    publicationService.createUploadForFiles(
                        Unit,
                        any(),
                        any(),
                        any()
                    )
                } answers {
                    throw PublicationException.NotConnected()
                }

                CloudMock.mockCall(
                    AuthDescriptions,
                    { AuthDescriptions.tokenExtension },
                    {
                        TestCallResult.Ok(
                            TokenExtensionResponse(
                                TokenValidationMock.createTokenForUser(
                                    "user",
                                    Role.USER
                                )
                            )
                        )
                    }
                )
                configureZenodoServer(
                    zenodoRpcService = zenodoRpcService,
                    publicationService = publicationService
                )
            },

            test = {
                with(engine) {
                    val response =
                        handleRequest(HttpMethod.Post, "/api/zenodo/publish") {
                            addHeader("Job-Id", UUID.randomUUID().toString())
                            setUser()
                            setBody(
                                """
                                {
                                "name":"Publish1",
                                "filePaths":
                                    [
                                        "home/sdu",
                                        "home/my/file"
                                    ]
                                }
                                """.trimIndent()
                            )
                        }.response

                    assertEquals(HttpStatusCode.Unauthorized, response.status())
                }
            }
        )
    }

    @Test
    fun `Request access test`() {
        withKtorTest(
            setup = {
                val zenodoRpcService = mockk<ZenodoRPCService>()
                val publicationService = mockk<PublicationService<Unit>>()

                every {
                    zenodoRpcService.createAuthorizationUrl(
                        any(),
                        any()
                    )
                } returns URL("http://cloud.sdu.dk")

                configureZenodoServer(
                    zenodoRpcService = zenodoRpcService,
                    publicationService = publicationService
                )
            },

            test = {
                with(engine) {
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
            }
        )
    }

    @Test
    fun `Find by ID test`() {
        withKtorTest(
            setup = {
                val zenodoRpcService = mockk<ZenodoRPCService>()
                val publicationService = mockk<PublicationService<Unit>>()

                every { publicationService.findById(Unit, any(), any()) } answers {
                    val zenodoUploadList = listOf(
                        ZenodoUpload(
                            "data",
                            true,
                            83901284901283
                        )
                    )
                    val result = ZenodoPublication(
                        1L,
                        "publication result",
                        ZenodoPublicationStatus.COMPLETE,
                        null,
                        99049028139,
                        8921048192301,
                        zenodoUploadList
                    )
                    result
                }

                configureZenodoServer(
                    zenodoRpcService = zenodoRpcService,
                    publicationService = publicationService
                )
            },

            test = {
                with(engine) {
                    val response =
                        handleRequest(HttpMethod.Get, "/api/zenodo/publications/1") {
                            addHeader("Job-Id", UUID.randomUUID().toString())
                            setUser()
                        }.response

                    assertEquals(HttpStatusCode.OK, response.status())
                    println(response.content)
                    assert(response.content.toString().contains(""""id":1,"name":"publication result""""))
                }
            }
        )
    }

    @Test
    fun `Find by ID - not connected - test`() {
        withKtorTest(
            setup = {
                val zenodoRpcService = mockk<ZenodoRPCService>()
                val publicationService = mockk<PublicationService<Unit>>()

                every { publicationService.findById(Unit, any(), any()) } answers {
                    throw PublicationException.NotConnected()
                }

                configureZenodoServer(
                    zenodoRpcService = zenodoRpcService,
                    publicationService = publicationService
                )
            },

            test = {
                with(engine) {
                    val response =
                        handleRequest(HttpMethod.Get, "/api/zenodo/publications/1") {
                            addHeader("Job-Id", UUID.randomUUID().toString())
                            setUser()
                        }.response

                    assertEquals(HttpStatusCode.Unauthorized, response.status())
                }
            }
        )
    }

    @Test
    fun `Find by ID - not Found - test`() {
        withKtorTest(
            setup = {
                val zenodoRpcService = mockk<ZenodoRPCService>()
                val publicationService = mockk<PublicationService<Unit>>()

                every { publicationService.findById(Unit, any(), any()) } answers {
                    throw PublicationException.NotFound()
                }

                configureZenodoServer(
                    zenodoRpcService = zenodoRpcService,
                    publicationService = publicationService
                )
            },

            test = {
                with(engine) {
                    val response =
                        handleRequest(HttpMethod.Get, "/api/zenodo/publications/1") {
                            addHeader("Job-Id", UUID.randomUUID().toString())
                            setUser()
                        }.response

                    assertEquals(HttpStatusCode.NotFound, response.status())

                }
            }
        )
    }
}
