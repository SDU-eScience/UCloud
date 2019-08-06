package dk.sdu.cloud.file.services

enum class FileAttribute(val value: Long) {
    /**
     * The file type
     */
    FILE_TYPE(1 shl 0),

    /**
     * Unix mode
     */
    UNIX_MODE(1 shl 4),

    /**
     * Unix creator information (this is the file creator)
     */
    CREATOR(1 shl 5),

    /**
     * Unix group information
     */
    GROUP(1 shl 6),

    /**
     * Timestamp information
     */
    TIMESTAMPS(1 shl 7),

    /**
     * The canonical path
     */
    PATH(1 shl 8),

    /**
     * The file ID (inode)
     */
    INODE(1 shl 10),

    /**
     * The file size (in bytes)
     */
    SIZE(1 shl 11),

    /**
     * The ACL of this file
     */
    SHARES(1 shl 12),

    // 13 and 14 are no longer in use

    /**
     * Sensitivity information
     */
    SENSITIVITY(1 shl 15),

    /**
     * The creator
     */
    OWNER(1 shl 16);

    companion object
}
