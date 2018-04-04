package dk.sdu.cloud.storage.services

data class ObjectStat(val size: Long, val modificationTime: Long)

sealed class ObjectStoreException(message: String, cause: Exception? = null) : RuntimeException(message, cause)
class DefaultObjectStoreException(message: String, cause: Exception? = null) : ObjectStoreException(message, cause)
class NotFoundObjectStoreException(message: String) : ObjectStoreException(message)

interface ObjectStore {
    suspend fun append(oid: String, buffer: ByteArray, length: Int = buffer.size)
    suspend fun write(oid: String, buffer: ByteArray, offset: Long)
    suspend fun read(oid: String, buffer: ByteArray, objectOffset: Long): Int
    suspend fun remove(oid: String)
    suspend fun stat(oid: String): ObjectStat?

    // Attributes
    suspend fun getAttribute(oid: String, name: String): String?

    suspend fun setAttribute(oid: String, name: String, value: String)
    suspend fun removeAttribute(oid: String, name: String)
    suspend fun listAttributes(oid: String): Map<String, String>?
}

suspend fun ObjectStore.readFullyToMemory(oid: String): ByteArray? {
    val stat = stat(oid) ?: return null
    if (stat.size > Int.MAX_VALUE) throw IllegalStateException("Cannot read to memory. Size is > Int.MAX_VALUE")
    val size = stat.size.toInt()
    val result = ByteArray(size)

    var offset = 0
    var read = 1
    while (read > 0) {
        read = read(oid, result, offset.toLong())
        if (read > 0) offset += read
    }
    return result
}

suspend fun ObjectStore.read(oid: String): String? {
    return readFullyToMemory(oid)?.let { String(it) }
}
