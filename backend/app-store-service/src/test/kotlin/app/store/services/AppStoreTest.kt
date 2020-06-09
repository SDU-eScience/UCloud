package dk.sdu.cloud.app.store.services

import app.store.services.ApplicationPublicAsyncDAO
import app.store.services.ApplicationPublicService
import app.store.services.ApplicationSearchAsyncDAO
import app.store.services.ApplicationSearchService
import app.store.services.ApplicationTagsAsyncDAO
import app.store.services.ApplicationTagsService
import app.store.services.FavoriteAsyncDAO
import app.store.services.FavoriteService
import dk.sdu.cloud.app.store.api.AppStoreServiceDescription
import dk.sdu.cloud.app.store.api.AppStoreStreams
import dk.sdu.cloud.app.store.api.NameAndVersion
import dk.sdu.cloud.app.store.services.acl.AclHibernateDao
import dk.sdu.cloud.app.store.util.*
import dk.sdu.cloud.auth.api.authenticator
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.OutgoingHttpCall
import dk.sdu.cloud.micro.*
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.PaginationRequest
import dk.sdu.cloud.service.db.HibernateSession
import dk.sdu.cloud.service.db.async.AsyncDBSessionFactory
import dk.sdu.cloud.service.test.TestUsers
import dk.sdu.cloud.service.test.initializeMicro
import io.mockk.mockk
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import kotlinx.coroutines.runBlocking
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Ignore
import org.junit.Test
import kotlin.test.assertEquals

class AppStoreTest {

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

    //Requires running Elastic with empty application index
    @Ignore
    @Test
    fun realTestOfCreateAndDeleteTag() {
        val micro = initializeMicro()
        micro.install(ElasticFeature)

        val toolHibernateDAO = mockk<ToolHibernateDAO>(relaxed = true)
        val aclHibernateDao = mockk<AclHibernateDao>(relaxed = true)
        val publicDao = mockk<ApplicationPublicAsyncDAO>(relaxed = true)
        val appDao = AppStoreAsyncDAO(toolHibernateDAO, aclHibernateDao, publicDao)
        val elasticDAO = ElasticDAO(micro.elasticHighLevelClient)
        val aclDao = AclHibernateDao()
        val authClient = mockk<AuthenticatedClient>(relaxed = true)
        val searchDao = mockk<ApplicationSearchAsyncDAO>(relaxed = true)
        val tagDao = mockk<ApplicationTagsAsyncDAO>(relaxed = true)
        val applicationService =
            AppStoreService(
                db,
                authClient,
                appDao,
                publicDao,
                toolHibernateDAO,
                aclDao,
                elasticDAO,
                micro.eventStreamService.createProducer(AppStoreStreams.AppDeletedStream)
            )
        val appSearchService = ApplicationSearchService(db, searchDao, elasticDAO, appDao, authClient)
        val appTagService = ApplicationTagsService(db, tagDao, elasticDAO)

        runBlocking {
            applicationService.create(TestUsers.admin, normAppDesc.withNameAndVersion("ansys", "1.2.1"), "content")
            applicationService.create(TestUsers.admin, normAppDesc.withNameAndVersion("ansys", "1.2.2"), "content")
            applicationService.create(TestUsers.admin, normAppDesc.withNameAndVersion("ansys", "1.2.3"), "content")
        }

        Thread.sleep(1000)

        runBlocking {
            val advancedSearchResultForTest1 = appSearchService.advancedSearch(
                TestUsers.admin,
                null,
                "",
                listOf("test1"),
                true,
                NormalizedPaginationRequest(10, 0)
            )

            val advancedSearchResultForTest2 = appSearchService.advancedSearch(
                TestUsers.admin,
                null,
                "",
                listOf("test2"),
                true,
                NormalizedPaginationRequest(10, 0)
            )

            val advancedSearchResultForAll = appSearchService.advancedSearch(
                TestUsers.admin,
                null,
                "",
                listOf("test1", "test2"),
                true,
                NormalizedPaginationRequest(10, 0)
            )

            assertEquals(0, advancedSearchResultForTest1.itemsInTotal)
            assertEquals(0, advancedSearchResultForTest2.itemsInTotal)
            assertEquals(0, advancedSearchResultForAll.itemsInTotal)
        }

        runBlocking {
            appTagService.createTags(listOf("test1", "test2"), "ansys", TestUsers.admin)
        }

        Thread.sleep(1000)

        runBlocking {
            val advancedSearchResultForTest1 = appSearchService.advancedSearch(
                TestUsers.admin,
                null,
                "",
                listOf("test1"),
                true,
                NormalizedPaginationRequest(10, 0)
            )

            val advancedSearchResultForTest2 = appSearchService.advancedSearch(
                TestUsers.admin,
                null,
                "",
                listOf("test2"),
                true,
                NormalizedPaginationRequest(10, 0)
            )

            val advancedSearchResultForAll = appSearchService.advancedSearch(
                TestUsers.admin,
                null,
                "",
                listOf("test1", "test2"),
                true,
                NormalizedPaginationRequest(10, 0)
            )

            assertEquals(3, advancedSearchResultForTest1.itemsInTotal)
            assertEquals(3, advancedSearchResultForTest2.itemsInTotal)
            assertEquals(3, advancedSearchResultForAll.itemsInTotal)
        }

        runBlocking {
            appTagService.deleteTags(listOf("test2"), "ansys", TestUsers.admin)
        }

        Thread.sleep(1000)

        runBlocking {
            val advancedSearchResultForTest1 = appSearchService.advancedSearch(
                TestUsers.admin,
                null,
                "",
                listOf("test1"),
                true,
                NormalizedPaginationRequest(10, 0)
            )

            val advancedSearchResultForTest2 = appSearchService.advancedSearch(
                TestUsers.admin,
                null,
                "",
                listOf("test2"),
                true,
                NormalizedPaginationRequest(10, 0)
            )

            val advancedSearchResultForAll = appSearchService.advancedSearch(
                TestUsers.admin,
                null,
                "",
                listOf("test1", "test2"),
                true,
                NormalizedPaginationRequest(10, 0)
            )

            assertEquals(3, advancedSearchResultForTest1.itemsInTotal)
            assertEquals(0, advancedSearchResultForTest2.itemsInTotal)
            assertEquals(3, advancedSearchResultForAll.itemsInTotal)

        }
    }

    private fun initAppStoreWithMockedElasticAndTool(): AppStoreService {
        val micro = initializeMicro()
        val toolHibernateDAO = mockk<ToolHibernateDAO>(relaxed = true)
        val aclHibernateDao = mockk<AclHibernateDao>(relaxed = true)
        val publicDao = mockk<ApplicationPublicAsyncDAO>(relaxed = true)
        val appDAO = AppStoreAsyncDAO(toolHibernateDAO, aclHibernateDao, publicDao)
        val aclDao = AclHibernateDao()
        val elasticDAO = mockk<ElasticDAO>(relaxed = true)
        val authClient = mockk<AuthenticatedClient>(relaxed = true)

        return AppStoreService(
            db,
            authClient,
            appDAO,
            publicDao,
            toolHibernateDAO,
            aclDao,
            elasticDAO,
            micro.eventStreamService.createProducer(AppStoreStreams.AppDeletedStream)
        )
    }


    @Test
    fun `toggle favorites and retrieve`() {
        val micro = initializeMicro()
        val toolHibernateDAO = mockk<ToolHibernateDAO>(relaxed = true)
        val aclHibernateDao = mockk<AclHibernateDao>(relaxed = true)
        val publicDao = mockk<ApplicationPublicAsyncDAO>(relaxed = true)
        val appDAO = AppStoreAsyncDAO(toolHibernateDAO, aclHibernateDao, publicDao)
        val aclDao = AclHibernateDao()
        val favoriteDao = mockk<FavoriteAsyncDAO>(relaxed = true)
        val elasticDAO = mockk<ElasticDAO>(relaxed = true)
        val authClient = mockk<AuthenticatedClient>(relaxed = true)

        val appStoreService = AppStoreService(
            db,
            authClient,
            appDAO,
            publicDao,
            toolHibernateDAO,
            aclDao,
            elasticDAO,
            micro.eventStreamService.createProducer(AppStoreStreams.AppDeletedStream)
        )

        val favoriteService = FavoriteService(db, favoriteDao, authClient)

        runBlocking {
            appStoreService.create(TestUsers.admin, normAppDesc, "content")
            favoriteService.toggleFavorite(
                TestUsers.user,
                null,
                normAppDesc.metadata.name,
                normAppDesc.metadata.version
            )

            val retrievedFav = favoriteService.retrieveFavorites(TestUsers.user, null, PaginationRequest())

            assertEquals(1, retrievedFav.itemsInTotal)
            assertEquals(normAppDesc.metadata.title, retrievedFav.items.first().metadata.title)

            favoriteService.toggleFavorite(
                TestUsers.user,
                null,
                normAppDesc.metadata.name,
                normAppDesc.metadata.version
            )

            val retrievedFavAfterRemoved = favoriteService.retrieveFavorites(TestUsers.user, null, PaginationRequest())

            assertEquals(0, retrievedFavAfterRemoved.itemsInTotal)
        }
    }

    @Test
    fun `add remove search tags`() {
        runBlocking {
            val micro = initializeMicro()
            val toolHibernateDAO = mockk<ToolHibernateDAO>(relaxed = true)
            val aclHibernateDao = mockk<AclHibernateDao>(relaxed = true)
            val publicDao = mockk<ApplicationPublicAsyncDAO>(relaxed = true)
            val appDAO = AppStoreAsyncDAO(toolHibernateDAO, aclHibernateDao, publicDao)
            val aclDao = AclHibernateDao()
            val tagDao = mockk<ApplicationTagsAsyncDAO>(relaxed = true)
            val elasticDAO = mockk<ElasticDAO>(relaxed = true)
            val authClient = mockk<AuthenticatedClient>(relaxed = true)
            val searchDao = mockk<ApplicationSearchAsyncDAO>(relaxed = true)
            val appStoreService = AppStoreService(
                db,
                authClient,
                appDAO,
                publicDao,
                toolHibernateDAO,
                aclDao,
                elasticDAO,
                micro.eventStreamService.createProducer(AppStoreStreams.AppDeletedStream)
            )

            val tagService = ApplicationTagsService(db, tagDao, elasticDAO)
            val searchService = ApplicationSearchService(db, searchDao, elasticDAO, appDAO, authClient)

            appStoreService.create(TestUsers.admin, normAppDesc, "content")
            appStoreService.create(TestUsers.admin, normAppDesc2, "content2")

            tagService.createTags(listOf("tag1", "tag2"), normAppDesc.metadata.name, TestUsers.admin)

            val tags =
                searchService.searchByTags(TestUsers.admin, null, listOf("tag1"), NormalizedPaginationRequest(10, 0))
            assertEquals(1, tags.itemsInTotal)
            assertEquals(normAppDesc.metadata.name, tags.items.first().metadata.name)

            tagService.deleteTags(listOf("tag1"), normAppDesc.metadata.name, TestUsers.admin)

            val tagsAfterDelete =
                searchService.searchByTags(TestUsers.admin, null, listOf("tag1"), NormalizedPaginationRequest(10, 0))
            assertEquals(0, tagsAfterDelete.itemsInTotal)
        }
    }

    @Test
    fun `add tags and delete tags - duplicate tags `() {
        runBlocking {
            val micro = initializeMicro()
            val toolHibernateDAO = mockk<ToolHibernateDAO>(relaxed = true)
            val aclHibernateDao = mockk<AclHibernateDao>(relaxed = true)
            val publicDao = mockk<ApplicationPublicAsyncDAO>(relaxed = true)
            val appDAO = AppStoreAsyncDAO(toolHibernateDAO, aclHibernateDao, publicDao)
            val aclDao = AclHibernateDao()
            val tagDao = mockk<ApplicationTagsAsyncDAO>(relaxed = true)
            val elasticDAO = mockk<ElasticDAO>(relaxed = true)
            val authClient = mockk<AuthenticatedClient>(relaxed = true)
            val searchDao = mockk<ApplicationSearchAsyncDAO>(relaxed = true)
            val appStoreService = AppStoreService(
                db,
                authClient,
                appDAO,
                publicDao,
                toolHibernateDAO,
                aclDao,
                elasticDAO,
                micro.eventStreamService.createProducer(AppStoreStreams.AppDeletedStream)
            )

            val tagService = ApplicationTagsService(db, tagDao, elasticDAO)
            val searchService = ApplicationSearchService(db, searchDao, elasticDAO, appDAO, authClient)

            appStoreService.create(TestUsers.admin, normAppDesc, "content")
            appStoreService.create(TestUsers.admin, normAppDesc2, "content2")

            tagService.createTags(listOf("tag1", "tag2"), normAppDesc.metadata.name, TestUsers.admin)

            tagService.createTags(listOf("tag2", "tag3"), normAppDesc.metadata.name, TestUsers.admin)

            val tags1 =
                searchService.searchByTags(TestUsers.admin, null, listOf("tag2"), NormalizedPaginationRequest(10, 0))
            assertEquals(1, tags1.itemsInTotal)
            assertEquals(normAppDesc.metadata.name, tags1.items.first().metadata.name)

            tagService.deleteTags(listOf("tag2"), normAppDesc.metadata.name, TestUsers.admin)

            val tags2 =
                searchService.searchByTags(TestUsers.admin, null, listOf("tag2"), NormalizedPaginationRequest(10, 0))
            assertEquals(0, tags2.itemsInTotal)
        }
    }

    @Test
    fun `Add and search for app`() {
        runBlocking {
            val micro = initializeMicro()
            val toolHibernateDAO = mockk<ToolHibernateDAO>(relaxed = true)
            val aclHibernateDao = mockk<AclHibernateDao>(relaxed = true)
            val publicDao = mockk<ApplicationPublicAsyncDAO>(relaxed = true)
            val appDAO = AppStoreAsyncDAO(toolHibernateDAO, aclHibernateDao, publicDao)
            val aclDao = AclHibernateDao()
            val elasticDAO = mockk<ElasticDAO>(relaxed = true)
            val authClient = mockk<AuthenticatedClient>(relaxed = true)
            val searchDao = mockk<ApplicationSearchAsyncDAO>(relaxed = true)
            val appStoreService = AppStoreService(
                db,
                authClient,
                appDAO,
                publicDao,
                toolHibernateDAO,
                aclDao,
                elasticDAO,
                micro.eventStreamService.createProducer(AppStoreStreams.AppDeletedStream)
            )

            val searchService = ApplicationSearchService(db, searchDao, elasticDAO, appDAO, authClient)

            appStoreService.create(TestUsers.admin, normAppDesc, "content")
            appStoreService.create(
                TestUsers.admin,
                normAppDesc.withNameAndVersionAndTitle(
                    "application", "4.4", "anotherTitle"
                ),
                "content2"
            )

            val foundApps =
                searchService.searchApps(TestUsers.admin, null, "anotherTitle", NormalizedPaginationRequest(10, 0))

            assertEquals(1, foundApps.itemsInTotal)
            assertEquals("anotherTitle", foundApps.items.first().metadata.title)

            val foundByNameAndVersion =
                appStoreService.findByNameAndVersion(
                    TestUsers.admin,
                    null,
                    normAppDesc.metadata.name,
                    normAppDesc.metadata.version
                )

            assertEquals(normAppDesc.metadata.title, foundByNameAndVersion.metadata.title)

            val foundByName =
                appStoreService.findByName(TestUsers.admin, null, "application", NormalizedPaginationRequest(10, 0))

            assertEquals(1, foundByName.itemsInTotal)
            assertEquals("anotherTitle", foundByName.items.first().metadata.title)

            val allApps =
                appStoreService.listAll(TestUsers.admin, null, NormalizedPaginationRequest(10, 0))

            assertEquals(2, allApps.itemsInTotal)
        }
    }

    @Test
    fun `app set public and bulk test is public`() {
        runBlocking {
            val micro = initializeMicro()
            val toolHibernateDAO = mockk<ToolHibernateDAO>(relaxed = true)
            val aclHibernateDao = mockk<AclHibernateDao>(relaxed = true)
            val publicDao = mockk<ApplicationPublicAsyncDAO>(relaxed = true)
            val appDAO = AppStoreAsyncDAO(toolHibernateDAO, aclHibernateDao, publicDao)
            val aclDao = AclHibernateDao()
            val elasticDAO = mockk<ElasticDAO>(relaxed = true)
            val authClient = mockk<AuthenticatedClient>(relaxed = true)
            val appStoreService = AppStoreService(
                db,
                authClient,
                appDAO,
                publicDao,
                toolHibernateDAO,
                aclDao,
                elasticDAO,
                micro.eventStreamService.createProducer(AppStoreStreams.AppDeletedStream)
            )

            val publicService = ApplicationPublicService(db, publicDao)

            appStoreService.create(TestUsers.admin, normAppDesc, "content")
            appStoreService.create(
                TestUsers.admin,
                normAppDesc.withNameAndVersionAndTitle(
                    "application", "4.4", "anotherTitle"
                ),
                "content2"
            )

            assertEquals(
                mapOf(
                    NameAndVersion(normAppDesc.metadata.name, normAppDesc.metadata.version) to true,
                    NameAndVersion("application", "4.4") to true
                ),
                publicService.isPublic(
                    TestUsers.admin, listOf(
                        NameAndVersion(normAppDesc.metadata.name, normAppDesc.metadata.version),
                        NameAndVersion("application", "4.4")
                    )
                )
            )

            publicService.setPublic(TestUsers.admin, normAppDesc.metadata.name, normAppDesc.metadata.version, false)
            publicService.setPublic(TestUsers.admin, "application", "4.4", false)

            assertEquals(
                mapOf(
                    NameAndVersion(normAppDesc.metadata.name, normAppDesc.metadata.version) to false,
                    NameAndVersion("application", "4.4") to false
                ),
                publicService.isPublic(
                    TestUsers.admin, listOf(
                        NameAndVersion(normAppDesc.metadata.name, normAppDesc.metadata.version),
                        NameAndVersion("application", "4.4")
                    )
                )
            )
        }
    }

    @Test(expected = ApplicationException.NotAllowed::class)
    fun `test delete - not allowed`() {
        val appStoreService = initAppStoreWithMockedElasticAndTool()
        runBlocking {
            appStoreService.delete(TestUsers.admin, null, "name", "2.2")
        }
    }

    @Test(expected = ApplicationException.NotFound::class)
    fun `test delete - not found`() {
        val appStoreService = initAppStoreWithMockedElasticAndTool()
        runBlocking {
            appStoreService.create(TestUsers.admin, normAppDesc, "content")
            appStoreService.create(TestUsers.admin, normAppDescDiffVersion, "content")
            appStoreService.delete(TestUsers.admin, null, "name", "0.0.0")
        }
    }

    @Test
    fun `test delete`() {
        val appStoreService = initAppStoreWithMockedElasticAndTool()
        runBlocking {
            appStoreService.create(TestUsers.admin, normAppDesc, "content")
            appStoreService.create(TestUsers.admin, normAppDescDiffVersion, "content")
            appStoreService.delete(TestUsers.admin, null, "name", "2.2")
        }
    }

    @Test(expected = ApplicationException.NotAllowed::class)
    fun `test delete - not same user`() {
        val appStoreService = initAppStoreWithMockedElasticAndTool()
        runBlocking {
            appStoreService.create(TestUsers.user, normAppDesc, "content")
            appStoreService.delete(TestUsers.user2, null, "name", "2.2")
        }
    }
/*
    @Test
    fun `advanced search CC Test - no description`() {
        val appStoreService = initAppStoreWithMockedElasticAndTool()
        val searchResults = appStoreService.advancedSearch(
            TestUsers.user,
            normAppDesc.metadata.title,
            null,
            null,
            null,
            NormalizedPaginationRequest(25, 0)
        )
    }

    @Test
    fun `advanced search CC Test - with description`() {
        val appStoreService = initAppStoreWithMockedElasticAndTool()
        val searchResults = appStoreService.advancedSearch(
            TestUsers.user,
            normAppDesc.metadata.title,
            null,
            null,
            "description",
            NormalizedPaginationRequest(25, 0)
        )
    }


    @Ignore //Requires running elasticsearch
    @Test
    fun `advanced search` () {
        val micro = initializeMicro()
        micro.install(HibernateFeature)
        micro.install(ElasticFeature)
        val toolHibernateDAO = mockk<ToolHibernateDAO>(relaxed = true)
        val appDAO = ApplicationHibernateDAO(toolHibernateDAO)
        val elasticDAO = ElasticDAO(micro.elasticHighLevelClient)

        val appStoreService = AppStoreService(micro.hibernateDatabase, appDAO, toolHibernateDAO, elasticDAO)

        appStoreService.create(TestUsers.admin, normAppDesc, "content")
        appStoreService.create(TestUsers.admin, normAppDesc2, "content2")

        appStoreService.createTags(listOf("tag1", "tag2", "tag3"), normAppDesc.metadata.name, TestUsers.admin)
        appStoreService.createTags(listOf("tag3", "tag4"), normAppDesc2.metadata.name, TestUsers.admin)

        run {
            val searchResults = appStoreService.advancedSearch(
                TestUsers.user,
                normAppDesc.metadata.title,
                null,
                null,
                null,
                NormalizedPaginationRequest(25, 0)
            )

            assertEquals(1, searchResults.itemsInTotal)
        }

        run {
            val searchResults = appStoreService.advancedSearch(
                TestUsers.user,
                null,
                "1",
                null,
                null,
                NormalizedPaginationRequest(25, 0)
            )

            assertEquals(1, searchResults.itemsInTotal)
        }

        run {
            val searchResults = appStoreService.advancedSearch(
                TestUsers.user,
                null,
                null,
                listOf("tag3"),
                null,
                NormalizedPaginationRequest(25, 0)
            )

            assertEquals(2, searchResults.itemsInTotal)
        }

        run {
            val searchResults = appStoreService.advancedSearch(
                TestUsers.user,
                null,
                null,
                null,
                normAppDesc2.metadata.description.split(" ").first(),
                NormalizedPaginationRequest(25, 0)
            )

            assertEquals(2, searchResults.itemsInTotal)
        }

        run {
            val searchResults = appStoreService.advancedSearch(
                TestUsers.user,
                normAppDesc.metadata.title,
                normAppDesc.metadata.version,
                listOf("tag1", "tag2"),
                null,
                NormalizedPaginationRequest(25, 0)
            )

            assertEquals(1, searchResults.itemsInTotal)
        }

        run {
            val searchResults = appStoreService.advancedSearch(
                TestUsers.user,
                normAppDesc2.metadata.title,
                normAppDesc2.metadata.version,
                listOf("tag1", "tag2"),
                null,
                NormalizedPaginationRequest(25, 0)
            )

            assertEquals(0, searchResults.itemsInTotal)
        }

        run {
            val searchResults = appStoreService.advancedSearch(
                TestUsers.user,
                normAppDesc.metadata.title,
                normAppDesc.metadata.version,
                listOf("tag1", "tag2"),
                normAppDesc.metadata.description.split(" ").first(),
                NormalizedPaginationRequest(25, 0)
            )

            assertEquals(1, searchResults.itemsInTotal)
        }

    }
*/
}
