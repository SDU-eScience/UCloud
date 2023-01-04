package dk.sdu.cloud.debugger

import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.atomic.AtomicReference

data class TrackedService(val title: String, val generation: String, val lastModified: Long)

fun main(args: Array<String>) {
    val directory = args.getOrNull(0)?.let { File(it) } ?: error("Missing root directory")
    if (args.contains("--producer")) {
        exampleProducer(directory)
        return
    }

    val sessions = ArrayList<ClientSession>()

    val trackedServices = AtomicReference(emptyMap<String, TrackedService>())

    @Suppress("OPT_IN_USAGE")
    GlobalScope.launch {
        coroutineScope {
            val serviceWatcher = launch(Dispatchers.IO) {
                while (isActive) {
                    val newServices = (directory.listFiles() ?: emptyArray())
                        .filter { it.isFile && it.name.endsWith(".service") }
                        .mapNotNull { serviceFile ->
                            runCatching {
                                val lines = serviceFile.readText().lines()
                                TrackedService(lines[0], lines[1], serviceFile.lastModified())
                            }.getOrNull()
                        }
                        .groupBy { it.title }
                        .mapValues { (_, group) ->
                            group.maxByOrNull { it.lastModified }!!
                        }

                    val oldServices = trackedServices.get()

                    // TODO Notify clients of new services
                    val serviceWhichAreNew = newServices.keys.filter { it !in oldServices }

                    trackedServices.set(newServices)
                    delay(50)
                }
            }

            val logWatcher = launch(Dispatchers.IO) {
                val openLogFiles = ArrayList<LogFileReader>()

                while (isActive) {
                    val currentServices = trackedServices.get()

                    // Close log files
                    run {
                        val iterator = openLogFiles.iterator()
                        while (iterator.hasNext()) {
                            val logFile = iterator.next()

                            val shouldClose =
                                // Close if generation is no longer valid
                                currentServices.none { it.value.generation == logFile.generation }
                            // TODO Close files which are no longer actively used

                            if (shouldClose) {
                                println("Closing service")
                                logFile.close()
                                iterator.remove()
                            }
                        }
                    }

                    // Open new log files
                    run {
                        for (service in currentServices) {
                            var idx = 0
                            while (true) {
                                if (!LogFileReader.exists(directory, service.value.generation, idx)) {
                                    idx--
                                    break
                                }
                                idx++
                            }

                            if (idx < 0) continue

                            val shouldOpen = openLogFiles.none {
                                it.generation == service.value.generation && it.idx == idx
                            }

                            if (shouldOpen) {
                                println("Opening $directory ${service} ${idx}")
                                val openFile = LogFileReader(directory, service.value.generation, idx)
                                openFile.seekToEnd()
                                openLogFiles.add(openFile)
                            }
                        }
                    }

                    // Find new messages from all log readers
                    run {
                        for (logFile in openLogFiles) {
                            while (logFile.next()) {
                                val message = logFile.retrieve() ?: break
                                println(message.toString())
                            }
                        }
                    }

                    delay(50)
                }
            }

            val contextWatcher = launch(Dispatchers.IO) {
                while (isActive) {
                    delay(50)
                }
            }
        }
    }

    embeddedServer(CIO) {
        install(WebSockets)

        routing {
            webSocket {
                while (isActive) {
                    this
                    val frame = incoming.receiveCatching().getOrNull() ?: break
                }
            }
        }
    }.start(wait = true)
}


data class ClientSession(
    val session: WebSocketServerSession,
    var activeContext: Long = 0,
    var minimumLevel: MessageImportance = MessageImportance.THIS_IS_NORMAL,
    var filterQuery: String? = null,
    var activeService: String? = null,
)
