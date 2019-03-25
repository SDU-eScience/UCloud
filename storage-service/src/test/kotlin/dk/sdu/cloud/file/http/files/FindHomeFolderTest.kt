package dk.sdu.cloud.file.http.files

import com.fasterxml.jackson.module.kotlin.readValue
import dk.sdu.cloud.Role
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.file.api.FindHomeFolderResponse
import dk.sdu.cloud.file.api.normalize
import dk.sdu.cloud.service.test.withKtorTest
import io.ktor.http.HttpStatusCode
import org.junit.Test
import kotlin.test.assertEquals

class FindHomeFolderTest {

    @Test
    fun `find home folder test`() {
        withKtorTest(
            setup = { configureServerWithFileController() },

            test = {
                val response = engine.findHome("user@name.dk")
                val result = defaultMapper.readValue<FindHomeFolderResponse>(response.content!!)
                assertEquals("/home/user@name.dk".normalize(), result.path.normalize())
            }
        )
    }

    @Test
    fun `find home folder test - not admin`() {
        withKtorTest(
            setup = { configureServerWithFileController() },

            test = {
                val response = engine.findHome("user@name.dk", role = Role.USER)
                assertEquals(HttpStatusCode.Unauthorized, response.status())
            }
        )
    }
}
