package dk.sdu.cloud.news.services

import dk.sdu.cloud.news.api.NewsServiceDescription
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.db.async.AsyncDBSessionFactory
import dk.sdu.cloud.service.db.async.sendPreparedStatement
import dk.sdu.cloud.service.db.async.withSession
import dk.sdu.cloud.service.test.TestDB
import dk.sdu.cloud.service.test.TestUsers
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import kotlinx.coroutines.runBlocking
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NewsServiceTest {
    companion object {
        lateinit var db: AsyncDBSessionFactory
        lateinit var embDB: EmbeddedPostgres

        @BeforeClass
        @JvmStatic
        fun setup() {
            val (db, embDB) = TestDB.from(NewsServiceDescription)
            this.db = db
            this.embDB = embDB
        }

        @AfterClass
        @JvmStatic
        fun close() {
            runBlocking {
                db.close()
            }
            embDB.close()
        }
    }

    fun truncateNews() {
        runBlocking {
            db.withSession {
                it.sendPreparedStatement(
                    {},
                    """
                        TRUNCATE news
                    """
                )
            }
        }
    }

    @BeforeTest
    fun before() {
        truncateNews()
    }

    @AfterTest
    fun after() {
        truncateNews()
    }

    @Test
    fun `create, find, toggle, list test`() {
        val newsService = NewsService()
        val now = System.currentTimeMillis()
        runBlocking {
            db.withSession { session ->
                newsService.createNewsPost(
                    db,
                    TestUsers.user.username,
                    "title of post",
                    "hello everybody",
                    "Hi I just want to welcome you all",
                    now,
                    now+10000,
                    "Welcomes"
                )

                newsService.createNewsPost(
                    db,
                    TestUsers.user.username,
                    "Another Post",
                    "Warning",
                    "Hi I just want to warn you all",
                    now,
                    now+10000,
                    "Warnings"
                )
            }

            val news = newsService.listNewsPosts(
                db,
                NormalizedPaginationRequest(10,0),
                null,
                withHidden = false,
                userIsAdmin = true
            )

            assertEquals(2, news.itemsInTotal)

            val cat = newsService.listCategories(db)
            assertEquals(2, cat.size)
            assertTrue(cat.contains("Warnings"))
            assertTrue(cat.contains("Welcomes"))

            val post = newsService.getPostById(db, 1)
            assertEquals("hello everybody", post.subtitle)

            newsService.togglePostHidden(db, 1)

            val newsAfterToggle = newsService.listNewsPosts(
                db,
                NormalizedPaginationRequest(10,0),
                null,
                withHidden = false,
                userIsAdmin = true
            )

            assertEquals(1, newsAfterToggle.itemsInTotal)
            assertEquals("Warnings", newsAfterToggle.items.first().category)
        }
    }
}
