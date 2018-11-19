package dk.sdu.cloud.file.util

import dk.sdu.cloud.file.api.StorageEvent
import dk.sdu.cloud.file.services.FileAttribute
import dk.sdu.cloud.file.services.FileRow

fun FileRow.toCreatedEvent() = StorageEvent.CreatedOrRefreshed(
    inode,
    path,
    owner,
    timestamps.created,
    fileType,
    timestamps,
    size,
    checksum,
    isLink,
    if (isLink) linkTarget else null,
    if (isLink) linkInode else null,
    annotations,
    sensitivityLevel
)

/**
 * A mode powerful enough to convert a [FileRow] into a [StorageEvent]
 */
val STORAGE_EVENT_MODE = setOf(
    FileAttribute.FILE_TYPE,
    FileAttribute.INODE,
    FileAttribute.PATH,
    FileAttribute.TIMESTAMPS,
    FileAttribute.OWNER,
    FileAttribute.SIZE,
    FileAttribute.CHECKSUM,
    FileAttribute.IS_LINK,
    FileAttribute.LINK_TARGET,
    FileAttribute.LINK_INODE,
    FileAttribute.ANNOTATIONS,
    FileAttribute.SENSITIVITY
)
