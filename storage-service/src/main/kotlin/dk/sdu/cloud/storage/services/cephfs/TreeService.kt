package dk.sdu.cloud.storage.services.cephfs

import dk.sdu.cloud.storage.api.FileType
import dk.sdu.cloud.storage.services.SyncItem
import org.slf4j.LoggerFactory

/**
 * Integrates with the `ceph-tree` executable.
 *
 * See native/tree.cpp
 */
class TreeService(
    private val processRunner: CephFSProcessRunner,
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
        user: String,
        mountedPath: String,
        modifiedSince: Long = 0,
        handler: suspend (SyncItem) -> Unit
    ) {
        val process = processRunner.runAsUser(user, listOf(executable, modifiedSince.toString(), mountedPath))
        process.inputStream.bufferedReader().use { reader ->
            var line: String? = reader.readLine()
            while (line != null) {
                var cursor = 0
                val chars = line.toCharArray()
                fun readToken(): String {
                    val builder = StringBuilder()
                    while (cursor < chars.size) {
                        val c = chars[cursor++]
                        if (c == ',') break
                        builder.append(c)
                    }
                    return builder.toString()
                }

                val type = when (readToken()) {
                    "D" -> FileType.DIRECTORY
                    "F" -> FileType.FILE
                    else -> throw IllegalStateException("Unknown file type: $cursor, $line")
                }

                val unixMode = readToken().toInt()
                val ownerUser = readToken()
                val ownerGroup = readToken()
                val size = readToken().toLong()
                val createdAt = readToken().toLong() * 1000
                val modifiedAt = readToken().toLong() * 1000
                val accessedAt = readToken().toLong() * 1000
                val inode = readToken()
                val checksum = readToken().takeIf { it.isNotEmpty() }
                val checksumType = readToken().takeIf { it.isNotEmpty() }
                val path = line.substring(cursor)

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