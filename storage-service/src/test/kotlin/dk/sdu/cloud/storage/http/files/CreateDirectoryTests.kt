package dk.sdu.cloud.storage.http.files

import dk.sdu.cloud.storage.util.withAuthMock
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.withTestApplication
import org.junit.Test
import kotlin.test.assertEquals

class CreateDirectoryTests {
    @Test
    fun `make directory valid`() {
        withAuthMock {
            withTestApplication(
                moduleFunction = { configureServerWithFileController() },

                test = {
                    val path = "/home/user1/newDir"

                    val response = makeDir(path)
                    assertEquals(HttpStatusCode.NoContent, response.status())

                    val response2 = stat(path)
                    assertEquals(HttpStatusCode.OK, response2.status())
                }
            )
        }
    }

    @Test
    fun `make folder that already exists`() {
        withAuthMock {
            withTestApplication(
                moduleFunction = { configureServerWithFileController() },

                test = {
                    val response = makeDir("/home/user1/folder")
                    assertEquals(HttpStatusCode.Conflict, response.status())

                }
            )
        }
    }

    @Test
    fun `make directory with missing permissions`() {
        withAuthMock {
            withTestApplication(
                moduleFunction = { configureServerWithFileController() },

                test = {
                    val response = makeDir("/home/newdir")
                    assertEquals(HttpStatusCode.Forbidden, response.status())

                }
            )
        }
    }

    @Test
    fun `test if directories are created recursively`() {
        withAuthMock {
            withTestApplication(
                moduleFunction = { configureServerWithFileController() },

                test = {
                    val path = "/home/user1/folder/newDir/newnew"

                    val response = makeDir(path)
                    assertEquals(HttpStatusCode.NotFound, response.status())

                    val response2 = stat(path)
                    assertEquals(HttpStatusCode.NotFound, response2.status())

                    val response3 = stat("/home/user1/folder/newDir")
                    assertEquals(HttpStatusCode.NotFound, response3.status())
                }
            )
        }
    }
}