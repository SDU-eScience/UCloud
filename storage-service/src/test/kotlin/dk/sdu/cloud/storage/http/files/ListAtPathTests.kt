package dk.sdu.cloud.storage.http.files

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.files.api.StorageFile
import dk.sdu.cloud.storage.util.withAuthMock
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.withTestApplication
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