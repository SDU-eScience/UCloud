package dk.sdu.cloud.metadata.http

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.Role
import dk.sdu.cloud.client.RESTResponse
import dk.sdu.cloud.file.api.FileDescriptions
import dk.sdu.cloud.file.api.FileType
import dk.sdu.cloud.file.api.FindByPath
import dk.sdu.cloud.file.api.StorageFile
import dk.sdu.cloud.metadata.api.ProjectEventProducer
import dk.sdu.cloud.metadata.services.ProjectHibernateDAO
import dk.sdu.cloud.metadata.services.ProjectService
import dk.sdu.cloud.metadata.utils.withAuthMock
import dk.sdu.cloud.metadata.utils.withDatabase
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.configureControllers
import dk.sdu.cloud.service.installDefaultFeatures
import io.ktor.application.Application
import io.ktor.client.response.HttpResponse
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.routing.routing
import io.ktor.server.testing.TestApplicationRequest
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.setBody
import io.ktor.server.testing.withTestApplication
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.objectMockk
import io.mockk.use
import org.junit.Test
import java.util.*
import kotlin.test.assertEquals

fun TestApplicationRequest.setUser(username: String = "user1", role: Role = Role.USER) {
    addHeader(HttpHeaders.Authorization, "Bearer $username/$role")
}

fun Application.configureBaseServer(vararg controllers: Controller) {
    installDefaultFeatures(
        mockk(relaxed = true),
        mockk(relaxed = true),
        mockk(relaxed = true),
        requireJobId = true
    )

    routing {
        configureControllers(*controllers)
    }
}

private fun Application.configureProjectServer(
    producer: ProjectEventProducer = mockk(relaxed = true),
    projectService: ProjectService<*>
) {
    configureBaseServer(ProjectsController(producer, projectService))
}

class ProjectTest {
    @Test
    fun `Create and get project test`() {
        withDatabase { db ->
            objectMockk(FileDescriptions).use {
                withAuthMock {
                    withTestApplication(
                        moduleFunction = {
                            coEvery { FileDescriptions.annotate.call(any(), any()) } answers {
                                RESTResponse.Ok(mockk(relaxed = true), Unit)
                            }

                            mockStat("user1")
                            configureProjectServer(projectService = ProjectService(db, ProjectHibernateDAO()))
                        },

                        test = {
                            val response =
                                handleRequest(HttpMethod.Put, "/api/projects") {
                                    addHeader("Job-Id", UUID.randomUUID().toString())
                                    setUser()
                                    setBody("""{ "fsRoot" : "/home/user1/folder/test1" }""")
                                }.response

                            assertEquals(HttpStatusCode.OK, response.status())

                            val response2 =
                                handleRequest(HttpMethod.Get, "/api/projects?path=/home/user1/folder/test1") {
                                    addHeader("Job-Id", UUID.randomUUID().toString())
                                    setUser()
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
                            configureProjectServer(projectService = ProjectService(db, ProjectHibernateDAO()))
                            mockStat("user1", found = false)
                        },

                        test = {
                            val response =
                                handleRequest(HttpMethod.Put, "/api/projects") {
                                    addHeader("Job-Id", UUID.randomUUID().toString())
                                    setUser()
                                    setBody("""{ "fsRoot" : "/home/user1/folder/notThere" }""")
                                }.response

                            assertEquals(HttpStatusCode.NotFound, response.status())
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
                            configureProjectServer(projectService = ProjectService(db, ProjectHibernateDAO()))
                            mockStat("some other user")
                        },

                        test = {
                            val response =
                                handleRequest(HttpMethod.Put, "/api/projects") {
                                    addHeader("Job-Id", UUID.randomUUID().toString())
                                    setUser()
                                    setBody("""{ "fsRoot" : "/home/user1/folder/test1" }""")
                                }.response

                            assertEquals(HttpStatusCode.Forbidden, response.status())
                        }
                    )
                }
            }
        }
    }

    private fun mockStat(realOwner: String, found: Boolean = true) {
        coEvery { FileDescriptions.stat.call(any(), any()) } answers {
            val path = args.first() as FindByPath
            if (found) {
                RESTResponse.Ok(
                    mockk(relaxed = true),
                    StorageFile(FileType.FILE, path.path, ownerName = realOwner)
                )
            } else {
                val httpResponse = mockk<HttpResponse>()
                every { httpResponse.status } returns HttpStatusCode.NotFound
                RESTResponse.Err(httpResponse, CommonErrorMessage("Not found"))
            }
        }

    }

    @Test
    fun `create project - already exists - test`() {
        val user = "user1"
        withDatabase { db ->
            objectMockk(FileDescriptions).use {
                withAuthMock {
                    withTestApplication(
                        moduleFunction = {
                            coEvery { FileDescriptions.annotate.call(any(), any()) } answers {
                                RESTResponse.Ok(mockk(relaxed = true), Unit)
                            }

                            mockStat(user)

                            configureProjectServer(projectService = ProjectService(db, ProjectHibernateDAO()))
                        },

                        test = {
                            val response =
                                handleRequest(HttpMethod.Put, "/api/projects") {
                                    addHeader("Job-Id", UUID.randomUUID().toString())
                                    setUser(user)
                                    setBody("""{ "fsRoot" : "/home/user1/folder" }""")
                                }.response

                            assertEquals(HttpStatusCode.OK, response.status())

                            val response2 =
                                handleRequest(HttpMethod.Put, "/api/projects") {
                                    addHeader("Job-Id", UUID.randomUUID().toString())
                                    setUser(user)
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
                            coEvery { FileDescriptions.annotate.call(any(), any()) } answers {
                                RESTResponse.Ok(mockk(relaxed = true), Unit)
                            }

                            configureProjectServer(projectService = ProjectService(db, ProjectHibernateDAO()))
                        },

                        test = {

                            val response =
                                handleRequest(HttpMethod.Get, "/api/projects?path=/home/user1/folder/notthere") {
                                    addHeader("Job-Id", UUID.randomUUID().toString())
                                    setUser()
                                }.response

                            assertEquals(HttpStatusCode.NotFound, response.status())
                        }
                    )
                }
            }
        }
    }
}
