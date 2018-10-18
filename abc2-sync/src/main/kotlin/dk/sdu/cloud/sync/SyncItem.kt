package dk.sdu.cloud.sync

data class SyncItem(
    val fileType: String,
    val uniqueId: String,
    val user: String,
    val modifiedAt: Long,
    val checksum: String?,
    val checksumType: String?,
    val path: String
)

private fun hasChecksumFromField(field: String): Boolean = when (field) {
    "0" -> false
    "1" -> true
    else -> throw IllegalStateException("Bad server response")
}

fun parseSyncItem(syncLine: String): SyncItem {
    var cursor = 0
    val chars = syncLine.toCharArray()
    fun readToken(): String {
        val builder = StringBuilder()
        while (cursor < chars.size) {
            val c = chars[cursor++]
            if (c == ',') break
            builder.append(c)
        }
        return builder.toString()
    }

    val fileType = readToken()
    val uniqueId = readToken()
    val user = readToken()
    val modifiedAt = readToken().toLong()
    val hasChecksum = hasChecksumFromField(readToken())

    val checksum = if (hasChecksum) readToken() else null
    val checksumType = if (hasChecksum) readToken() else null

    val path = syncLine.substring(cursor)

    return SyncItem(
        fileType,
        uniqueId,
        user,
        modifiedAt,
        checksum,
        checksumType,
        path
    )
}
