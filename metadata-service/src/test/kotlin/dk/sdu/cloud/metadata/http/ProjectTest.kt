package dk.sdu.cloud.metadata.http

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import dk.sdu.cloud.auth.api.JWTProtection
import dk.sdu.cloud.auth.api.Role
import dk.sdu.cloud.auth.api.protect
import dk.sdu.cloud.client.RESTResponse
import dk.sdu.cloud.metadata.api.ProjectEventProducer
import dk.sdu.cloud.metadata.services.ProjectHibernateDAO
import dk.sdu.cloud.metadata.services.ProjectService
import dk.sdu.cloud.metadata.utils.withAuthMock
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.configureControllers
import dk.sdu.cloud.service.db.H2_TEST_CONFIG
import dk.sdu.cloud.service.db.HibernateSessionFactory
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
import io.ktor.routing.routing
import io.ktor.server.testing.TestApplicationRequest
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.setBody
import io.ktor.server.testing.withTestApplication
import io.mockk.*
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
        configureControllers(*controllers)
    }
}

private fun withDatabase(closure: (HibernateSessionFactory) -> Unit) {
    HibernateSessionFactory.create(H2_TEST_CONFIG).use(closure)
}

private fun Application.configureProjectServer(
    producer: ProjectEventProducer = mockk(relaxed = true),
    projectService: ProjectService<*>
) {
    configureBaseServer(ProjectsController(producer, projectService))
}

class ProjectTest {
    private fun mockTreeCall(vararg payload: SyncItem) {
        coEvery { FileDescriptions.syncFileList.call(any(), any()) } answers {
            val response: HttpResponse = mockk(relaxed = true)

            every { response.content } answers {
                payload.joinToString("\n") { it.toString() }.byteInputStream().toByteReadChannel()
            }
            RESTResponse.Ok(response, Unit)
        }
    }

    private fun mockTreeCallOneFile(owner: String = "user1", path: String = "/home/user1/folder/") {
        mockTreeCall(
            SyncItem(
                FileType.DIRECTORY,
                "someid",
                owner,
                0,
                null,
                null,
                path
            )
        )
    }

    @Test
    fun `Create and get project test`() {
        withDatabase { db ->
            objectMockk(FileDescriptions).use {
                withAuthMock {
                    withTestApplication(
                        moduleFunction = {
                            mockTreeCallOneFile(path = "/home/user1/folder/test1")
                            coEvery { FileDescriptions.annotate.call(any(), any()) } answers {
                                RESTResponse.Ok(mockk(relaxed = true), Unit)
                            }

                            configureProjectServer(projectService = ProjectService(db, ProjectHibernateDAO()))
                        },

                        test = {
                            val response =
                                handleRequest(HttpMethod.Put, "/api/projects") {
                                    addHeader("Job-Id", UUID.randomUUID().toString())
                                    setUser("user1")
                                    setBody("""{ "fsRoot" : "/home/user1/folder/test1" }""")
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
    }

    @Test
    fun `create project - wrong path - test`() {
        withDatabase { db ->
            objectMockk(FileDescriptions).use {
                withAuthMock {
                    withTestApplication(
                        moduleFunction = {
                            mockTreeCallOneFile()
                            configureProjectServer(projectService = ProjectService(db, ProjectHibernateDAO()))
                        },

                        test = {
                            val response =
                                handleRequest(HttpMethod.Put, "/api/projects") {
                                    addHeader("Job-Id", UUID.randomUUID().toString())
                                    setUser("user1")
                                    setBody("""{ "fsRoot" : "/home/user1/folder/notThere" }""")
                                }.response

                            assertEquals(HttpStatusCode.Forbidden, response.status())
                        }
                    )
                }
            }
        }
    }

    @Test
    fun `create project - not owner - test`() {
        withDatabase { db ->
            objectMockk(FileDescriptions).use {
                withAuthMock {
                    withTestApplication(
                        moduleFunction = {
                            mockTreeCallOneFile(owner = "some other user")
                            configureProjectServer(projectService = ProjectService(db, ProjectHibernateDAO()))
                        },

                        test = {
                            val response =
                                handleRequest(HttpMethod.Put, "/api/projects") {
                                    addHeader("Job-Id", UUID.randomUUID().toString())
                                    setUser("user1")
                                    setBody("""{ "fsRoot" : "/home/user1/folder/test1" }""")
                                }.response

                            assertEquals(HttpStatusCode.Forbidden, response.status())
                        }
                    )
                }
            }
        }
    }

    @Test
    fun `create project - already exists - test`() {
        withDatabase { db ->
            objectMockk(FileDescriptions).use {
                withAuthMock {
                    withTestApplication(
                        moduleFunction = {
                            mockTreeCallOneFile()
                            coEvery { FileDescriptions.annotate.call(any(), any()) } answers {
                                RESTResponse.Ok(mockk(relaxed = true), Unit)
                            }

                            configureProjectServer(projectService = ProjectService(db, ProjectHibernateDAO()))
                        },

                        test = {
                            val response =
                                handleRequest(HttpMethod.Put, "/api/projects") {
                                    addHeader("Job-Id", UUID.randomUUID().toString())
                                    setUser("user1")
                                    setBody("""{ "fsRoot" : "/home/user1/folder" }""")
                                }.response

                            assertEquals(HttpStatusCode.OK, response.status())

                            val response2 =
                                handleRequest(HttpMethod.Put, "/api/projects") {
                                    addHeader("Job-Id", UUID.randomUUID().toString())
                                    setUser("user1")
                                    setBody("""{ "fsRoot" : "/home/user1/folder" }""")
                                }.response

                            //TODO Should fail due to duplicate. Project creating does not check to se if fsRoot have been used.
                            //TODO If not handled, project duplicates will exists and findByPath will fail due to singleOrNull
                            assertEquals(HttpStatusCode.Conflict, response2.status())
                        }
                    )
                }
            }
        }
    }

    @Test
    fun `get project - not existing - test`() {
        withDatabase { db ->
            objectMockk(FileDescriptions).use {
                withAuthMock {
                    withTestApplication(
                        moduleFunction = {
                            mockTreeCallOneFile()
                            coEvery { FileDescriptions.annotate.call(any(), any()) } answers {
                                RESTResponse.Ok(mockk(relaxed = true), Unit)
                            }

                            configureProjectServer(projectService = ProjectService(db, ProjectHibernateDAO()))
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
}