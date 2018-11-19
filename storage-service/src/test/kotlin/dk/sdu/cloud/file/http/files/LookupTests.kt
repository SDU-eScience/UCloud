package dk.sdu.cloud.file.http.files

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import dk.sdu.cloud.file.api.FileSortBy
import dk.sdu.cloud.file.api.SortOrder
import dk.sdu.cloud.file.api.StorageFile
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.storage.util.mkdir
import dk.sdu.cloud.storage.util.touch
import dk.sdu.cloud.storage.util.withAuthMock
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.withTestApplication
import org.junit.Test
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LookupTests {
    private val mapper = jacksonObjectMapper()

    private fun fsForLookup(): File {
        val fsRoot = Files.createTempDirectory("share-service-test").toFile()
        fsRoot.apply {
            mkdir("home") {
                mkdir("user1") {
                    mkdir("folder") {
                        repeat(100) { touch(String(CharArray(1 + it) { 'a' })) }
                    }

                    mkdir("another-one") {
                        touch("file")
                    }

                    mkdir("Favorites") {}
                }
            }
        }
        return fsRoot
    }

    @Test
    fun `look up file in directory`() {
        withAuthMock {
            withTestApplication(
                moduleFunction = { configureServerWithFileController(fsRootInitializer = { fsForLookup() }) },

                test = {
                    repeat(100) { testLookupOfFile(it) }
                }
            )
        }
    }

    @Test
    fun `look up bad file`() {
        withAuthMock {
            withTestApplication(
                moduleFunction = { configureServerWithFileController(fsRootInitializer = { fsForLookup() }) },

                test = {
                    val response =
                        lookupFileInDirectory("/home/user1/bad", 10, FileSortBy.PATH, SortOrder.ASCENDING)
                    assertEquals(HttpStatusCode.NotFound, response.status())
                }
            )
        }
    }

    private fun TestApplicationEngine.testLookupOfFile(
        item: Int
    ) {
        val path = "/home/user1/folder/${String(CharArray(1 + item) { 'a' })}"
        val itemsPerPage = 10
        val expectedPage = item / itemsPerPage
        val response =
            lookupFileInDirectory(path, itemsPerPage, FileSortBy.PATH, SortOrder.ASCENDING)
        assertEquals(HttpStatusCode.OK, response.status())

        val page = mapper.readValue<Page<StorageFile>>(response.content!!)
        assertTrue(page.items.any { it.path == path }, "Could not find file. Expected it at $path")
        assertEquals(expectedPage, page.pageNumber, "Expected item $item to be in page $expectedPage")
        assertEquals(itemsPerPage, page.itemsPerPage, "Expected itemsPerPage to match $itemsPerPage")
    }

    private val log = LoggerFactory.getLogger(LookupTests::class.java)
}
