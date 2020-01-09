package dk.sdu.cloud.app.store.services

import dk.sdu.cloud.app.store.api.NameAndVersion
import dk.sdu.cloud.app.store.services.acl.AclHibernateDao
import dk.sdu.cloud.app.store.util.*
import dk.sdu.cloud.auth.api.authenticator
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.OutgoingHttpCall
import dk.sdu.cloud.micro.ElasticFeature
import dk.sdu.cloud.micro.HibernateFeature
import dk.sdu.cloud.micro.elasticHighLevelClient
import dk.sdu.cloud.micro.hibernateDatabase
import dk.sdu.cloud.micro.install
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.PaginationRequest
import dk.sdu.cloud.service.db.HibernateSession
import dk.sdu.cloud.service.test.TestUsers
import dk.sdu.cloud.service.test.initializeMicro
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Ignore
import org.junit.Test
import kotlin.test.assertEquals

class AppStoreTest {

    //Requires running Elastic with empty application index
    @Ignore
    @Test
    fun realTestOfCreateAndDeleteTag() {
        val micro = initializeMicro()
        micro.install(HibernateFeature)
        micro.install(ElasticFeature)

        val toolHibernateDAO = mockk<ToolHibernateDAO>(relaxed = true)
        val aclHibernateDao = mockk<AclHibernateDao>(relaxed = true)
        val appDao = ApplicationHibernateDAO(toolHibernateDAO, aclHibernateDao)
        val elasticDAO = ElasticDAO(micro.elasticHighLevelClient)
        val aclDao = AclHibernateDao()
        val authClient = mockk<AuthenticatedClient>(relaxed = true)
        val applicationService =
            AppStoreService(
                micro.hibernateDatabase,
                authClient,
                appDao, toolHibernateDAO, aclDao, elasticDAO
            )

        runBlocking {
            applicationService.create(TestUsers.admin, normAppDesc.withNameAndVersion("ansys", "1.2.1"), "content")
            applicationService.create(TestUsers.admin, normAppDesc.withNameAndVersion("ansys", "1.2.2"), "content")
            applicationService.create(TestUsers.admin, normAppDesc.withNameAndVersion("ansys", "1.2.3"), "content")
        }

        Thread.sleep(1000)

        runBlocking {
            val advancedSearchResultForTest1 = applicationService.advancedSearch(
                TestUsers.admin,
                "",
                listOf("test1"),
                true,
                NormalizedPaginationRequest(10, 0)
            )

            val advancedSearchResultForTest2 = applicationService.advancedSearch(
                TestUsers.admin,
                "",
                listOf("test2"),
                true,
                NormalizedPaginationRequest(10, 0)
            )

            val advancedSearchResultForAll = applicationService.advancedSearch(
                TestUsers.admin,
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
            applicationService.createTags(listOf("test1", "test2"), "ansys", TestUsers.admin)
        }

        Thread.sleep(1000)

        runBlocking {
            val advancedSearchResultForTest1 = applicationService.advancedSearch(
                TestUsers.admin,
                "",
                listOf("test1"),
                true,
                NormalizedPaginationRequest(10, 0)
            )

            val advancedSearchResultForTest2 = applicationService.advancedSearch(
                TestUsers.admin,
                "",
                listOf("test2"),
                true,
                NormalizedPaginationRequest(10, 0)
            )

            val advancedSearchResultForAll = applicationService.advancedSearch(
                TestUsers.admin,
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
            applicationService.deleteTags(listOf("test2"), "ansys", TestUsers.admin)
        }

        Thread.sleep(1000)

        runBlocking {
            val advancedSearchResultForTest1 = applicationService.advancedSearch(
                TestUsers.admin,
                "",
                listOf("test1"),
                true,
                NormalizedPaginationRequest(10, 0)
            )

            val advancedSearchResultForTest2 = applicationService.advancedSearch(
                TestUsers.admin,
                "",
                listOf("test2"),
                true,
                NormalizedPaginationRequest(10, 0)
            )

            val advancedSearchResultForAll = applicationService.advancedSearch(
                TestUsers.admin,
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

    private fun initAppStoreWithMockedElasticAndTool(): AppStoreService<HibernateSession> {
        val micro = initializeMicro()
        micro.install(HibernateFeature)
        val toolHibernateDAO = mockk<ToolHibernateDAO>(relaxed = true)
        val aclHibernateDao = mockk<AclHibernateDao>(relaxed = true)
        val appDAO = ApplicationHibernateDAO(toolHibernateDAO, aclHibernateDao)
        val aclDao = AclHibernateDao()
        val elasticDAO = mockk<ElasticDAO>(relaxed = true)
        val authClient = mockk<AuthenticatedClient>(relaxed = true)

        return AppStoreService(
            micro.hibernateDatabase,
            authClient,
            appDAO, toolHibernateDAO, aclDao, elasticDAO
        )
    }

    @Test
    fun `toggle favorites and retrieve`() {
        val appStoreService = initAppStoreWithMockedElasticAndTool()
        runBlocking {
            appStoreService.create(TestUsers.admin, normAppDesc, "content")
            appStoreService.toggleFavorite(
                TestUsers.user,
                normAppDesc.metadata.name,
                normAppDesc.metadata.version
            )

            val retrievedFav = appStoreService.retrieveFavorites(TestUsers.user, PaginationRequest())

            assertEquals(1, retrievedFav.itemsInTotal)
            assertEquals(normAppDesc.metadata.title, retrievedFav.items.first().metadata.title)

            appStoreService.toggleFavorite(
                TestUsers.user,
                normAppDesc.metadata.name,
                normAppDesc.metadata.version
            )

            val retrievedFavAfterRemoved = appStoreService.retrieveFavorites(TestUsers.user, PaginationRequest())

            assertEquals(0, retrievedFavAfterRemoved.itemsInTotal)
        }
    }

    @Test
    fun `add remove search tags`() {
        runBlocking {
            val appStoreService = initAppStoreWithMockedElasticAndTool()
            appStoreService.create(TestUsers.admin, normAppDesc, "content")
            appStoreService.create(TestUsers.admin, normAppDesc2, "content2")

            appStoreService.createTags(listOf("tag1", "tag2"), normAppDesc.metadata.name, TestUsers.admin)

            val tags =
                appStoreService.searchTags(TestUsers.admin, listOf("tag1"), NormalizedPaginationRequest(10, 0))
            assertEquals(1, tags.itemsInTotal)
            assertEquals(normAppDesc.metadata.name, tags.items.first().metadata.name)

            appStoreService.deleteTags(listOf("tag1"), normAppDesc.metadata.name, TestUsers.admin)

            val tagsAfterDelete =
                appStoreService.searchTags(TestUsers.admin, listOf("tag1"), NormalizedPaginationRequest(10, 0))
            assertEquals(0, tagsAfterDelete.itemsInTotal)
        }
    }

    @Test
    fun `add tags and delete tags - duplicate tags `() {
        runBlocking {
            val appStoreService = initAppStoreWithMockedElasticAndTool()
            appStoreService.create(TestUsers.admin, normAppDesc, "content")
            appStoreService.create(TestUsers.admin, normAppDesc2, "content2")

            appStoreService.createTags(listOf("tag1", "tag2"), normAppDesc.metadata.name, TestUsers.admin)

            appStoreService.createTags(listOf("tag2", "tag3"), normAppDesc.metadata.name, TestUsers.admin)

            val tags1 =
                appStoreService.searchTags(TestUsers.admin, listOf("tag2"), NormalizedPaginationRequest(10, 0))
            assertEquals(1, tags1.itemsInTotal)
            assertEquals(normAppDesc.metadata.name, tags1.items.first().metadata.name)

            appStoreService.deleteTags(listOf("tag2"), normAppDesc.metadata.name, TestUsers.admin)

            val tags2 =
                appStoreService.searchTags(TestUsers.admin, listOf("tag2"), NormalizedPaginationRequest(10, 0))
            assertEquals(0, tags2.itemsInTotal)
        }
    }

    @Test
    fun `Add and search for app`() {
        runBlocking {
            val appStoreService = initAppStoreWithMockedElasticAndTool()
            appStoreService.create(TestUsers.admin, normAppDesc, "content")
            appStoreService.create(
                TestUsers.admin,
                normAppDesc.withNameAndVersionAndTitle(
                    "application", "4.4", "anotherTitle"
                ),
                "content2"
            )

            val foundApps =
                appStoreService.searchApps(TestUsers.admin, "anotherTitle", NormalizedPaginationRequest(10, 0))

            assertEquals(1, foundApps.itemsInTotal)
            assertEquals("anotherTitle", foundApps.items.first().metadata.title)

            val foundByNameAndVersion =
                appStoreService.findByNameAndVersion(
                    TestUsers.admin,
                    normAppDesc.metadata.name,
                    normAppDesc.metadata.version
                )

            assertEquals(normAppDesc.metadata.title, foundByNameAndVersion.metadata.title)

            val foundByName =
                appStoreService.findByName(TestUsers.admin, "application", NormalizedPaginationRequest(10, 0))

            assertEquals(1, foundByName.itemsInTotal)
            assertEquals("anotherTitle", foundByName.items.first().metadata.title)

            val allApps =
                appStoreService.listAll(TestUsers.admin, NormalizedPaginationRequest(10, 0))

            assertEquals(2, allApps.itemsInTotal)
        }
    }

    @Test
    fun `app set public and bulk test is public`() {
        runBlocking {
            val appStoreService = initAppStoreWithMockedElasticAndTool()
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
                appStoreService.isPublic(
                    TestUsers.admin, listOf(
                        NameAndVersion(normAppDesc.metadata.name, normAppDesc.metadata.version),
                        NameAndVersion("application", "4.4")
                    )
                )
            )

            appStoreService.setPublic(TestUsers.admin, normAppDesc.metadata.name, normAppDesc.metadata.version, false)
            appStoreService.setPublic(TestUsers.admin, "application", "4.4", false)

            assertEquals(
                mapOf(
                    NameAndVersion(normAppDesc.metadata.name, normAppDesc.metadata.version) to false,
                    NameAndVersion("application", "4.4") to false
                ),
                appStoreService.isPublic(
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
            appStoreService.delete(TestUsers.admin, "name", "2.2")
        }
    }

    @Test(expected = ApplicationException.NotFound::class)
    fun `test delete - not found`() {
        val appStoreService = initAppStoreWithMockedElasticAndTool()
        runBlocking {
            appStoreService.create(TestUsers.admin, normAppDesc, "content")
            appStoreService.create(TestUsers.admin, normAppDescDiffVersion, "content")
            appStoreService.delete(TestUsers.admin, "name", "0.0.0")
        }
    }

    @Test
    fun `test delete`() {
        val appStoreService = initAppStoreWithMockedElasticAndTool()
        runBlocking {
            appStoreService.create(TestUsers.admin, normAppDesc, "content")
            appStoreService.create(TestUsers.admin, normAppDescDiffVersion, "content")
            appStoreService.delete(TestUsers.admin, "name", "2.2")
        }
    }

    @Test(expected = ApplicationException.NotAllowed::class)
    fun `test delete - not same user`() {
        val appStoreService = initAppStoreWithMockedElasticAndTool()
        runBlocking {
            appStoreService.create(TestUsers.user, normAppDesc, "content")
            appStoreService.delete(TestUsers.user2, "name", "2.2")
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
