package dk.sdu.cloud.app.store.rpc

import com.fasterxml.jackson.module.kotlin.readValue
import dk.cloud.sdu.app.store.rpc.AppLogoController
import dk.sdu.cloud.app.store.api.*
import dk.sdu.cloud.app.store.services.AppStoreAsyncDao
import dk.sdu.cloud.app.store.services.AppStoreService
import dk.sdu.cloud.app.store.services.ApplicationLogoAsyncDao
import dk.sdu.cloud.app.store.services.ApplicationPublicAsyncDao
import dk.sdu.cloud.app.store.services.ApplicationPublicService
import dk.sdu.cloud.app.store.services.ApplicationSearchAsyncDao
import dk.sdu.cloud.app.store.services.ApplicationSearchService
import dk.sdu.cloud.app.store.services.ApplicationTagsAsyncDao
import dk.sdu.cloud.app.store.services.ApplicationTagsService
import dk.sdu.cloud.app.store.services.ElasticDao
import dk.sdu.cloud.app.store.services.FavoriteAsyncDao
import dk.sdu.cloud.app.store.services.FavoriteService
import dk.sdu.cloud.app.store.services.LogoService
import dk.sdu.cloud.app.store.services.ToolAsyncDao
import dk.sdu.cloud.app.store.services.acl.AclAsyncDao
import dk.sdu.cloud.app.store.util.normAppDesc
import dk.sdu.cloud.app.store.util.normAppDesc2
import dk.sdu.cloud.app.store.util.normToolDesc
import dk.sdu.cloud.app.store.util.truncate
import dk.sdu.cloud.app.store.util.withNameAndVersion
import dk.sdu.cloud.app.store.util.withNameAndVersionAndTitle
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.micro.*
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.db.async.AsyncDBSessionFactory
import dk.sdu.cloud.service.db.async.withSession
import dk.sdu.cloud.service.test.*
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
import org.junit.Test
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.assertEquals

private fun KtorApplicationTestSetupContext.configureAppServer(
    toolDao: ToolAsyncDao,
    aclDao: AclAsyncDao,
    appDao: AppStoreAsyncDao,
    tagDao: ApplicationTagsAsyncDao,
    searchDao: ApplicationSearchAsyncDao,
    appPublicDao: ApplicationPublicAsyncDao,
    favoriteDao: FavoriteAsyncDao,
    appLogoDao: ApplicationLogoAsyncDao,
    elasticDao: ElasticDao,
    db: AsyncDBSessionFactory
): List<Controller> {
    val authClient = mockk<AuthenticatedClient>(relaxed = true)
    val appStore = AppStoreService(
        db,
        authClient,
        appDao,
        appPublicDao,
        toolDao,
        aclDao,
        elasticDao,
        micro.eventStreamService.createProducer(AppStoreStreams.AppDeletedStream)
    )
    val logoService = LogoService(db, appLogoDao, toolDao)
    val tagService = ApplicationTagsService(db, tagDao, elasticDao)
    val searchService = ApplicationSearchService(db, searchDao, elasticDao, appDao, authClient)
    val publicService = ApplicationPublicService(db, appPublicDao)
    val favoriteService = FavoriteService(db, favoriteDao, authClient)
    return listOf(
        AppStoreController(appStore), 
        AppLogoController(logoService), 
        AppTagController(tagService), 
        AppSearchController(searchService), 
        AppPublicController(publicService), 
        AppFavoriteController(favoriteService)
    )
}

class AppTest {
    companion object {
        private lateinit var embDB: EmbeddedPostgres
        private lateinit var db: AsyncDBSessionFactory

        @BeforeClass
        @JvmStatic
        fun before() {
            val (db,embDB) = TestDB.from(AppStoreServiceDescription)
            this.db = db
            this.embDB = embDB
        }

        @AfterClass
        @JvmStatic
        fun after() {
            runBlocking {
                db.close()
            }
            embDB.close()
        }
    }

    @BeforeTest
    fun beforeEach() {
        truncate(db)
    }
    @AfterTest
    fun afterEach() {
        truncate(db)
    }

    @Test
    fun `Favorite test`() {
        withKtorTest(
            setup = {
                val user = TestUsers.user

                val elasticDAO = mockk<ElasticDao>()

                val toolDao = ToolAsyncDao()
                val aclDao = AclAsyncDao()
                val appPublicDao = ApplicationPublicAsyncDao()
                val appDao = AppStoreAsyncDao(toolDao, aclDao, appPublicDao)

                val tagDao = ApplicationTagsAsyncDao()
                val searchDao = ApplicationSearchAsyncDao(appDao)
                val favoriteDao = FavoriteAsyncDao(appPublicDao, aclDao)
                val appLogoDao = ApplicationLogoAsyncDao(appDao)
                runBlocking {
                    db.withSession {
                        toolDao.create(it, user, normToolDesc, "original")
                        appDao.create(it, user, normAppDesc, "original")
                        appDao.create(
                            it,
                            user,
                            normAppDesc2.copy(metadata = normAppDesc2.metadata.copy(name = "App4", version = "4.4")),
                            "original")
                    }
                }
                configureAppServer(toolDao, aclDao, appDao, tagDao, searchDao, appPublicDao, favoriteDao, appLogoDao, elasticDAO, db)
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
                val elasticDAO = mockk<ElasticDao>()

                val toolDao = ToolAsyncDao()
                val aclDao = AclAsyncDao()
                val appPublicDao = ApplicationPublicAsyncDao()
                val appDao = AppStoreAsyncDao(toolDao, aclDao, appPublicDao)

                val tagDao = ApplicationTagsAsyncDao()
                val searchDao = mockk<ApplicationSearchAsyncDao>()
                val favoriteDao = FavoriteAsyncDao(appPublicDao, aclDao)
                val appLogoDao = ApplicationLogoAsyncDao(appDao)
                coEvery { searchDao.searchByTags(any(), any(), any(), any(), any(), any()) } answers {
                    val items = listOf(
                        ApplicationSummaryWithFavorite(
                            normAppDesc.metadata,
                            true,
                            listOf("tag1", "tag2")
                        )
                    )
                    val page = Page(1, 10, 0, items)
                    page
                }
                configureAppServer(toolDao, aclDao, appDao, tagDao, searchDao, appPublicDao, favoriteDao, appLogoDao, elasticDAO, db)
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
                val elasticDAO = mockk<ElasticDao>()

                val toolDao = ToolAsyncDao()
                val aclDao = AclAsyncDao()
                val appPublicDao = ApplicationPublicAsyncDao()
                val appDao = AppStoreAsyncDao(toolDao, aclDao, appPublicDao)

                val tagDao = ApplicationTagsAsyncDao()
                val searchDao = ApplicationSearchAsyncDao(appDao)
                val favoriteDao = FavoriteAsyncDao(appPublicDao, aclDao)
                val appLogoDao = ApplicationLogoAsyncDao(appDao)
                runBlocking {
                    db.withSession {
                        toolDao.create(it, user, normToolDesc, "original")
                        appDao.create(
                            it,
                            user,
                            normAppDesc.withNameAndVersionAndTitle("name1", "1", "1title"),
                            "original"
                        )
                        appDao.create(
                            it,
                            user,
                            normAppDesc2.withNameAndVersionAndTitle("name2", "2", "2title"),
                            "original"
                        )

                        tagDao.createTags(it, user, "name1", listOf("tag1", "tag2"))
                        tagDao.createTags(it, user, "name2", listOf("tag2", "tag3"))
                    }
                }
                configureAppServer(toolDao, aclDao, appDao, tagDao, searchDao, appPublicDao, favoriteDao, appLogoDao, elasticDAO, db)
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
                val elasticDAO = mockk<ElasticDao>()

                val toolDao = ToolAsyncDao()
                val aclDao = AclAsyncDao()
                val appPublicDao = ApplicationPublicAsyncDao()
                val appDao = AppStoreAsyncDao(toolDao, aclDao, appPublicDao)

                val tagDao = ApplicationTagsAsyncDao()
                val searchDao = ApplicationSearchAsyncDao(appDao)
                val favoriteDao = FavoriteAsyncDao(appPublicDao, aclDao)
                val appLogoDao = ApplicationLogoAsyncDao(appDao)
                runBlocking {
                    db.withSession {
                        toolDao.create(it, TestUsers.user, normToolDesc, "original")
                        appDao.create(it, TestUsers.user, app, "original")

                    }
                }
                configureAppServer(toolDao, aclDao, appDao, tagDao, searchDao, appPublicDao, favoriteDao, appLogoDao, elasticDAO, db)
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
                val elasticDAO = mockk<ElasticDao>()

                val toolDao = mockk<ToolAsyncDao>()
                val aclDao = AclAsyncDao()
                val appPublicDao = mockk<ApplicationPublicAsyncDao>()
                val appDao = mockk<AppStoreAsyncDao>()

                val tagDao = mockk<ApplicationTagsAsyncDao>()
                val searchDao = mockk<ApplicationSearchAsyncDao>()
                val favoriteDao = mockk<FavoriteAsyncDao>()
                val appLogoDao = mockk<ApplicationLogoAsyncDao>()

                coEvery { toolDao.findByNameAndVersion(any(), any(), any(), any()) } answers {
                    Tool(
                        "owner",
                        123456,
                        123456,
                        normToolDesc
                    )
                }

                coEvery { appDao.findByNameAndVersionForUser(any(), any(), any(), any(), any(), any()) } answers {
                    ApplicationWithFavoriteAndTags(
                        application.metadata,
                        application.invocation,
                        false,
                        emptyList()
                    )
                }

                configureAppServer(toolDao, aclDao, appDao, tagDao, searchDao, appPublicDao, favoriteDao, appLogoDao, elasticDAO, db)

            },
            test = {
                val request = sendRequest(
                    method = HttpMethod.Get,
                    path = "/api/hpc/apps/$name/$version?itemsPerPage=10&page=0",
                    user = TestUsers.user
                )
                println(request)
                println(request.response.status())
                request.assertSuccess()
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
                val elasticDAO = mockk<ElasticDao>()

                val toolDao = ToolAsyncDao()
                val aclDao = AclAsyncDao()
                val appPublicDao = mockk<ApplicationPublicAsyncDao>()
                val appDao = mockk<AppStoreAsyncDao>()

                val tagDao = mockk<ApplicationTagsAsyncDao>()
                val searchDao = mockk<ApplicationSearchAsyncDao>()
                val favoriteDao = mockk<FavoriteAsyncDao>()
                val appLogoDao = mockk<ApplicationLogoAsyncDao>()

                coEvery { appDao.findAllByName(any(), any(), any(), any(), any(), any()) } answers {
                    Page(
                        1,
                        10,
                        0,
                        listOf(ApplicationSummaryWithFavorite(application.metadata, true, emptyList()))
                    )
                }

                configureAppServer(toolDao, aclDao, appDao, tagDao, searchDao, appPublicDao, favoriteDao, appLogoDao, elasticDAO, db)
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
                val elasticDAO = mockk<ElasticDao>()

                val toolDao = ToolAsyncDao()
                val aclDao = AclAsyncDao()
                val appPublicDao = mockk<ApplicationPublicAsyncDao>()
                val appDao = mockk<AppStoreAsyncDao>()

                val tagDao = mockk<ApplicationTagsAsyncDao>()
                val searchDao = mockk<ApplicationSearchAsyncDao>()
                val favoriteDao = mockk<FavoriteAsyncDao>()
                val appLogoDao = mockk<ApplicationLogoAsyncDao>()

                coEvery { appDao.listLatestVersion(any(), any(), any(), any(), any()) } answers {
                    Page(
                        1,
                        10,
                        0,
                        listOf(ApplicationSummaryWithFavorite(normAppDesc.metadata, true, emptyList()))
                    )
                }

                configureAppServer(toolDao, aclDao, appDao, tagDao, searchDao, appPublicDao, favoriteDao, appLogoDao, elasticDAO, db)
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
                val elasticDAO = mockk<ElasticDao>()

                val toolDao = ToolAsyncDao()
                val aclDao = AclAsyncDao()
                val appPublicDao = mockk<ApplicationPublicAsyncDao>()
                val appDao = AppStoreAsyncDao(toolDao, aclDao, appPublicDao)

                val tagDao = mockk<ApplicationTagsAsyncDao>()
                val searchDao = mockk<ApplicationSearchAsyncDao>()
                val favoriteDao = mockk<FavoriteAsyncDao>()
                val appLogoDao = mockk<ApplicationLogoAsyncDao>()

                configureAppServer(toolDao, aclDao, appDao, tagDao, searchDao, appPublicDao, favoriteDao, appLogoDao, elasticDAO, db)
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
                val elasticDAO = mockk<ElasticDao>()

                val toolDao = ToolAsyncDao()
                val aclDao = AclAsyncDao()
                val appPublicDao = mockk<ApplicationPublicAsyncDao>()
                val appDao = AppStoreAsyncDao(toolDao, aclDao, appPublicDao)

                val tagDao = mockk<ApplicationTagsAsyncDao>()
                val searchDao = mockk<ApplicationSearchAsyncDao>()
                val favoriteDao = mockk<FavoriteAsyncDao>()
                val appLogoDao = mockk<ApplicationLogoAsyncDao>()

                coEvery { tagDao.createTags(any(), any(), any(), any()) } just runs
                coEvery { tagDao.deleteTags(any(), any(), any(), any()) } just runs
                every { elasticDAO.addTagToElastic(any(), any()) } just runs
                every { elasticDAO.removeTagFromElastic(any(), any()) } just runs
                configureAppServer(toolDao, aclDao, appDao, tagDao, searchDao, appPublicDao, favoriteDao, appLogoDao, elasticDAO, db)
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
                val elasticDAO = mockk<ElasticDao>()

                val toolDao = ToolAsyncDao()
                val aclDao = AclAsyncDao()
                val appPublicDao = mockk<ApplicationPublicAsyncDao>(relaxed = true)
                val appDao = AppStoreAsyncDao(toolDao, aclDao, appPublicDao)

                val tagDao = mockk<ApplicationTagsAsyncDao>(relaxed = true)
                val searchDao = mockk<ApplicationSearchAsyncDao>(relaxed = true)
                val favoriteDao = mockk<FavoriteAsyncDao>(relaxed = true)
                val appLogoDao = mockk<ApplicationLogoAsyncDao>(relaxed = true)
                configureAppServer(toolDao, aclDao, appDao, tagDao, searchDao, appPublicDao, favoriteDao, appLogoDao, elasticDAO, db)
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
                val elasticDAO = mockk<ElasticDao>()

                val toolDao = ToolAsyncDao()
                val aclDao = AclAsyncDao()
                val appPublicDao = mockk<ApplicationPublicAsyncDao>()
                val appDao = AppStoreAsyncDao(toolDao, aclDao, appPublicDao)

                val tagDao = mockk<ApplicationTagsAsyncDao>()
                val searchDao = mockk<ApplicationSearchAsyncDao>()
                val favoriteDao = mockk<FavoriteAsyncDao>()
                val appLogoDao = mockk<ApplicationLogoAsyncDao>(relaxed = true)
                configureAppServer(toolDao, aclDao, appDao, tagDao, searchDao, appPublicDao, favoriteDao, appLogoDao, elasticDAO, db)
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
                val elasticDAO = mockk<ElasticDao>()

                val toolDao = ToolAsyncDao()
                val aclDao = AclAsyncDao()
                val appPublicDao = mockk<ApplicationPublicAsyncDao>()
                val appDao = AppStoreAsyncDao(toolDao, aclDao, appPublicDao)

                val tagDao = mockk<ApplicationTagsAsyncDao>()
                val searchDao = mockk<ApplicationSearchAsyncDao>()
                val favoriteDao = mockk<FavoriteAsyncDao>()
                val appLogoDao = mockk<ApplicationLogoAsyncDao>()
                coEvery {appLogoDao.fetchLogo(any(), any())} answers {
                    ByteArray(1234)
                }
                configureAppServer(toolDao, aclDao, appDao, tagDao, searchDao, appPublicDao, favoriteDao, appLogoDao, elasticDAO, db)
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
                val elasticDAO = mockk<ElasticDao>()

                val toolDao = ToolAsyncDao()
                val aclDao = AclAsyncDao()
                val appPublicDao = mockk<ApplicationPublicAsyncDao>()
                val appDao = AppStoreAsyncDao(toolDao, aclDao, appPublicDao)

                val tagDao = mockk<ApplicationTagsAsyncDao>()
                val searchDao = mockk<ApplicationSearchAsyncDao>()
                val favoriteDao = mockk<FavoriteAsyncDao>()
                val appLogoDao = mockk<ApplicationLogoAsyncDao>(relaxed = true)
                configureAppServer(toolDao, aclDao, appDao, tagDao, searchDao, appPublicDao, favoriteDao, appLogoDao, elasticDAO, db)
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
                val elasticDAO = mockk<ElasticDao>()

                val toolDao = ToolAsyncDao()
                val aclDao = AclAsyncDao()
                val appPublicDao = mockk<ApplicationPublicAsyncDao>()
                val appDao = mockk<AppStoreAsyncDao>()

                val tagDao = mockk<ApplicationTagsAsyncDao>()
                val searchDao = mockk<ApplicationSearchAsyncDao>()
                val favoriteDao = mockk<FavoriteAsyncDao>()
                val appLogoDao = mockk<ApplicationLogoAsyncDao>()

                coEvery { appDao.delete(any(), any(), any(), any(), any(), any()) } just runs
                every { elasticDAO.deleteApplicationInElastic(any(), any()) } just runs
                configureAppServer(toolDao, aclDao, appDao, tagDao, searchDao, appPublicDao, favoriteDao, appLogoDao, elasticDAO, db)
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
