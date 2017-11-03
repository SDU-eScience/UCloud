package org.esciencecloud.storage.model

import java.net.URI

// Contains the shared model (used as part of interface) of this service

enum class UserType {
    USER,
    ADMIN,
    GROUP_ADMIN
}

data class StoragePath private constructor(private val uri: URI) {
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

    override fun hashCode(): Int {
        return uri.hashCode()
    }

    companion object {
        fun internalCreateFromHostAndAbsolutePath(host: String, path: String): StoragePath {
            return StoragePath(URI("storage", host, path, null, null).normalize())
        }
    }
}

enum class AccessRight {
    NONE,
    READ,
    READ_WRITE,
    OWN
}

data class AccessEntry(val entity: Entity, val right: AccessRight)typealias AccessControlList = List<AccessEntry>
data class MetadataEntry(val key: String, val value: String)

typealias Metadata = List<MetadataEntry>

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
