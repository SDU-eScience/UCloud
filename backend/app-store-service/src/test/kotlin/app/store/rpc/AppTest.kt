package dk.sdu.cloud.app.store.rpc

import app.store.services.ApplicationLogoAsyncDAO
import app.store.services.ApplicationPublicAsyncDAO
import app.store.services.ApplicationPublicService
import app.store.services.ApplicationSearchAsyncDAO
import app.store.services.ApplicationSearchService
import app.store.services.ApplicationTagsAsyncDAO
import app.store.services.ApplicationTagsService
import app.store.services.FavoriteAsyncDAO
import app.store.services.FavoriteService
import com.fasterxml.jackson.module.kotlin.readValue
import dk.sdu.cloud.app.store.api.*
import dk.sdu.cloud.app.store.services.AppStoreAsyncDAO
import dk.sdu.cloud.app.store.services.AppStoreService
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
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.micro.*
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.db.async.AsyncDBSessionFactory
import dk.sdu.cloud.service.db.async.withSession
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
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import kotlinx.coroutines.runBlocking
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Ignore
import org.junit.Test
import kotlin.test.assertEquals

private fun KtorApplicationTestSetupContext.configureAppServer(
    appDao: AppStoreAsyncDAO,
    tagDao: ApplicationTagsAsyncDAO,
    searchDao: ApplicationSearchAsyncDAO,
    appPublicDao: ApplicationPublicAsyncDAO,
    favoriteDao: FavoriteAsyncDAO,
    appLogoDao: ApplicationLogoAsyncDAO,
    elasticDAO: ElasticDAO,
    db: AsyncDBSessionFactory
): List<Controller> {
    val toolDao = mockk<ToolDAO>(relaxed = true)
    val aclDao = mockk<AclDao>(relaxed = true)
    val authClient = mockk<AuthenticatedClient>(relaxed = true)
    val appStore = AppStoreService(
        db,
        authClient,
        appDao,
        appPublicDao,
        toolDao,
        aclDao,
        elasticDAO,
        micro.eventStreamService.createProducer(AppStoreStreams.AppDeletedStream)
    )
    val logoService = LogoService(db, appLogoDao, toolDao)
    val tagService = ApplicationTagsService(db, tagDao, elasticDAO)
    val searchService = ApplicationSearchService(db, searchDao, elasticDAO, appDao, authClient)
    val publicService = ApplicationPublicService(db, appPublicDao)
    val favoriteService = FavoriteService(db, favoriteDao, authClient)
    return listOf(AppStoreController(appStore, logoService, tagService, searchService, publicService, favoriteService))
}

class AppTest {

    private lateinit var embDB: EmbeddedPostgres
    private lateinit var db: AsyncDBSessionFactory

    @BeforeClass
    fun before() {
        val (db,embDB) = TestDB.from(AppStoreServiceDescription)
        this.db = db
        this.embDB = embDB
    }

    @AfterClass
    fun after() {
        runBlocking {
            db.close()
        }
        embDB.close()
    }

    @Test
    fun `Favorite test`() {
        withKtorTest(
            setup = {
                val user = TestUsers.user

                val elasticDAO = mockk<ElasticDAO>()

                val toolDao = ToolHibernateDAO()
                val aclDao = AclHibernateDao()
                val appPublicDao = mockk<ApplicationPublicAsyncDAO>()
                val appDao = AppStoreAsyncDAO(toolDao, aclDao, appPublicDao)

                val tagDao = mockk<ApplicationTagsAsyncDAO>()
                val searchDao = mockk<ApplicationSearchAsyncDAO>()
                val favoriteDao = mockk<FavoriteAsyncDAO>()
                val appLogoDao = mockk<ApplicationLogoAsyncDAO>()
                runBlocking {
                    db.withSession {
                        toolDao.create(it, user, normToolDesc)
                        appDao.create(it, user, normAppDesc)
                        appDao.create(
                            it,
                            user,
                            normAppDesc2.copy(metadata = normAppDesc2.metadata.copy(name = "App4", version = "4.4"))
                        )
                    }
                }
                configureAppServer(appDao, tagDao, searchDao, appPublicDao, favoriteDao, appLogoDao, elasticDAO, db)
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
                val elasticDAO = mockk<ElasticDAO>()

                val toolDao = ToolHibernateDAO()
                val aclDao = AclHibernateDao()
                val appPublicDao = mockk<ApplicationPublicAsyncDAO>()
                val appDao = AppStoreAsyncDAO(toolDao, aclDao, appPublicDao)

                val tagDao = mockk<ApplicationTagsAsyncDAO>()
                val searchDao = mockk<ApplicationSearchAsyncDAO>()
                val favoriteDao = mockk<FavoriteAsyncDAO>()
                val appLogoDao = mockk<ApplicationLogoAsyncDAO>()
                coEvery { searchDao.searchByTags(any(), any(), any(), any(), any(), any()) } answers {
                    val items = listOf(
                        mockk<ApplicationSummaryWithFavorite>(relaxed = true)
                    )
                    val page = Page(1, 10, 0, items)
                    page
                }
                configureAppServer(appDao, tagDao, searchDao, appPublicDao, favoriteDao, appLogoDao, elasticDAO, db)
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
                val elasticDAO = mockk<ElasticDAO>()

                val toolDao = ToolHibernateDAO()
                val aclDao = AclHibernateDao()
                val appPublicDao = mockk<ApplicationPublicAsyncDAO>()
                val appDao = AppStoreAsyncDAO(toolDao, aclDao, appPublicDao)

                val tagDao = mockk<ApplicationTagsAsyncDAO>()
                val searchDao = mockk<ApplicationSearchAsyncDAO>()
                val favoriteDao = mockk<FavoriteAsyncDAO>()
                val appLogoDao = mockk<ApplicationLogoAsyncDAO>()
                runBlocking {
                    db.withSession {
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

                        tagDao.createTags(it, user, "name1", listOf("tag1", "tag2"))
                        tagDao.createTags(it, user, "name2", listOf("tag2", "tag3"))
                    }
                }
                configureAppServer(appDao, tagDao, searchDao, appPublicDao, favoriteDao, appLogoDao, elasticDAO, db)
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
                val elasticDAO = mockk<ElasticDAO>()

                val toolDao = ToolHibernateDAO()
                val aclDao = AclHibernateDao()
                val appPublicDao = mockk<ApplicationPublicAsyncDAO>()
                val appDao = AppStoreAsyncDAO(toolDao, aclDao, appPublicDao)

                val tagDao = mockk<ApplicationTagsAsyncDAO>()
                val searchDao = mockk<ApplicationSearchAsyncDAO>()
                val favoriteDao = mockk<FavoriteAsyncDAO>()
                val appLogoDao = mockk<ApplicationLogoAsyncDAO>()
                runBlocking {
                    db.withSession {
                        toolDao.create(it, TestUsers.user, normToolDesc)
                        appDao.create(it, TestUsers.user, app)

                    }
                }
                configureAppServer(appDao, tagDao, searchDao, appPublicDao, favoriteDao, appLogoDao, elasticDAO, db)
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
                val elasticDAO = mockk<ElasticDAO>()

                val toolDao = ToolHibernateDAO()
                val aclDao = AclHibernateDao()
                val appPublicDao = mockk<ApplicationPublicAsyncDAO>()
                val appDao = AppStoreAsyncDAO(toolDao, aclDao, appPublicDao)

                val tagDao = mockk<ApplicationTagsAsyncDAO>()
                val searchDao = mockk<ApplicationSearchAsyncDAO>()
                val favoriteDao = mockk<FavoriteAsyncDAO>()
                val appLogoDao = mockk<ApplicationLogoAsyncDAO>()

                coEvery { appDao.findByNameAndVersionForUser(any(), any(), any(), any(), any(), any()) } answers {
                    ApplicationWithFavoriteAndTags(
                        application.metadata,
                        application.invocation,
                        false,
                        emptyList()
                    )
                }

                configureAppServer(appDao, tagDao, searchDao, appPublicDao, favoriteDao, appLogoDao, elasticDAO, db)

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
                val elasticDAO = mockk<ElasticDAO>()

                val toolDao = ToolHibernateDAO()
                val aclDao = AclHibernateDao()
                val appPublicDao = mockk<ApplicationPublicAsyncDAO>()
                val appDao = AppStoreAsyncDAO(toolDao, aclDao, appPublicDao)

                val tagDao = mockk<ApplicationTagsAsyncDAO>()
                val searchDao = mockk<ApplicationSearchAsyncDAO>()
                val favoriteDao = mockk<FavoriteAsyncDAO>()
                val appLogoDao = mockk<ApplicationLogoAsyncDAO>()

                coEvery { appDao.findAllByName(any(), any(), any(), any(), any(), any()) } answers {
                    Page(
                        1,
                        10,
                        0,
                        listOf(ApplicationSummaryWithFavorite(application.metadata, true, emptyList()))
                    )
                }

                configureAppServer(appDao, tagDao, searchDao, appPublicDao, favoriteDao, appLogoDao, elasticDAO, db)
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
                val elasticDAO = mockk<ElasticDAO>()

                val toolDao = ToolHibernateDAO()
                val aclDao = AclHibernateDao()
                val appPublicDao = mockk<ApplicationPublicAsyncDAO>()
                val appDao = AppStoreAsyncDAO(toolDao, aclDao, appPublicDao)

                val tagDao = mockk<ApplicationTagsAsyncDAO>()
                val searchDao = mockk<ApplicationSearchAsyncDAO>()
                val favoriteDao = mockk<FavoriteAsyncDAO>()
                val appLogoDao = mockk<ApplicationLogoAsyncDAO>()

                coEvery { appDao.listLatestVersion(any(), any(), any(), any(), any()) } answers {
                    Page(
                        1,
                        10,
                        0,
                        listOf(ApplicationSummaryWithFavorite(normAppDesc.metadata, true, emptyList()))
                    )
                }

                configureAppServer(appDao, tagDao, searchDao, appPublicDao, favoriteDao, appLogoDao, elasticDAO, db)
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
                val elasticDAO = mockk<ElasticDAO>()

                val toolDao = ToolHibernateDAO()
                val aclDao = AclHibernateDao()
                val appPublicDao = mockk<ApplicationPublicAsyncDAO>()
                val appDao = AppStoreAsyncDAO(toolDao, aclDao, appPublicDao)

                val tagDao = mockk<ApplicationTagsAsyncDAO>()
                val searchDao = mockk<ApplicationSearchAsyncDAO>()
                val favoriteDao = mockk<FavoriteAsyncDAO>()
                val appLogoDao = mockk<ApplicationLogoAsyncDAO>()

                configureAppServer(appDao, tagDao, searchDao, appPublicDao, favoriteDao, appLogoDao, elasticDAO, db)
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
                val elasticDAO = mockk<ElasticDAO>()

                val toolDao = ToolHibernateDAO()
                val aclDao = AclHibernateDao()
                val appPublicDao = mockk<ApplicationPublicAsyncDAO>()
                val appDao = AppStoreAsyncDAO(toolDao, aclDao, appPublicDao)

                val tagDao = mockk<ApplicationTagsAsyncDAO>()
                val searchDao = mockk<ApplicationSearchAsyncDAO>()
                val favoriteDao = mockk<FavoriteAsyncDAO>()
                val appLogoDao = mockk<ApplicationLogoAsyncDAO>()

                coEvery { tagDao.createTags(any(), any(), any(), any()) } just runs
                coEvery { tagDao.deleteTags(any(), any(), any(), any()) } just runs
                every { elasticDAO.addTagToElastic(any(), any()) } just runs
                every { elasticDAO.removeTagFromElastic(any(), any()) } just runs
                configureAppServer(appDao, tagDao, searchDao, appPublicDao, favoriteDao, appLogoDao, elasticDAO, db)
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

    @Test
    fun `update Logo test`() {
        withKtorTest(
            setup = {
                val elasticDAO = mockk<ElasticDAO>()

                val toolDao = ToolHibernateDAO()
                val aclDao = AclHibernateDao()
                val appPublicDao = mockk<ApplicationPublicAsyncDAO>()
                val appDao = AppStoreAsyncDAO(toolDao, aclDao, appPublicDao)

                val tagDao = mockk<ApplicationTagsAsyncDAO>()
                val searchDao = mockk<ApplicationSearchAsyncDAO>()
                val favoriteDao = mockk<FavoriteAsyncDAO>()
                val appLogoDao = mockk<ApplicationLogoAsyncDAO>()
                configureAppServer(appDao, tagDao, searchDao, appPublicDao, favoriteDao, appLogoDao, elasticDAO, db)
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
                val elasticDAO = mockk<ElasticDAO>()

                val toolDao = ToolHibernateDAO()
                val aclDao = AclHibernateDao()
                val appPublicDao = mockk<ApplicationPublicAsyncDAO>()
                val appDao = AppStoreAsyncDAO(toolDao, aclDao, appPublicDao)

                val tagDao = mockk<ApplicationTagsAsyncDAO>()
                val searchDao = mockk<ApplicationSearchAsyncDAO>()
                val favoriteDao = mockk<FavoriteAsyncDAO>()
                val appLogoDao = mockk<ApplicationLogoAsyncDAO>()
                configureAppServer(appDao, tagDao, searchDao, appPublicDao, favoriteDao, appLogoDao, elasticDAO, db)
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
                val elasticDAO = mockk<ElasticDAO>()

                val toolDao = ToolHibernateDAO()
                val aclDao = AclHibernateDao()
                val appPublicDao = mockk<ApplicationPublicAsyncDAO>()
                val appDao = AppStoreAsyncDAO(toolDao, aclDao, appPublicDao)

                val tagDao = mockk<ApplicationTagsAsyncDAO>()
                val searchDao = mockk<ApplicationSearchAsyncDAO>()
                val favoriteDao = mockk<FavoriteAsyncDAO>()
                val appLogoDao = mockk<ApplicationLogoAsyncDAO>()
                configureAppServer(appDao, tagDao, searchDao, appPublicDao, favoriteDao, appLogoDao, elasticDAO, db)
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
                val elasticDAO = mockk<ElasticDAO>()

                val toolDao = ToolHibernateDAO()
                val aclDao = AclHibernateDao()
                val appPublicDao = mockk<ApplicationPublicAsyncDAO>()
                val appDao = AppStoreAsyncDAO(toolDao, aclDao, appPublicDao)

                val tagDao = mockk<ApplicationTagsAsyncDAO>()
                val searchDao = mockk<ApplicationSearchAsyncDAO>()
                val favoriteDao = mockk<FavoriteAsyncDAO>()
                val appLogoDao = mockk<ApplicationLogoAsyncDAO>()
                configureAppServer(appDao, tagDao, searchDao, appPublicDao, favoriteDao, appLogoDao, elasticDAO, db)
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
                val elasticDAO = mockk<ElasticDAO>()

                val toolDao = ToolHibernateDAO()
                val aclDao = AclHibernateDao()
                val appPublicDao = mockk<ApplicationPublicAsyncDAO>()
                val appDao = AppStoreAsyncDAO(toolDao, aclDao, appPublicDao)

                val tagDao = mockk<ApplicationTagsAsyncDAO>()
                val searchDao = mockk<ApplicationSearchAsyncDAO>()
                val favoriteDao = mockk<FavoriteAsyncDAO>()
                val appLogoDao = mockk<ApplicationLogoAsyncDAO>()

                coEvery { appDao.delete(any(), any(), any(), any(), any(), any()) } just runs
                every { elasticDAO.deleteApplicationInElastic(any(), any()) } just runs
                configureAppServer(appDao, tagDao, searchDao, appPublicDao, favoriteDao, appLogoDao, elasticDAO, db)
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
