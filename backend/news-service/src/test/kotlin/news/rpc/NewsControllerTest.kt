package dk.sdu.cloud.news.rpc

import com.fasterxml.jackson.module.kotlin.readValue
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.news.api.GetPostByIdResponse
import dk.sdu.cloud.news.api.ListCategoriesResponse
import dk.sdu.cloud.news.api.ListDownTimesResponse
import dk.sdu.cloud.news.api.ListPostsResponse
import dk.sdu.cloud.news.api.NewPostRequest
import dk.sdu.cloud.news.api.NewsServiceDescription
import dk.sdu.cloud.news.api.TogglePostHiddenRequest
import dk.sdu.cloud.news.services.NewsService
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.db.async.AsyncDBSessionFactory
import dk.sdu.cloud.service.test.KtorApplicationTestSetupContext
import dk.sdu.cloud.service.test.TestDB
import dk.sdu.cloud.service.test.TestUsers
import dk.sdu.cloud.service.test.assertFailure
import dk.sdu.cloud.service.test.assertStatus
import dk.sdu.cloud.service.test.assertSuccess
import dk.sdu.cloud.service.test.sendJson
import dk.sdu.cloud.service.test.sendRequest
import dk.sdu.cloud.service.test.withKtorTest
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import kotlinx.coroutines.runBlocking
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NewsControllerTest {
    companion object {
        lateinit var db: AsyncDBSessionFactory
        lateinit var embDb: EmbeddedPostgres

        @BeforeClass
        @JvmStatic
        fun setup() {
            val (db, embDb) = TestDB.from(NewsServiceDescription)
            this.db = db
            this.embDb = embDb
        }

        @AfterClass
        @JvmStatic
        fun close() {
            runBlocking {
                db.close()
            }
            embDb.close()
        }
    }

    private val setup: KtorApplicationTestSetupContext.() -> List<Controller> = {
        val service = NewsService()
        listOf(NewsController(db, service))
    }

    private val now = System.currentTimeMillis()

    @Test
    fun `only admins can create test`() {
        withKtorTest(
            setup,
            test = {
                val failingResponse = sendJson(
                    method = HttpMethod.Put,
                    path = "/api/news/post",
                    user = TestUsers.user,
                    request = NewPostRequest(
                        "title of post",
                        "hello everybody",
                        "Hi I just want to welcome you all",
                        now,
                        "Welcomes",
                        now + 10000
                    )
                )

                failingResponse.assertFailure()
                failingResponse.assertStatus(HttpStatusCode.Unauthorized)
            }
        )
    }

    @Test
    fun `create test`() {
        withKtorTest(
            setup,
            test = {
                sendJson(
                    method = HttpMethod.Put,
                    path = "/api/news/post",
                    user = TestUsers.admin,
                    request = NewPostRequest(
                        "title of post",
                        "hello everybody",
                        "Hi I just want to welcome you all",
                        now,
                        "Welcomes",
                        now + 10000
                    )
                ).assertSuccess()

                sendJson(
                    method = HttpMethod.Put,
                    path = "/api/news/post",
                    user = TestUsers.admin,
                    request = NewPostRequest(
                        "Another Post",
                        "Warning",
                        "Hi I just want to warn you all",
                        now,
                        "Warnings",
                        now+10000
                    )
                ).assertSuccess()

                val listResponse = sendRequest(
                    method = HttpMethod.Get,
                    path = "api/news/list",
                    user = TestUsers.admin,
                    params = mapOf(
                        "withHidden" to false,
                        "itemsPerPage" to 10,
                        "page" to 0
                    )
                )
                listResponse.assertSuccess()

                val list = defaultMapper.readValue<ListPostsResponse>(listResponse.response.content!!)
                assertEquals(2, list.itemsInTotal)

                val catResponse = sendRequest(
                    method = HttpMethod.Get,
                    path = "api/news/listCategories",
                    user = TestUsers.admin
                )

                catResponse.assertSuccess()
                val categories = defaultMapper.readValue<ListCategoriesResponse>(catResponse.response.content!!)
                assertEquals(2, categories.size)
                assertTrue(categories.contains("Warnings"))
                assertTrue(categories.contains("Welcomes"))

                val getResponse = sendRequest(
                    method = HttpMethod.Get,
                    path = "api/news/byId",
                    user = TestUsers.admin,
                    params = mapOf(
                        "id" to 1
                    )
                )
                getResponse.assertSuccess()

                val foundPost = defaultMapper.readValue<GetPostByIdResponse>(getResponse.response.content!!)
                assertEquals("hello everybody", foundPost.subtitle)

                sendJson(
                    method = HttpMethod.Post,
                    path = "api/news/toggleHidden",
                    user = TestUsers.admin,
                    request = TogglePostHiddenRequest(
                        1
                    )
                ).assertSuccess()

                val listResponseAfterToggle = sendRequest(
                    method = HttpMethod.Get,
                    path = "api/news/list",
                    user = TestUsers.admin,
                    params = mapOf(
                        "withHidden" to false,
                        "itemsPerPage" to 10,
                        "page" to 0
                    )
                )
                listResponseAfterToggle.assertSuccess()

                val listAfterToggle = defaultMapper.readValue<ListPostsResponse>(
                    listResponseAfterToggle.response.content!!
                )

                assertEquals(1, listAfterToggle.itemsInTotal)
                assertEquals("Warnings", listAfterToggle.items.first().category)

                val downtimeResponse = sendRequest(
                    method = HttpMethod.Get,
                    path = "api/news/listDowntimes",
                    user = TestUsers.admin
                )
                downtimeResponse.assertSuccess()
                val downtime = defaultMapper.readValue<ListDownTimesResponse>(downtimeResponse.response.content!!)
                assertEquals(0, downtime.itemsInTotal)

                sendJson(
                    method = HttpMethod.Put,
                    path = "/api/news/post",
                    user = TestUsers.admin,
                    request = NewPostRequest(
                        "Another Post",
                        "Warning",
                        "Hi I just want to warn you all",
                        now,
                        "downtime",
                        now+10000
                    )
                ).assertSuccess()

                val downtimeResponseAfterInsert = sendRequest(
                    method = HttpMethod.Get,
                    path = "api/news/listDowntimes",
                    user = TestUsers.admin
                )
                downtimeResponseAfterInsert.assertSuccess()
                val downtimeAfterInsert = defaultMapper.readValue<ListDownTimesResponse>(downtimeResponseAfterInsert.response.content!!)
                assertEquals(1, downtimeAfterInsert.itemsInTotal)

            }
        )
    }
}
