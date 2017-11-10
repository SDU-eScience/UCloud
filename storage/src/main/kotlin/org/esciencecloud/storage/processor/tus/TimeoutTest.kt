package org.esciencecloud.storage.processor.tus

import io.ktor.application.call
import io.ktor.application.install
import io.ktor.application.log
import io.ktor.cio.toReadChannel
import io.ktor.features.CallLogging
import io.ktor.http.HttpStatusCode
import io.ktor.request.PartData
import io.ktor.request.receiveChannel
import io.ktor.request.receiveMultipart
import io.ktor.response.header
import io.ktor.response.respond
import io.ktor.response.respondWrite
import io.ktor.routing.head
import io.ktor.routing.post
import io.ktor.routing.patch
import io.ktor.routing.routing
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import kotlinx.coroutines.experimental.delay
import java.nio.ByteBuffer
import java.util.*
import java.util.concurrent.TimeUnit

fun main(args: Array<String>) {
    embeddedServer(CIO, port = 8080) {
        install(CallLogging)
        routing {
            head {
                call.response.header("Upload-Offset", "0")
                call.respond(HttpStatusCode.NoContent)
            }

            patch {
                println("Starting at ${Date()}")
                val channel = call.receiveChannel()
                val buffer = ByteBuffer.allocate(1024 * 32)
                while (channel.read(buffer) != -1) {
                    buffer.clear()
                    delay(1, TimeUnit.SECONDS)
                    log.info("Hello!")
                }
                call.respondWrite {
                    write("Move on")
                }
            }

            post("file") {
                log.info("Starting")
                val multipart = call.receiveMultipart()
                val part = multipart.readPart()
                when (part) {
                    is PartData.FileItem -> {
                        val ins = part.streamProvider()

                        val channel = ins.toReadChannel()
                        val buffer = ByteBuffer.allocate(1024) // Small buffer to trigger timeout
                        while (channel.read(buffer) != -1) {
                            buffer.clear()
//                            Thread.sleep(10)
                        }
                    }
                }
                call.respond(HttpStatusCode.NoContent)
            }
        }
    }.start(wait = true)
}