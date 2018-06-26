package dk.sdu.cloud.storage.api

enum class WriteConflictPolicy {
    OVERWRITE,
    RENAME,
    REJECT;

    fun allowsOverwrite(): Boolean = this == OVERWRITE
}