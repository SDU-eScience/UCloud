package dk.sdu.cloud.file.http.files

import dk.sdu.cloud.service.test.withKtorTest
import io.ktor.http.HttpStatusCode
import org.junit.Test
import kotlin.test.assertEquals

class DeletionTests {
    @Test
    fun `delete a file`() {
        withKtorTest(
            setup = { configureServerWithFileController() },

            test = {
                val path = "/home/user1/folder/a"
                val response = engine.stat(path)
                assertEquals(HttpStatusCode.OK, response.status())

                val response2 = engine.delete(path)
                assertEquals(HttpStatusCode.OK, response2.status())

                val response3 = engine.stat(path)
                assertEquals(HttpStatusCode.NotFound, response3.status())
            }
        )
    }

    @Test
    fun `delete a folder`() {
        withKtorTest(
            setup = { configureServerWithFileController() },

            test = {
                val path = "/home/user1/folder"
                val response = engine.stat(path)
                assertEquals(HttpStatusCode.OK, response.status())

                val response2 = engine.delete(path)
                assertEquals(HttpStatusCode.OK, response2.status())

                val response3 = engine.stat(path)
                assertEquals(HttpStatusCode.NotFound, response3.status())
            }
        )
    }
}
