package dk.sdu.cloud.file.orchestrator.api

import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.accounting.api.ProductReference
import dk.sdu.cloud.accounting.api.providers.ProductSupport
import dk.sdu.cloud.accounting.api.providers.ResolvedSupport
import dk.sdu.cloud.calls.*
import dk.sdu.cloud.provider.api.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames
import kotlinx.serialization.json.JsonObject

@Serializable
@UCloudApiDoc("A hint to clients about which icon should be used in user-interfaces when representing a `UFile`", importance = 102)
enum class FileIconHint {
    @UCloudApiDoc("A directory containing 'starred' items")
    DIRECTORY_STAR,

    @UCloudApiDoc("A directory which contains items that are shared between users and projects")
    DIRECTORY_SHARES,

    @UCloudApiDoc("A directory which contains items that have been 'trashed'")
    DIRECTORY_TRASH,

    @UCloudApiDoc("A directory which contains items that are related to job results")
    DIRECTORY_JOBS,
}

@Serializable
@UCloudApiDoc("The type of a `UFile`", importance = 101)
enum class FileType {
    @UCloudApiDoc("A regular file")
    FILE,

    @UCloudApiDoc("A directory of files used for organization")
    DIRECTORY,

    @UCloudApiDoc("A soft symbolic link which points to a different file path")
    SOFT_LINK,

    @UCloudApiDoc("Indicates that there used to be a file with metadata here, but the file no longer exists")
    DANGLING_METADATA
}

typealias FilePermission = Permission

@Serializable
data class UFileIncludeFlags(
    override val includeOthers: Boolean = false,
    override val includeUpdates: Boolean = false,
    override val includeSupport: Boolean = false,
    override val includeProduct: Boolean = false,
    val includePermissions: Boolean? = null,
    val includeTimestamps: Boolean? = null,
    val includeSizes: Boolean? = null,
    val includeUnixInfo: Boolean? = null,
    val includeMetadata: Boolean? = null,
    val includeSyncStatus: Boolean? = null,

    override val filterCreatedBy: String? = null,
    override val filterCreatedAfter: Long? = null,
    override val filterCreatedBefore: Long? = null,
    override val filterProvider: String? = null,
    override val filterProductId: String? = null,
    override val filterProductCategory: String? = null,
    override val filterProviderIds: String? = null,
    val filterByFileExtension: String? = null,
    @UCloudApiDoc("Path filter")
    @JsonNames("filterPath")
    val path: String? = null,

    @UCloudApiDoc("""Determines if the request should succeed if the underlying system does not support this data.
This value is `true` by default """)
    val allowUnsupportedInclude: Boolean? = null,
    @UCloudApiDoc("Determines if dot files should be hidden from the result-set")
    val filterHiddenFiles: Boolean = false,
    override val filterIds: String? = null,
    override val hideProductId: String? = null,
    override val hideProductCategory: String? = null,
    override val hideProvider: String? = null,
) : ResourceIncludeFlags

@Serializable
@UCloudApiDoc("""
A $TYPE_REF UFile is a resource for storing, retrieving and organizing data in UCloud
    
A file in UCloud ($TYPE_REF UFile) closely follows the concept of a computer file you might already be familiar with.
The functionality of a file is mostly determined by its [`type`]($TYPE_REF_LINK UFileStatus). The two most important
types are the [`DIRECTORY`]($TYPE_REF_LINK FileType) and [`FILE`]($TYPE_REF_LINK FileType) types. A
[`DIRECTORY`]($TYPE_REF_LINK FileType) is a container of $TYPE_REF UFile s. A directory can itself contain more
directories, which leads to a natural tree-like structure. [`FILE`]($TYPE_REF_LINK FileType)s, also referred to as a
regular files, are data records which each contain a series of bytes.

All files in UCloud have a name associated with them. This name uniquely identifies them within their directory. All
files in UCloud belong to exactly one directory.

File operations must be able to reference the files on which they operate. In UCloud, these references are made through
the `id` property, also known as a path. Paths use the tree-like structure of files to reference a file, it does so by
declaring which directories to go through, starting at the top, to reach the file we are referencing. This information
is serialized as a textual string, where each step of the path is separated by forward-slash `/` (`U+002F`). The path
must start with a single forward-slash, which signifies the root of the file tree. UCloud never users 'relative' file
paths, which some systems use.

All files in UCloud additionally have metadata associated with them. For this we differentiate between system-level
metadata and user-defined metadata.

We have just covered two examples of system-level metadata, the [`id`]($TYPE_REF_LINK UFile) (path) and
[`type`]($TYPE_REF_LINK UFileStatus). UCloud additionally supports metadata such as general
[stats]($TYPE_REF_LINK UFileStatus) about the files, such as file sizes. All files have a set of
[`permissions`]($TYPE_REF_LINK UFile) associated with them, providers may optionally expose this information to
UCloud and the users.

User-defined metadata describe the contents of a file. All metadata is described by a template
($TYPE_REF FileMetadataTemplate), this template defines a document structure for the metadata. User-defined metadata
can be used for a variety of purposes, such as: [Datacite metadata](https://schema.datacite.org/), sensitivity levels,
and other field specific metadata formats.
""", importance = 500)
@UCloudApiOwnedBy(Files::class)
data class UFile(
    @UCloudApiDoc(
        """
A unique reference to a file

All files in UCloud have a `name` associated with them. This name uniquely identifies them within their directory. All
files in UCloud belong to exactly one directory. A `name` can be any textual string, for example: `thesis-42.docx`.
However, certain restrictions apply to file `name`s, see below for a concrete list of rules and recommendations.

The `extension` of a file is typically used as a hint to clients how to treat a specific file. For example, an extension
might indicate that the file contains a video of a specific format. In UCloud, the file's `extension` is derived from
its `name`. In UCloud, it is simply defined as the text immediately following, and not including, the last
period `.` (`U+002E`). The table below shows some examples of how UCloud determines the extension of a file:

| File `name` | Derived `extension` | Comment |
|-------------|---------------------|---------|
| `thesis-42.docx` | `docx` | - |
| `thesis-43-final.tar` | `tar` | - |
| `thesis-43-FINAL2.tar.gz` | `gz` | Note that UCloud does not recognize `tar` as being part of the extension |
| `thesis` |  | Empty string |
| `.ssh` | `ssh` | 'Hidden' files also have a surprising extension in UCloud | 

File operations must be able to reference the files on which they operate. In UCloud, these references are made through
the `path` property. Paths use the tree-like structure of files to reference a file, it does so by declaring which
directories to go through, starting at the top, to reach the file we are referencing. This information is serialized as
a textual string, where each step of the path is separated by forward-slash `/` (`U+002F`). The path must start with a
single forward-slash, which signifies the root of the file tree. UCloud never users 'relative' file paths, which some
systems use.

A path in UCloud is structured in such a way that they are unique across all providers and file systems. The figure
below shows how a UCloud path is structured, and how it can be mapped to an internal file-system path.

![](/backend/file-orchestrator-service/wiki/path.png)

__Figure:__ At the top, a UCloud path along with the components of it. At the bottom, an example of an internal,
provider specific, file-system path.

The figure shows how a UCloud path consists of four components:

1. The ['Provider ID'](/backend/provider-service/README.md) references the provider who owns and hosts the file
2. The product reference, this references the product that is hosting the `FileCollection`
3. The `FileCollection` ID references the ID of the internal file collection. These are controlled by the provider and
   match the different types of file-systems they have available. A single file collection typically maps to a specific
   folder on the provider's file-system.
4. The internal path, which tells the provider how to find the file within the collection. Providers can typically pass
   this as a one-to-one mapping.

__Rules of a file `name`:__

1. The `name` cannot be equal to `.` (commonly interpreted to mean the current directory)
2. The `name` cannot be equal to `..` (commonly interpreted to mean the parent directory)
3. The `name` cannot contain a forward-slash `/` (`U+002F`)
4. Names are strictly unicode

UCloud will normalize a path which contain `.` or `..` in a path's step. It is normalized according to the comments
mentioned in rule 1 and 2.

Note that all paths in unicode are strictly unicode (rule 4). __This is different from the unix standard.__ Unix file
names can contain _arbitrary_ binary data. (TODO determine how providers should handle this edge-case)

__Additionally regarding file `name`s, UCloud recommends to users the following:__

- Avoid the following file names:
    - Containing Windows reserved characters: `<`, `>`, `:`, `"`, `/`, `|`, `?`, `*`, `\`
    - Any of the reserved file names in Windows:
        - `AUX`
        - `COM1`, `COM2`, `COM3`, `COM4`, `COM5`, `COM6`, `COM7`, `COM8`, `COM9`
        - `CON`
        - `LPT1`, `LPT2`, `LPT3`, `LPT4`, `LPT5`, `LPT6`, `LPT7`, `LPT8`, `LPT9`
        - `NUL`
        - `PRN`
        - Any of the above followed by an extension
    - Avoid ASCII control characters (decimal value 0-31 both inclusive)
    - Avoid Unicode control characters (e.g. right-to-left override)
    - Avoid line breaks, paragraph separators and other unicode separators which is typically interpreted as a
      line-break
    - Avoid binary names

UCloud will attempt to reject these for file operations initiated through the client, but it cannot ensure that these
files do not appear regardless. This is due to the fact that the file systems are typically mounted directly by
user-controlled jobs.

__Rules of a file `path`:__

1. All paths must be absolute, that is they must start with `/`
2. UCloud will normalize all path 'steps' containing either `.` or `..`

__Additionally UCloud recommends to users the following regarding `path`s:__

- Avoid long paths:
    - Older versions of Unixes report `PATH_MAX` as 1024
    - Newer versions of Unixes report `PATH_MAX` as 4096
    - Older versions of Windows start failing above 256 characters
"""
    )
    override val id: String,
    override val specification: UFileSpecification,
    override val createdAt: Long,
    override val status: UFileStatus,
    override val owner: ResourceOwner,
    override val permissions: ResourcePermissions? = null
) : Resource<Product.Storage, FSSupport> {
    override val updates: List<UFileUpdate> = emptyList()
}

@UCloudApiExperimental(ExperimentalLevel.ALPHA)
@UCloudApiDoc("General system-level stats about a file", importance = 400)
@Serializable
data class UFileStatus(
    @UCloudApiDoc("Which type of file this is, see `FileType` for more information.")
    val type: FileType = FileType.FILE,
    @UCloudApiDoc("A hint to clients about which icon to display next to this file. See `FileIconHint` for details.")
    val icon: FileIconHint? = null,

    @UCloudApiDoc("The size of this file in bytes (Requires `includeSizes`)")
    val sizeInBytes: Long? = null,
    @UCloudApiDoc("The size of this file and any child (Requires `includeSizes`)")
    val sizeIncludingChildrenInBytes: Long? = null,

    @UCloudApiDoc("The modified at timestamp (Requires `includeTimestamps`)")
    val modifiedAt: Long? = null,
    @UCloudApiDoc("The accessed at timestamp (Requires `includeTimestamps`)")
    val accessedAt: Long? = null,

    @UCloudApiDoc("The unix mode of a file (Requires `includeUnixInfo`")
    val unixMode: Int? = null,
    @UCloudApiDoc("The unix owner of a file as a UID (Requires `includeUnixInfo`)")
    val unixOwner: Int? = null,
    @UCloudApiDoc("The unix group of a file as a GID (Requires `includeUnixInfo`)")
    val unixGroup: Int? = null,

    @UCloudApiDoc("User-defined metadata for this file. See `FileMetadataTemplate` for details.")
    val metadata: FileMetadataHistory? = null,

    @UCloudApiDoc("If the file is added to synchronization or not")
    val synced: Boolean? = null,

    override var resolvedSupport: ResolvedSupport<Product.Storage, FSSupport>? = null,
    override var resolvedProduct: Product.Storage? = null,
) : ResourceStatus<Product.Storage, FSSupport>

@UCloudApiExperimental(ExperimentalLevel.ALPHA)
@UCloudApiDoc("", importance = 98)
@Serializable
data class UFileSpecification(
    val collection: String,
    override val product: ProductReference,
) : ResourceSpecification

@Serializable
sealed class FileMetadataOrDeleted {
    abstract val id: String
    abstract val status: FileMetadataDocument.Status
    abstract val createdAt: Long
    abstract val createdBy: String

    @UCloudApiExperimental(ExperimentalLevel.ALPHA)
    @UCloudApiDoc("Indicates that the metadata document has been deleted is no longer in use", importance = 96)
    @Serializable
    @SerialName("deleted")
    data class Deleted(
        override val id: String,
        @UCloudApiDoc("Reason for this change")
        val changeLog: String,
        @UCloudApiDoc("Timestamp indicating when this change was made")
        override val createdAt: Long,
        @UCloudApiDoc("A reference to the user who made this change")
        override val createdBy: String,
        override val status: FileMetadataDocument.Status
    ) : FileMetadataOrDeleted()
}

@UCloudApiExperimental(ExperimentalLevel.ALPHA)
@UCloudApiDoc("A metadata document which conforms to a `FileMetadataTemplate`", importance = 97)
@Serializable
@SerialName("metadata")
@UCloudApiOwnedBy(FileMetadata::class)
data class FileMetadataDocument(
    override val id: String,
    val specification: Spec,
    override val createdAt: Long,
    override val status: Status,
    override val createdBy: String,
) : FileMetadataOrDeleted(){
    @Serializable
    @UCloudApiDoc("Specification of a FileMetadataDocument", importance = 96)
    @UCloudApiOwnedBy(FileMetadata::class)
    data class Spec(
        @UCloudApiDoc("The ID of the `FileMetadataTemplate` that this document conforms to")
        val templateId: String,
        @UCloudApiDoc("The version of the `FileMetadataTemplate` that this document conforms to")
        val version: String,
        @UCloudApiDoc("The document which fills out the template")
        val document: JsonObject,
        @UCloudApiDoc("Reason for this change")
        val changeLog: String,
    )

    @Serializable
    @UCloudApiDoc("The current status of a metadata document", importance = 95)
    @UCloudApiOwnedBy(FileMetadata::class)
    data class Status(
        val approval: ApprovalStatus,
    )

    @Serializable
    @UCloudApiDoc("The approval status of a metadata document", importance = 94)
    @UCloudApiOwnedBy(FileMetadata::class)
    sealed class ApprovalStatus {
        @Serializable
        @SerialName("approved")
        @UCloudApiDoc("The metadata change has been approved by an admin in the workspace", importance = 93)
        class Approved(val approvedBy: String) : ApprovalStatus()

        @Serializable
        @SerialName("pending")
        @UCloudApiDoc("The metadata document has not yet been approved", importance = 92)
        object Pending : ApprovalStatus()

        @Serializable
        @SerialName("rejected")
        @UCloudApiDoc("The metadata document has been rejected by an admin of the workspace", importance = 91)
        class Rejected(val rejectedBy: String) : ApprovalStatus()

        @Serializable
        @SerialName("not_required")
        @UCloudApiDoc("The metadata document does not require approval", importance = 90)
        object NotRequired : ApprovalStatus()
    }
}

@UCloudApiExperimental(ExperimentalLevel.ALPHA)
@UCloudApiDoc("A policy for how UCloud should handle potential naming conflicts for certain operations (e.g. copy)")
@Serializable
enum class WriteConflictPolicy {
    @UCloudApiDoc("UCloud should handle the conflict by renaming the file")
    RENAME,

    @UCloudApiDoc("UCloud should fail the request entirely")
    REJECT,

    @UCloudApiDoc("UCloud should replace the existing file")
    REPLACE,

    @UCloudApiDoc(
        """"Attempt to merge the results
        
This will result in the merging of folders. Concretely this means that _directory_ conflicts will be resolved by
re-using the existing directory. If there any file conflicts in the operation then this will act identical to `RENAME`.

Note: This mode is not supported for all operations.
""""
    )
    MERGE_RENAME
}

@UCloudApiExperimental(ExperimentalLevel.ALPHA)
@UCloudApiDoc("""
    Filter for member files. 
    
    A member files collection must use the following format to be recognized: "Member Files: ${"$"}username"
""")
enum class MemberFilesFilter {
    @UCloudApiDoc("Shows only the requesting user's personal member file along with all other collections")
    SHOW_ONLY_MINE,
    @UCloudApiDoc("Shows only the member file collections and hides all others")
    SHOW_ONLY_MEMBER_FILES,
    @UCloudApiDoc("Applies no filter and shows both normal collections and member files")
    DONT_FILTER_COLLECTIONS,
}

@Serializable
data class FileCollectionIncludeFlags(
    val filterMemberFiles: MemberFilesFilter? = null,
    override val includeOthers: Boolean = false,
    override val includeUpdates: Boolean = false,
    override val includeSupport: Boolean = false,
    override val includeProduct: Boolean = false,
    override val filterCreatedBy: String? = null,
    override val filterCreatedAfter: Long? = null,
    override val filterCreatedBefore: Long? = null,
    override val filterProvider: String? = null,
    override val filterProductId: String? = null,
    override val filterProductCategory: String? = null,
    override val filterProviderIds: String? = null,
    override val filterIds: String? = null,
    override val hideProductId: String? = null,
    override val hideProductCategory: String? = null,
    override val hideProvider: String? = null,
) : ResourceIncludeFlags

// This would also be able to replace the repository, since the ACL could replicate this
@UCloudApiExperimental(ExperimentalLevel.ALPHA)
@UCloudApiDoc(
    """
"""
)
@Serializable
data class FileCollection(
    override val id: String, // corresponds to the path prefix
    override val specification: Spec,
    override val createdAt: Long,
    override val status: Status,
    override val updates: List<Update>,
    override val owner: ResourceOwner,
    override val permissions: ResourcePermissions? = null,
    override val providerGeneratedId: String? = null
) : Resource<Product.Storage, FSSupport> {
    @Serializable
    data class Spec(
        val title: String,
        override val product: ProductReference,
    ) : ResourceSpecification {
        init {
            checkSingleLine(::title, title)
        }
    }

    @Serializable
    @UCloudApiOwnedBy(FileCollections::class)
    data class Update(
        override val timestamp: Long,
        override val status: String?,
    ) : ResourceUpdate

    @Serializable
    data class Status(
        override var resolvedSupport: ResolvedSupport<Product.Storage, FSSupport>? = null,
        override var resolvedProduct: Product.Storage? = null,
    ) : ResourceStatus<Product.Storage, FSSupport>
}

@Serializable
@UCloudApiOwnedBy(FileCollections::class)
data class FSSupport(
    override val product: ProductReference,
    val stats: FSProductStatsSupport = FSProductStatsSupport(),
    val collection: FSCollectionSupport = FSCollectionSupport(),
    val files: FSFileSupport = FSFileSupport(),
) : ProductSupport

@UCloudApiDoc("Declares which stats a given product supports")
@Serializable
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

@UCloudApiDoc("Declares which `FileCollection` operations are supported for a product")
@Serializable
data class FSCollectionSupport(
    val aclModifiable: Boolean? = null,

    val usersCanCreate: Boolean? = null,
    val usersCanDelete: Boolean? = null,
    val usersCanRename: Boolean? = null,
)

@UCloudApiDoc("Declares which file-level operations a product supports")
@Serializable
data class FSFileSupport(
    val aclModifiable: Boolean = false,

    // Nothing about metadata here because it is built-into UCloud as opposed to the file system

    val trashSupported: Boolean = false,

    val isReadOnly: Boolean = false,
)
