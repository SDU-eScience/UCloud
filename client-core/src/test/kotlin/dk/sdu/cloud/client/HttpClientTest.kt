package dk.sdu.cloud.client

import dk.sdu.cloud.CommonErrorMessage
import io.ktor.http.HttpMethod
import kotlinx.coroutines.experimental.io.ByteReadChannel
import kotlinx.coroutines.experimental.runBlocking
import org.junit.Ignore
import org.junit.Test
import java.io.OutputStream
import java.nio.file.Files

data class CreateDirectoryRequest(
    val path: String,
    val owner: String?
)

object FileDescriptions : RESTDescriptions(TDescription) {
    private val baseContext = "/api/files"
    val createDirectory = callDescription<CreateDirectoryRequest, Unit, CommonErrorMessage> {
        prettyName = "createDirectory"
        method = HttpMethod.Post

        path {
            using(baseContext)
            +"directory"
        }

        body {
            bindEntireRequestFromBody()
        }
    }
}

typealias BinaryStream = Unit

object TDescription : ServiceDescription {
    override val name: String = "t"
    override val version: String = "1.0.0"
}

object TestObjects : RESTDescriptions(TDescription) {
    val download = callDescription<Unit, BinaryStream, CommonErrorMessage> {
        method = HttpMethod.Get
        prettyName = "download"
        path {
            +"test-objects"
            +"5G.bin"
        }
    }

    val listNotifications = callDescription<Unit, JustTestingNotificationPage, CommonErrorMessage> {
        method = HttpMethod.Get
        prettyName = "notifications"
        path {
            +"api"
            +"notifications"
        }
    }
}

suspend fun ByteReadChannel.copyToDebug(out: OutputStream, limit: Long = Long.MAX_VALUE): Long {
    require(limit >= 0) { "Limit shouldn't be negative: $limit" }

    val buffer = ByteArray(1024 * 64)
    var copied = 0L
    val bufferSize = buffer.size.toLong()
    var nextTarget = 1024 * 1024

    while (copied < limit) {
        val rc = readAvailable(buffer, 0, minOf(limit - copied, bufferSize).toInt())
        if (rc == -1) break
        if (rc > 0) {
            out.write(buffer, 0, rc)
            copied += rc

            if (copied > nextTarget) {
                println(copied)
                nextTarget += 1024 * 1024
            }
        }
    }

    return copied
}

data class JustTestingNotificationPage(
    val items: List<Any>,
    val itemsInTotal: Int,
    val itemsPerPage: Int,
    val pageNumber: Int,
    val pagesInTotal: Int
)

class HttpClientTest {
    val token = "TODO Missing token"

    @Test
    fun testDownload() {
        val cloud: AuthenticatedCloud = SDUCloud("https://cloud.sdu.dk").jwtAuth(token)
        runBlocking {
            val file = Files.createTempFile("", ".bin").toFile()
            TestObjects.download.call(cloud).response.content.copyToDebug(file.outputStream())
            println("WE ARE DONE ${file.length()}")
        }
    }

    @Ignore
    @Test
    fun testJson() {
        val cloud: AuthenticatedCloud = SDUCloud("https://cloud.sdu.dk").jwtAuth(token)
        runBlocking {
            val okResp = TestObjects.listNotifications.call(cloud) as RESTResponse.Ok
            println(okResp.result)
        }
    }

    @Test
    fun testJsonBody() {
        val cloud: AuthenticatedCloud = SDUCloud("https://cloud.sdu.dk").jwtAuth(token)
        runBlocking {
            val okResp = FileDescriptions.createDirectory.call(CreateDirectoryRequest(
                "/home/jonas@hinchely.dk/MyNewFolder",
                null
            ), cloud)

            println(okResp.status)
        }
    }
}