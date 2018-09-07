package dk.sdu.cloud.app.http

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import dk.sdu.cloud.app.api.*
import dk.sdu.cloud.app.services.ToolHibernateDAO
import dk.sdu.cloud.auth.api.Role
import dk.sdu.cloud.app.utils.withAuthMock
import dk.sdu.cloud.app.utils.withDatabase
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.db.HibernateSession
import dk.sdu.cloud.service.db.HibernateSessionFactory
import io.ktor.application.Application
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.withTestApplication
import io.mockk.every
import io.mockk.mockk
import org.junit.Test
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertTrue


private fun Application.configureToolServer(
    db: HibernateSessionFactory,
    toolDao: ToolHibernateDAO
) {
    configureBaseServer(ToolController<HibernateSession>(db, toolDao))
}

class ToolTest{

    private val mapper = jacksonObjectMapper()

    private val normAppDesc = NormalizedApplicationDescription(
        NameAndVersion("name", "2.2"),
        NameAndVersion("name", "2.2"),
        listOf("Authors"),
        "title",
        "app description",
        mockk(relaxed = true),
        mockk(relaxed = true),
        listOf("glob")
    )

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

    private val app = dk.sdu.cloud.app.api.Application(
        "owner",
        1234567,
        123456789,
        normAppDesc,
        tool
    )

    @Test
    fun `find By name test `() {
        withDatabase { db ->
            withAuthMock {
                withTestApplication(
                    moduleFunction = {
                        val toolDao = mockk<ToolHibernateDAO>()
                        configureToolServer(db, toolDao)

                        every { toolDao.findAllByName(any(), any(), any(), any()) } answers {
                            Page(1, 10, 0, listOf(tool))

                        }
                    },

                    test = {
                        val response =
                            handleRequest(HttpMethod.Get, "/api/hpc/tools/name?itemsPerPage=10&page=0") {
                                addHeader("Job-Id", UUID.randomUUID().toString())
                                setUser()
                            }.response

                        assertEquals(HttpStatusCode.OK, response.status())

                        val obj = mapper.readTree(response.content)
                        assertEquals(1, obj["itemsInTotal"].asInt())
                    }
                )
            }
        }
    }

    @Test
    fun `find By name and version test `() {
        withDatabase { db ->
            withAuthMock {
                withTestApplication(
                    moduleFunction = {
                        val toolDao = mockk<ToolHibernateDAO>()
                        configureToolServer(db, toolDao)

                        every { toolDao.findByNameAndVersion(any(), any(), any(), any()) } returns tool
                    },

                    test = {
                        val response =
                            handleRequest(HttpMethod.Get, "/api/hpc/tools/name/2.2") {
                                addHeader("Job-Id", UUID.randomUUID().toString())
                                setUser()
                            }.response

                        assertEquals(HttpStatusCode.OK, response.status())

                        val obj = mapper.readTree(response.content)

                        assertEquals("\"owner\"", obj["owner"].toString())
                    }
                )
            }
        }
    }

    @Test
    fun `list all test `() {
        withDatabase { db ->
            withAuthMock {
                withTestApplication(
                    moduleFunction = {
                        val toolDao = mockk<ToolHibernateDAO>()
                        configureToolServer(db, toolDao)

                        every { toolDao.listLatestVersion(any(), any(), any()) } answers {
                            Page(1, 10, 0, listOf(tool))
                        }
                    },

                    test = {
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
                )
            }
        }
    }

    //TODO NEED SUPPORT FOR YAML CANT TEST
    @Test
    fun `Create test - Cant read YAML`() {
        withDatabase { db ->
            withAuthMock {
                withTestApplication(
                    moduleFunction = {
                        val toolDao = mockk<ToolHibernateDAO>()
                        configureToolServer(db, toolDao)

                    },

                    test = {
                        val response =
                            handleRequest(HttpMethod.Put, "/api/hpc/tools") {
                                addHeader("Job-Id", UUID.randomUUID().toString())
                                setUser(role = Role.ADMIN)
                            }.response

                        assertEquals(HttpStatusCode.BadRequest, response.status())

                    }
                )
            }
        }
    }

    @Test
    fun `Create test - not priviliged `() {
        withDatabase { db ->
            withAuthMock {
                withTestApplication(
                    moduleFunction = {
                        val toolDao = mockk<ToolHibernateDAO>()
                        configureToolServer(db, toolDao)

                    },

                    test = {
                        val response =
                            handleRequest(HttpMethod.Put, "/api/hpc/tools") {
                                addHeader("Job-Id", UUID.randomUUID().toString())
                                setUser()
                            }.response

                        assertEquals(HttpStatusCode.Unauthorized, response.status())

                    }
                )
            }
        }
    }

}