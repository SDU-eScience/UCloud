package dk.sdu.cloud.file.http

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import dk.sdu.cloud.Role
import dk.sdu.cloud.file.api.FileSortBy
import dk.sdu.cloud.file.api.StorageFile
import dk.sdu.cloud.file.api.StorageFileAttribute
import dk.sdu.cloud.file.api.path
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.test.withKtorTest
import io.ktor.http.HttpStatusCode
import org.junit.Ignore
import org.junit.Test
import org.slf4j.LoggerFactory
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ListAtPathTests {
    private val mapper = jacksonObjectMapper()

    @Ignore
    @Test
    fun `list files at path`() {
        withKtorTest(
            setup = { configureServerWithFileController() },

            test = {
                val response = engine.listDir("/home/user1/folder")
                assertEquals(HttpStatusCode.OK, response.status())
                val items = mapper.readValue<Page<StorageFile>>(response.content!!)
                assertEquals(5, items.items.size)
                log.debug("Received items: $items")
                assertTrue("a file is contained in response") { items.items.any { it.path == "/home/user1/folder/a" } }
                assertTrue("b file is contained in response") { items.items.any { it.path == "/home/user1/folder/b" } }
                assertTrue("c file is contained in response") { items.items.any { it.path == "/home/user1/folder/c" } }
                assertTrue("d file is contained in response") { items.items.any { it.path == "/home/user1/folder/d" } }
                assertTrue("e file is contained in response") { items.items.any { it.path == "/home/user1/folder/e" } }
            }
        )
    }

    @Test
    fun `list files at path which does not exist`() {
        withKtorTest(
            setup = { configureServerWithFileController() },

            test = {
                val path = "/home/user1/notThere"
                val response = engine.listDir(path)
                assertEquals(HttpStatusCode.NotFound, response.status())
            }
        )
    }

    @Ignore
    @Test
    fun `missing permissions`() {
        withKtorTest(
            setup = { configureServerWithFileController() },

            test = {
                // TODO FIXME This test will not work on OSX. User also doesn't exist
                val response = engine.listDir("/home/user1", user = "user2")
                assertEquals(HttpStatusCode.Unauthorized, response.status())
            }
        )
    }

    @Test
    fun `list with partial attributes and sort by sensitivity`() {
        withKtorTest(
            setup = { configureServerWithFileController() },

            test = {
                val status = engine.listDir(
                    "/home/user1",
                    user = "user1",
                    role = Role.USER,
                    attributes = setOf(
                        StorageFileAttribute.fileId,
                        StorageFileAttribute.link,
                        StorageFileAttribute.modifiedAt,
                        StorageFileAttribute.size
                    ),
                    sortBy = FileSortBy.SENSITIVITY
                )

                assertEquals(HttpStatusCode.OK, status.status())
            }
        )
    }

    companion object {
        private val log = LoggerFactory.getLogger(ListAtPathTests::class.java)
    }
}
