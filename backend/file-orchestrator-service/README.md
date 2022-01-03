# File Orchestration in UCloud

## Files

### `UFile`

<!-- typedoc:dk.sdu.cloud.file.orchestrator.api.UFile:includeOwnDoc=true:includeProps=true:includePropDoc=true -->
<!--<editor-fold desc="Generated documentation">-->
A `UFile` is a resource for storing, retrieving and organizing data in UCloud

| Property | Type | Description |
|----------|------|-------------|
| `path` | `String` | A unique reference to a file |
| `type` | `("FILE" or "DIRECTORY" or "SOFT_LINK" or "DANGLING_METADATA")` | Which type of file this is, see `FileType` for more information. |
| `icon` | `("DIRECTORY_STAR" or "DIRECTORY_SHARES" or "DIRECTORY_TRASH" or "DIRECTORY_JOBS")` | A hint to clients about which icon to display next to this file. See `FileIconHint` for details. |
| `stats` | `Stats` | General system-level stats about the file. See `UFile.Stats` for details. |
| `permissions` | `Permissions` | System-level permissions for this file. See `UFile.Permissions` for details. |
| `metadata` | `FileMetadataHistory` | User-defined metadata for this file. See `FileMetadataTemplate` for details. |

    
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


---
`path`: A unique reference to a file

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


---
`type`: Which type of file this is, see `FileType` for more information.

---
`icon`: A hint to clients about which icon to display next to this file. See `FileIconHint` for details.

---
`stats`: General system-level stats about the file. See `UFile.Stats` for details.

---
`permissions`: System-level permissions for this file. See `UFile.Permissions` for details.

---
`metadata`: User-defined metadata for this file. See `FileMetadataTemplate` for details.



<!--</editor-fold>-->
<!-- /typedoc -->

#### `Stats`

<!-- typedoc:dk.sdu.cloud.file.orchestrator.api.UFile.Stats:includeOwnDoc=true:includeProps=true -->
<!--<editor-fold desc="Generated documentation">-->
General system-level stats about a file

| Property | Type | Description |
|----------|------|-------------|
| `sizeInBytes` | `Long` | The size of this file in bytes (Requires `includeSizes`) |
| `sizeIncludingChildrenInBytes` | `Long` | The size of this file and any child (Requires `includeSizes`) |
| `modifiedAt` | `Long` | The modified at timestamp (Requires `includeTimestamps`) |
| `createdAt` | `Long` | The created at timestamp (Requires `includeTimestamps`) |
| `accessedAt` | `Long` | The accessed at timestamp (Requires `includeTimestamps`) |
| `unixMode` | `Int` | The unix mode of a file (Requires `includeUnixInfo` |
| `unixOwner` | `Int` | The unix owner of a file as a UID (Requires `includeUnixInfo`) |
| `unixGroup` | `Int` | The unix group of a file as a GID (Requires `includeUnixInfo`) |




<!--</editor-fold>-->
<!-- /typedoc -->

#### `Permissions`

<!-- typedoc:dk.sdu.cloud.file.orchestrator.api.UFile.Permissions:includeOwnDoc=true:includeProps=true -->
<!--<editor-fold desc="Generated documentation">-->
| Property | Type | Description |
|----------|------|-------------|
| `myself` | `Array<("READ" or "WRITE" or "ADMINISTRATOR")>` | What can the user, who requested this data, do with this file? |
| `others` | `Array<ResourceAclEntry_enum_READ_WRITE_ADMINISTRATOR)_ >` | Information about what other users and entities can do with this file |


<!--</editor-fold>-->
<!-- /typedoc -->

## File Collections

### `FileCollection`

<!-- typedoc:dk.sdu.cloud.file.orchestrator.api.FileCollection:includeOwnDoc=true:includeProps=true:includePropDoc=true -->
<!--<editor-fold desc="Generated documentation">-->
A `FileCollection` is an entrypoint to a user's files

| Property | Type | Description |
|----------|------|-------------|
| `id` | `String` | A unique identifier referencing the `Resource` |
| `specification` | `Spec` | No documentation |
| `createdAt` | `Long` | Timestamp referencing when the request for creation was received by UCloud |
| `status` | `Status` | Holds the current status of the `Resource` |
| `updates` | `Array<Update>` | Contains a list of updates from the provider as well as UCloud |
| `billing` | `Billing` | Contains information related to billing information for this `Resource` |
| `owner` | `SimpleResourceOwner` | Contains information about the original creator of the `Resource` along with project association |
| `acl` | `Array<ResourceAclEntry_enum_READ_WRITE_ADMINISTRATOR)_ >` | An ACL for this `Resource` |
| `permissions` | `ResourcePermissions?` | Permissions assigned to this resource |


This entrypoint allows the user to access all the files they have access to within a single project. It is important to
note that a file collection is not the same as a directory! Common real-world examples of a file collection is listed
below:

| Name              | Typical path                | Comment                                                     |
|-------------------|-----------------------------|-------------------------------------------------------------|
| Home directory    | `/home/$username/`     | The home folder is typically the main collection for a user |
| Work directory    | `/work/$projectId/`    | The project 'home' folder                                   |
| Scratch directory | `/scratch/$projectId/` | Temporary storage for a project                             |

The provider of storage manages a 'database' of these file collections and who they belong to. The file collections also
play an important role in accounting and resource management. A file collection can have a quota attached to it and
billing information is also stored in this object. Each file collection can be attached to a different product type, and
as a result, can have different billing information attached to it. This is, for example, useful if a storage provider
has both fast and slow tiers of storage, which is typically billed very differently.

All file collections additionally have a title. This title can be used for a user-friendly version of the folder. This
title does not have to be unique, and can with great benefit choose to not reference who it belongs to. For example,
if every user has exactly one home directory, then it would make sense to give this collection `"Home"` as its title.


---
`id`: A unique identifier referencing the `Resource`

The ID is unique across a provider for a single resource type.

---
`specification`: No documentation

---
`createdAt`: Timestamp referencing when the request for creation was received by UCloud

---
`status`: Holds the current status of the `Resource`

---
`updates`: Contains a list of updates from the provider as well as UCloud

Updates provide a way for both UCloud, and the provider to communicate to the user what is happening with their
resource.

---
`billing`: Contains information related to billing information for this `Resource`

---
`owner`: Contains information about the original creator of the `Resource` along with project association

---
`acl`: An ACL for this `Resource`

---
`permissions`: Permissions assigned to this resource

A null value indicates that permissions are not supported by this resource type.



<!--</editor-fold>-->
<!-- /typedoc -->

#### `Spec`

<!-- typedoc:dk.sdu.cloud.file.orchestrator.api.FileCollection.Spec:includeOwnDoc=true:includeProps=true -->
<!--<editor-fold desc="Generated documentation">-->
| Property | Type | Description |
|----------|------|-------------|
| `title` | `String` | No documentation |
| `product` | `ProductReference` | A reference to the product which backs this `Resource` |


<!--</editor-fold>-->
<!-- /typedoc -->

#### `Update`

<!-- typedoc:dk.sdu.cloud.file.orchestrator.api.FileCollection.Update:includeOwnDoc=true:includeProps=true -->
<!--<editor-fold desc="Generated documentation">-->
Describes an update to the `Resource`

| Property | Type | Description |
|----------|------|-------------|
| `timestamp` | `Long` | A timestamp referencing when UCloud received this update |
| `status` | `String` | A generic text message describing the current status of the `Resource` |


Updates can optionally be fetched for a `Resource`. The updates describe how the `Resource` changes state over time.
The current state of a `Resource` can typically be read from its `status` field. Thus, it is typically not needed to
use the full update history if you only wish to know the _current_ state of a `Resource`.

An update will typically contain information similar to the `status` field, for example:

- A state value. For example, a compute `Job` might be `RUNNING`.
- Change in key metrics.
- Bindings to related `Resource`s.



<!--</editor-fold>-->
<!-- /typedoc -->

#### `Status`

<!-- typedoc:dk.sdu.cloud.file.orchestrator.api.FileCollection.Status:includeOwnDoc=true:includeProps=true -->
<!--<editor-fold desc="Generated documentation">-->
Describes the current state of the `Resource`

| Property | Type | Description |
|----------|------|-------------|
| `quota` | `Quota` | No documentation |
| `support` | `FSSupport` | No documentation |


The contents of this field depends almost entirely on the specific `Resource` that this field is managing. Typically,
this will contain information such as:

- A state value. For example, a compute `Job` might be `RUNNING`
- Key metrics about the resource.
- Related resources. For example, certain `Resource`s are bound to another `Resource` in a mutually exclusive way, this
  should be listed in the `status` section.



<!--</editor-fold>-->
<!-- /typedoc -->

#### `Quota`

<!-- typedoc:dk.sdu.cloud.file.orchestrator.api.FileCollection.Quota:includeOwnDoc=true:includeProps=true -->
<!--<editor-fold desc="Generated documentation">-->
| Property | Type | Description |
|----------|------|-------------|
| `usedInBytes` | `Long?` | No documentation |
| `capacityInBytes` | `Long?` | No documentation |
| `availableInBytes` | `Long?` | No documentation |


<!--</editor-fold>-->
<!-- /typedoc -->

## Support

### `FSSupport`

<!-- typedoc:dk.sdu.cloud.file.orchestrator.api.FSSupport:includeOwnDoc=true:includeProps=true -->
<!--<editor-fold desc="Generated documentation">-->
| Property | Type | Description |
|----------|------|-------------|
| `product` | `ProductReference` | No documentation |
| `stats` | `FSProductStatsSupport?` | No documentation |
| `collection` | `FSCollectionSupport?` | No documentation |
| `files` | `FSFileSupport?` | No documentation |


<!--</editor-fold>-->
<!-- /typedoc -->

### `FSProductStatsSupport`

<!-- typedoc:dk.sdu.cloud.file.orchestrator.api.FSProductStatsSupport:includeOwnDoc=true:includeProps=true -->
<!--<editor-fold desc="Generated documentation">-->
Declares which stats a given product supports

| Property | Type | Description |
|----------|------|-------------|
| `sizeInBytes` | `Boolean?` | No documentation |
| `sizeIncludingChildrenInBytes` | `Boolean?` | No documentation |
| `modifiedAt` | `Boolean?` | No documentation |
| `createdAt` | `Boolean?` | No documentation |
| `accessedAt` | `Boolean?` | No documentation |
| `unixPermissions` | `Boolean?` | No documentation |
| `unixOwner` | `Boolean?` | No documentation |
| `unixGroup` | `Boolean?` | No documentation |




<!--</editor-fold>-->
<!-- /typedoc -->

### `FSCollectionSupport`

<!-- typedoc:dk.sdu.cloud.file.orchestrator.api.FSCollectionSupport:includeOwnDoc=true:includeProps=true -->
<!--<editor-fold desc="Generated documentation">-->
Declares which `FileCollection` operations are supported for a product

| Property | Type | Description |
|----------|------|-------------|
| `aclSupported` | `Boolean?` | No documentation |
| `aclModifiable` | `Boolean?` | No documentation |
| `usersCanCreate` | `Boolean?` | No documentation |
| `usersCanDelete` | `Boolean?` | No documentation |
| `usersCanRename` | `Boolean?` | No documentation |
| `searchSupported` | `Boolean?` | No documentation |




<!--</editor-fold>-->
<!-- /typedoc -->

### `FSFileSupport`

<!-- typedoc:dk.sdu.cloud.file.orchestrator.api.FSFileSupport:includeOwnDoc=true:includeProps=true -->
<!--<editor-fold desc="Generated documentation">-->
Declares which file-level operations a product supports

| Property | Type | Description |
|----------|------|-------------|
| `aclSupported` | `Boolean?` | No documentation |
| `aclModifiable` | `Boolean?` | No documentation |
| `trashSupported` | `Boolean?` | No documentation |
| `isReadOnly` | `Boolean?` | No documentation |




<!--</editor-fold>-->
<!-- /typedoc -->

## Metadata

### `FileMetadataTemplate`

<!-- typedoc:dk.sdu.cloud.file.orchestrator.api.FileMetadataTemplate:includeOwnDoc=true:includeProps=true -->
<!--<editor-fold desc="Generated documentation">-->
A `FileMetadataTemplate` allows users to attach user-defined metadata to any `UFile`

| Property | Type | Description |
|----------|------|-------------|
| `id` | `String` | A unique identifier referencing the `Resource` |
| `specification` | `Spec` | No documentation |
| `status` | `Status` | Holds the current status of the `Resource` |
| `updates` | `Array<Update>` | Contains a list of updates from the provider as well as UCloud |
| `owner` | `SimpleResourceOwner` | Contains information about the original creator of the `Resource` along with project association |
| `acl` | `Array<ResourceAclEntry_enum_READ_WRITE)_ >` | An ACL for this `Resource` |
| `createdAt` | `Long` | Timestamp referencing when the request for creation was received by UCloud |
| `public` | `Boolean` | No documentation |
| `permissions` | `ResourcePermissions?` | Permissions assigned to this resource |
| `billing` | `Free` | No documentation |




<!--</editor-fold>-->
<!-- /typedoc -->

#### `Spec`

<!-- typedoc:dk.sdu.cloud.file.orchestrator.api.FileMetadataTemplate.Spec:includeOwnDoc=true:includeProps=true -->
<!--<editor-fold desc="Generated documentation">-->
| Property | Type | Description |
|----------|------|-------------|
| `id` | `String` | The unique ID for this template |
| `title` | `String` | The title of this template. It does not have to be unique. |
| `version` | `String` | Version identifier for this version. It must be unique within a single template group. |
| `schema` | `Map<String, Any>` | JSON-Schema for this document |
| `inheritable` | `Boolean` | Makes this template inheritable by descendants of the file that the template is attached to |
| `requireApproval` | `Boolean` | If `true` then a user with `ADMINISTRATOR` rights must approve all changes to metadata |
| `description` | `String` | Description of this template. Markdown is supported. |
| `changeLog` | `String` | A description of the change since last version. Markdown is supported. |
| `namespaceType` | `("COLLABORATORS" or "PER_USER")` | Determines how this metadata template is namespaces |
| `uiSchema` | `Map<String, Any>?` | No documentation |
| `product` | `Any` | No documentation |


<!--</editor-fold>-->
<!-- /typedoc -->

#### `Status`

<!-- typedoc:dk.sdu.cloud.file.orchestrator.api.FileMetadataTemplate.Status:includeOwnDoc=true:includeProps=true -->
<!--<editor-fold desc="Generated documentation">-->
Describes the current state of the `Resource`

| Property | Type | Description |
|----------|------|-------------|
| `oldVersions` | `Array<String>` | No documentation |


The contents of this field depends almost entirely on the specific `Resource` that this field is managing. Typically,
this will contain information such as:

- A state value. For example, a compute `Job` might be `RUNNING`
- Key metrics about the resource.
- Related resources. For example, certain `Resource`s are bound to another `Resource` in a mutually exclusive way, this
  should be listed in the `status` section.



<!--</editor-fold>-->
<!-- /typedoc -->

#### `Update`

<!-- typedoc:dk.sdu.cloud.file.orchestrator.api.FileMetadataTemplate.Update:includeOwnDoc=true:includeProps=true -->
<!--<editor-fold desc="Generated documentation">-->
Describes an update to the `Resource`

| Property | Type | Description |
|----------|------|-------------|
| `timestamp` | `Long` | A timestamp referencing when UCloud received this update |
| `status` | `String` | A generic text message describing the current status of the `Resource` |


Updates can optionally be fetched for a `Resource`. The updates describe how the `Resource` changes state over time.
The current state of a `Resource` can typically be read from its `status` field. Thus, it is typically not needed to
use the full update history if you only wish to know the _current_ state of a `Resource`.

An update will typically contain information similar to the `status` field, for example:

- A state value. For example, a compute `Job` might be `RUNNING`.
- Change in key metrics.
- Bindings to related `Resource`s.



<!--</editor-fold>-->
<!-- /typedoc -->

### `FileMetadataDocument`

<!-- typedoc:dk.sdu.cloud.file.orchestrator.api.FileMetadataDocument:includeOwnDoc=true:includeProps=true -->
<!--<editor-fold desc="Generated documentation">-->
A metadata document which conforms to a `FileMetadataTemplate`

| Property | Type | Description |
|----------|------|-------------|
| `id` | `String` | A unique identifier referencing the `Resource` |
| `specification` | `Spec` | No documentation |
| `createdAt` | `Long` | No documentation |
| `status` | `Status` | No documentation |
| `updates` | `Array<ResourceUpdate>` | Contains a list of updates from the provider as well as UCloud |
| `owner` | `SimpleResourceOwner` | Contains information about the original creator of the `Resource` along with project association |
| `acl` | `Any` | No documentation |
| `billing` | `Free` | No documentation |
| `permissions` | `ResourcePermissions` | No documentation |
| `type` | `("metadata")` | No documentation |




<!--</editor-fold>-->
<!-- /typedoc -->

#### `Spec`

<!-- typedoc:dk.sdu.cloud.file.orchestrator.api.FileMetadataDocument.Spec:includeOwnDoc=true:includeProps=true -->
<!--<editor-fold desc="Generated documentation">-->
| Property | Type | Description |
|----------|------|-------------|
| `templateId` | `String` | The ID of the `FileMetadataTemplate` that this document conforms to |
| `document` | `Map<String, Any>` | The document which fills out the template |
| `changeLog` | `String` | Reason for this change |
| `product` | `Any` | No documentation |


<!--</editor-fold>-->
<!-- /typedoc -->

#### `Status`

<!-- typedoc:dk.sdu.cloud.file.orchestrator.api.FileMetadataDocument.Status:includeOwnDoc=true:includeProps=true -->
<!--<editor-fold desc="Generated documentation">-->
Describes the current state of the `Resource`

| Property | Type | Description |
|----------|------|-------------|
| `approval` | `ApprovalStatus` | No documentation |


The contents of this field depends almost entirely on the specific `Resource` that this field is managing. Typically,
this will contain information such as:

- A state value. For example, a compute `Job` might be `RUNNING`
- Key metrics about the resource.
- Related resources. For example, certain `Resource`s are bound to another `Resource` in a mutually exclusive way, this
  should be listed in the `status` section.



<!--</editor-fold>-->
<!-- /typedoc -->

### `FileMetadataDocument.ApprovalStatus`

#### `Approved`

<!-- typedoc:dk.sdu.cloud.file.orchestrator.api.FileMetadataDocument.ApprovalStatus.Approved:includeOwnDoc=true:includeProps=true -->
<!--<editor-fold desc="Generated documentation">-->
| Property | Type | Description |
|----------|------|-------------|
| `approvedBy` | `String` | No documentation |
| `type` | `("approved")` | No documentation |


<!--</editor-fold>-->
<!-- /typedoc -->

#### `Pending`

<!-- typedoc:dk.sdu.cloud.file.orchestrator.api.FileMetadataDocument.ApprovalStatus.Pending:includeOwnDoc=true:includeProps=true -->
<!--<editor-fold desc="Generated documentation">-->
| Property | Type | Description |
|----------|------|-------------|
| `type` | `("pending")` | No documentation |


<!--</editor-fold>-->
<!-- /typedoc -->

#### `Rejected`

<!-- typedoc:dk.sdu.cloud.file.orchestrator.api.FileMetadataDocument.ApprovalStatus.Rejected:includeOwnDoc=true:includeProps=true -->
<!--<editor-fold desc="Generated documentation">-->
| Property | Type | Description |
|----------|------|-------------|
| `rejectedBy` | `String` | No documentation |
| `type` | `("rejected")` | No documentation |


<!--</editor-fold>-->
<!-- /typedoc -->

#### `NotRequired`

<!-- typedoc:dk.sdu.cloud.file.orchestrator.api.FileMetadataDocument.ApprovalStatus.NotRequired:includeOwnDoc=true:includeProps=true -->
<!--<editor-fold desc="Generated documentation">-->
| Property | Type | Description |
|----------|------|-------------|
| `type` | `("not_required")` | No documentation |


<!--</editor-fold>-->
<!-- /typedoc -->
