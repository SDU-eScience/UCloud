package dk.sdu.cloud.file.http.files

import dk.sdu.cloud.service.test.withKtorTest
import io.ktor.http.HttpStatusCode
import org.junit.Test
import kotlin.test.assertEquals

class SyncFilesTest {
    // TODO
    @Test
    fun syncFileTest() {
        withKtorTest(
            setup = { configureServerWithFileController() },

            test = {
                val response = engine.sync("/home/user1/folder")
                assertEquals(HttpStatusCode.OK, response.status())
            }
        )
    }
}
