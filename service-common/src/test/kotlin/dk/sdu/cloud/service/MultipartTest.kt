package dk.sdu.cloud.service

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.calls.CallDescriptionContainer
import dk.sdu.cloud.calls.auth
import dk.sdu.cloud.calls.bindEntireRequestFromBody
import dk.sdu.cloud.calls.call
import dk.sdu.cloud.calls.http
import dk.sdu.cloud.calls.types.StreamingFile
import dk.sdu.cloud.calls.types.StreamingRequest
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import kotlinx.coroutines.runBlocking
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

data class EvilFormRequest3(
    val normal: String,
    val json: JsonPayload,
    val streamingOne: StreamingFile?,
    val normal2: JsonPayload? // Should fail because type is incorrect
)

data class BadFormRequest(
    val normal: String,
    val streamingOne: StreamingFile?,
    val shouldHaveBeenNullable: String
)

object MultipartDescriptions : CallDescriptionContainer("foo") {
    val evilMultipart = call<StreamingRequest<EvilFormRequest>, Unit, Unit>("evilMultipart") {
        auth {
            access = AccessRight.READ
        }

        http {
            method = HttpMethod.Post

            path {
                +"foo"
            }

            body { bindEntireRequestFromBody() }
        }
    }

    val evilMultipart3 = call<StreamingRequest<EvilFormRequest3>, Unit, Unit>("evilMultipart3") {
        auth {
            access = AccessRight.READ
        }

        http {
            method = HttpMethod.Post

            path {
                +"foo"
            }

            body { bindEntireRequestFromBody() }
        }
    }

    val multipart = call<StreamingRequest<FormRequest>, Unit, Unit>("multipart") {
        auth {
            access = AccessRight.READ
        }

        http {
            method = HttpMethod.Post

            path {
                +"foo"
            }

            body { bindEntireRequestFromBody() }
        }
    }

    val invalidMultipart = call<StreamingRequest<BadFormRequest>, Unit, Unit>("invalidMultipart") {
        auth {
            access = AccessRight.READ
        }

        http {
            method = HttpMethod.Post

            path {
                +"bar"
            }

            body { bindEntireRequestFromBody() }
        }
    }
}

class MultipartTest {
    /*
    @Test
    fun `basic testing`() {
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

                implement(MultipartDescriptions.invalidMultipart) { req ->
                    req.receiveBlocks { }
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

        runBlocking {
            val expectedText1 = "Hello"
            val file = Files.createTempFile("", ".txt").toFile().also { it.writeText(expectedText1) }
            val result = MultipartDescriptions.invalidMultipart.call(
                MultipartRequest.create(
                    BadFormRequest(
                        "normal",
                        StreamingFile.fromFile(file),
                        "w1"
                    )
                ),
                cloud
            )

            assertEquals(HttpStatusCode.BadRequest.value, result.status)
        }

        server.stop(5, 5, TimeUnit.SECONDS)
    }
    */
}
