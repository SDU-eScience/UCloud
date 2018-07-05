package dk.sdu.cloud.storage.http.files

import dk.sdu.cloud.storage.api.WriteConflictPolicy
import dk.sdu.cloud.storage.util.withAuthMock
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.withTestApplication
import org.junit.Test
import kotlin.test.assertEquals

class CopyingTests {
    @Test
    fun `test copying of file`() {
        withAuthMock {
            withTestApplication(
                moduleFunction = { configureServerWithFileController() },

                test = {
                    val path = "/home/user1/folder/a"
                    val newPath = "/home/user1/a"

                    val response = stat(path)
                    assertEquals(HttpStatusCode.OK, response.status())

                    val response1 = stat(newPath)
                    assertEquals(HttpStatusCode.NotFound, response1.status())

                    val response2 = copy(path, newPath, WriteConflictPolicy.REJECT)
                    assertEquals(HttpStatusCode.NoContent, response2.status())

                    val response3 = stat(path)
                    assertEquals(HttpStatusCode.OK, response3.status())

                    val response4 = stat(newPath)
                    assertEquals(HttpStatusCode.OK, response4.status())
                }
            )
        }
    }

    @Test
    fun `attempt to overwrite file via copy - OVERWRITE`() {
        withAuthMock {
            withTestApplication(
                moduleFunction = { configureServerWithFileController() },

                test = {
                    val path = "/home/user1/folder/a"
                    val newPath = "/home/user1/folder/b"

                    val response = stat(path)
                    assertEquals(HttpStatusCode.OK, response.status())

                    val response1 = stat(newPath)
                    assertEquals(HttpStatusCode.OK, response1.status())

                    val response2 = copy(path, newPath, WriteConflictPolicy.OVERWRITE)
                    assertEquals(HttpStatusCode.NoContent, response2.status())
                }
            )
        }
    }

    @Test
    fun `attempt to overwrite file via copy - RENAME`() {
        withAuthMock {
            withTestApplication(
                moduleFunction = { configureServerWithFileController() },

                test = {
                    val path = "/home/user1/folder/a"
                    val newPath = "/home/user1/folder/b"

                    val response = stat(path)
                    assertEquals(HttpStatusCode.OK, response.status())

                    val response1 = stat(newPath)
                    assertEquals(HttpStatusCode.OK, response1.status())

                    val response2 = copy(path, newPath, WriteConflictPolicy.RENAME)
                    assertEquals(HttpStatusCode.NoContent, response2.status())

                    val response3 = stat("$newPath(1)")
                    assertEquals(HttpStatusCode.OK, response3.status())
                }
            )
        }
    }

    @Test
    fun `attempt to overwrite file via copy - REJECT`() {
        withAuthMock {
            withTestApplication(
                moduleFunction = { configureServerWithFileController() },

                test = {
                    val path = "/home/user1/folder/a"
                    val newPath = "/home/user1/folder/b"

                    val response = stat(path)
                    assertEquals(HttpStatusCode.OK, response.status())

                    val response1 = stat(newPath)
                    assertEquals(HttpStatusCode.OK, response1.status())

                    val response2 = copy(path, newPath, WriteConflictPolicy.REJECT)
                    assertEquals(HttpStatusCode.Conflict, response2.status())
                }
            )
        }
    }

    @Test
    fun `copy a folder`() {
        withAuthMock {
            withTestApplication(
                moduleFunction = { configureServerWithFileController() },

                test = {
                    val path = "/home/user1/folder"
                    val newPath = "/home/user1/new-folder"

                    val response = stat(path)
                    assertEquals(HttpStatusCode.OK, response.status())

                    val response2 = copy(path, newPath, WriteConflictPolicy.REJECT)
                    assertEquals(HttpStatusCode.NoContent, response2.status())

                    val response3 = listDir(newPath)
                    assertEquals(HttpStatusCode.OK, response3.status())
                }
            )
        }
    }

    @Test
    fun `copy file which does not exist`() {
        withAuthMock {
            withTestApplication(
                moduleFunction = { configureServerWithFileController() },

                test = {
                    val path = "/home/user1/folder/notHere"
                    val newPath = "/home/user1/notHere"

                    val response = stat(path)
                    assertEquals(HttpStatusCode.NotFound, response.status())

                    val response2 = copy(path, newPath, WriteConflictPolicy.OVERWRITE)
                    assertEquals(HttpStatusCode.NotFound, response2.status())
                }
            )
        }
    }
}