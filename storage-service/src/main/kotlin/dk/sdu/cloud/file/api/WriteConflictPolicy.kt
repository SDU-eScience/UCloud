package dk.sdu.cloud.file.api

enum class WriteConflictPolicy {
    OVERWRITE,
    RENAME,
    REJECT;

    fun allowsOverwrite(): Boolean = this == OVERWRITE
}
