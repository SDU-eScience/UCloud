package dk.sdu.cloud.storage.http.files

import dk.sdu.cloud.auth.api.Role
import dk.sdu.cloud.storage.util.withAuthMock
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.withTestApplication
import org.junit.Test
import kotlin.test.assertEquals

class MakeOpenAccessTests {
    @Test
    fun markAsOpenAccessTest() {
        withAuthMock {
            withTestApplication(
                moduleFunction = { configureServerWithFileController() },

                test = {
                    val response = makeOpen("/home/user1/folder/a", "user1", role = Role.ADMIN)
                    assertEquals(HttpStatusCode.OK, response.status())
                }
            )
        }
    }
}