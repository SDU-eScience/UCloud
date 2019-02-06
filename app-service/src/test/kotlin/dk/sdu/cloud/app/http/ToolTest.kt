package dk.sdu.cloud.app.http

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import dk.sdu.cloud.Role
import dk.sdu.cloud.app.api.NameAndVersion
import dk.sdu.cloud.app.api.NormalizedToolDescription
import dk.sdu.cloud.app.api.SimpleDuration
import dk.sdu.cloud.app.api.Tool
import dk.sdu.cloud.app.api.ToolBackend
import dk.sdu.cloud.app.services.ToolHibernateDAO
import dk.sdu.cloud.service.HibernateFeature
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.db.HibernateSession
import dk.sdu.cloud.service.hibernateDatabase
import dk.sdu.cloud.service.install
import dk.sdu.cloud.service.test.KtorApplicationTestSetupContext
import dk.sdu.cloud.service.test.withKtorTest
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.handleRequest
import io.mockk.every
import io.mockk.mockk
import org.junit.Test
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private fun KtorApplicationTestSetupContext.configureToolServer(
    toolDao: ToolHibernateDAO
): List<ToolController<HibernateSession>> {
    micro.install(HibernateFeature)
    return listOf(ToolController(micro.hibernateDatabase, toolDao))
}

class ToolTest {
    private val mapper = jacksonObjectMapper()

    private val normToolDesc = NormalizedToolDescription(
        NameAndVersion("name", "2.2"),
        "container",
        2,
        2,
        SimpleDuration(1, 0, 0),
        listOf(""),
        listOf("auther"),
        "title",
        "description",
        ToolBackend.UDOCKER
    )

    private val tool = Tool(
        "owner",
        1234567,
        123456789,
        normToolDesc
    )

    @Test
    fun `find By name test `() {
        withKtorTest(
            setup = {
                val toolDao = mockk<ToolHibernateDAO>()
                every { toolDao.findAllByName(any(), any(), any(), any()) } answers {
                    Page(1, 10, 0, listOf(tool))
                }

                configureToolServer(toolDao)
            },

            test = {
                with(engine) {
                    val response =
                        handleRequest(HttpMethod.Get, "/api/hpc/tools/name?itemsPerPage=10&page=0") {
                            addHeader("Job-Id", UUID.randomUUID().toString())
                            setUser()
                        }.response

                    assertEquals(HttpStatusCode.OK, response.status())

                    val obj = mapper.readTree(response.content)
                    assertEquals(1, obj["itemsInTotal"].asInt())
                }
            }
        )
    }

    @Test
    fun `find By name and version test `() {
        withKtorTest(
            setup = {
                val toolDao = mockk<ToolHibernateDAO>()
                every { toolDao.findByNameAndVersion(any(), any(), any(), any()) } returns tool
                configureToolServer(toolDao)
            },

            test = {
                with(engine) {
                    val response =
                        handleRequest(HttpMethod.Get, "/api/hpc/tools/name/2.2") {
                            addHeader("Job-Id", UUID.randomUUID().toString())
                            setUser()
                        }.response

                    assertEquals(HttpStatusCode.OK, response.status())

                    val obj = mapper.readTree(response.content)

                    assertEquals("\"owner\"", obj["owner"].toString())
                }
            }
        )
    }

    @Test
    fun `list all test `() {
        withKtorTest(
            setup = {
                val toolDao = mockk<ToolHibernateDAO>()
                every { toolDao.listLatestVersion(any(), any(), any()) } answers {
                    Page(1, 10, 0, listOf(tool))
                }
                configureToolServer(toolDao)
            },

            test = {
                with(engine) {
                    val response =
                        handleRequest(HttpMethod.Get, "/api/hpc/tools") {
                            addHeader("Job-Id", UUID.randomUUID().toString())
                            setUser()
                        }.response

                    assertEquals(HttpStatusCode.OK, response.status())

                    val obj = mapper.readTree(response.content)
                    assertEquals(1, obj["itemsInTotal"].asInt())
                    assertTrue(obj["items"].toString().contains("\"owner\":\"owner\""))
                }
            }
        )
    }

    //TODO NEED SUPPORT FOR YAML CANT TEST
    @Test
    fun `Create test - Cant read YAML`() {
        withKtorTest(
            setup = {
                val toolDao = mockk<ToolHibernateDAO>()
                configureToolServer(toolDao)
            },

            test = {
                with(engine) {
                    val response =
                        handleRequest(HttpMethod.Put, "/api/hpc/tools") {
                            addHeader("Job-Id", UUID.randomUUID().toString())
                            setUser(role = Role.ADMIN)
                        }.response

                    assertEquals(HttpStatusCode.BadRequest, response.status())
                }
            }
        )
    }

    @Test
    fun `Create test - not privileged`() {
        withKtorTest(
            setup = {
                val toolDao = mockk<ToolHibernateDAO>()
                configureToolServer(toolDao)
            },

            test = {
                with(engine) {
                    val response =
                        handleRequest(HttpMethod.Put, "/api/hpc/tools") {
                            addHeader("Job-Id", UUID.randomUUID().toString())
                            setUser()
                        }.response

                    assertEquals(HttpStatusCode.Unauthorized, response.status())
                }
            }
        )
    }
}
