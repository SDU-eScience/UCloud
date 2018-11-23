package dk.sdu.cloud.file.http.files

import dk.sdu.cloud.storage.util.withAuthMock
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.withTestApplication
import org.junit.Test
import kotlin.test.assertEquals

class SyncFilesTest {
    // TODO
    @Test
    fun syncFileTest() {
        withAuthMock {
            withTestApplication(
                moduleFunction = { configureServerWithFileController() },

                test = {
                    val response = sync("/home/user1/folder")
                    assertEquals(HttpStatusCode.OK, response.status())
                }
            )
        }
    }
}
