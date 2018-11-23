package dk.sdu.cloud.app.http

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import dk.sdu.cloud.Role
import dk.sdu.cloud.app.api.ApplicationForUser
import dk.sdu.cloud.app.api.NameAndVersion
import dk.sdu.cloud.app.api.NormalizedApplicationDescription
import dk.sdu.cloud.app.api.NormalizedToolDescription
import dk.sdu.cloud.app.api.SimpleDuration
import dk.sdu.cloud.app.api.Tool
import dk.sdu.cloud.app.api.ToolBackend
import dk.sdu.cloud.app.services.ApplicationHibernateDAO
import dk.sdu.cloud.app.services.ToolHibernateDAO
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.HibernateFeature
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.db.withTransaction
import dk.sdu.cloud.service.hibernateDatabase
import dk.sdu.cloud.service.install
import dk.sdu.cloud.service.test.KtorApplicationTestSetupContext
import dk.sdu.cloud.service.test.TokenValidationMock
import dk.sdu.cloud.service.test.createTokenForUser
import dk.sdu.cloud.service.test.withKtorTest
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.TestApplicationRequest
import io.ktor.server.testing.handleRequest
import io.mockk.every
import io.mockk.mockk
import org.junit.Test
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertTrue

fun TestApplicationRequest.setUser(username: String = "user1", role: Role = Role.USER) {
    addHeader(HttpHeaders.Authorization, "Bearer ${TokenValidationMock.createTokenForUser(username, role)}")
}

private fun KtorApplicationTestSetupContext.configureAppServer(
    appDao: ApplicationHibernateDAO
): List<Controller> {
    return listOf(AppController(micro.hibernateDatabase, appDao))
}

class AppTest {
    private val mapper = jacksonObjectMapper()

    private val normAppDesc = NormalizedApplicationDescription(
        NameAndVersion("name", "2.2"),
        NameAndVersion("name", "2.2"),
        listOf("Authors"),
        "title",
        "app description",
        mockk(relaxed = true),
        mockk(relaxed = true),
        listOf("glob"),
        listOf()
    )

    private val normAppDesc2 = NormalizedApplicationDescription(
        NameAndVersion("app", "1.2"),
        NameAndVersion("name", "2.2"),
        listOf("Authors"),
        "title",
        "app description",
        mockk(relaxed = true),
        mockk(relaxed = true),
        listOf("glob"),
        listOf()
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
    fun `Favorite test`() {
        withKtorTest(
            setup = {
                val user = "user"
                val toolDao = ToolHibernateDAO()
                val appDao = ApplicationHibernateDAO(toolDao)
                micro.install(HibernateFeature)
                micro.hibernateDatabase.withTransaction {
                    toolDao.create(it, user, normToolDesc)
                    appDao.create(it, user, normAppDesc)
                    appDao.create(it, user, normAppDesc2.copy(info = NameAndVersion("App4", "4.4")))
                }

                configureAppServer(appDao)
            },

            test = {
                with(engine) {
                    run {
                        val favorites =
                            handleRequest(
                                HttpMethod.Get,
                                "/api/hpc/apps/favorites?itemsPerPage=10&page=0"
                            )
                            {
                                addHeader("Job-Id", UUID.randomUUID().toString())
                                setUser()
                            }.response

                        assertEquals(HttpStatusCode.OK, favorites.status())
                        val obj = mapper.readTree(favorites.content)
                        assertEquals(0, obj["itemsInTotal"].asInt())
                    }

                    run {
                        val response =
                            handleRequest(
                                HttpMethod.Post,
                                "/api/hpc/apps/favorites/App4/4.4"
                            )
                            {
                                addHeader("Job-Id", UUID.randomUUID().toString())
                                setUser()
                            }.response

                        assertEquals(HttpStatusCode.OK, response.status())


                        val favorites =
                            handleRequest(
                                HttpMethod.Get,
                                "/api/hpc/apps/favorites?itemsPerPage=10&page=0"
                            )
                            {
                                addHeader("Job-Id", UUID.randomUUID().toString())
                                setUser()
                            }.response


                        assertEquals(HttpStatusCode.OK, favorites.status())
                        val obj = mapper.readTree(favorites.content)
                        assertEquals(1, obj["itemsInTotal"].asInt())
                    }

                    run {
                        val response =
                            handleRequest(
                                HttpMethod.Post,
                                "/api/hpc/apps/favorites/App4/4.4"
                            )
                            {
                                addHeader("Job-Id", UUID.randomUUID().toString())
                                setUser()
                            }.response

                        assertEquals(HttpStatusCode.OK, response.status())


                        val favorites =
                            handleRequest(
                                HttpMethod.Get,
                                "/api/hpc/apps/favorites?itemsPerPage=10&page=0"
                            )
                            {
                                addHeader("Job-Id", UUID.randomUUID().toString())
                                setUser()
                            }.response


                        assertEquals(HttpStatusCode.OK, favorites.status())
                        val obj = mapper.readTree(favorites.content)
                        assertEquals(0, obj["itemsInTotal"].asInt())
                    }

                }
            }
        )
    }


    @Test
    fun `Searchtags test`() {
        withKtorTest(
            setup = {
                val user = "user"
                val toolDao = ToolHibernateDAO()
                val appDao = ApplicationHibernateDAO(toolDao)
                micro.install(HibernateFeature)
                micro.hibernateDatabase.withTransaction {
                    toolDao.create(it, user, normToolDesc)
                    appDao.create(it, user, normAppDesc.copy(tags = listOf("tag1", "tag2")))
                    appDao.create(it, user, normAppDesc2.copy(tags = listOf("tag2", "tag3")))
                }
                configureAppServer(appDao)
            },

            test = {
                with(engine) {
                    //Search for tag that only exists once
                    run {
                        val response =
                            handleRequest(
                                HttpMethod.Get,
                                "/api/hpc/apps/searchTags?query=tag1&itemsPerPage=10&Page=0"
                            )
                            {
                                addHeader("Job-Id", UUID.randomUUID().toString())
                                setUser()
                            }.response

                        assertEquals(HttpStatusCode.OK, response.status())
                        val obj = mapper.readTree(response.content)
                        assertEquals(1, obj["itemsInTotal"].asInt())

                    }
                    //Search for tag that are multiple places
                    run {
                        val response =
                            handleRequest(
                                HttpMethod.Get,
                                "/api/hpc/apps/searchTags?query=tag2&itemsPerPage=10&Page=0"
                            )
                            {
                                addHeader("Job-Id", UUID.randomUUID().toString())
                                setUser()
                            }.response

                        assertEquals(HttpStatusCode.OK, response.status())
                        val obj = mapper.readTree(response.content)
                        assertEquals(2, obj["itemsInTotal"].asInt())
                    }
                    //Search for non existing tag
                    run {
                        val response =
                            handleRequest(
                                HttpMethod.Get,
                                "/api/hpc/apps/searchTags?query=a&itemsPerPage=10&Page=0"
                            )
                            {
                                addHeader("Job-Id", UUID.randomUUID().toString())
                                setUser()
                            }.response

                        assertEquals(HttpStatusCode.OK, response.status())
                        val obj = mapper.readTree(response.content)
                        assertEquals(0, obj["itemsInTotal"].asInt())
                    }
                }
            }
        )
    }

    @Test
    fun `Search test`() {
        withKtorTest(
            setup = {
                val user = "user"
                val toolDao = ToolHibernateDAO()
                val appDao = ApplicationHibernateDAO(toolDao)
                micro.install(HibernateFeature)
                micro.hibernateDatabase.withTransaction {
                    toolDao.create(it, user, normToolDesc)
                    appDao.create(it, user, normAppDesc)
                    appDao.create(it, user, normAppDesc2)
                }

                configureAppServer(appDao)
            },

            test = {
                with(engine) {
                    //Search for single instance (query = *am*, result = name)
                    run {
                        val response =
                            handleRequest(HttpMethod.Get, "/api/hpc/apps/search?query=am&itemsPerPage=10&Page=0") {
                                addHeader("Job-Id", UUID.randomUUID().toString())
                                setUser()
                            }.response

                        assertEquals(HttpStatusCode.OK, response.status())
                        val obj = mapper.readTree(response.content)
                        assertEquals(1, obj["itemsInTotal"].asInt())
                    }
                    // Search for everything (query = *, result = app, name)
                    run {
                        val response =
                            handleRequest(HttpMethod.Get, "/api/hpc/apps/search?query=&itemsPerPage=10&Page=0") {
                                addHeader("Job-Id", UUID.randomUUID().toString())
                                setUser()
                            }.response

                        assertEquals(HttpStatusCode.OK, response.status())
                        val obj = mapper.readTree(response.content)
                        assertEquals(2, obj["itemsInTotal"].asInt())
                    }
                    // Search for multiple (query = *a*, result = app, name)
                    run {
                        val response =
                            handleRequest(HttpMethod.Get, "/api/hpc/apps/search?query=a&itemsPerPage=10&Page=0") {
                                addHeader("Job-Id", UUID.randomUUID().toString())
                                setUser()
                            }.response

                        assertEquals(HttpStatusCode.OK, response.status())
                        val obj = mapper.readTree(response.content)
                        assertEquals(2, obj["itemsInTotal"].asInt())
                    }
                    // Search for none (query = *notpossible*, result = null)
                    run {
                        val response =
                            handleRequest(
                                HttpMethod.Get,
                                "/api/hpc/apps/search?query=notpossible&itemsPerPage=10&Page=0"
                            ) {
                                addHeader("Job-Id", UUID.randomUUID().toString())
                                setUser()
                            }.response

                        assertEquals(HttpStatusCode.OK, response.status())
                        val obj = mapper.readTree(response.content)
                        assertEquals(0, obj["itemsInTotal"].asInt())
                    }
                }
            }
        )
    }

    @Test
    fun `find By Name And Version test`() {
        withKtorTest(
            setup = {
                val appDao = mockk<ApplicationHibernateDAO>()

                every { appDao.findByNameAndVersion(any(), any(), any(), any()) } answers {
                    app
                }

                micro.install(HibernateFeature)
                configureAppServer(appDao)
            },

            test = {
                with(engine) {
                    val response =
                        handleRequest(HttpMethod.Get, "/api/hpc/apps/name/2.2?itemsPerPage=10&page=0") {
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
    fun `find By Name test`() {
        withKtorTest(
            setup = {
                val appDao = mockk<ApplicationHibernateDAO>()

                every { appDao.findAllByName(any(), any(), any(), any()) } answers {
                    val page = Page(1, 10, 0, listOf(app))
                    page
                }

                micro.install(HibernateFeature)
                configureAppServer(appDao)
            },

            test = {
                with(engine) {
                    val response =
                        handleRequest(HttpMethod.Get, "/api/hpc/apps/nameOfApp") {
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

    @Test
    fun `list all test`() {
        withKtorTest(
            setup = {
                val appDao = mockk<ApplicationHibernateDAO>()
                every { appDao.listLatestVersion(any(), any(), any()) } answers {
                    val page = Page(1, 10, 0, listOf(ApplicationForUser(app, true)))
                    page
                }

                micro.install(HibernateFeature)
                configureAppServer(appDao)
            },

            test = {
                with(engine) {
                    val response =
                        handleRequest(HttpMethod.Get, "/api/hpc/apps") {
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


    //TODO Can not complete since we cant add YAML.
    @Test
    fun `create test`() {
        withKtorTest(
            setup = {
                val appDao = mockk<ApplicationHibernateDAO>()
                micro.install(HibernateFeature)
                configureAppServer(appDao)
            },

            test = {
                with(engine) {
                    val response =
                        handleRequest(HttpMethod.Put, "/api/hpc/apps/") {
                            addHeader("Job-Id", UUID.randomUUID().toString())
                            setUser(role = Role.ADMIN)
                        }.response

                    assertEquals(HttpStatusCode.BadRequest, response.status())
                }
            }
        )
    }
}
