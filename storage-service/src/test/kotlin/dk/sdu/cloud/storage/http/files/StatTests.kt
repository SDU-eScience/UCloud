package dk.sdu.cloud.storage.http.files

import dk.sdu.cloud.storage.util.withAuthMock
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.withTestApplication
import org.junit.Test
import kotlin.test.assertEquals

class StatTests {
    @Test
    fun `stat a normal file`() {
        withAuthMock {
            withTestApplication(
                moduleFunction = { configureServerWithFileController() },

                test = {
                    val response = stat("/home/user1/folder/a")
                    assertEquals(HttpStatusCode.OK, response.status())

                }
            )
        }
    }

    @Test
    fun `stat a non-existing file`() {
        withAuthMock {
            withTestApplication(
                moduleFunction = { configureServerWithFileController() },

                test = {
                    val response = stat("/home/yep/folder/a")
                    assertEquals(HttpStatusCode.NotFound, response.status())

                }
            )
        }
    }
}