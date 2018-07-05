package dk.sdu.cloud.storage.http.files

import dk.sdu.cloud.storage.util.withAuthMock
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.withTestApplication
import org.junit.Test
import kotlin.test.assertEquals

class DeletionTests {
    @Test
    fun `delete a file`() {
        withAuthMock {
            withTestApplication(
                moduleFunction = { configureServerWithFileController() },

                test = {
                    val path = "/home/user1/folder/a"
                    val response = stat(path)
                    assertEquals(HttpStatusCode.OK, response.status())

                    val response2 = delete(path)
                    assertEquals(HttpStatusCode.NoContent, response2.status())

                    val response3 = stat(path)
                    assertEquals(HttpStatusCode.NotFound, response3.status())
                }
            )
        }
    }

    @Test
    fun `delete a folder`() {
        withAuthMock {
            withTestApplication(
                moduleFunction = { configureServerWithFileController() },

                test = {
                    val path = "/home/user1/folder"
                    val response = stat(path)
                    assertEquals(HttpStatusCode.OK, response.status())

                    val response2 = delete(path)
                    assertEquals(HttpStatusCode.NoContent, response2.status())

                    val response3 = stat(path)
                    assertEquals(HttpStatusCode.NotFound, response3.status())
                }
            )
        }
    }
}