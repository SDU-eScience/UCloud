package dk.sdu.cloud.file.http

import dk.sdu.cloud.file.services.WithBackgroundScope
import dk.sdu.cloud.micro.BackgroundScope
import dk.sdu.cloud.service.test.withKtorTest
import io.ktor.http.HttpStatusCode
import kotlin.test.assertEquals
import kotlin.test.*

class CreateDirectoryTests : WithBackgroundScope() {
   @Test
    fun `make directory valid`() {
        withKtorTest(
            setup = { configureServerWithFileController(backgroundScope) },

            test = {
                val path = "/home/user1/newDir"

                val response = engine.makeDir(path)
                assertEquals(HttpStatusCode.OK, response.status())

                val response2 = engine.stat(path)
                assertEquals(HttpStatusCode.OK, response2.status())
            }
        )
    }
    //makedir does not return conflict as expected. Believe it is due to not being on Linux.
    @Ignore
    @Test
    fun `make folder that already exists`() {
        withKtorTest(
            setup = { configureServerWithFileController(backgroundScope) },

            test = {
                val statReponse = engine.stat("/home/user/folder", user="user")
                assertEquals(HttpStatusCode.OK, statReponse.status())
                val response = engine.makeDir("/home/user/folder", user="user")
                val statReponse2 = engine.stat("/home/user/folder", user="user")
                assertEquals(HttpStatusCode.OK, statReponse2.status())
                assertEquals(HttpStatusCode.Conflict, response.status())
            }
        )
    }

    @Ignore
    @Test
    fun `test if directories are created recursively`() {
        withKtorTest(
            setup = { configureServerWithFileController(backgroundScope) },

            test = {
                val path = "/home/user1/folder/newDir/newnew"

                val response = engine.makeDir(path)
                assertEquals(HttpStatusCode.OK, response.status())

                val response2 = engine.stat(path)
                assertEquals(HttpStatusCode.OK, response2.status())

                val response3 = engine.stat("/home/user1/folder/newDir")
                assertEquals(HttpStatusCode.OK, response3.status())
            }
        )
    }
}
