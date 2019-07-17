package dk.sdu.cloud.app.store.rpc

import com.fasterxml.jackson.module.kotlin.readValue
import dk.sdu.cloud.app.store.api.ApplicationSummaryWithFavorite
import dk.sdu.cloud.app.store.api.ApplicationWithFavorite
import dk.sdu.cloud.app.store.api.CreateTagRequest
import dk.sdu.cloud.app.store.api.DeleteTagRequest
import dk.sdu.cloud.app.store.services.AppStoreService
import dk.sdu.cloud.app.store.services.ApplicationHibernateDAO
import dk.sdu.cloud.app.store.services.ToolHibernateDAO
import dk.sdu.cloud.app.store.util.*
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.micro.HibernateFeature
import dk.sdu.cloud.micro.hibernateDatabase
import dk.sdu.cloud.micro.install
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.db.withTransaction
import dk.sdu.cloud.service.test.*
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import org.junit.Ignore
import org.junit.Test
import kotlin.test.assertEquals

private fun KtorApplicationTestSetupContext.configureAppServer(
    appDao: ApplicationHibernateDAO
): List<Controller> {
    val appStore = AppStoreService(micro.hibernateDatabase, appDao, mockk(relaxed = true))
    return listOf(AppStoreController(appStore))
}

class AppTest {

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
                run {
                    val favorites =
                        sendRequest(
                            method = HttpMethod.Get,
                            path = "/api/hpc/apps/favorites?itemsPerPage=10&page=0",
                            user = TestUsers.user
                        )
                    favorites.assertSuccess()

                    val results =
                        defaultMapper.readValue<Page<ApplicationSummaryWithFavorite>>(favorites.response.content!!)
                    assertEquals(0, results.itemsInTotal)
                }

                run {
                    val response =
                        sendRequest(
                            method = HttpMethod.Post,
                            path = "/api/hpc/apps/favorites/App4/4.4",
                            user = TestUsers.user
                        )
                    response.assertSuccess()

                    val favorites =
                        sendRequest(
                            method = HttpMethod.Get,
                            path = "/api/hpc/apps/favorites?itemsPerPage=10&page=0",
                            user = TestUsers.user
                        )
                    favorites.assertSuccess()

                    val results =
                        defaultMapper.readValue<Page<ApplicationSummaryWithFavorite>>(favorites.response.content!!)
                    assertEquals(1, results.itemsInTotal)
                }

                run {
                    val response =
                        sendRequest(
                            method = HttpMethod.Post,
                            path = "/api/hpc/apps/favorites/App4/4.4",
                            user = TestUsers.user
                        )
                    response.assertSuccess()

                    val favorites =
                        sendRequest(
                            method = HttpMethod.Get,
                            path = "/api/hpc/apps/favorites?itemsPerPage=10&page=0",
                            user = TestUsers.user
                        )
                    favorites.assertSuccess()

                    val results =
                        defaultMapper.readValue<Page<ApplicationSummaryWithFavorite>>(favorites.response.content!!)
                    assertEquals(0, results.itemsInTotal)
                }
            }
        )
    }

    @Test
    fun `Search tags CC test`() {
        withKtorTest(
            setup = {
                micro.install(HibernateFeature)
                val appDao = mockk<ApplicationHibernateDAO>()
                every { appDao.searchTags(any(), any(), any(), any()) } answers {
                    val items = listOf(
                        mockk<ApplicationSummaryWithFavorite>(relaxed = true)
                    )
                    val page = Page(1, 10, 0, items)
                    page
                }
                configureAppServer(appDao)
            },
            test = {
                run {
                    val response =
                        sendRequest(
                            method = HttpMethod.Get,
                            path = "/api/hpc/apps/searchTags?query=tag1&itemsPerPage=10&Page=0",
                            user = TestUsers.user
                        )
                    response.assertSuccess()

                    val results = defaultMapper.readValue<Page<ApplicationSummaryWithFavorite>>(
                        response.response.content!!
                    )
                    assertEquals(1, results.itemsInTotal)
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
                //Search for tag that only exists once
                run {
                    val response = sendRequest(
                        method = HttpMethod.Get,
                        path = "/api/hpc/apps/searchTags?query=tag1&itemsPerPage=10&Page=0",
                        user = TestUsers.user
                    )
                    response.assertSuccess()

                    val results = defaultMapper.readValue<Page<ApplicationSummaryWithFavorite>>(
                        response.response.content!!
                    )

                    assertEquals(1, results.itemsInTotal)

                }
                //Search for tag that are multiple places
                run {
                    val response = sendRequest(
                        method = HttpMethod.Get,
                        path = "/api/hpc/apps/searchTags?query=tag2&itemsPerPage=10&Page=0",
                        user = TestUsers.user
                    )
                    response.assertSuccess()

                    val results = defaultMapper.readValue<Page<ApplicationSummaryWithFavorite>>(
                        response.response.content!!
                    )

                    assertEquals(2, results.itemsInTotal)
                }
                //Search for non existing tag
                run {
                    val response = sendRequest(
                        method = HttpMethod.Get,
                        path = "/api/hpc/apps/searchTags?query=a&itemsPerPage=10&Page=0",
                        user = TestUsers.user
                    )
                    response.assertSuccess()

                    val results = defaultMapper.readValue<Page<ApplicationSummaryWithFavorite>>(
                        response.response.content!!
                    )

                    assertEquals(0, results.itemsInTotal)
                }
            }
        )
    }

    @Test
    fun `Search test`() {
        val name = "application"
        val version = "1"
        val title = "Application"
        val app = normAppDesc.withNameAndVersionAndTitle(name, version, title)

        withKtorTest(
            setup = {
                val toolDao = ToolHibernateDAO()
                val appDao = ApplicationHibernateDAO(toolDao)
                micro.install(HibernateFeature)
                micro.hibernateDatabase.withTransaction {
                    toolDao.create(it, TestUsers.user.username, normToolDesc)
                    appDao.create(it, TestUsers.user.username, app)

                }
                configureAppServer(appDao)
            },

            test = {
                (1..3).forEach { numChars ->
                    run {
                        val query = name.take(numChars)
                        val response = sendRequest(
                            method = HttpMethod.Get,
                            path = "/api/hpc/apps/search?query=$query&itemsPerPage=10&Page=0",
                            user = TestUsers.user
                        )
                        response.assertSuccess()

                        val results = defaultMapper.readValue<Page<ApplicationSummaryWithFavorite>>(
                            response.response.content!!
                        )
                        assertEquals(1, results.itemsInTotal)
                    }
                }

                // Search for none (query = *notpossible*, result = null)
                run {
                    val response = sendRequest(
                        method = HttpMethod.Get,
                        path = "/api/hpc/apps/search?query=notpossible&itemsPerPage=10&Page=0",
                        user = TestUsers.user
                    )
                    response.assertSuccess()

                    val results = defaultMapper.readValue<Page<ApplicationSummaryWithFavorite>>(
                        response.response.content!!
                    )
                    assertEquals(0, results.itemsInTotal)
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
                sendRequest(
                    method = HttpMethod.Get,
                    path = "/api/hpc/apps/$name/$version?itemsPerPage=10&page=0",
                    user = TestUsers.user
                ).assertSuccess()
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
                val response = sendRequest(
                    method = HttpMethod.Get,
                    path = "/api/hpc/apps/$name",
                    user = TestUsers.user
                )
                response.assertSuccess()

                val results = defaultMapper.readValue<Page<ApplicationSummaryWithFavorite>>(
                    response.response.content!!
                )
                assertEquals(1, results.itemsInTotal)
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
                val response = sendRequest(
                    method = HttpMethod.Get,
                    path = "/api/hpc/apps",
                    user = TestUsers.user
                )
                response.assertSuccess()

                val results = defaultMapper.readValue<Page<ApplicationSummaryWithFavorite>>(
                    response.response.content!!
                )
                assertEquals(1, results.itemsInTotal)
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
                sendRequest(
                    method = HttpMethod.Put,
                    path = "/api/hpc/apps/",
                    user = TestUsers.admin
                ).assertStatus(HttpStatusCode.BadRequest)
            }
        )
    }

    @Test
    fun `createTag deleteTag test`() {
        withKtorTest(
            setup = {
                val appDao = mockk<ApplicationHibernateDAO>()
                every { appDao.createTags(any(), any(), any(), any()) } just runs
                every { appDao.deleteTags(any(), any(), any(), any()) } just runs
                micro.install(HibernateFeature)
                configureAppServer(appDao)
            },

            test = {
                val createRequest = sendJson(
                    method = HttpMethod.Post,
                    path = "/api/hpc/apps/createTag",
                    user = TestUsers.admin,
                    request = CreateTagRequest(
                        listOf("tag1", "tag2"),
                        "applicationName",
                        "2.2"
                    )
                )

                createRequest.assertSuccess()

                val deleteRequest = sendJson(
                    method = HttpMethod.Post,
                    path = "/api/hpc/apps/deleteTag",
                    user = TestUsers.admin,
                    request = DeleteTagRequest(
                        listOf("tag1", "tag2"),
                        "applicationName",
                        "2.2"
                    )
                )

                deleteRequest.assertSuccess()
            }
        )
    }
}
