package dk.sdu.cloud.file.orchestrator

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import dk.sdu.cloud.accounting.api.ProductReference
import dk.sdu.cloud.calls.UCloudApiDoc
import dk.sdu.cloud.provider.api.*

enum class FileIconHint {
    DIRECTORY_GENERIC,
    DIRECTORY_STAR,
    DIRECTORY_SHARES,
    DIRECTORY_TRASH,
    DIRECTORY_JOBS,

    FILE_GENERIC,
    FILE_CODE,
    FILE_IMAGE,
    FILE_TEXT,
    FILE_AUDIO,
    FILE_VIDEO,
    FILE_ARCHIVE,
    FILE_DOCUMENT,
    FILE_BINARY,
    FILE_PDF
}

enum class FileType {
    FILE,
    DIRECTORY,
    SOFT_LINK,

    @UCloudApiDoc("Indicates that there used to be a file with metadata here, but the file no longer exists")
    DANGLING_METADATA
}

enum class FilePermission {
    READ,
    WRITE,

    // Maybe? Would correspond to being admin in the project in our current system. Other systems
    // would likely refer to this as 'owner'
    ADMINISTRATOR,
}

data class UFile(
    val path: String,
    val type: FileType,
    val icon: FileIconHint,
    val stats: FileStats?,
    val permissions: FilePermissions?,
    val metadata: Map<String, List<FileMetadataOrDeleted>>?
)

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "type"
)
@JsonSubTypes(
    JsonSubTypes.Type(value = FileMetadataOrDeleted.Deleted::class, name = "deleted"),
    JsonSubTypes.Type(value = FileMetadataDocument::class, name = "metadata"),
)
sealed class FileMetadataOrDeleted {
    data class Deleted(
        val changeLog: String,
        val createdAt: Long,
    ) : FileMetadataOrDeleted()
}

data class FileMetadataDocument(
    val templateId: String,
    val document: Map<String, Any?>,
    val changeLog: String,
    val createdAt: Long,
) : FileMetadataOrDeleted()

// TODO Naming conflict with FilePermission
data class FilePermissions(
    @UCloudApiDoc("What can the user, who requested this data, do with this file?")
    val myself: List<FilePermission>?,

    @UCloudApiDoc("Information about what other users and entities can do with this file")
    val others: List<ResourceAclEntry<FilePermission>>?
)

data class FileStats(
    @UCloudApiDoc("The size of this file in bytes (Requires `includeSizes`)")
    val sizeInBytes: Long?,
    @UCloudApiDoc("The size of this file and any child (Requires `includeSizes`)")
    val sizeIncludingChildrenInBytes: Long?,

    @UCloudApiDoc("The modified at timestamp (Requires `includeTimestamps`)")
    val modifiedAt: Long?,
    @UCloudApiDoc("The created at timestamp (Requires `includeTimestamps`)")
    val createdAt: Long?,
    @UCloudApiDoc("The accessed at timestamp (Requires `includeTimestamps`)")
    val accessedAt: Long?,

    @UCloudApiDoc("The unix mode of a file (Requires `includeUnixInfo`")
    val unixMode: Int?,
    @UCloudApiDoc("The unix owner of a file as a UID (Requires `includeUnixInfo`)")
    val unixOwner: Int?,
    @UCloudApiDoc("The unix group of a file as a GID (Requires `includeUnixInfo`)")
    val unixGroup: Int?,
)

enum class WriteConflictPolicy {
    RENAME,
    REJECT,
    OVERWRITE
}

// This corresponds to a single 'home' directory
// This allows us to read billing information from this single 'home' directory
// This would also be able to replace the repository, since the ACL could replicate this
data class FileCollection(
    override val id: String, // corresponds to the path prefix
    override val specification: Spec,
    override val createdAt: Long,
    override val status: Status,
    override val updates: List<Update>,
    override val billing: ResourceBilling,
    override val owner: ResourceOwner,
    override val acl: List<ResourceAclEntry<FilePermission>>?
) : Resource<FilePermission> {
    data class Spec(
        val title: String,
        // TODO Define which type of product we are dealing with
        override val product: ProductReference,
    ) : ResourceSpecification

    data class Update(
        override val timestamp: Long,
        override val status: String?,
    ) : ResourceUpdate

    interface Status : ResourceStatus {
        val quota: Quota
    }

    interface Quota {
        val usedInBytes: Long
        val capacityInBytes: Long

        // NOTE: If this FS shares quota between several other FS then this is not simply "capacity - used"
        val availableInBytes: Long
    }
}

data class FSSupport(
    val product: ProductReference,
    val stats: FSProductStatsSupport = FSProductStatsSupport(),
    val collection: FSCollectionSupport = FSCollectionSupport(),
    val files: FSFileSupport = FSFileSupport(),
)

data class FSProductStatsSupport(
    val sizeInBytes: Boolean? = null,
    val sizeIncludingChildrenInBytes: Boolean? = null,

    val modifiedAt: Boolean? = null,
    val createdAt: Boolean? = null,
    val accessedAt: Boolean? = null,

    val unixPermissions: Boolean? = null,
    val unixOwner: Boolean? = null,
    val unixGroup: Boolean? = null,
)

data class FSCollectionSupport(
    val aclSupported: Boolean? = null,
    val aclModifiable: Boolean? = null,

    val usersCanCreate: Boolean? = null,
    val usersCanDelete: Boolean? = null,
    val usersCanRename: Boolean? = null,

    val searchSupported: Boolean? = null,
)

data class FSFileSupport(
    val aclSupported: Boolean? = null,
    val aclModifiable: Boolean? = null,

    // Nothing about metadata here because it is built-into UCloud as opposed to the file system

    val trashSupported: Boolean? = null,
)
