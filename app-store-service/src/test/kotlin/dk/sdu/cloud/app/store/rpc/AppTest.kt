package dk.sdu.cloud.app.store.rpc

import com.fasterxml.jackson.module.kotlin.readValue
import dk.sdu.cloud.app.store.api.ApplicationSummaryWithFavorite
import dk.sdu.cloud.app.store.api.ApplicationWithFavoriteAndTags
import dk.sdu.cloud.app.store.api.CreateTagsRequest
import dk.sdu.cloud.app.store.api.DeleteAppRequest
import dk.sdu.cloud.app.store.api.DeleteTagsRequest
import dk.sdu.cloud.app.store.api.UploadApplicationLogoRequest
import dk.sdu.cloud.app.store.services.AppStoreService
import dk.sdu.cloud.app.store.services.ApplicationHibernateDAO
import dk.sdu.cloud.app.store.services.ElasticDAO
import dk.sdu.cloud.app.store.services.LogoService
import dk.sdu.cloud.app.store.services.ToolDAO
import dk.sdu.cloud.app.store.services.ToolHibernateDAO
import dk.sdu.cloud.app.store.services.acl.AclDao
import dk.sdu.cloud.app.store.services.acl.AclHibernateDao
import dk.sdu.cloud.app.store.util.normAppDesc
import dk.sdu.cloud.app.store.util.normAppDesc2
import dk.sdu.cloud.app.store.util.normToolDesc
import dk.sdu.cloud.app.store.util.withNameAndVersion
import dk.sdu.cloud.app.store.util.withNameAndVersionAndTitle
import dk.sdu.cloud.auth.api.authenticator
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.OutgoingHttpCall
import dk.sdu.cloud.calls.types.BinaryStream
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.micro.ElasticFeature
import dk.sdu.cloud.micro.HibernateFeature
import dk.sdu.cloud.micro.hibernateDatabase
import dk.sdu.cloud.micro.install
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.db.HibernateSession
import dk.sdu.cloud.service.db.withTransaction
import dk.sdu.cloud.service.test.KtorApplicationTestSetupContext
import dk.sdu.cloud.service.test.TestUsers
import dk.sdu.cloud.service.test.assertStatus
import dk.sdu.cloud.service.test.assertSuccess
import dk.sdu.cloud.service.test.sendJson
import dk.sdu.cloud.service.test.sendRequest
import dk.sdu.cloud.service.test.withKtorTest
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.setBody
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlinx.coroutines.runBlocking
import org.junit.Ignore
import org.junit.Test
import kotlin.test.assertEquals

private fun KtorApplicationTestSetupContext.configureAppServer(
    appDao: ApplicationHibernateDAO,
    elasticDAO: ElasticDAO
): List<Controller> {
    val toolDao = mockk<ToolDAO<HibernateSession>>(relaxed = true)
    val aclDao = mockk<AclDao<HibernateSession>>(relaxed = true)
    val authClient = mockk<AuthenticatedClient>(relaxed = true)
    val appStore = AppStoreService(
        micro.hibernateDatabase,
        authClient,
        appDao,
        toolDao,
        aclDao,
        elasticDAO
    )
    val logoService = LogoService(micro.hibernateDatabase, appDao, toolDao)
    return listOf(AppStoreController(appStore, logoService))
}

class AppTest {

    @Test
    fun `Favorite test`() {
        withKtorTest(
            setup = {
                val user = TestUsers.user
                val toolDao = ToolHibernateDAO()
                val elasticDAO = mockk<ElasticDAO>()
                val aclDao = AclHibernateDao()
                val appDao = ApplicationHibernateDAO(toolDao, aclDao)
                micro.install(HibernateFeature)
                runBlocking {
                    micro.hibernateDatabase.withTransaction {
                        toolDao.create(it, user, normToolDesc)
                        appDao.create(it, user, normAppDesc)
                        appDao.create(
                            it,
                            user,
                            normAppDesc2.copy(metadata = normAppDesc2.metadata.copy(name = "App4", version = "4.4"))
                        )
                    }
                }
                configureAppServer(appDao, elasticDAO)
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
                val elasticDAO = mockk<ElasticDAO>()
                every { appDao.searchTags(any(), any(), any(), any()) } answers {
                    val items = listOf(
                        mockk<ApplicationSummaryWithFavorite>(relaxed = true)
                    )
                    val page = Page(1, 10, 0, items)
                    page
                }
                configureAppServer(appDao, elasticDAO)
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

    @Test
    fun `Searchtags test`() {
        withKtorTest(
            setup = {
                val user = TestUsers.user
                val toolDao = ToolHibernateDAO()
                val aclDao = AclHibernateDao()
                val elasticDAO = mockk<ElasticDAO>()
                val appDao = ApplicationHibernateDAO(toolDao, aclDao)
                micro.install(HibernateFeature)
                runBlocking {
                    micro.hibernateDatabase.withTransaction {
                        toolDao.create(it, user, normToolDesc)
                        appDao.create(
                            it,
                            user,
                            normAppDesc.withNameAndVersionAndTitle("name1", "1", "1title")
                        )
                        appDao.create(
                            it,
                            user,
                            normAppDesc2.withNameAndVersionAndTitle("name2", "2", "2title")
                        )

                        appDao.createTags(it, user, "name1", listOf("tag1", "tag2"))
                        appDao.createTags(it, user, "name2", listOf("tag2", "tag3"))
                    }
                }
                configureAppServer(appDao, elasticDAO)
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
                val aclDao = AclHibernateDao()
                val elasticDAO = mockk<ElasticDAO>()
                val appDao = ApplicationHibernateDAO(toolDao, aclDao)
                micro.install(HibernateFeature)
                runBlocking {
                    micro.hibernateDatabase.withTransaction {
                        toolDao.create(it, TestUsers.user, normToolDesc)
                        appDao.create(it, TestUsers.user, app)

                    }
                }
                configureAppServer(appDao, elasticDAO)
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

    @Ignore
    @Test
    fun `find By Name And Version test`() {
        val name = "app"
        val version = "version"
        val application = normAppDesc.withNameAndVersion(name, version)

        withKtorTest(
            setup = {
                val appDao = mockk<ApplicationHibernateDAO>()
                val elasticDAO = mockk<ElasticDAO>()

                every { appDao.findByNameAndVersionForUser(any(), any(), any(), any()) } answers {
                    ApplicationWithFavoriteAndTags(
                        application.metadata,
                        application.invocation,
                        false,
                        emptyList()
                    )
                }

                micro.install(HibernateFeature)
                configureAppServer(appDao, elasticDAO)

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
                val elasticDAO = mockk<ElasticDAO>()

                every { appDao.findAllByName(any(), any(), any(), any()) } answers {
                    Page(
                        1,
                        10,
                        0,
                        listOf(ApplicationSummaryWithFavorite(application.metadata, true, emptyList()))
                    )
                }

                micro.install(HibernateFeature)
                configureAppServer(appDao, elasticDAO)
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
                val elasticDAO = mockk<ElasticDAO>()

                every { appDao.listLatestVersion(any(), any(), any()) } answers {
                    Page(
                        1,
                        10,
                        0,
                        listOf(ApplicationSummaryWithFavorite(normAppDesc.metadata, true, emptyList()))
                    )
                }

                micro.install(HibernateFeature)
                configureAppServer(appDao, elasticDAO)
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
                val elasticDAO = mockk<ElasticDAO>()

                micro.install(HibernateFeature)
                configureAppServer(appDao, elasticDAO)
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
                val elasticDAO = mockk<ElasticDAO>()

                every { appDao.createTags(any(), any(), any(), any()) } just runs
                every { appDao.deleteTags(any(), any(), any(), any()) } just runs
                every { elasticDAO.addTagToElastic(any(), any()) } just runs
                every { elasticDAO.removeTagFromElastic(any(), any()) } just runs
                micro.install(HibernateFeature)
                configureAppServer(appDao, elasticDAO)
            },

            test = {
                val createRequest = sendJson(
                    method = HttpMethod.Post,
                    path = "/api/hpc/apps/createTag",
                    user = TestUsers.admin,
                    request = CreateTagsRequest(
                        listOf("tag1", "tag2"),
                        "applicationName"
                    )
                )

                createRequest.assertSuccess()

                val deleteRequest = sendJson(
                    method = HttpMethod.Post,
                    path = "/api/hpc/apps/deleteTag",
                    user = TestUsers.admin,
                    request = DeleteTagsRequest(
                        listOf("tag1", "tag2"),
                        "applicationName"
                    )
                )

                deleteRequest.assertSuccess()
            }
        )
    }

    /*
        @Test
        fun advancedSearch() {
            withKtorTest(
                setup = {
                    micro.install(ElasticFeature)
                    micro.install(HibernateFeature)
                    val appDao = mockk<ApplicationHibernateDAO>(relaxed = true)
                    val elasticDAO = mockk<ElasticDAO>(relaxed = true)
                    configureAppServer(appDao, elasticDAO)
                },
                test = {
                    val advancedSearchRequest = sendJson(
                        method = HttpMethod.Post,
                        path = "api/hpc/apps/advancedSearch",
                        user = TestUsers.admin,
                        request = AdvancedSearchRequest(
                            normAppDesc2.metadata.name,
                            normAppDesc2.metadata.version,
                            normAppDesc2.metadata.description,
                            null,
                            10,
                            0
                        )
                    )

                    advancedSearchRequest.assertSuccess()
                }
            )
        }
    */
    @Test
    fun `update Logo test`() {
        withKtorTest(
            setup = {
                micro.install(HibernateFeature)
                val appDao = mockk<ApplicationHibernateDAO>(relaxed = true)
                val elasticDAO = mockk<ElasticDAO>(relaxed = true)
                configureAppServer(appDao, elasticDAO)
            },
            test = {
                val updateLogoRequest = sendRequest(
                    method = HttpMethod.Post,
                    path = "api/hpc/apps/uploadLogo",
                    user = TestUsers.admin,
                    configure = {
                        addHeader("Upload-Name", "name")
                        addHeader("Content-Length", "4")
                        setBody(byteArrayOf(1, 2, 3, 4))
                    }
                )

                updateLogoRequest.assertSuccess()
            }
        )
    }

    @Test
    fun `clear Logo test`() {
        withKtorTest(
            setup = {
                micro.install(HibernateFeature)
                val appDao = mockk<ApplicationHibernateDAO>(relaxed = true)
                val elasticDAO = mockk<ElasticDAO>(relaxed = true)
                configureAppServer(appDao, elasticDAO)
            },
            test = {
                val clearLogoRequest = sendRequest(
                    method = HttpMethod.Delete,
                    path = "api/hpc/apps/clearLogo/nameOfApp",
                    user = TestUsers.admin
                )

                clearLogoRequest.assertSuccess()
            }
        )
    }

    @Test
    fun `fetch Logo test`() {
        withKtorTest(
            setup = {
                micro.install(HibernateFeature)
                val appDao = mockk<ApplicationHibernateDAO>(relaxed = true)
                val elasticDAO = mockk<ElasticDAO>(relaxed = true)
                configureAppServer(appDao, elasticDAO)
            },
            test = {
                val fetchLogoRequest = sendRequest(
                    method = HttpMethod.Get,
                    path = "api/hpc/apps/logo/nameOfApp",
                    user = TestUsers.admin
                )

                fetchLogoRequest.assertSuccess()
            }
        )
    }

    @Test
    fun `find latest by tool test`() {
        withKtorTest(
            setup = {
                micro.install(HibernateFeature)
                val appDao = mockk<ApplicationHibernateDAO>(relaxed = true)
                val elasticDAO = mockk<ElasticDAO>(relaxed = true)
                configureAppServer(appDao, elasticDAO)
            },
            test = {
                val findLatestByToolRequest = sendRequest(
                    method = HttpMethod.Get,
                    path = "api/hpc/apps/byTool/toolname",
                    user = TestUsers.user
                )

                findLatestByToolRequest.assertSuccess()
            }
        )
    }

    @Test
    fun `delete app test`() {
        withKtorTest(
            setup = {
                micro.install(HibernateFeature)
                val appDao = mockk<ApplicationHibernateDAO>()
                val elasticDAO = mockk<ElasticDAO>()

                every { appDao.delete(any(), any(), any(), any()) } just runs
                every { elasticDAO.deleteApplicationInElastic(any(), any()) } just runs
                configureAppServer(appDao, elasticDAO)
            },
            test = {
                val deleteRequest = sendJson(
                    method = HttpMethod.Delete,
                    path = "api/hpc/apps/",
                    user = TestUsers.admin,
                    request = DeleteAppRequest(
                        "name",
                        "2.2"
                    )
                )

                deleteRequest.assertSuccess()
            }
        )
    }
}
