package dk.sdu.cloud.application

import dk.sdu.cloud.debug.DebugMessage
import dk.sdu.cloud.debug.ServiceMetadata
import dk.sdu.cloud.debug.Time
import dk.sdu.cluod.debug.defaultMapper
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.serialization.decodeFromString
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.system.exitProcess
import kotlin.time.ExperimentalTime

data class MessageAndId(val message: DebugMessage, val id: Int)

class LogWatcher(private val folders: List<String>) {
    private val isRunning = AtomicBoolean(false)

    private val metadataOutputChannel = Channel<ServiceMetadata>(Channel.BUFFERED)
    val metadataChannel: ReceiveChannel<ServiceMetadata> = metadataOutputChannel

    private val outputChannel = Channel<MessageAndId>(Channel.BUFFERED)
    val messageChannel: ReceiveChannel<MessageAndId> = outputChannel

    @OptIn(DelicateCoroutinesApi::class)
    fun start() {
        if (!isRunning.compareAndSet(false, true)) error("Already running")

        GlobalScope.launch(Dispatchers.IO) {
            var nextWalk = 0L
            var idAllocator = 0
            val watchedFiles = ArrayList<WatchedFile>()

            while (isRunning.get() && isActive) {
                // NOTE(Dan): Don't attempt to run at more than ~60Hz. I think a reasonable assumption is that this
                // loop itself will take a few milliseconds. We are not really aiming for much more than a
                // ~20Hz update rate.
                delay(16)

                val now = Time.now()
                for (file in watchedFiles) {
                    val messages = file.read()
                    for (message in messages) {
                        outputChannel.send(MessageAndId(message, file.id))
                    }
                }

                if (now >= nextWalk) {
                    nextWalk = now + 5_000
                    for (folder in folders) {
                        val path = File(folder)
                        if (!path.isDirectory) continue

                        path.walkTopDown().forEach { entry ->
                            val fileName = entry.name
                            when {
                                entry.isFile && fileName.endsWith(".json") -> {
                                    if (watchedFiles.none { it.entry == entry }) {
                                        watchedFiles.add(WatchedFile(entry, idAllocator++))
                                        if (idAllocator >= MAX_SERVICE_ID) {
                                            println("Unable to allocate any more services. This is fatal error.")
                                            exitProcess(1)
                                        }
                                    }
                                }

                                entry.isFile && fileName.endsWith(".json.meta") -> {
                                    val logFile = entry.resolveSibling(fileName.removeSuffix(".meta"))
                                    for (file in watchedFiles) {
                                        if (file.entry == logFile) {
                                            val data = try {
                                                defaultMapper.decodeFromString<ServiceMetadata>(entry.readText())
                                            } catch (ex: Throwable) {
                                                println(
                                                    "Failed to parse metadata for $entry. " +
                                                        "${ex.javaClass.simpleName}: ${ex.message}"
                                                )
                                                break
                                            }

                                            data.id = file.id
                                            metadataOutputChannel.send(data)
                                            break
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    fun stop() {
        if (!isRunning.compareAndSet(true, false)) error("Already stopped")
    }
}

class WatchedFile(val entry: File, val id: Int) {
    private val stream = entry.inputStream()
    private val buffer = ByteArray(bufferSize)
    private var head = 0
    private var tail = 0
    private var didWarnAboutCorruption = false

    @OptIn(ExperimentalTime::class)
    private fun tryFromReadFromBuffer(): DebugMessage? {
        val newLineIdx = buffer.indexOf(newLineByte)
        if (newLineIdx == -1) return null
        buffer[newLineIdx] = 0 // Remove the new-line to deal with indexOf call
        val decodedToString = buffer.decodeToString(head, newLineIdx, throwOnInvalidSequence = false)
        val message = try {
            defaultMapper.decodeFromString<DebugMessage>(decodedToString)
        } catch (ex: Throwable) {
            println(
                "Invalid message from $entry" +
                    "\n  Message: ${decodedToString.removeSuffix("\n")}" +
                    "\n  ${ex.javaClass.simpleName}: ${ex.message?.prependIndent("    ")?.trim()}"
            )
            null
        }

        head = newLineIdx + 1
        return message
    }

    fun read(): List<DebugMessage> {
        val result = ArrayList<DebugMessage>()
        while (true) {
            val message = tryFromReadFromBuffer() ?: break
            result.add(message)
        }

        var capacityRemaining = bufferSize - tail
        if (capacityRemaining <= 0 && head > 0) {
            buffer.copyInto(buffer, destinationOffset = 0, startIndex = head)
            tail -= head
            head = 0

            capacityRemaining = bufferSize - tail
        }

        if (capacityRemaining <= 0) {
            if (!didWarnAboutCorruption) {
                println("$entry is corrupt - Will no longer consume content from this file")
                stream.close()
                didWarnAboutCorruption = true
            }

            return result
        }

        val read = stream.read(buffer, tail, capacityRemaining)
        if (read <= 0) return result
        tail += read

        while (true) {
            val message = tryFromReadFromBuffer() ?: break
            result.add(message)
        }
        return result
    }

    companion object {
        const val bufferSize = 1024 * 64
        const val newLineByte = '\n'.code.toByte()
    }
}
