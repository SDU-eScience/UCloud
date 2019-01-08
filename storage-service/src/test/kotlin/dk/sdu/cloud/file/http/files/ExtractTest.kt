package dk.sdu.cloud.file.http.files

import dk.sdu.cloud.storage.util.withAuthMock
import io.ktor.server.testing.withTestApplication
import org.junit.Test

class ExtractTest{

    @Test
    fun `Tar test`() {
        withAuthMock {
            withTestApplication(
                moduleFunction = { configureServerWithFileController() },

                test = {
                    val path = "/home/user1/folder/a.tar.gz"
                    val response = extract(path)
                }
            )
        }
    }

    @Test
    fun `Zip test`() {
        withAuthMock {
            withTestApplication(
                moduleFunction = { configureServerWithFileController() },

                test = {
                    val path = "/home/user1/folder/a.zip"
                    val response = extract(path)
                }
            )
        }
    }

    @Test
    fun `Unknown format test`() {
        withAuthMock {
            withTestApplication(
                moduleFunction = { configureServerWithFileController() },

                test = {
                    val path = "/home/user1/folder/a.txt"
                    val response = extract(path)
                }
            )
        }
    }
}
