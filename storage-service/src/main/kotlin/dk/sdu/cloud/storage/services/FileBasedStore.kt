package dk.sdu.cloud.storage.services

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import java.io.File
import kotlin.math.min

class FileBasedStore(private val baseDirectory: File) : ObjectStore {
    private val mapper = jacksonObjectMapper()

    init {
        if (!baseDirectory.exists()) baseDirectory.mkdirs()
        if (!baseDirectory.exists()) throw IllegalStateException("Unable to create directory for file based store")
    }

    private fun file(oid: String): File = File(baseDirectory, oid)
    private fun metaFile(oid: String): File = File(baseDirectory, "$oid.meta")

    override suspend fun append(oid: String, buffer: ByteArray, length: Int) {
        val bytes = if (buffer.size != length) buffer.sliceArray(0 until length) else buffer
        file(oid).appendBytes(bytes)
    }

    override suspend fun write(oid: String, buffer: ByteArray, offset: Long) {
        if (offset != 0L) TODO()
        file(oid).writeBytes(buffer)
    }

    override suspend fun read(oid: String, buffer: ByteArray, objectOffset: Long): Int {
        val allTheBytes = file(oid).readBytes()
        val bytesToCopy = min((allTheBytes.size - objectOffset).toInt(), buffer.size)
        System.arraycopy(allTheBytes, objectOffset.toInt(), buffer, 0, bytesToCopy)
        return bytesToCopy
    }

    override suspend fun remove(oid: String) {
        file(oid).delete()
    }

    override suspend fun stat(oid: String): ObjectStat? {
        val file = file(oid)
        return if (file.exists()) ObjectStat(file.length(), file.lastModified()) else null
    }

    override suspend fun getAttribute(oid: String, name: String): String? {
        if (!file(oid).exists()) throw NotFoundObjectStoreException(oid)
        synchronized(lock) {
            val metaFile = metaFile(oid).takeIf { it.exists() } ?: return null
            val tree = mapper.readTree(metaFile)
            return try {
                tree[name]?.asText()
            } catch (ex: Exception) {
                null
            }
        }
    }

    override suspend fun setAttribute(oid: String, name: String, value: String) {
        if (!file(oid).exists()) throw NotFoundObjectStoreException(oid)
        synchronized(lock) {
            val metaFile =
                metaFile(oid).takeIf { it.exists() }?.let { mapper.readValue<Map<String, String>>(it) } ?: emptyMap()
            val result = metaFile.toMutableMap()
            result[name] = value
            mapper.writeValue(metaFile(oid), result)
        }
    }

    override suspend fun removeAttribute(oid: String, name: String) {
        if (!file(oid).exists()) throw NotFoundObjectStoreException(oid)
        synchronized(lock) {
            val metaFile =
                metaFile(oid).takeIf { it.exists() }?.let { mapper.readValue<Map<String, String>>(it) } ?: emptyMap()
            val result = metaFile.toMutableMap()
            result.remove(name)
            mapper.writeValue(metaFile(oid), result)
        }
    }

    override suspend fun listAttributes(oid: String): Map<String, String>? {
        if (!file(oid).exists()) throw NotFoundObjectStoreException(oid)
        synchronized(lock) {
            val metaFile =
                metaFile(oid).takeIf { it.exists() }?.let { mapper.readValue<Map<String, String>>(it) } ?: emptyMap()
            return metaFile
        }
    }

    companion object {
        private val lock = Any()
    }
}