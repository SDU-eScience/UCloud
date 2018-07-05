package dk.sdu.cloud.storage.http.files

import dk.sdu.cloud.storage.util.withAuthMock
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.withTestApplication
import org.junit.Test
import kotlin.test.assertEquals

class FavoritesTest {
    @Test
    fun `mark file as favorite and remove`() {
        withAuthMock {
            withTestApplication(
                moduleFunction = { configureServerWithFileController() },

                test = {
                    val favLink = "/home/user1/Favorites/a"
                    val favPath = "/home/user1/folder/a"

                    val response = stat(favLink)
                    assertEquals(HttpStatusCode.NotFound, response.status())

                    val response1 = createFavorite(favPath)
                    assertEquals(HttpStatusCode.NoContent, response1.status())

                    val response2 = stat(favLink)
                    assertEquals(HttpStatusCode.OK, response2.status())

                    val response3 = deleteFavorite(favPath)
                    assertEquals(HttpStatusCode.NoContent, response3.status())

                    val response4 = stat(favLink)
                    assertEquals(HttpStatusCode.NotFound, response4.status())
                }
            )
        }
    }

    @Test
    fun `mark folder as favorite and remove`() {
        withAuthMock {
            withTestApplication(
                moduleFunction = { configureServerWithFileController() },

                test = {
                    val favLink = "/home/user1/Favorites/folder"
                    val favPath = "/home/user1/folder"
                    val response = stat(favLink)
                    assertEquals(HttpStatusCode.NotFound, response.status())

                    val response1 = createFavorite(favPath)
                    assertEquals(HttpStatusCode.NoContent, response1.status())

                    val response2 = stat(favLink)
                    assertEquals(HttpStatusCode.OK, response2.status())

                    val response3 = deleteFavorite(favPath)
                    assertEquals(HttpStatusCode.NoContent, response3.status())

                    val response4 = stat(favLink)
                    assertEquals(HttpStatusCode.NotFound, response4.status())
                }
            )
        }
    }
}
