package dk.sdu.cloud.app.http

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import dk.sdu.cloud.Role
import dk.sdu.cloud.app.api.ApplicationSummaryWithFavorite
import dk.sdu.cloud.app.api.ApplicationWithFavorite
import dk.sdu.cloud.app.services.ApplicationHibernateDAO
import dk.sdu.cloud.app.services.ToolHibernateDAO
import dk.sdu.cloud.app.services.normAppDesc
import dk.sdu.cloud.app.services.normAppDesc2
import dk.sdu.cloud.app.services.normToolDesc
import dk.sdu.cloud.app.services.withNameAndVersion
import dk.sdu.cloud.micro.HibernateFeature
import dk.sdu.cloud.micro.hibernateDatabase
import dk.sdu.cloud.micro.install
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.db.withTransaction
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
import org.junit.Ignore
import org.junit.Test
import java.util.*
import kotlin.test.assertEquals

fun TestApplicationRequest.setUser(username: String = "user1", role: Role = Role.USER) {
    addHeader(HttpHeaders.Authorization, "Bearer ${TokenValidationMock.createTokenForUser(username, role)}")
}

private fun KtorApplicationTestSetupContext.configureAppServer(
    appDao: ApplicationHibernateDAO
): List<Controller> {
    return listOf(AppController(micro.hibernateDatabase, appDao, mockk(relaxed = true)))
}

class AppTest {
    private val mapper = jacksonObjectMapper()

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
                    appDao.create(
                        it,
                        user,
                        normAppDesc2.copy(metadata = normAppDesc2.metadata.copy(name = "App4", version = "4.4"))
                    )
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


    @Ignore // Code only works for postgresql
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
                    appDao.create(
                        it,
                        user,
                        normAppDesc.copy(metadata = normAppDesc.metadata.copy(tags = listOf("tag1", "tag2")))
                    )

                    appDao.create(
                        it,
                        user,
                        normAppDesc2.copy(metadata = normAppDesc2.metadata.copy(tags = listOf("tag1", "tag2")))
                    )
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
        val name = "application"
        val version = "1"
        val app = normAppDesc.withNameAndVersion(name, version)

        withKtorTest(
            setup = {
                val user = "user"
                val toolDao = ToolHibernateDAO()
                val appDao = ApplicationHibernateDAO(toolDao)
                micro.install(HibernateFeature)
                micro.hibernateDatabase.withTransaction {
                    toolDao.create(it, user, normToolDesc)
                    appDao.create(it, user, app)
                }

                configureAppServer(appDao)
            },

            test = {
                with(engine) {
                    (1..3).forEach { numChars ->
                        run {
                            val query = name.take(numChars)
                            val response =
                                handleRequest(
                                    HttpMethod.Get,
                                    "/api/hpc/apps/search?query=$query&itemsPerPage=10&Page=0"
                                ) {
                                    addHeader("Job-Id", UUID.randomUUID().toString())
                                    setUser()
                                }.response

                            assertEquals(HttpStatusCode.OK, response.status())
                            val obj = mapper.readTree(response.content)
                            assertEquals(1, obj["itemsInTotal"].asInt())
                        }
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
        val name = "app"
        val version = "version"
        val application = normAppDesc.withNameAndVersion(name, version)

        withKtorTest(
            setup = {
                val appDao = mockk<ApplicationHibernateDAO>()

                every { appDao.findByNameAndVersionForUser(any(), any(), any(), any()) } answers {
                    ApplicationWithFavorite(application.metadata, application.invocation, false)
                }

                micro.install(HibernateFeature)
                configureAppServer(appDao)
            },

            test = {
                with(engine) {
                    val response =
                        handleRequest(HttpMethod.Get, "/api/hpc/apps/$name/$version?itemsPerPage=10&page=0") {
                            addHeader("Job-Id", UUID.randomUUID().toString())
                            setUser()
                        }.response

                    assertEquals(HttpStatusCode.OK, response.status())
                }
            }
        )
    }

    @Test
    fun `find By Name test`() {
        val name = "app"
        val version = "version"
        val application = normAppDesc.withNameAndVersion(name, version)

        withKtorTest(
            setup = {
                val appDao = mockk<ApplicationHibernateDAO>()

                every { appDao.findAllByName(any(), any(), any(), any()) } answers {
                    Page(1, 10, 0, listOf(ApplicationSummaryWithFavorite(application.metadata, true)))
                }

                micro.install(HibernateFeature)
                configureAppServer(appDao)
            },

            test = {
                with(engine) {
                    val response =
                        handleRequest(HttpMethod.Get, "/api/hpc/apps/$name") {
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
    fun `list all test`() {
        withKtorTest(
            setup = {
                val appDao = mockk<ApplicationHibernateDAO>()
                every { appDao.listLatestVersion(any(), any(), any()) } answers {
                    Page(1, 10, 0, listOf(ApplicationSummaryWithFavorite(normAppDesc.metadata, true)))
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
