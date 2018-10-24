package dk.sdu.cloud.service

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.client.MultipartRequest
import dk.sdu.cloud.client.RESTDescriptions
import dk.sdu.cloud.client.SDUCloud
import dk.sdu.cloud.client.StreamingFile
import dk.sdu.cloud.client.bindEntireRequestFromBody
import dk.sdu.cloud.client.jwtAuth
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import kotlinx.coroutines.experimental.runBlocking
import org.junit.Test
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals

data class Wrapper<T>(val value: T)

data class JsonPayload(
    val foo: Int,
    val bar: String,
    val list: List<Wrapper<String>>
)

data class FormRequest(
    val normal: String,
    val json: JsonPayload,
    val streamingOne: StreamingFile?,
    val normal2: String?,
    val streamingTwo: StreamingFile?
)

data class EvilFormRequest(
    val normal: StreamingFile, // Should fail because all the types are incorrect
    val json: Int,
    val streamingOne: Int,
    val normal2: Int,
    val streamingTwo: Int
)

data class EvilFormRequest2(
    val normal: StreamingFile, // Should fail because type is incorrect
    val json: JsonPayload,
    val streamingOne: StreamingFile?,
    val normal2: String?,
    val streamingTwo: StreamingFile?
)

data class EvilFormRequest3(
    val normal: String,
    val json: JsonPayload,
    val streamingOne: StreamingFile?,
    val normal2: JsonPayload? // Should fail because type is incorrect
)

object MultipartDescriptions : RESTDescriptions("foo") {
    val evilMultipart = callDescription<MultipartRequest<EvilFormRequest>, Unit, Unit> {
        name = "multipart"
        method = HttpMethod.Post

        auth {
            access = AccessRight.READ
        }

        path {
            +"foo"
        }

        body { bindEntireRequestFromBody() }
    }

    val evilMultipart2 = callDescription<MultipartRequest<EvilFormRequest2>, Unit, Unit> {
        name = "multipart"
        method = HttpMethod.Post

        auth {
            access = AccessRight.READ
        }

        path {
            +"foo"
        }

        body { bindEntireRequestFromBody() }
    }

    val evilMultipart3 = callDescription<MultipartRequest<EvilFormRequest3>, Unit, Unit> {
        name = "multipart"
        method = HttpMethod.Post

        auth {
            access = AccessRight.READ
        }

        path {
            +"foo"
        }

        body { bindEntireRequestFromBody() }
    }

    val multipart = callDescription<MultipartRequest<FormRequest>, Unit, Unit> {
        name = "multipart"
        method = HttpMethod.Post

        auth {
            access = AccessRight.READ
        }

        path {
            +"foo"
        }

        body { bindEntireRequestFromBody() }
    }
}

class MultipartTest {
    @Test
    fun `basic testing`() {
        val cloud = SDUCloud("http://127.0.0.1:8080").jwtAuth("token")
        RESTServerSupport.allowMissingKafkaHttpLogger = true
        var callCount = 0
        var text1: String? = null
        var text2: String? = null

        val server = embeddedServer(Netty, port = 8080) {
            routing {
                implement(MultipartDescriptions.multipart) { req ->
                    callCount = 0
                    text1 = null
                    text2 = null

                    req.receiveBlocks {
                        callCount++
                        println(it)

                        if (it.streamingOne != null) {
                            text1 = it.streamingOne.payload.bufferedReader().readText()
                        }

                        if (it.streamingTwo != null) {
                            text2 = it.streamingTwo.payload.bufferedReader().readText()
                        }
                    }

                    ok(Unit)
                }
            }
        }
        server.start()

        runBlocking {
            val expectedText1 = "Hello!"
            val expectedText2 = "Hello 2!"
            val file = Files.createTempFile("", ".txt").toFile().also { it.writeText(expectedText1) }
            val file2 = Files.createTempFile("", ".txt").toFile().also { it.writeText(expectedText2) }

            val result = MultipartDescriptions.multipart.call(
                MultipartRequest.create(
                    FormRequest(
                        "testing",
                        JsonPayload(42, "bar", listOf("a", "b", "c").map { Wrapper(it) }),
                        StreamingFile.fromFile(file),
                        "Testing",
                        StreamingFile.fromFile(file2)
                    )
                ),
                cloud
            )

            assertEquals(2, callCount)
            assertEquals(expectedText1, text1)
            assertEquals(expectedText2, text2)
            assertEquals(HttpStatusCode.OK.value, result.status)
        }

        runBlocking {
            val file = Files.createTempFile("", ".txt").toFile().also { it.writeText("Hello 2!") }
            val result = MultipartDescriptions.evilMultipart.call(
                MultipartRequest.create(
                    EvilFormRequest(
                        StreamingFile.fromFile(file),
                        42,
                        42,
                        42,
                        42
                    )
                ),
                cloud
            )

            assertEquals(0, callCount)
            assertEquals(HttpStatusCode.BadRequest.value, result.status)
        }

        runBlocking {
            val file = Files.createTempFile("", ".txt").toFile().also { it.writeText("Hello 2!") }
            val result = MultipartDescriptions.evilMultipart2.call(
                MultipartRequest.create(
                    EvilFormRequest2(
                        StreamingFile.fromFile(file),
                        JsonPayload(42, "bar", listOf("a", "b", "c").map { Wrapper(it) }),
                        StreamingFile.fromFile(file),
                        "Testing",
                        StreamingFile.fromFile(file)
                    )
                ),
                cloud
            )

            assertEquals(0, callCount)
            assertEquals(HttpStatusCode.BadRequest.value, result.status)
        }

        runBlocking {
            val expectedText1 = "Hello"
            val file = Files.createTempFile("", ".txt").toFile().also { it.writeText(expectedText1) }
            val result = MultipartDescriptions.evilMultipart3.call(
                MultipartRequest.create(
                    EvilFormRequest3(
                        "normal",
                        JsonPayload(42, "bar", listOf("a", "b", "c").map { Wrapper(it) }),
                        StreamingFile.fromFile(file),
                        JsonPayload(42, "bar", emptyList())
                    )
                ),
                cloud
            )

            assertEquals(1, callCount)
            assertEquals(expectedText1, text1)
            assertEquals(HttpStatusCode.BadRequest.value, result.status)
        }

        server.stop(5, 5, TimeUnit.SECONDS)
    }
}
