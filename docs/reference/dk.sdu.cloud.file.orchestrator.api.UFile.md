[UCloud Developer Guide](/docs/developer-guide/README.md) / [Orchestration of Resources](/docs/developer-guide/orchestration/README.md) / [Storage](/docs/developer-guide/orchestration/storage/README.md) / [Files](/docs/developer-guide/orchestration/storage/files.md)

# `UFile`


[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_A [`UFile`](/docs/reference/dk.sdu.cloud.file.orchestrator.api.UFile.md)  is a resource for storing, retrieving and organizing data in UCloud_

```kotlin
data class UFile(
    val id: String,
    val specification: UFileSpecification,
    val createdAt: Long,
    val status: UFileStatus,
    val owner: ResourceOwner,
    val permissions: ResourcePermissions?,
    val updates: List<UFileUpdate>,
    val providerGeneratedId: String?,
)
```
A file in UCloud ([`UFile`](/docs/reference/dk.sdu.cloud.file.orchestrator.api.UFile.md)) closely follows the concept of a computer file you might already be familiar with.
The functionality of a file is mostly determined by its [`type`](/docs/reference/dk.sdu.cloud.file.orchestrator.api.UFileStatus.md). The two most important
types are the [`DIRECTORY`](/docs/reference/dk.sdu.cloud.file.orchestrator.api.FileType.md) and [`FILE`](/docs/reference/dk.sdu.cloud.file.orchestrator.api.FileType.md) types. A
[`DIRECTORY`](/docs/reference/dk.sdu.cloud.file.orchestrator.api.FileType.md) is a container of [`UFile`](/docs/reference/dk.sdu.cloud.file.orchestrator.api.UFile.md)s. A directory can itself contain more
directories, which leads to a natural tree-like structure. [`FILE`](/docs/reference/dk.sdu.cloud.file.orchestrator.api.FileType.md)s, also referred to as a
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

We have just covered two examples of system-level metadata, the [`id`](/docs/reference/dk.sdu.cloud.file.orchestrator.api.UFile.md) (path) and
[`type`](/docs/reference/dk.sdu.cloud.file.orchestrator.api.UFileStatus.md). UCloud additionally supports metadata such as general
[stats](/docs/reference/dk.sdu.cloud.file.orchestrator.api.UFileStatus.md) about the files, such as file sizes. All files have a set of
[`permissions`](/docs/reference/dk.sdu.cloud.file.orchestrator.api.UFile.md) associated with them, providers may optionally expose this information to
UCloud and the users.

User-defined metadata describe the contents of a file. All metadata is described by a template
([`FileMetadataTemplate`](/docs/reference/dk.sdu.cloud.file.orchestrator.api.FileMetadataTemplate.md)), this template defines a document structure for the metadata. User-defined metadata
can be used for a variety of purposes, such as: [Datacite metadata](https://schema.datacite.org/), sensitivity levels,
and other field specific metadata formats.

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>id</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code> A unique reference to a file
</summary>



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


</details>

<details>
<summary>
<code>specification</code>: <code><code><a href='#ufilespecification'>UFileSpecification</a></code></code>
</summary>





</details>

<details>
<summary>
<code>createdAt</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a></code></code> Timestamp referencing when the request for creation was received by UCloud
</summary>





</details>

<details>
<summary>
<code>status</code>: <code><code><a href='#ufilestatus'>UFileStatus</a></code></code> Holds the current status of the `Resource`
</summary>





</details>

<details>
<summary>
<code>owner</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.provider.api.ResourceOwner.md'>ResourceOwner</a></code></code> Contains information about the original creator of the `Resource` along with project association
</summary>





</details>

<details>
<summary>
<code>permissions</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.provider.api.ResourcePermissions.md'>ResourcePermissions</a>?</code></code> Permissions assigned to this resource
</summary>



A null value indicates that permissions are not supported by this resource type.


</details>

<details>
<summary>
<code>updates</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='#ufileupdate'>UFileUpdate</a>&gt;</code></code>
</summary>

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)




</details>

<details>
<summary>
<code>providerGeneratedId</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)




</details>



</details>


