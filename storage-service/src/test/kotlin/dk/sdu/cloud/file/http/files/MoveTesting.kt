package dk.sdu.cloud.file.http.files

import dk.sdu.cloud.service.test.withKtorTest
import io.ktor.http.HttpStatusCode
import org.junit.Test
import kotlin.test.assertEquals

class MoveTesting {

    @Test
    fun `moving a file to a new directory`() {
        withKtorTest(
            setup = { configureServerWithFileController() },

            test = {
                val fileToMove = "/home/user1/folder/a"
                val newPath = "/home/user1/a"

                val response = engine.stat(fileToMove)
                assertEquals(HttpStatusCode.OK, response.status())

                val response2 = engine.move(fileToMove, newPath)
                assertEquals(HttpStatusCode.OK, response2.status())

                val response3 = engine.stat(fileToMove)
                assertEquals(HttpStatusCode.NotFound, response3.status())

                val response4 = engine.stat(newPath)
                assertEquals(HttpStatusCode.OK, response4.status())
            }
        )
    }

    @Test
    fun `moving a file to its current position`() {
        withKtorTest(
            setup = { configureServerWithFileController() },

            test = {
                val fileToMove = "/home/user1/folder/a"
                val newLocation = "/home/user1/folder/a"

                val response = engine.stat(fileToMove)
                assertEquals(HttpStatusCode.OK, response.status())

                val response2 = engine.move(fileToMove, newLocation)
                assertEquals(HttpStatusCode.OK, response2.status())
            }
        )
    }

    @Test
    fun `moving a file to a directory that does not exist`() {
        withKtorTest(
            setup = { configureServerWithFileController() },

            test = {
                val fileToMove = "/home/user1/folder/a"
                val newLocation = "/home/user1/folder/notThere/a"

                val response = engine.stat(fileToMove)
                assertEquals(HttpStatusCode.OK, response.status())

                val response2 = engine.move(fileToMove, newLocation)
                assertEquals(HttpStatusCode.NotFound, response2.status())
            }
        )
    }


    @Test
    fun `move file then rename`() {
        withKtorTest(
            setup = { configureServerWithFileController() },

            test = {
                val fileToMove = "/home/user1/folder/a"
                val newPath = "/home/user1/folder/newName"

                val response = engine.stat(fileToMove)
                assertEquals(HttpStatusCode.OK, response.status())

                val response2 = engine.move(fileToMove, newPath)
                assertEquals(HttpStatusCode.OK, response2.status())

                val response3 = engine.stat(fileToMove)
                assertEquals(HttpStatusCode.NotFound, response3.status())

                val response4 = engine.stat(newPath)
                assertEquals(HttpStatusCode.OK, response4.status())
            }
        )
    }

    @Test
    fun `move file that does not exist`() {
        withKtorTest(
            setup = { configureServerWithFileController() },

            test = {
                val fileToMove = "/home/user1/folder/k"
                val newLocation = "/home/user1/another-one/k"

                val response = engine.stat(fileToMove)
                assertEquals(HttpStatusCode.NotFound, response.status())

                val response2 = engine.move(fileToMove, newLocation)
                assertEquals(HttpStatusCode.NotFound, response2.status())
            }
        )
    }

    @Test
    fun `move file with unsupported path`() {
        withKtorTest(
            setup = { configureServerWithFileController() },

            test = {
                val fileToMove = "/home/user1/folder/a"
                val newLocation =
                    "/home/user1/another-one/ด้้้้้็็็็็้้้้้็็็็็้้้้้้้้็็็็็้้้้้็็็็็้้้้้้้้็็็็็้้้้้็็็็็้้้้้้้้็็็็็้้้้้็็็็ ด้้้้้็็็็็้้้้้็็็็็้้้้้้้้็็็็็้้้้้็็็็็้้้้้้้้็็็็็้้้้้็็็็็้้้้้้้้็็็็็้้้้้็็็็ ด้้้้้็็็็็้้้้้็็็็็้้้้้้้้็็็็็้้้้้็็็็็้้้้้้้้็็็็็้้้้้็็็็็้้้้้้้้็็็็็้้้้้็็็็"

                val response = engine.stat(fileToMove)
                assertEquals(HttpStatusCode.OK, response.status())

                val response2 = engine.move(fileToMove, newLocation)
                assertEquals(HttpStatusCode.BadRequest, response2.status())
            }
        )
    }
}
