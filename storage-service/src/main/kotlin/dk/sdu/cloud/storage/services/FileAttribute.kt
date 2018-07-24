package dk.sdu.cloud.storage.services

enum class FileAttribute(val value: Long) {
    FILE_TYPE(1 shl 0),
    IS_LINK(1 shl 1),
    LINK_TARGET(1 shl 2),
    LINK_INODE(1 shl 3),
    UNIX_MODE(1 shl 4),
    OWNER(1 shl 5),
    GROUP(1 shl 6),
    TIMESTAMPS(1 shl 7),
    PATH(1 shl 8), // The canonical path
    RAW_PATH(1 shl 9), // The path used to get to the file (not canonical)
    INODE(1 shl 10),
    SIZE(1 shl 11),
    SHARES(1 shl 12),
    ANNOTATIONS(1 shl 13),
    CHECKSUM(1 shl 14),
    SENSITIVITY(1 shl 15),
    ;

    companion object
}
