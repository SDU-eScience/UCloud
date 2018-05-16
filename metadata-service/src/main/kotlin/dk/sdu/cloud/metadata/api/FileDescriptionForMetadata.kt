package dk.sdu.cloud.metadata.api

import dk.sdu.cloud.storage.api.FileType

class FileDescriptionForMetadata(
    val id: String,
    val type: FileType,
    val path: String
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as FileDescriptionForMetadata

        if (id != other.id) return false

        return true
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }
}