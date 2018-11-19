package dk.sdu.cloud.file.http.files

import dk.sdu.cloud.storage.util.withAuthMock
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.withTestApplication
import org.junit.Test
import kotlin.test.assertEquals

class MoveTesting {

    @Test
    fun `moving a file to a new directory`() {
        withAuthMock {
            withTestApplication(
                moduleFunction = { configureServerWithFileController() },

                test = {
                    val fileToMove = "/home/user1/folder/a"
                    val newPath = "/home/user1/a"

                    val response = stat(fileToMove)
                    assertEquals(HttpStatusCode.OK, response.status())

                    val response2 = move(fileToMove, newPath)
                    assertEquals(HttpStatusCode.OK, response2.status())

                    val response3 = stat(fileToMove)
                    assertEquals(HttpStatusCode.NotFound, response3.status())

                    val response4 = stat(newPath)
                    assertEquals(HttpStatusCode.OK, response4.status())
                }
            )
        }
    }

    @Test
    fun `moving a file to its current position`() {
        withAuthMock {
            withTestApplication(
                moduleFunction = { configureServerWithFileController() },

                test = {
                    val fileToMove = "/home/user1/folder/a"
                    val newLocation = "/home/user1/folder/a"

                    val response = stat(fileToMove)
                    assertEquals(HttpStatusCode.OK, response.status())

                    val response2 = move(fileToMove, newLocation)
                    assertEquals(HttpStatusCode.OK, response2.status())

                }
            )
        }
    }

    @Test
    fun `moving a file to a directory that does not exist`() {
        withAuthMock {
            withTestApplication(
                moduleFunction = { configureServerWithFileController() },

                test = {
                    val fileToMove = "/home/user1/folder/a"
                    val newLocation = "/home/user1/folder/notThere/a"

                    val response = stat(fileToMove)
                    assertEquals(HttpStatusCode.OK, response.status())

                    val response2 = move(fileToMove, newLocation)
                    assertEquals(HttpStatusCode.NotFound, response2.status())
                }
            )
        }
    }


    @Test
    fun `move file then rename`() {
        withAuthMock {
            withTestApplication(
                moduleFunction = { configureServerWithFileController() },

                test = {
                    val fileToMove = "/home/user1/folder/a"
                    val newPath = "/home/user1/folder/newName"

                    val response = stat(fileToMove)
                    assertEquals(HttpStatusCode.OK, response.status())

                    val response2 = move(fileToMove, newPath)
                    assertEquals(HttpStatusCode.OK, response2.status())

                    val response3 = stat(fileToMove)
                    assertEquals(HttpStatusCode.NotFound, response3.status())

                    val response4 = stat(newPath)
                    assertEquals(HttpStatusCode.OK, response4.status())

                }
            )
        }
    }

    @Test
    fun `move file that does not exist`() {
        withAuthMock {
            withTestApplication(
                moduleFunction = { configureServerWithFileController() },

                test = {
                    val fileToMove = "/home/user1/folder/k"
                    val newLocation = "/home/user1/another-one/k"

                    val response = stat(fileToMove)
                    assertEquals(HttpStatusCode.NotFound, response.status())

                    val response2 = move(fileToMove, newLocation)
                    assertEquals(HttpStatusCode.NotFound, response2.status())
                }
            )
        }
    }
}
