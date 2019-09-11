package dk.sdu.cloud.file.api

enum class WriteConflictPolicy {
    OVERWRITE,
    MERGE,
    RENAME,
    REJECT;

    fun allowsOverwrite(): Boolean = this == OVERWRITE || this == MERGE



}
