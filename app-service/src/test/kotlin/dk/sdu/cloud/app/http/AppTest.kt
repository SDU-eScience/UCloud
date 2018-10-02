package dk.sdu.cloud.app.http

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import dk.sdu.cloud.app.api.*
import dk.sdu.cloud.app.services.ApplicationHibernateDAO
import dk.sdu.cloud.app.utils.withAuthMock
import dk.sdu.cloud.Role
import dk.sdu.cloud.app.services.ToolHibernateDAO
import dk.sdu.cloud.app.utils.withDatabase
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.configureControllers
import dk.sdu.cloud.service.db.HibernateSessionFactory
import dk.sdu.cloud.service.db.withTransaction
import dk.sdu.cloud.service.installDefaultFeatures
import io.ktor.application.Application
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.routing.routing
import io.ktor.server.testing.TestApplicationRequest
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.withTestApplication
import io.mockk.every
import io.mockk.mockk
import org.junit.Test
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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

private fun Application.configureAppServer(
    db: HibernateSessionFactory,
    appDao: ApplicationHibernateDAO
) {
    configureBaseServer(AppController(db, appDao))
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
        withDatabase { db ->
            withAuthMock {
                withTestApplication(
                    moduleFunction = {
                        val user = "user"
                        val toolDao = ToolHibernateDAO()
                        val appDao = ApplicationHibernateDAO(toolDao)
                        configureAppServer(db, appDao)
                        db.withTransaction {
                            toolDao.create(it, user, normToolDesc)
                            appDao.create(it, user, normAppDesc)
                            appDao.create(it, user, normAppDesc2.copy(info = NameAndVersion("App4", "4.4")))
                        }

                    },

                    test = {
                        run {
                            val favorites =
                                handleRequest(HttpMethod.Get,
                                    "/api/hpc/apps/favorites?itemsPerPage=10&page=0")
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
                                handleRequest(HttpMethod.Post,
                                    "/api/hpc/apps/favorite/App4/4.4")
                                {
                                    addHeader("Job-Id", UUID.randomUUID().toString())
                                    setUser()
                                }.response

                            assertEquals(HttpStatusCode.OK, response.status())


                            val favorites =
                                handleRequest(HttpMethod.Get,
                                    "/api/hpc/apps/favorites?itemsPerPage=10&page=0")
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
                                handleRequest(HttpMethod.Post,
                                    "/api/hpc/apps/unfavorite/App4/4.4")
                                {
                                    addHeader("Job-Id", UUID.randomUUID().toString())
                                    setUser()
                                }.response

                            assertEquals(HttpStatusCode.OK, response.status())


                            val favorites =
                                handleRequest(HttpMethod.Get,
                                    "/api/hpc/apps/favorites?itemsPerPage=10&page=0")
                                {
                                    addHeader("Job-Id", UUID.randomUUID().toString())
                                    setUser()
                                }.response


                            assertEquals(HttpStatusCode.OK, favorites.status())
                            val obj = mapper.readTree(favorites.content)
                            assertEquals(0, obj["itemsInTotal"].asInt())
                        }

                    }
                )
            }
        }
    }


    @Test
    fun `Searchtags test`() {
        withDatabase { db ->
            withAuthMock {
                withTestApplication(
                    moduleFunction = {
                        val user = "user"
                        val toolDao = ToolHibernateDAO()
                        val appDao = ApplicationHibernateDAO(toolDao)
                        configureAppServer(db, appDao)
                        db.withTransaction {
                            toolDao.create(it, user, normToolDesc)
                            appDao.create(it, user, normAppDesc.copy(tags = listOf("tag1", "tag2")))
                            appDao.create(it, user, normAppDesc2.copy(tags = listOf("tag2", "tag3")))
                        }
                    },

                    test = {
                        //Search for tag that only exists once
                        run {
                            val response =
                                handleRequest(HttpMethod.Get,
                                    "/api/hpc/apps/searchTags?query=tag1&itemsPerPage=10&Page=0")
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
                                handleRequest(HttpMethod.Get,
                                    "/api/hpc/apps/searchTags?query=tag2&itemsPerPage=10&Page=0")
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
                                handleRequest(HttpMethod.Get,
                                    "/api/hpc/apps/searchTags?query=a&itemsPerPage=10&Page=0")
                                {
                                    addHeader("Job-Id", UUID.randomUUID().toString())
                                    setUser()
                                }.response

                            assertEquals(HttpStatusCode.OK, response.status())
                            val obj = mapper.readTree(response.content)
                            assertEquals(0, obj["itemsInTotal"].asInt())
                        }
                    }
                )
            }
        }
    }

    @Test
    fun `Search test`() {
        withDatabase { db ->
            withAuthMock {
                withTestApplication(
                    moduleFunction = {
                        val user = "user"
                        val toolDao = ToolHibernateDAO()
                        val appDao = ApplicationHibernateDAO(toolDao)
                        configureAppServer(db, appDao)
                        db.withTransaction {
                            toolDao.create(it, user, normToolDesc)
                            appDao.create(it, user, normAppDesc)
                            appDao.create(it, user, normAppDesc2)
                        }

                    },

                    test = {
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
                                handleRequest(HttpMethod.Get, "/api/hpc/apps/search?query=notpossible&itemsPerPage=10&Page=0") {
                                    addHeader("Job-Id", UUID.randomUUID().toString())
                                    setUser()
                                }.response

                            assertEquals(HttpStatusCode.OK, response.status())
                            val obj = mapper.readTree(response.content)
                            assertEquals(0, obj["itemsInTotal"].asInt())
                        }
                    }
                )
            }
        }
    }

    @Test
    fun `find By Name And Version test`() {
        withDatabase { db ->
            withAuthMock {
                withTestApplication(
                    moduleFunction = {
                        val appDao = mockk<ApplicationHibernateDAO>()
                        configureAppServer(db, appDao)

                        every { appDao.findByNameAndVersion(any(), any(), any(), any()) } answers {
                            app
                        }

                    },

                    test = {
                        val response =
                            handleRequest(HttpMethod.Get, "/api/hpc/apps/name/2.2?itemsPerPage=10&page=0") {
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
    fun `find By Name test`() {
        withDatabase { db ->
            withAuthMock {
                withTestApplication(
                    moduleFunction = {
                        val appDao = mockk<ApplicationHibernateDAO>()
                        configureAppServer(db, appDao)

                        every { appDao.findAllByName(any(), any(), any(), any()) } answers {
                            val page = Page(1, 10, 0, listOf(app))
                            page
                        }

                    },

                    test = {
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
                )
            }
        }
    }

    @Test
    fun `list all test`() {
        withDatabase { db ->
            withAuthMock {
                withTestApplication(
                    moduleFunction = {
                        val appDao = mockk<ApplicationHibernateDAO>()
                        configureAppServer(db, appDao)

                        every { appDao.listLatestVersion(any(), any(), any()) } answers {
                            val page = Page(1, 10, 0, listOf(app))
                            page
                        }

                    },

                    test = {
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
                )
            }
        }
    }


    //TODO Can not complete since we cant add YAML.
    @Test
    fun `create test`() {
        withDatabase { db ->
            withAuthMock {
                withTestApplication(
                    moduleFunction = {
                        val appDao = mockk<ApplicationHibernateDAO>()

                        configureAppServer(db, appDao)
                    },

                    test = {
                        val response =
                            handleRequest(HttpMethod.Put, "/api/hpc/apps/") {
                                addHeader("Job-Id", UUID.randomUUID().toString())
                                setUser(role = Role.ADMIN)
                            }.response

                        assertEquals(HttpStatusCode.BadRequest, response.status())

                    }
                )
            }
        }
    }

}