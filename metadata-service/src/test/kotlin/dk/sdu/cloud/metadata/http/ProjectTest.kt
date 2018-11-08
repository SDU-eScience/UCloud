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
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.HibernateFeature
import dk.sdu.cloud.service.hibernateDatabase
import dk.sdu.cloud.service.install
import dk.sdu.cloud.service.test.CloudMock
import dk.sdu.cloud.service.test.TestCallResult
import dk.sdu.cloud.service.test.TokenValidationMock
import dk.sdu.cloud.service.test.createTokenForUser
import dk.sdu.cloud.service.test.withKtorTest
import io.ktor.client.response.HttpResponse
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.TestApplicationRequest
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.setBody
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import org.junit.Test
import java.util.*
import kotlin.test.assertEquals

fun TestApplicationRequest.setUser(username: String = "user1", role: Role = Role.USER) {
    addHeader(HttpHeaders.Authorization, "Bearer ${TokenValidationMock.createTokenForUser(username, role)}")
}

private fun configureProjectServer(
    producer: ProjectEventProducer = mockk(relaxed = true),
    projectService: ProjectService<*>
): List<Controller> {
    return listOf(ProjectsController(producer, projectService))
}

class ProjectTest {
    @Test
    fun `Create and get project test`() {
        withKtorTest(
            setup = {
                micro.install(HibernateFeature)
                CloudMock.mockCallSuccess(FileDescriptions, { FileDescriptions.annotate }, Unit)
                mockStat("user1")

                configureProjectServer(projectService = ProjectService(micro.hibernateDatabase, ProjectHibernateDAO()))
            },

            test = {
                with(engine) {
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
            }
        )
    }

    @Test
    fun `create project - wrong path - test`() {
        withKtorTest(
            setup = {
                micro.install(HibernateFeature)
                mockStat("user1", found = false)
                configureProjectServer(projectService = ProjectService(micro.hibernateDatabase, ProjectHibernateDAO()))
            },

            test = {
                with(engine) {
                    val response =
                        handleRequest(HttpMethod.Put, "/api/projects") {
                            addHeader("Job-Id", UUID.randomUUID().toString())
                            setUser()
                            setBody("""{ "fsRoot" : "/home/user1/folder/notThere" }""")
                        }.response

                    assertEquals(HttpStatusCode.NotFound, response.status())
                }
            }
        )
    }

    @Test
    fun `create project - not owner - test`() {
        withKtorTest(
            setup = {
                micro.install(HibernateFeature)
                mockStat("some other user")
                configureProjectServer(projectService = ProjectService(micro.hibernateDatabase, ProjectHibernateDAO()))
            },

            test = {
                with(engine) {
                    val response =
                        handleRequest(HttpMethod.Put, "/api/projects") {
                            addHeader("Job-Id", UUID.randomUUID().toString())
                            setUser()
                            setBody("""{ "fsRoot" : "/home/user1/folder/test1" }""")
                        }.response

                    assertEquals(HttpStatusCode.Forbidden, response.status())
                }
            }
        )
    }

    private fun mockStat(realOwner: String, found: Boolean = true) {
        if (found) {
            CloudMock.mockCall(
                FileDescriptions,
                { FileDescriptions.stat },
                { TestCallResult.Ok(StorageFile(FileType.FILE, it.path, ownerName = realOwner)) }
            )
        } else {
            CloudMock.mockCallError(
                FileDescriptions,
                { FileDescriptions.stat },
                CommonErrorMessage("Not Found"),
                HttpStatusCode.NotFound
            )
        }
    }

    @Test
    fun `create project - already exists - test`() {
        val user = "user1"
        withKtorTest(
            setup = {
                micro.install(HibernateFeature)
                CloudMock.mockCallSuccess(FileDescriptions, { FileDescriptions.annotate }, Unit)
                mockStat(user)

                configureProjectServer(projectService = ProjectService(micro.hibernateDatabase, ProjectHibernateDAO()))
            },

            test = {
                with(engine) {
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
            }
        )
    }

    @Test
    fun `get project - not existing - test`() {
        withKtorTest(
            setup = {
                micro.install(HibernateFeature)
                CloudMock.mockCallSuccess(FileDescriptions, { FileDescriptions.annotate }, Unit)

                configureProjectServer(projectService = ProjectService(micro.hibernateDatabase, ProjectHibernateDAO()))
            },

            test = {
                with(engine) {
                    val response =
                        handleRequest(HttpMethod.Get, "/api/projects?path=/home/user1/folder/notthere") {
                            addHeader("Job-Id", UUID.randomUUID().toString())
                            setUser()
                        }.response

                    assertEquals(HttpStatusCode.NotFound, response.status())
                }
            }
        )
    }
}
