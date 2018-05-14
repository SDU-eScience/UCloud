package dk.sdu.cloud.storage.services.cephfs

import dk.sdu.cloud.storage.api.FileType
import dk.sdu.cloud.storage.services.FSUserContext
import dk.sdu.cloud.storage.services.SyncItem
import dk.sdu.cloud.storage.util.CommaSeparatedLexer
import org.slf4j.LoggerFactory

/**
 * Integrates with the `ceph-tree` executable.
 *
 * See native/tree.cpp
 */
class TreeService(
    private val isDevelopment: Boolean
) {
    /**
     * Lists files starting at [mountedPath]
     *
     * Note that this function will return information that is not translated to the cloud
     * equivalent. It is the responsibility of the caller to make this mapping. This means that paths use
     * the internal (i.e. include the mount point). Users are the internal users, not the cloud users.
     */
    suspend fun listAt(
        ctx: FSUserContext,
        mountedPath: String,
        modifiedSince: Long = 0,
        handler: suspend (SyncItem) -> Unit
    ) {
        val process = ctx.run(listOf(executable, modifiedSince.toString(), mountedPath))
        process.inputStream.bufferedReader().use { reader ->
            val lexer = CommaSeparatedLexer()
            var line: String? = reader.readLine()
            while (line != null) {
                lexer.line = line

                val type = when (lexer.readToken()) {
                    "D" -> FileType.DIRECTORY
                    "F" -> FileType.FILE
                    else -> throw IllegalStateException("Unknown file type: ${lexer.cursor}, $line")
                }

                val unixMode = lexer.readToken().toInt()
                val ownerUser = lexer.readToken()
                val ownerGroup = lexer.readToken()
                val size = lexer.readToken().toLong()
                val createdAt = lexer.readToken().toLong() * 1000
                val modifiedAt = lexer.readToken().toLong() * 1000
                val accessedAt = lexer.readToken().toLong() * 1000
                val inode = lexer.readToken()
                val checksum = lexer.readToken().takeIf { it.isNotEmpty() }
                val checksumType = lexer.readToken().takeIf { it.isNotEmpty() }
                val path = line.substring(lexer.cursor)

                handler(
                    SyncItem(
                        type = type,
                        unixMode = unixMode,
                        user = ownerUser,
                        group = ownerGroup,
                        size = size,
                        createdAt = createdAt,
                        modifiedAt = modifiedAt,
                        accessedAt = accessedAt,
                        uniqueId = inode,
                        checksum = checksum,
                        checksumType = checksumType,
                        path = path
                    )
                )

                line = reader.readLine()
            }
        }


        val status = process.waitFor()
        if (status != 0) {
            log.warn("ceph-tree failed $status")
            val errStream = process.errorStream.bufferedReader().readText()
            log.warn("Error stream: $errStream")
        }
    }

    private val executable: String = if (isDevelopment) "./bin/osx/ceph-tree" else "ceph-tree"

    companion object {
        private val log = LoggerFactory.getLogger(TreeService::class.java)
    }
}