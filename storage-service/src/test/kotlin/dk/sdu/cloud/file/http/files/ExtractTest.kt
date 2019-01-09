package dk.sdu.cloud.file.http.files

import dk.sdu.cloud.storage.util.mkdir
import dk.sdu.cloud.storage.util.touch
import dk.sdu.cloud.storage.util.withAuthMock
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.withTestApplication
import org.junit.Test
import org.kamranzafar.jtar.TarEntry
import org.kamranzafar.jtar.TarHeader
import org.kamranzafar.jtar.TarOutputStream
import java.io.File
import java.io.OutputStream
import java.nio.file.Files
import java.util.zip.GZIPOutputStream
import kotlin.test.assertEquals

class ExtractTest{

    private fun fsForTest(): File {
        val fsRoot = Files.createTempDirectory("share-service-test").toFile()
        fsRoot.apply {
            mkdir("home") {
                mkdir("user1") {
                    mkdir("folder") {
                        touch("a.zip")
                        touch("a.txt")
                    }

                    mkdir("another-one") {
                        touch("a.tar.gz")
                    }

                    mkdir("Favorites") {}
                }
            }
        }
        return fsRoot
    }

    // NONE of these tests actually extracts, but tests stat and auto detect type
    @Test
    fun `Tar test`() {
        withAuthMock {
            withTestApplication(
                moduleFunction = { configureServerWithFileController(fsRootInitializer = {fsForTest()}) },

                test = {
                    val path = "/home/user1/another-one/a.tar.gz"
                    val response = extract(path)
                    assertEquals(HttpStatusCode.OK, response.status())
                }
            )
        }
    }

    @Test
    fun `Zip test`() {
        withAuthMock {
            withTestApplication(
                moduleFunction = { configureServerWithFileController(fsRootInitializer = {fsForTest()}) },

                test = {
                    val path = "/home/user1/folder/a.zip"
                    val response = extract(path)
                    assertEquals(HttpStatusCode.OK, response.status())
                }
            )
        }
    }

    @Test
    fun `Unknown format test`() {
        withAuthMock {
            withTestApplication(
                moduleFunction = { configureServerWithFileController(fsRootInitializer = {fsForTest()}) },

                test = {
                    val path = "/home/user1/folder/a.txt"
                    val response = extract(path)
                    assertEquals(HttpStatusCode.BadRequest, response.status())
                }
            )
        }
    }
}
