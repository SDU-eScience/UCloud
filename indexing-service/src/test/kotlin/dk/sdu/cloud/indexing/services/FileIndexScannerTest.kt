package dk.sdu.cloud.indexing.services

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.client.AuthenticatedCloud
import dk.sdu.cloud.client.RESTResponse
import dk.sdu.cloud.file.api.DeliverMaterializedFileSystemRequest
import dk.sdu.cloud.file.api.DeliverMaterializedFileSystemResponse
import dk.sdu.cloud.file.api.FileDescriptions
import io.ktor.client.response.HttpResponse
import io.ktor.http.Headers
import io.ktor.http.HttpStatusCode
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.objectMockk
import io.mockk.use
import kotlinx.coroutines.experimental.io.ByteReadChannel
import org.elasticsearch.client.RestHighLevelClient
import org.junit.Test
import java.io.File

class FileIndexScannerTest {

    @Test
    fun `test scanner`() {
        val cloud = mockk<AuthenticatedCloud>(relaxed = true)
        val rest = mockk<RestHighLevelClient>(relaxed = true)
        var numRequests = 0

        val scanner = FileIndexScanner(cloud, rest)

        rest.use {
            File("/tmp/debug.txt").printWriter().use { debugStream ->
                objectMockk(FileDescriptions).use {
                    coEvery { FileDescriptions.deliverMaterializedFileSystem.call(any(), any()) } answers {
                        val request = invocation.args.first() as DeliverMaterializedFileSystemRequest
                        numRequests++

                        request.rootsToMaterialized.forEach { t, u ->
                            debugStream.println(t)
                            u.forEach {
                                debugStream.println("  $it")
                            }
                        }


                        RESTResponse.Ok(
                            mockk(relaxed = true),
                            DeliverMaterializedFileSystemResponse(request.rootsToMaterialized.keys.map { it to true }.toMap())
                        )
                    }
                    scanner.scan()

                    println("Performed $numRequests requests to storage-service")
                }

            }
        }
    }

    @Test(expected = RuntimeException::class)
    fun `test scanner - not okay`() {
        val cloud = mockk<AuthenticatedCloud>(relaxed = true)
        val rest = mockk<RestHighLevelClient>(relaxed = true)

        val scanner = FileIndexScanner(cloud, rest)

        rest.use {
            objectMockk(FileDescriptions).use {
                coEvery { FileDescriptions.deliverMaterializedFileSystem.call(any(), any()) } answers {

                    val response = mockk<HttpResponse>()

                    every { response.status } returns HttpStatusCode.BadRequest
                    every { response.content } answers {
                        ByteReadChannel(ByteArray(10))
                    }
                    every { response.headers } answers {
                        Headers.build { }
                    }

                    RESTResponse.Err(
                        response,
                        CommonErrorMessage("problem happened")
                    )

                }

                scanner.scan()
            }
        }
    }

}
