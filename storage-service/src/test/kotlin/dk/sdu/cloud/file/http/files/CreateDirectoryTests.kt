package dk.sdu.cloud.file.http.files

import dk.sdu.cloud.service.test.withKtorTest
import io.ktor.http.HttpStatusCode
import org.junit.Ignore
import org.junit.Test
import kotlin.test.assertEquals

class CreateDirectoryTests {
    @Test
    fun `make directory valid`() {
        withKtorTest(
            setup = { configureServerWithFileController() },

            test = {
                val path = "/home/user1/newDir"

                val response = engine.makeDir(path)
                assertEquals(HttpStatusCode.OK, response.status())

                val response2 = engine.stat(path)
                assertEquals(HttpStatusCode.OK, response2.status())
            }
        )
    }

    @Test
    fun `make folder that already exists`() {
        withKtorTest(
            setup = { configureServerWithFileController() },

            test = {
                val response = engine.makeDir("/home/user1/folder")
                assertEquals(HttpStatusCode.Conflict, response.status())

            }
        )
    }

    @Ignore
    @Test
    fun `make directory with missing permissions`() {
        withKtorTest(
            setup = { configureServerWithFileController() },

            test = {
                val response = engine.makeDir("/home/newdir")
                assertEquals(HttpStatusCode.Forbidden, response.status())

            }
        )
    }

    @Test
    fun `test if directories are created recursively`() {
        withKtorTest(
            setup = { configureServerWithFileController() },

            test = {
                val path = "/home/user1/folder/newDir/newnew"

                val response = engine.makeDir(path)
                assertEquals(HttpStatusCode.NotFound, response.status())

                val response2 = engine.stat(path)
                assertEquals(HttpStatusCode.NotFound, response2.status())

                val response3 = engine.stat("/home/user1/folder/newDir")
                assertEquals(HttpStatusCode.NotFound, response3.status())
            }
        )
    }
}
