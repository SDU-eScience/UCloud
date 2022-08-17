package dk.sdu.cloud.debug

import dk.sdu.cloud.io.CommonFile
import dk.sdu.cloud.io.CommonFileOutputStream
import dk.sdu.cloud.io.*
import dk.sdu.cloud.service.Time
import dk.sdu.cloud.ThreadLocal
import dk.sdu.cloud.calls.client.atomicInt
import dk.sdu.cloud.defaultMapper

class CommonDebugSystem(id: String, directory: CommonFile) : DebugSystem {
    private val idGen = atomicInt(0)
    private val local = ThreadLocal { ThreadLocalDebugSystem(id, directory, idGen.getAndIncrement().toString()) }

    override suspend fun sendMessage(message: DebugMessage) {
        local.get().sendMessage(message)
    }
}

class ThreadLocalDebugSystem(
    private val id: String,
    private val directory: CommonFile,
    private val suffix: String,
) {
    private val outputStream: CommonFileOutputStream
    private val newLine = "\n".encodeToByteArray()

    init {
        val now = Time.now()
        directory.child("${id.replace("/", "-")}-${now}-${suffix}.meta.json").writeText(
            """
                {
                    "path": "$id",
                    "startedAt": ${Time.now()}
                }
            """.trimIndent()
        )

        outputStream = CommonFileOutputStream(directory.child("${id.replace("/", "-")}-${now}-${suffix}.json"))
    }

    fun sendMessage(message: DebugMessage) {
        outputStream.writeFully(
            defaultMapper.encodeToString(DebugMessage.serializer(), message).encodeToByteArray(),
            autoClose = false
        )

        outputStream.writeFully(newLine, autoClose = false)
    }
}
