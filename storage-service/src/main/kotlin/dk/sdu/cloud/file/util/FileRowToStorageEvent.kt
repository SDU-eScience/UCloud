package dk.sdu.cloud.file.util

import dk.sdu.cloud.file.api.SensitivityLevel
import dk.sdu.cloud.file.api.StorageEvent
import dk.sdu.cloud.file.api.StorageFile
import dk.sdu.cloud.file.services.FileAttribute
import dk.sdu.cloud.file.services.FileRow

fun FileRow.toCreatedEvent(copyCausedBy: Boolean = false): StorageEvent.CreatedOrRefreshed =
    StorageEvent.CreatedOrRefreshed(
        file = toStorageFileForEvent(),
        timestamp = System.currentTimeMillis(),
        eventCausedBy = if (copyCausedBy) creator else null
    )

fun FileRow.toDeletedEvent(copyCausedBy: Boolean = false): StorageEvent.Deleted =
    StorageEvent.Deleted(
        file = toStorageFileForEvent(),
        timestamp = System.currentTimeMillis(),
        eventCausedBy = if (copyCausedBy) creator else null
    )

fun FileRow.toSensitivityEvent(eventCausedBy: String?): StorageEvent.SensitivityUpdated =
    StorageEvent.SensitivityUpdated(
        file = toStorageFileForEvent(),
        timestamp = System.currentTimeMillis(),
        eventCausedBy = eventCausedBy
    )

fun FileRow.toMovedEvent(oldPath: String, copyCausedBy: Boolean = false): StorageEvent.Moved =
    StorageEvent.Moved(
        oldPath,
        file = toStorageFileForEvent(),
        timestamp = System.currentTimeMillis(),
        eventCausedBy = if (copyCausedBy) creator else null
    )

private fun FileRow.toStorageFileForEvent(): StorageFile = StorageFile(
    fileType = fileType,
    path = path,
    createdAt = timestamps.created,
    modifiedAt = timestamps.modified,
    ownerName = owner,
    creator = creator,
    size = size,
    acl = shares,
    sensitivityLevel = sensitivityLevel ?: SensitivityLevel.PRIVATE,
    fileId = inode,
    ownSensitivityLevel = sensitivityLevel
)

/**
 * A mode powerful enough to convert a [FileRow] into a [StorageEvent]
 */
val STORAGE_EVENT_MODE = setOf(
    FileAttribute.FILE_TYPE,
    FileAttribute.PATH,
    FileAttribute.TIMESTAMPS,
    FileAttribute.OWNER,
    FileAttribute.CREATOR,
    FileAttribute.SIZE,
    FileAttribute.SHARES,
    FileAttribute.SENSITIVITY,
    FileAttribute.INODE,
    FileAttribute.SENSITIVITY
)
