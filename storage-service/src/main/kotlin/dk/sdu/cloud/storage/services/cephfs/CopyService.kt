package dk.sdu.cloud.storage.services.cephfs

import dk.sdu.cloud.storage.api.FileType
import dk.sdu.cloud.storage.api.StorageEvent
import dk.sdu.cloud.storage.services.FSUserContext
import dk.sdu.cloud.storage.util.CommaSeparatedLexer
import org.slf4j.LoggerFactory
import java.io.File

class CopyService(
    isDevelopment: Boolean
) {
    private val executable: String = if (isDevelopment) File("./bin/osx/ceph-copy").absolutePath else "ceph-copy"

    fun copy(ctx: FSUserContext, fromMountedPath: String, toMountedPath: String): List<StorageEvent.CreatedOrModified> {
        val (status, stdout, stderr) = ctx.runWithResultAsInMemoryString(
            listOf(
                executable,
                toMountedPath,
                fromMountedPath
            )
        )

        val lexer = CommaSeparatedLexer()
        if (status == 0) {
            return stdout.lines().mapNotNull { line ->
                if (line.isBlank()) return@mapNotNull null
                log.debug("Parsing line: $line")
                lexer.line = line

                val uniqueId = lexer.readToken()
                val type = when (lexer.readToken()) {
                    "D" -> FileType.DIRECTORY
                    "F" -> FileType.FILE
                    else -> throw IllegalStateException("Unknown type: $lexer")
                }
                val path = lexer.remaining

                StorageEvent.CreatedOrModified(
                    uniqueId,
                    path,
                    ctx.user,
                    System.currentTimeMillis(),
                    type
                )
            }
        } else {
            throw IllegalStateException("ceph-copy did not return 0. $status, $stderr")
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(CopyService::class.java)
    }
}