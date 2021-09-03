package dk.sdu.cloud.file.orchestrator.api

import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.accounting.api.ProductReference
import dk.sdu.cloud.accounting.api.providers.ProductSupport
import dk.sdu.cloud.accounting.api.providers.ResolvedSupport
import dk.sdu.cloud.calls.ExperimentalLevel
import dk.sdu.cloud.calls.UCloudApiDoc
import dk.sdu.cloud.calls.UCloudApiExperimental
import dk.sdu.cloud.provider.api.*
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames
import kotlinx.serialization.json.JsonObject

@Serializable
@UCloudApiDoc("A hint to clients about which icon should be used in user-interfaces when representing a `UFile`")
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
@UCloudApiDoc("The type of a `UFile`")
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

//@Serializable
// @UCloudApiDoc("Represents a permission that a user might have over a `UFile` or `FileCollection`")
typealias FilePermission = Permission/* {
    @UCloudApiDoc(
        """The user is allowed to read the contents of the file

- For regular files (`FILE`) this allows a user to read the data and metadata of the file.
- For directories (`DIRECTORY`) this allows the user to read metadata about the folder along with _listing_ the files
  of that directory
- For soft symbolic links (`SOFT_LINK`) this allows the user to read metadata about the link along with reading the
  pointer of this link. That is, the user is not necessarily allowed to read the file which the pointer points to.
- For dangling metadata (`DANGLING_METADATA`) this allows the user to read the metadata which is dangling

In addition, this will allow users to read the metadata of a file.
"""
    )
    READ,

    @UCloudApiDoc(
        """The user is allowed to write to this file

- For regular files (`FILE`) this allows a user to change the contents of a file, including deleting the file entirely
- For directories (`DIRECTORY`) this allows a user to change the contents of a folder. This includes creating and
  deleting files from a directory.
- For soft symbolic links (`SOFT_LINK`) this allows a user to change where the link points to
- For dangling metadata (`DANGLING_METADATA`) this allows the user to move the metadata to another file

In addition, this will allow users to change the metadata of a file. However, it will not allow the user to add new
metadata templates to the file.
"""
    )
    WRITE,

    @UCloudApiDoc(
        """The user is allowed to perform administrative actions to this file

For all files, this will allow the entity to change the permissions and ACL of a file.

This permission also allows the user to attach new metadata templates to a file. If a metadata template is inheritable,
see `FileMetadataTemplate.specification.inheritable`, then users will be able to change the value on descendants of this
file.
"""
    )
    ADMINISTRATOR,
}
*/

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

    override val filterCreatedBy: String? = null,
    override val filterCreatedAfter: Long? = null,
    override val filterCreatedBefore: Long? = null,
    override val filterProvider: String? = null,
    override val filterProductId: String? = null,
    override val filterProductCategory: String? = null,
    @UCloudApiDoc("Path filter")
    @JsonNames("filterPath")
    val path: String? = null,

    @UCloudApiDoc("""Determines if the request should succeed if the underlying system does not support this data.
This value is `true` by default """)
    val allowUnsupportedInclude: Boolean? = null,
) : ResourceIncludeFlags

@Serializable
data class UFile(
    @UCloudApiDoc("""A unique reference to a file

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
    override val billing = ResourceBilling.Free
    override val acl: List<ResourceAclEntry>? = null
    override val updates: List<ResourceUpdate> = emptyList()
}

@UCloudApiExperimental(ExperimentalLevel.ALPHA)
@UCloudApiDoc("General system-level stats about a file")
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

    override var resolvedSupport: ResolvedSupport<Product.Storage, FSSupport>? = null,
    override var resolvedProduct: Product.Storage? = null,
) : ResourceStatus<Product.Storage, FSSupport>

@UCloudApiExperimental(ExperimentalLevel.ALPHA)
@UCloudApiDoc(
    """A `UFile` is a resource for storing, retrieving and organizing data in UCloud
    
A file in UCloud (`UFile`) closely follows the concept of a computer file you might already be familiar with. The
functionality of a file is mostly determined by its `type`. The two most important types are the `DIRECTORY` and `FILE`
types. A `DIRECTORY` is a container of `UFile`s. A directory can itself contain more directories, which leads to a
natural tree-like structure. `FILE`s, also referred to as a regular files, are data records which each contain a series
of bytes.

All files in UCloud have a name associated with them. This name uniquely identifies them within their directory. All
files in UCloud belong to exactly one directory.

File operations must be able to reference the files on which they operate. In UCloud, these references are made through
the `path` property. Paths use the tree-like structure of files to reference a file, it does so by declaring which
directories to go through, starting at the top, to reach the file we are referencing. This information is serialized
as a textual string, where each step of the path is separated by forward-slash `/` (`U+002F`). The path must start with
a single forward-slash, which signifies the root of the file tree. UCloud never users 'relative' file paths, which some
systems use.

All files in UCloud additionally have metadata associated with them. For this we differentiate between system-level
metadata and user-defined metadata.

We have just covered two examples of system-level metadata, the `path` and `type`. UCloud additionally supports
metadata such as general `stats` about the files, such as file sizes. All files have a set of `permissions` associated
with them, providers may optionally expose this information to UCloud and the users.

User-defined metadata describe the contents of a file. All metadata is described by a template (`FileMetadataTemplate`),
this template defines a document structure for the metadata. User-defined metadata can be used for a variety of
purposes, such as: [Datacite metadata](https://schema.datacite.org/), sensitivity levels, and other field specific
metadata formats.
"""
)
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
    @UCloudApiDoc("Indicates that the metadata document has been deleted is no longer in use")
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
@UCloudApiDoc("A metadata document which conforms to a `FileMetadataTemplate`")
@Serializable
@SerialName("metadata")
data class FileMetadataDocument(
    override val id: String,
    val specification: Spec,
    override val createdAt: Long,
    override val status: Status,
    override val createdBy: String,
) : FileMetadataOrDeleted(){
    @Serializable
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
    data class Status(
        val approval: ApprovalStatus,
    )

    @Serializable
    sealed class ApprovalStatus {
        @Serializable
        @SerialName("approved")
        class Approved(val approvedBy: String) : ApprovalStatus()

        @Serializable
        @SerialName("pending")
        object Pending : ApprovalStatus()

        @Serializable
        @SerialName("rejected")
        class Rejected(val rejectedBy: String) : ApprovalStatus()

        @Serializable
        @SerialName("not_required")
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

@Serializable
data class FileCollectionIncludeFlags(
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
) : ResourceIncludeFlags

// This would also be able to replace the repository, since the ACL could replicate this
@UCloudApiExperimental(ExperimentalLevel.ALPHA)
@UCloudApiDoc(
    """A `FileCollection` is an entrypoint to a user's files

This entrypoint allows the user to access all the files they have access to within a single project. It is important to
note that a file collection is not the same as a directory! Common real-world examples of a file collection is listed
below:

| Name              | Typical path                | Comment                                                     |
|-------------------|-----------------------------|-------------------------------------------------------------|
| Home directory    | `/home/${"$"}username/`     | The home folder is typically the main collection for a user |
| Work directory    | `/work/${"$"}projectId/`    | The project 'home' folder                                   |
| Scratch directory | `/scratch/${"$"}projectId/` | Temporary storage for a project                             |

The provider of storage manages a 'database' of these file collections and who they belong to. The file collections also
play an important role in accounting and resource management. A file collection can have a quota attached to it and
billing information is also stored in this object. Each file collection can be attached to a different product type, and
as a result, can have different billing information attached to it. This is, for example, useful if a storage provider
has both fast and slow tiers of storage, which is typically billed very differently.

All file collections additionally have a title. This title can be used for a user-friendly version of the folder. This
title does not have to be unique, and can with great benefit choose to not reference who it belongs to. For example,
if every user has exactly one home directory, then it would make sense to give this collection `"Home"` as its title.
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
    override val acl: List<ResourceAclEntry>? = null
    override val billing = ResourceBilling.Free

    @Serializable
    data class Spec(
        val title: String,
        override val product: ProductReference,
    ) : ResourceSpecification {
        init {
            require(title.isNotEmpty())
        }
    }

    @Serializable
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

data class FSSupportResolved(
    val product: Product.Storage,
    val support: FSSupport,
)

@Serializable
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

    val searchSupported: Boolean? = null,
)

@UCloudApiDoc("Declares which file-level operations a product supports")
@Serializable
data class FSFileSupport(
    val aclModifiable: Boolean = false,

    // Nothing about metadata here because it is built-into UCloud as opposed to the file system

    val trashSupported: Boolean = false,

    val isReadOnly: Boolean = false,
)
