package dk.sdu.cloud.storage.services.cephfs

import dk.sdu.cloud.storage.api.StorageEvent
import dk.sdu.cloud.storage.services.FSUserContext
import dk.sdu.cloud.storage.util.CommaSeparatedLexer
import java.io.File

class RemoveService(isDevelopment: Boolean) {
    private val executable: String = if (isDevelopment) File("./bin/osx/ceph-remove").absolutePath else "ceph-remove"

    fun remove(ctx: FSUserContext, mountedPath: String): List<StorageEvent.Deleted> {
        val (status, stdout, stderr) = ctx.runWithResultAsInMemoryString(
            listOf(
                executable,
                mountedPath
            )
        )

        val lexer = CommaSeparatedLexer()
        if (status == 0) {
            return stdout.lines().mapNotNull { line ->
                if (line.isBlank()) return@mapNotNull null
                lexer.line = line

                val uniqueId = lexer.readToken()
                val path = lexer.remaining

                StorageEvent.Deleted(
                    uniqueId,
                    path,
                    ctx.user,
                    System.currentTimeMillis()
                )
            }
        } else {
            throw IllegalStateException("ceph-remove did not return 0. $status, $stderr")
        }
    }
}