package dk.sdu.cloud.metadata.http

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import dk.sdu.cloud.auth.api.JWTProtection
import dk.sdu.cloud.auth.api.Role
import dk.sdu.cloud.auth.api.protect
import dk.sdu.cloud.client.RESTResponse
import dk.sdu.cloud.metadata.api.ProjectEventProducer
import dk.sdu.cloud.metadata.services.ProjectSQLDao
import dk.sdu.cloud.metadata.services.ProjectService
import dk.sdu.cloud.metadata.services.Projects
import dk.sdu.cloud.metadata.utils.withAuthMock
import dk.sdu.cloud.service.installDefaultFeatures
import dk.sdu.cloud.storage.api.FileDescriptions
import dk.sdu.cloud.storage.api.FileType
import io.ktor.application.Application
import io.ktor.application.install
import io.ktor.cio.toByteReadChannel
import io.ktor.client.response.HttpResponse
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.routing.route
import io.ktor.routing.routing
import io.ktor.server.testing.TestApplicationRequest
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.setBody
import io.ktor.server.testing.withTestApplication
import io.mockk.*
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils.create
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.Test
import java.util.*
import kotlin.test.assertEquals

fun TestApplicationRequest.setUser(username: String = "user", role: Role = Role.USER) {
    addHeader(HttpHeaders.Authorization, "Bearer $username/$role")
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

private fun Application.configureProjectServer(
    producer: ProjectEventProducer = mockk(relaxed = true),
    projectService: ProjectService = ProjectService(ProjectSQLDao())
) {
    Database.connect(
        url = "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1",
        driver = "org.h2.Driver"
    )

    transaction {
        create(Projects)
    }

    configureBaseServer(ProjectsController(producer, projectService))
}

class ProjectTest {
    @Test
    fun createAndGetProjectTest() {
        objectMockk(FileDescriptions).use {
            withAuthMock {
                withTestApplication(
                    moduleFunction = {
                        coEvery { FileDescriptions.syncFileList.call(any(), any()) } answers {
                            val response: HttpResponse = mockk(relaxed = true)

                            every { response.content } answers {
                                val payload = listOf(
                                    SyncItem(
                                        FileType.DIRECTORY,
                                        "someid",
                                        "user1",
                                        0,
                                        null,
                                        null,
                                        "/home/user1/folder/test1"
                                    )
                                ).joinToString("\n") { it.toString() }

                                payload.byteInputStream().toByteReadChannel()
                            }
                            RESTResponse.Ok(response, Unit)
                        }

                        coEvery { FileDescriptions.annotate.call(any(), any()) } answers {
                            RESTResponse.Ok(mockk(relaxed = true), Unit)
                        }

                        configureProjectServer()
                    },

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

                    }
                )
            }
        }
    }

    @Test
    fun createProjectAnnotationErr() {
        objectMockk(FileDescriptions).use {
            withAuthMock {
                withTestApplication(
                    moduleFunction = {
                        coEvery { FileDescriptions.syncFileList.call(any(), any()) } answers {
                            val response: HttpResponse = mockk(relaxed = true)

                            every { response.content } answers {
                                val payload = listOf(
                                    SyncItem(
                                        FileType.DIRECTORY,
                                        "someid",
                                        "user1",
                                        0,
                                        null,
                                        null,
                                        "/home/user1/folder/"
                                    )
                                ).joinToString("\n") { it.toString() }

                                payload.byteInputStream().toByteReadChannel()
                            }
                            RESTResponse.Ok(response, Unit)
                        }

                        coEvery { FileDescriptions.annotate.call(any(), any()) } answers {
                            RESTResponse.Err(mockk(relaxed = true))
                        }

                        configureProjectServer()
                    },

                    test = {
                        val response =
                            handleRequest(HttpMethod.Put, "/api/projects") {
                                addHeader("Job-Id", UUID.randomUUID().toString())
                                setUser("user1")
                                setBody(
                                    """
                                {
                                "fsRoot" : "/home/user1/folder"
                                }
                                """.trimIndent()
                                )
                            }.response

                        assertEquals(HttpStatusCode.InternalServerError, response.status())
                    }
                )
            }
        }
    }

    @Test
    fun createProjectWrongPathTest() {
        objectMockk(FileDescriptions).use {
            withAuthMock {
                withTestApplication(
                    moduleFunction = {
                        coEvery { FileDescriptions.syncFileList.call(any(), any()) } answers {
                            val response: HttpResponse = mockk(relaxed = true)

                            every { response.content } answers {
                                val payload = listOf(
                                    SyncItem(
                                        FileType.DIRECTORY,
                                        "someid",
                                        "user",
                                        0,
                                        null,
                                        null,
                                        "/home/user1/folder/"
                                    )
                                ).joinToString("\n") { it.toString() }

                                payload.byteInputStream().toByteReadChannel()
                            }
                            RESTResponse.Ok(response, Unit)
                        }

                        configureProjectServer()
                    },

                    test = {
                        val response =
                            handleRequest(HttpMethod.Put, "/api/projects") {
                                addHeader("Job-Id", UUID.randomUUID().toString())
                                setUser("user1")
                                setBody(
                                    """
                                {
                                "fsRoot" : "/home/user1/folder/notThere"
                                }
                                """.trimIndent()
                                )
                            }.response

                        assertEquals(HttpStatusCode.Forbidden, response.status())
                    }
                )
            }
        }
    }

    @Test
    fun createProjectNotOwnerTest() {
        objectMockk(FileDescriptions).use {
            withAuthMock {
                withTestApplication(
                    moduleFunction = {
                        coEvery { FileDescriptions.syncFileList.call(any(), any()) } answers {
                            val response: HttpResponse = mockk(relaxed = true)

                            every { response.content } answers {
                                val payload = listOf(
                                    SyncItem(
                                        FileType.DIRECTORY,
                                        "someid",
                                        "user",
                                        0,
                                        null,
                                        null,
                                        "/home/user1/folder/"
                                    )
                                ).joinToString("\n") { it.toString() }

                                payload.byteInputStream().toByteReadChannel()
                            }
                            RESTResponse.Ok(response, Unit)
                        }

                        configureProjectServer()
                    },

                    test = {
                        val response =
                            handleRequest(HttpMethod.Put, "/api/projects") {
                                addHeader("Job-Id", UUID.randomUUID().toString())
                                setUser("user1")
                                setBody(
                                    """
                                {
                                "fsRoot" : "/home/user1/folder/"
                                }
                                """.trimIndent()
                                )
                            }.response

                        assertEquals(HttpStatusCode.Forbidden, response.status())
                    }
                )
            }
        }
    }

    @Test
    fun createExistingProjectTest() {
        objectMockk(FileDescriptions).use {
            withAuthMock {
                withTestApplication(
                    moduleFunction = {
                        coEvery { FileDescriptions.syncFileList.call(any(), any()) } answers {
                            val response: HttpResponse = mockk(relaxed = true)

                            every { response.content } answers {
                                val payload = listOf(
                                    SyncItem(
                                        FileType.DIRECTORY,
                                        "someid",
                                        "user1",
                                        0,
                                        null,
                                        null,
                                        "/home/user1/folder/"
                                    )
                                ).joinToString("\n") { it.toString() }

                                payload.byteInputStream().toByteReadChannel()
                            }
                            RESTResponse.Ok(response, Unit)
                        }

                        coEvery { FileDescriptions.annotate.call(any(), any()) } answers {
                            RESTResponse.Ok(mockk(relaxed = true), Unit)
                        }

                        configureProjectServer()
                    },

                    test = {
                        val response =
                            handleRequest(HttpMethod.Put, "/api/projects") {
                                addHeader("Job-Id", UUID.randomUUID().toString())
                                setUser("user1")
                                setBody(
                                    """
                                {
                                "fsRoot" : "/home/user1/folder"
                                }
                                """.trimIndent()
                                )
                            }.response

                        assertEquals(HttpStatusCode.OK, response.status())

                       val response2 =
                            handleRequest(HttpMethod.Put, "/api/projects") {
                                addHeader("Job-Id", UUID.randomUUID().toString())
                                setUser("user1")
                                setBody(
                                    """
                                {
                                "fsRoot" : "/home/user1/folder"
                                }
                                """.trimIndent()
                                )
                            }.response
                        //TODO Should fail due to duplicate. Project creating does not check to se if fsroot have been used.
                        //TODO If not handled, project dublicates will exisit and findByPath will fail due to singleOrNull
                        assertEquals(HttpStatusCode.BadRequest, response2.status())
                    }
                )
            }
        }
    }

    @Test
    fun getNonexistingProjectTest() {
        objectMockk(FileDescriptions).use {
            withAuthMock {
                withTestApplication(
                    moduleFunction = {
                        coEvery { FileDescriptions.syncFileList.call(any(), any()) } answers {
                            val response: HttpResponse = mockk(relaxed = true)

                            every { response.content } answers {
                                val payload = listOf(
                                    SyncItem(
                                        FileType.DIRECTORY,
                                        "someid",
                                        "user1",
                                        0,
                                        null,
                                        null,
                                        "/home/user1/folder/test1"
                                    )
                                ).joinToString("\n") { it.toString() }

                                payload.byteInputStream().toByteReadChannel()
                            }
                            RESTResponse.Ok(response, Unit)
                        }

                        coEvery { FileDescriptions.annotate.call(any(), any()) } answers {
                            RESTResponse.Ok(mockk(relaxed = true), Unit)
                        }

                        configureProjectServer()
                    },

                    test = {

                        val response =
                            handleRequest(HttpMethod.Get, "/api/projects?path=/home/user1/folder/notthere") {
                                addHeader("Job-Id", UUID.randomUUID().toString())
                                setUser("user1")
                            }.response

                        assertEquals(HttpStatusCode.NotFound, response.status())

                    }
                )
            }
        }
    }
}