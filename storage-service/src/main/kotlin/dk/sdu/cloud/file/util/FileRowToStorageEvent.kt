package dk.sdu.cloud.file.util

import dk.sdu.cloud.file.api.FileChecksum
import dk.sdu.cloud.file.api.StorageEvent
import dk.sdu.cloud.file.services.FileAttribute
import dk.sdu.cloud.file.services.FileRow

fun FileRow.toCreatedEvent(copyCausedBy: Boolean = false) = StorageEvent.CreatedOrRefreshed(
    id = inode,
    path = path,
    creator = creator,
    owner = owner,
    timestamp = timestamps.created,
    fileType = fileType,
    fileTimestamps = timestamps,
    size = size,
    isLink = isLink,
    linkTarget = if (isLink) linkTarget else null,
    linkTargetId = if (isLink) linkInode else null,
    sensitivityLevel = sensitivityLevel,

    annotations = emptySet(),
    checksum = FileChecksum("", ""),
    eventCausedBy = if (copyCausedBy) creator else null
)

/**
 * A mode powerful enough to convert a [FileRow] into a [StorageEvent]
 */
val STORAGE_EVENT_MODE = setOf(
    FileAttribute.FILE_TYPE,
    FileAttribute.INODE,
    FileAttribute.PATH,
    FileAttribute.TIMESTAMPS,
    FileAttribute.CREATOR,
    FileAttribute.SIZE,
    FileAttribute.IS_LINK,
    FileAttribute.LINK_TARGET,
    FileAttribute.LINK_INODE,
    FileAttribute.SENSITIVITY,
    FileAttribute.OWNER
)
