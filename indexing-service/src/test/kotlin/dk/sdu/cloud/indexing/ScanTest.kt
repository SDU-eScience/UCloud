package dk.sdu.cloud.indexing

import dk.sdu.cloud.client.AuthenticatedCloud
import dk.sdu.cloud.client.RESTResponse
import dk.sdu.cloud.indexing.services.FileIndexScanner
import dk.sdu.cloud.storage.api.DeliverMaterializedFileSystemRequest
import dk.sdu.cloud.storage.api.DeliverMaterializedFileSystemResponse
import dk.sdu.cloud.storage.api.FileDescriptions
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.objectMockk
import io.mockk.use
import org.apache.http.HttpHost
import org.elasticsearch.client.RestClient
import org.elasticsearch.client.RestHighLevelClient
import java.io.File
import kotlin.test.assertEquals

fun main(args: Array<String>) {
    val cloud = mockk<AuthenticatedCloud>(relaxed = true)
    var numRequests = 0
    val time = System.currentTimeMillis()

    File("/tmp/debug.txt").printWriter().use { debugStream ->
        RestHighLevelClient(RestClient.builder(HttpHost("localhost", 9200, "http"))).use { elasticClient ->
            objectMockk(FileDescriptions).use {
                coEvery { FileDescriptions.deliverMaterializedFileSystem.call(any(), any()) } answers {
                    val request = invocation.args.first() as DeliverMaterializedFileSystemRequest
                    numRequests++

                    request.rootsToMaterialized.forEach { t, u ->
//                        debugStream.println(t)
                        u.forEach {
                            debugStream.println("  $it")
                        }

                        assertEquals(10, u.size, "Unexpected size for $t")
                    }


                    RESTResponse.Ok(
                        mockk(relaxed = true),
                        DeliverMaterializedFileSystemResponse(request.rootsToMaterialized.keys.map { it to true }.toMap())
                    )
                }

                val scanner = FileIndexScanner(cloud, elasticClient)
                scanner.scan()
            }
        }
    }

    println("Performed $numRequests requests to storage-service")
    println("Took ${System.currentTimeMillis() - time} ms")
}