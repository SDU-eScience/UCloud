package dk.sdu.cloud.storage.http.files

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import dk.sdu.cloud.auth.api.JWTProtection
import dk.sdu.cloud.auth.api.Role
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.ServiceInstance
import dk.sdu.cloud.service.definition
import dk.sdu.cloud.service.installDefaultFeatures
import dk.sdu.cloud.storage.api.StorageFile
import dk.sdu.cloud.storage.api.StorageServiceDescription
import dk.sdu.cloud.storage.http.FilesController
import dk.sdu.cloud.storage.services.cephFSWithRelaxedMocks
import dk.sdu.cloud.storage.services.createDummyFS
import dk.sdu.cloud.storage.util.withAuthMock
import io.ktor.application.install
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.routing.route
import io.ktor.routing.routing
import io.ktor.server.testing.TestApplicationRequest
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.withTestApplication
import io.mockk.mockk
import org.junit.Test
import org.slf4j.LoggerFactory
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ListAtPathTests {
    private val mapper = jacksonObjectMapper()

    @Test
    fun `list files at path`() {
        withAuthMock {
            withTestApplication(
                moduleFunction = { configureServerWithFileController() },

                test = {
                    val response = listDir("/home/user1/folder")
                    assertEquals(HttpStatusCode.OK, response.status())
                    val items = mapper.readValue<Page<StorageFile>>(response.content!!)
                    assertEquals(3, items.items.size)
                    log.debug("Received items: $items")
                    assertTrue("a file is contained in response") { items.items.any { it.path == "/home/user1/folder/a" } }
                    assertTrue("b file is contained in response") { items.items.any { it.path == "/home/user1/folder/b" } }
                    assertTrue("c file is contained in response") { items.items.any { it.path == "/home/user1/folder/c" } }
                }
            )
        }
    }

    @Test
    fun `list files at path which does not exist`() {
        withAuthMock {
            withTestApplication(
                moduleFunction = { configureServerWithFileController() },

                test = {
                    val path = "/home/notThere"
                    val response = listDir(path)
                    assertEquals(HttpStatusCode.NotFound, response.status())
                }
            )
        }
    }

    @Test
    fun `missing permissions`() {
        withTestApplication(
            moduleFunction = { configureServerWithFileController() },

            test = {
                // TODO FIXME This test will not work on OSX. User also doesn't exist
                val response = listDir("/home/user1", user = "user2")
                assertEquals(HttpStatusCode.Unauthorized, response.status())
            }
        )
    }

    companion object {
        private val log = LoggerFactory.getLogger(ListAtPathTests::class.java)
    }
}