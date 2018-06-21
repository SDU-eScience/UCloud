package dk.sdu.cloud.storage.api

enum class WriteConflictPolicy {
    OVERWRITE,
    RENAME,
    REJECT
}