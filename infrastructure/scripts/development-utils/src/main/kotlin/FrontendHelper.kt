import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.features.CORS
import io.ktor.features.CallLogging
import io.ktor.features.DefaultHeaders
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.response.respondText
import io.ktor.routing.get
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import java.io.File

//DEPS com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.9.9
//DEPS com.fasterxml.jackson.core:jackson-core:2.9.9
//DEPS com.fasterxml.jackson.core:jackson-databind:2.9.9
//DEPS com.fasterxml.jackson.module:jackson-module-kotlin:2.9.9
//DEPS io.ktor:ktor-server-core:1.2.3
//DEPS io.ktor:ktor-server-netty:1.2.3

val mapper = jacksonObjectMapper()

private data class Override(
    val path: String,
    val method: HttpMethod,
    val destination: Destination
)

private data class Destination(val scheme: String = "http", val host: String = "localhost", val port: Int)

fun main(args: Array<String>) {
    val directoryPath = args.firstOrNull() ?: throw IllegalArgumentException("Could not find service dir")
    val directory =
        File(directoryPath).takeIf { it.exists() } ?: throw IllegalArgumentException("could not find service dir")

    embeddedServer(Netty, port = 9900) {
        install(DefaultHeaders)
        install(CallLogging)
        install(CORS) {
            anyHost()
            method(HttpMethod.Get)
            allowNonSimpleContentTypes = true
        }

        routing {
            get("/") {
                val result = mapper.writeValueAsString(
                    directory.listFiles().flatMap { file ->
                        mapper.readValue<List<Override>>(file).map {
                            it.copy(path = "/" + it.path.removePrefix("/"))
                        }
                    }.sortedBy { it.path.length }.asReversed()
                )

                call.respondText(result, ContentType.Application.Json)
            }
        }
    }.start(wait = true)
}
