package org.esciencecloud.storage.model

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.fasterxml.jackson.databind.ser.std.StdSerializer
import java.net.URI

// Contains the shared model (used as part of interface) of this service

enum class UserType {
    USER,
    ADMIN,
    GROUP_ADMIN
}

@JsonSerialize(using = StoragePath.Companion.Serializer::class)
class StoragePath private constructor(private val uri: URI) {
    val host get() = uri.host
    val path get() = uri.path
    val name get() = components.last()

    init {
        uri.host!!
    }

    val components: List<String>
        get() = uri.path.split('/')

    fun pushRelative(relativeURI: String): StoragePath {
        return StoragePath(URI(uri.scheme, uri.host, URI(uri.path + '/' + relativeURI).normalize().path,
                null, null))
    }

    fun push(vararg components: String): StoragePath {
        return pushRelative(components.joinToString("/"))
    }

    fun pop(): StoragePath = StoragePath(uri.resolve(".").normalize())

    override fun toString(): String = uri.toString()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as StoragePath

        if (uri != other.uri) return false

        return true
    }

    override fun hashCode(): Int = uri.hashCode()

    companion object {
        object Serializer : StdSerializer<StoragePath>(StoragePath::class.java) {
            override fun serialize(value: StoragePath, gen: JsonGenerator, provider: SerializerProvider) {
                gen.writeStartObject()
                gen.writeStringField("uri", value.uri.toString())
                gen.writeStringField("host", value.uri.host)
                gen.writeStringField("path", value.uri.path)
                gen.writeStringField("name", value.name)
                gen.writeEndObject()
            }
        }

        fun internalCreateFromHostAndAbsolutePath(host: String, path: String): StoragePath =
                StoragePath(URI("storage", host, path, null, null).normalize())
    }
}

enum class AccessRight {
    NONE,
    READ,
    READ_WRITE,
    OWN
}

data class MetadataEntry(val key: String, val value: String)
typealias Metadata = List<MetadataEntry>

data class AccessEntry(val entity: Entity, val right: AccessRight)
typealias AccessControlList = List<AccessEntry>

enum class FileType {
    FILE,
    DIRECTORY
}

class StorageFile(
        val type: FileType,
        val path: StoragePath,
        val createdAt: Long,
        val modifiedAt: Long,
        val size: Int,
        val acl: List<AccessEntry>
)

data class FileStat(
        val path: StoragePath,
        val createdAtUnixMs: Long,
        val modifiedAtUnixMs: Long,
        val ownerName: String,
        val sizeInBytes: Long,
        val systemDefinedChecksum: String
)

enum class ArchiveType {
    TAR,
    TAR_GZ,
    ZIP
}
