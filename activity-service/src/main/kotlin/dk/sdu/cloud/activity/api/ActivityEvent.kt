package dk.sdu.cloud.activity.api

import dk.sdu.cloud.file.api.AccessRight
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.PaginationRequest
import dk.sdu.cloud.service.WithPaginationRequest

@Suppress("EnumEntryName") // backwards-compatibility
enum class ActivityEventType {
    download,
    deleted,
    favorite,
    moved,
    copy,
    usedInApp,
    directoryCreated,
    reclassify,
    upload,
    updatedACL,
    sharedWith,
    allUsedInApp
}

sealed class ActivityEvent {
    // NOTE(Dan): Please consult the README before you add new entries here. This should only contain
    // events related to file activity

    // When adding new entries here, you will also need to add entries in:
    // ActivityEventDao

    abstract val timestamp: Long
    abstract val filePath: String
    abstract val username: String

    // TODO We cannot reliably track who uploaded a file (due to bulk uploads)

    data class Reclassify(
        override val username: String,
        override val timestamp: Long,
        override val filePath: String,
        val newSensitivity: String
    ) : ActivityEvent()

    data class DirectoryCreated(
        override val username: String,
        override val timestamp: Long,
        override val filePath: String
    ) : ActivityEvent()

    data class Download(
        override val username: String,
        override val timestamp: Long,
        override val filePath: String
    ) : ActivityEvent()

    data class Copy(
        override val username: String,
        override val timestamp: Long,
        override val filePath: String,
        val copyFilePath: String
    ) : ActivityEvent()

    data class Uploaded(
        override val username: String,
        override val timestamp: Long,
        override val filePath: String
    ) : ActivityEvent()

    data class RightsAndUser(
        val rights: Set<AccessRight>,
        val user: String
    )

    data class UpdatedAcl(
        override val username: String,
        override val timestamp: Long,
        override val filePath: String,
        val rightsAndUser: List<RightsAndUser>
    ) : ActivityEvent()

    data class UpdateProjectAcl(
        override val username: String,
        override val timestamp: Long,
        override val filePath: String,
        val project: String,
        val acl: List<ProjectAclEntry>
    ) : ActivityEvent()

    data class ProjectAclEntry(val group: String, val rights: Set<AccessRight>)

    data class Favorite(
        override val username: String,
        val isFavorite: Boolean,
        override val timestamp: Long,
        override val filePath: String
    ) : ActivityEvent()

    data class Moved(
        override val username: String,
        val newName: String,
        override val timestamp: Long,
        override val filePath: String
    ) : ActivityEvent()

    data class Deleted(
        override val username: String,
        override val timestamp: Long,
        override val filePath: String
    ) : ActivityEvent()

    data class SingleFileUsedByApplication(
        override val username: String, //used By
        override val timestamp: Long,
        override val filePath: String,
        val applicationName: String,
        val applicationVersion: String
    ) : ActivityEvent()

    data class AllFilesUsedByApplication(
        override val username: String, //used By
        override val timestamp: Long,
        override val filePath: String,
        val applicationName: String,
        val applicationVersion: String
    ) : ActivityEvent()

    data class SharedWith(
        override val username: String,
        override val timestamp: Long,
        override val filePath: String,
        val sharedWith: String,
        val status: Set<AccessRight>
    ) : ActivityEvent()
}

val ActivityEvent.type: ActivityEventType get() = when (this) {
    is ActivityEvent.Download -> ActivityEventType.download
    is ActivityEvent.Favorite -> ActivityEventType.favorite
    is ActivityEvent.Moved -> ActivityEventType.moved
    is ActivityEvent.Deleted -> ActivityEventType.deleted
    is ActivityEvent.SingleFileUsedByApplication -> ActivityEventType.usedInApp
    is ActivityEvent.DirectoryCreated -> ActivityEventType.directoryCreated
    is ActivityEvent.UpdatedAcl -> ActivityEventType.updatedACL
    is ActivityEvent.Uploaded -> ActivityEventType.upload
    is ActivityEvent.Reclassify -> ActivityEventType.reclassify
    is ActivityEvent.Copy -> ActivityEventType.copy
    is ActivityEvent.SharedWith -> ActivityEventType.sharedWith
    is ActivityEvent.AllFilesUsedByApplication -> ActivityEventType.allUsedInApp
    is ActivityEvent.UpdateProjectAcl -> ActivityEventType.updatedACL
}

data class ActivityForFrontend(
    val type: ActivityEventType,
    val timestamp: Long,
    val activityEvent: ActivityEvent
)

data class ListActivityByPathRequest(
    val path: String,
    override val itemsPerPage: Int?,
    override val page: Int?
) : WithPaginationRequest
typealias ListActivityByPathResponse = Page<ActivityForFrontend>
