package dk.sdu.cloud.file.http

import dk.sdu.cloud.file.services.FileSensitivityService
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.test.KtorApplicationTestSetupContext
import dk.sdu.cloud.service.test.withKtorTest
import dk.sdu.cloud.file.util.mkdir
import dk.sdu.cloud.file.util.touch
import io.ktor.http.HttpStatusCode
import org.junit.Test
import java.io.File
import java.nio.file.Files
import kotlin.test.assertEquals

class ExtractTest {

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

    private fun KtorApplicationTestSetupContext.setupApplication(): List<Controller> {
        return configureServerWithFileController(
            fsRootInitializer = {
                fsForTest()
            },

            additional = {
                listOf(
                    ExtractController(
                        it.authenticatedClient,
                        it.coreFs,
                        it.lookupService,
                        it.runner,
                        FileSensitivityService(it.fs, it.eventProducer)
                    )
                )
            }
        )
    }

    // NONE of these tests actually extracts, but tests stat and auto detect type
    @Test
    fun `Tar test`() {
        withKtorTest(
            setup = { setupApplication() },
            test = {
                val path = "/home/user1/another-one/a.tar.gz"
                val response = engine.extract(path)
                assertEquals(HttpStatusCode.OK, response.status())
            }
        )
    }


    @Test
    fun `Zip test`() {
        withKtorTest(
            setup = { setupApplication() },
            test = {
                val path = "/home/user1/folder/a.zip"
                val response = engine.extract(path)
                assertEquals(HttpStatusCode.OK, response.status())
            }
        )
    }

    @Test
    fun `Unknown format test`() {
        withKtorTest(
            setup = { setupApplication() },

            test = {
                val path = "/home/user1/folder/a.txt"
                val response = engine.extract(path)
                assertEquals(HttpStatusCode.BadRequest, response.status())
            }
        )
    }
}
