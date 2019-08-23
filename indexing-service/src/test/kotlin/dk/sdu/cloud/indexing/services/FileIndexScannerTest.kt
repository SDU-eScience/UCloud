package dk.sdu.cloud.indexing.services

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.file.api.DeliverMaterializedFileSystemResponse
import dk.sdu.cloud.file.api.FileDescriptions
import dk.sdu.cloud.service.test.ClientMock
import dk.sdu.cloud.service.test.TestCallResult
import dk.sdu.cloud.service.test.initializeMicro
import io.ktor.http.HttpStatusCode
import io.mockk.mockk
import io.mockk.mockkObject
import org.elasticsearch.client.RestHighLevelClient
import org.junit.Ignore
import org.junit.Test
import java.io.File

class FileIndexScannerTest {

    @Test
    fun `test scanner`() {
        val micro = initializeMicro()
        val client = ClientMock.authenticatedClient
        val rest = mockk<RestHighLevelClient>(relaxed = true)
        var numRequests = 0

        val scanner = FileIndexScanner(client, rest)

        rest.use {
            File("/tmp/debug.txt").printWriter().use { debugStream ->
                mockkObject(FileDescriptions)
                ClientMock.mockCall(FileDescriptions.deliverMaterializedFileSystem) { request ->
                    numRequests++

                    request.rootsToMaterialized.forEach { t, u ->
                        debugStream.println(t)
                        u.forEach {
                            debugStream.println("  $it")
                        }
                    }


                    TestCallResult.Ok(
                        DeliverMaterializedFileSystemResponse(request.rootsToMaterialized.keys.map { it to true }.toMap())
                    )
                }
                scanner.scan()

                println("Performed $numRequests requests to storage-service")
            }

        }
    }

    @Ignore("Slow test")
    @Test(expected = RuntimeException::class)
    fun `test scanner - not okay`() {
        val micro = initializeMicro()
        val client = ClientMock.authenticatedClient
        val rest = mockk<RestHighLevelClient>(relaxed = true)

        val scanner = FileIndexScanner(client, rest)

        rest.use {
            mockkObject(FileDescriptions)
            ClientMock.mockCall(FileDescriptions.deliverMaterializedFileSystem) { request ->
                TestCallResult.Error(
                    CommonErrorMessage("problem happened"),
                    HttpStatusCode.InternalServerError
                )
            }

            scanner.scan()
        }
    }
}
