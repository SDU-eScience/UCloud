package dk.sdu.cloud.file.api

import kotlinx.serialization.Serializable

@Serializable
enum class WriteConflictPolicy {
    OVERWRITE,
    MERGE,
    RENAME,
    REJECT;

    fun allowsOverwrite(): Boolean = this == OVERWRITE || this == MERGE
}
