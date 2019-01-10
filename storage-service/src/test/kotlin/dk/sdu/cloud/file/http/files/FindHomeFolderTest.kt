package dk.sdu.cloud.file.http.files

import com.fasterxml.jackson.module.kotlin.readValue
import dk.sdu.cloud.Role
import dk.sdu.cloud.client.defaultMapper
import dk.sdu.cloud.file.api.FindHomeFolderResponse
import dk.sdu.cloud.file.api.normalize
import io.ktor.server.testing.withTestApplication
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FindHomeFolderTest{

    @Test
    fun `find home folder test`() {
        withTestApplication(
            moduleFunction = { configureServerWithFileController() },

            test = {
                val response = findHome("user@name.dk")
                val result = defaultMapper.readValue<FindHomeFolderResponse>(response.content!!)
                assertEquals("/home/user@name.dk".normalize(), result.path.normalize())
            }
        )
    }

    @Test
    fun `find home folder test - not admin`() {
        withTestApplication(
            moduleFunction = { configureServerWithFileController() },

            test = {
                val response = findHome("user@name.dk", role = Role.USER)
                assertNull(response.content)
            }
        )
    }
}
