package dk.sdu.cloud.app.http

import com.fasterxml.jackson.module.kotlin.readValue
import dk.sdu.cloud.app.api.NameAndVersion
import dk.sdu.cloud.app.api.NormalizedToolDescription
import dk.sdu.cloud.app.api.SimpleDuration
import dk.sdu.cloud.app.api.Tool
import dk.sdu.cloud.app.api.ToolBackend
import dk.sdu.cloud.app.services.ToolHibernateDAO
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.micro.HibernateFeature
import dk.sdu.cloud.micro.hibernateDatabase
import dk.sdu.cloud.micro.install
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.db.HibernateSession
import dk.sdu.cloud.service.test.KtorApplicationTestSetupContext
import dk.sdu.cloud.service.test.TestUsers
import dk.sdu.cloud.service.test.assertStatus
import dk.sdu.cloud.service.test.assertSuccess
import dk.sdu.cloud.service.test.sendRequest
import dk.sdu.cloud.service.test.withKtorTest
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.mockk.every
import io.mockk.mockk
import org.junit.Test
import kotlin.test.assertEquals

private fun KtorApplicationTestSetupContext.configureToolServer(
    toolDao: ToolHibernateDAO
): List<ToolController<HibernateSession>> {
    micro.install(HibernateFeature)
    return listOf(ToolController(micro.hibernateDatabase, toolDao))
}

class ToolTest {

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
        ToolBackend.DOCKER,
        "MIT"
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
                val response = sendRequest(
                    method = HttpMethod.Get,
                    path = "/api/hpc/tools/name?itemsPerPage=10&page=0",
                    user = TestUsers.user
                )
                response.assertSuccess()

                val result = defaultMapper.readValue<Page<Tool>>(response.response.content!!)
                assertEquals(1, result.itemsInTotal)
                assertEquals("owner", result.items.first().owner)
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
                val response = sendRequest(
                    method = HttpMethod.Get,
                    path = "/api/hpc/tools/name/2.2",
                    user = TestUsers.user
                )
                response.assertSuccess()

                val results = defaultMapper.readValue<Tool>(response.response.content!!)
                assertEquals("owner", results.owner)
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
                val response = sendRequest(
                    method = HttpMethod.Get,
                    path = "/api/hpc/tools",
                    user = TestUsers.user
                )
                response.assertSuccess()

                val results = defaultMapper.readValue<Page<Tool>>(response.response.content!!)
                assertEquals(1, results.itemsInTotal)
                assertEquals("owner", results.items.first().owner)
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
                sendRequest(
                    method = HttpMethod.Put,
                    path = "/api/hpc/tools",
                    user = TestUsers.admin
                ).assertStatus(HttpStatusCode.BadRequest)
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
                sendRequest(
                    method = HttpMethod.Put,
                    path = "/api/hpc/tools",
                    user = TestUsers.user
                ).assertStatus(HttpStatusCode.Unauthorized)
            }
        )
    }
}
