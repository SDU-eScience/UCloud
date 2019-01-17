package dk.sdu.cloud.file.services

enum class FileAttribute(val value: Long) {
    /**
     * The file type
     */
    FILE_TYPE(1 shl 0),

    /**
     * A flag which determines if this file is a boolean
     */
    IS_LINK(1 shl 1),

    /**
     * The link target
     */
    LINK_TARGET(1 shl 2),

    /**
     * inode (ID) of the link target
     */
    LINK_INODE(1 shl 3),

    /**
     * Unix mode
     */
    UNIX_MODE(1 shl 4),

    /**
     * Unix owner information (this is the file creator)
     */
    OWNER(1 shl 5),

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
     * The path used to get to the file (not canonical)
     */
    RAW_PATH(1 shl 9),

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

    /**
     * Annotations of the file
     */
    ANNOTATIONS(1 shl 13),

    /**
     * Checksum information (if computed)
     */
    CHECKSUM(1 shl 14),

    /**
     * Sensitivity information
     */
    SENSITIVITY(1 shl 15),

    /**
     * The owner (as derived from the xattr)
     */
    XOWNER(1 shl 16);

    companion object
}
