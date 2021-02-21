package dk.sdu.cloud.file.http

import dk.sdu.cloud.file.services.WithBackgroundScope
import dk.sdu.cloud.micro.BackgroundScope
import dk.sdu.cloud.service.test.withKtorTest
import io.ktor.http.HttpStatusCode
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.*

class StatTests : WithBackgroundScope() {
    @Test
    fun `stat a normal file`() {
        withKtorTest(
            setup = { configureServerWithFileController(backgroundScope) },

            test = {
                val response = engine.stat("/home/user1/folder/a")
                assertEquals(HttpStatusCode.OK, response.status())
            }
        )
    }

    @Test
    fun `stat a non-existing file`() {
        withKtorTest(
            setup = { configureServerWithFileController(backgroundScope) },

            test = {
                val response = engine.stat("/home/user1/folder/notThere")
                assertEquals(HttpStatusCode.NotFound, response.status())
            }
        )
    }

    @Test
    fun `stat unsupported path`() {
        withKtorTest(
            setup = { configureServerWithFileController(backgroundScope) },

            test = {
                val response =
                    engine.stat("/home/user1/ด้้้้้็็็็็้้้้้็็็็็้้้้้้้้็็็็็้้้้้็็็็็้้้้้้้้็็็็็้้้้้็็็็็้้้้้้้้็็็็็้้้้้็็็็ ด้้้้้็็็็็้้้้้็็็็็้้้้้้้้็็็็็้้้้้็็็็็้้้้้้้้็็็็็้้้้้็็็็็้้้้้้้้็็็็็้้้้้็็็็ ด้้้้้็็็็็้้้้้็็็็็้้้้้้้้็็็็็้้้้้็็็็็้้้้้้้้็็็็็้้้้้็็็็็้้้้้้้้็็็็็้้้้้็็็็")
                assertEquals(HttpStatusCode.BadRequest, response.status())
            }
        )
    }
}
