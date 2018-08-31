package dk.sdu.cloud.files.api

enum class WriteConflictPolicy {
    OVERWRITE,
    RENAME,
    REJECT;

    fun allowsOverwrite(): Boolean = this == OVERWRITE
}