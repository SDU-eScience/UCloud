package dk.sdu.cloud.file.http

import dk.sdu.cloud.service.test.withKtorTest
import io.ktor.http.HttpStatusCode
import org.junit.Test
import kotlin.test.assertEquals

class StatTests {
    @Test
    fun `stat a normal file`() {
        withKtorTest(
            setup = { configureServerWithFileController() },

            test = {
                val response = engine.stat("/home/user1/folder/a")
                assertEquals(HttpStatusCode.OK, response.status())
            }
        )
    }

    @Test
    fun `stat a non-existing file`() {
        withKtorTest(
            setup = { configureServerWithFileController() },

            test = {
                val response = engine.stat("/home/user1/folder/notThere")
                assertEquals(HttpStatusCode.NotFound, response.status())
            }
        )
    }

    @Test
    fun `stat unsupported path`() {
        withKtorTest(
            setup = { configureServerWithFileController() },

            test = {
                val response =
                    engine.stat("/home/user1/ด้้้้้็็็็็้้้้้็็็็็้้้้้้้้็็็็็้้้้้็็็็็้้้้้้้้็็็็็้้้้้็็็็็้้้้้้้้็็็็็้้้้้็็็็ ด้้้้้็็็็็้้้้้็็็็็้้้้้้้้็็็็็้้้้้็็็็็้้้้้้้้็็็็็้้้้้็็็็็้้้้้้้้็็็็็้้้้้็็็็ ด้้้้้็็็็็้้้้้็็็็็้้้้้้้้็็็็็้้้้้็็็็็้้้้้้้้็็็็็้้้้้็็็็็้้้้้้้้็็็็็้้้้้็็็็")
                assertEquals(HttpStatusCode.BadRequest, response.status())
            }
        )
    }
}
