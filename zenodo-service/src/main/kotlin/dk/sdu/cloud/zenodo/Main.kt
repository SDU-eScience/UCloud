package dk.sdu.cloud.zenodo

import dk.sdu.cloud.service.HttpServerProvider
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer

fun main(args: Array<String>) {
    val serverProvider: HttpServerProvider = { block ->
        embeddedServer(CIO, port = 9000, module = block)
    }

    Server(serverProvider)
}