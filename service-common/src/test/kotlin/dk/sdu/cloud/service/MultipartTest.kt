package dk.sdu.cloud.service

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.client.MultipartRequest
import dk.sdu.cloud.client.RESTDescriptions
import dk.sdu.cloud.client.SDUCloud
import dk.sdu.cloud.client.StreamingFile
import dk.sdu.cloud.client.bindEntireRequestFromBody
import dk.sdu.cloud.client.jwtAuth
import io.ktor.http.HttpMethod
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.runBlocking
import org.junit.Test
import java.nio.file.Files
import java.util.concurrent.TimeUnit

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

object MultipartDescriptions : RESTDescriptions("foo") {
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
    fun `test some stuff`() {
        val cloud = SDUCloud("http://127.0.0.1:8080").jwtAuth("token")
        RESTServerSupport.allowMissingKafkaHttpLogger = true
        val server = embeddedServer(Netty, port = 8080) {
            routing {
                /*
                post("/foo") {
                    val parts = call.receiveMultipart()
                    parts.forEachPart { part ->
                        println(part.name)
                        part.dispose()
                    }

                    call.respondText("OK")
                }
                */
                implement(MultipartDescriptions.multipart) { req ->
                    req.receiveBlocks {
                        println(it)

                        if (it.streamingOne != null) {
                            println("Streaming one: ${it.streamingOne.payload.bufferedReader().readText()}")
                        }

                        if (it.streamingTwo != null) {
                            println("Streaming one: ${it.streamingTwo.payload.bufferedReader().readText()}")
                        }
                    }
                    ok(Unit)
                }
            }
        }
        server.start()

        runBlocking {
            val file = Files.createTempFile("", ".txt").toFile().also { it.writeText("Hello!") }
            val file2 = Files.createTempFile("", ".txt").toFile().also { it.writeText("Hello 2!") }
            MultipartDescriptions.multipart.call(
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
        }



        launch {
            delay(10000)
            server.stop(5, 5, TimeUnit.SECONDS)
        }
    }
}
