<p align='center'>
<a href='/docs/developer-guide/orchestration/storage/filecollections.md'>« Previous section</a>
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<a href='/docs/developer-guide/orchestration/storage/shares.md'>Next section »</a>
</p>


[UCloud Developer Guide](/docs/developer-guide/README.md) / [Orchestration of Resources](/docs/developer-guide/orchestration/README.md) / [Storage](/docs/developer-guide/orchestration/storage/README.md) / Files
# Files

[![API: Experimental/Beta](https://img.shields.io/static/v1?label=API&message=Experimental/Beta&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)

_Files in UCloud is a resource for storing, retrieving and organizing data in UCloud._

## Rationale

__📝 NOTE:__ This API follows the standard Resources API. We recommend that you have already read and understood the
concepts described [here](/docs/developer-guide/orchestration/resources.md).
        
---

    

The file-system of UCloud provide researchers with a way of storing large data-sets efficiently and securely. The
file-system is one of UCloud's core features and almost all other features, either directly or indirectly, interact
with it. For example:

- All interactions in UCloud (including files) are automatically [audited](/docs/developer-guide/core/monitoring/auditing.md)
- UCloud allows compute [`Jobs`](/docs/developer-guide/orchestration/compute/jobs.md) to consume UCloud files. Either
  through containerized workloads or virtual machines.
- Authorization and [project management](/docs/developer-guide/accounting-and-projects/projects/projects.md)
- Powerful [file metadata system](/docs/developer-guide/orchestration/storage/metadata/templates.md) for data management

A file in UCloud ([`UFile`](/docs/reference/dk.sdu.cloud.file.orchestrator.api.UFile.md)) closely follows the concept of a computer file you might already be familiar with.
The functionality of a file is mostly determined by its `type`. The two most important types are the
[`DIRECTORY`](/docs/reference/dk.sdu.cloud.file.orchestrator.api.FileType.md) and [`FILE`](/docs/reference/dk.sdu.cloud.file.orchestrator.api.FileType.md) types. A
[`DIRECTORY`](/docs/reference/dk.sdu.cloud.file.orchestrator.api.FileType.md) is a container of [`UFile`](/docs/reference/dk.sdu.cloud.file.orchestrator.api.UFile.md)s. A directory can itself contain more
directories, which leads to a natural tree-like structure. [`FILE`s](/docs/reference/dk.sdu.cloud.file.orchestrator.api.FileType.md), also referred to as a
regular files, are data records which each contain a series of bytes.

---

__📝 Provider Note:__ This is the API exposed to end-users. See the table below for other relevant APIs.

| End-User | Provider (Ingoing) | Control (Outgoing) |
|----------|--------------------|--------------------|
| [`Files`](/docs/developer-guide/orchestration/storage/files.md) | [`FilesProvider`](/docs/developer-guide/orchestration/storage/providers/files/ingoing.md) | [`FilesControl`](/docs/developer-guide/orchestration/storage/providers/files/outgoing.md) |

---

## Table of Contents
<details>
<summary>
<a href='#example-renaming-a-file'>1. Examples</a>
</summary>

<table><thead><tr>
<th>Description</th>
</tr></thread>
<tbody>
<tr><td><a href='#example-renaming-a-file'>Renaming a file</a></td></tr>
<tr><td><a href='#example-copying-a-file-to-itself'>Copying a file to itself</a></td></tr>
<tr><td><a href='#example-uploading-a-file'>Uploading a file</a></td></tr>
<tr><td><a href='#example-downloading-a-file'>Downloading a file</a></td></tr>
<tr><td><a href='#example-creating-a-folder'>Creating a folder</a></td></tr>
<tr><td><a href='#example-moving-multiple-files-to-trash'>Moving multiple files to trash</a></td></tr>
<tr><td><a href='#example-emptying-trash-folder'>Emptying trash folder</a></td></tr>
<tr><td><a href='#example-browsing-the-contents-of-a-folder'>Browsing the contents of a folder</a></td></tr>
<tr><td><a href='#example-retrieving-a-single-file'>Retrieving a single file</a></td></tr>
<tr><td><a href='#example-deleting-a-file-permanently'>Deleting a file permanently</a></td></tr>
<tr><td><a href='#example-retrieving-a-list-of-products-supported-by-accessible-providers'>Retrieving a list of products supported by accessible providers</a></td></tr>
</tbody></table>


</details>

<details>
<summary>
<a href='#remote-procedure-calls'>2. Remote Procedure Calls</a>
</summary>

<table><thead><tr>
<th>Name</th>
<th>Description</th>
</tr></thread>
<tbody>
<tr>
<td><a href='#browse'><code>browse</code></a></td>
<td>Browses the contents of a directory.</td>
</tr>
<tr>
<td><a href='#retrieve'><code>retrieve</code></a></td>
<td>Retrieves information about a single file.</td>
</tr>
<tr>
<td><a href='#retrieveproducts'><code>retrieveProducts</code></a></td>
<td>Retrieve product support for all accessible providers</td>
</tr>
<tr>
<td><a href='#search'><code>search</code></a></td>
<td>Searches the catalog of available resources</td>
</tr>
<tr>
<td><a href='#copy'><code>copy</code></a></td>
<td>Copies a file from one path to another</td>
</tr>
<tr>
<td><a href='#createdownload'><code>createDownload</code></a></td>
<td>Creates a download session between the user and the provider</td>
</tr>
<tr>
<td><a href='#createfolder'><code>createFolder</code></a></td>
<td>Creates one or more folders</td>
</tr>
<tr>
<td><a href='#createupload'><code>createUpload</code></a></td>
<td>Creates an upload session between the user and the provider</td>
</tr>
<tr>
<td><a href='#delete'><code>delete</code></a></td>
<td>Permanently deletes one or more files</td>
</tr>
<tr>
<td><a href='#emptytrash'><code>emptyTrash</code></a></td>
<td>Permanently deletes all files from the selected trash folder thereby emptying it</td>
</tr>
<tr>
<td><a href='#init'><code>init</code></a></td>
<td>Request (potential) initialization of resources</td>
</tr>
<tr>
<td><a href='#move'><code>move</code></a></td>
<td>Move a file from one path to another</td>
</tr>
<tr>
<td><a href='#trash'><code>trash</code></a></td>
<td>Moves a file to the trash</td>
</tr>
<tr>
<td><a href='#updateacl'><code>updateAcl</code></a></td>
<td>Updates the ACL of a single file.</td>
</tr>
</tbody></table>


</details>

<details>
<summary>
<a href='#data-models'>3. Data Models</a>
</summary>

<table><thead><tr>
<th>Name</th>
<th>Description</th>
</tr></thread>
<tbody>
<tr>
<td><a href='#ufile'><code>UFile</code></a></td>
<td>A [`UFile`](/docs/reference/dk.sdu.cloud.file.orchestrator.api.UFile.md)  is a resource for storing, retrieving and organizing data in UCloud</td>
</tr>
<tr>
<td><a href='#ufilestatus'><code>UFileStatus</code></a></td>
<td>General system-level stats about a file</td>
</tr>
<tr>
<td><a href='#fileiconhint'><code>FileIconHint</code></a></td>
<td>A hint to clients about which icon should be used in user-interfaces when representing a `UFile`</td>
</tr>
<tr>
<td><a href='#filetype'><code>FileType</code></a></td>
<td>The type of a `UFile`</td>
</tr>
<tr>
<td><a href='#ufilespecification'><code>UFileSpecification</code></a></td>
<td></td>
</tr>
<tr>
<td><a href='#filemetadataordeleted.deleted'><code>FileMetadataOrDeleted.Deleted</code></a></td>
<td>Indicates that the metadata document has been deleted is no longer in use</td>
</tr>
<tr>
<td><a href='#filemetadatahistory'><code>FileMetadataHistory</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#filemetadataordeleted'><code>FileMetadataOrDeleted</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#findbypath'><code>FindByPath</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#longrunningtask'><code>LongRunningTask</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#longrunningtask.complete'><code>LongRunningTask.Complete</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#longrunningtask.continuesinbackground'><code>LongRunningTask.ContinuesInBackground</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#ufileincludeflags'><code>UFileIncludeFlags</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#uploadprotocol'><code>UploadProtocol</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#writeconflictpolicy'><code>WriteConflictPolicy</code></a></td>
<td>A policy for how UCloud should handle potential naming conflicts for certain operations (e.g. copy)</td>
</tr>
<tr>
<td><a href='#filescopyrequestitem'><code>FilesCopyRequestItem</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#filescreatedownloadrequestitem'><code>FilesCreateDownloadRequestItem</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#filescreatefolderrequestitem'><code>FilesCreateFolderRequestItem</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#filescreateuploadrequestitem'><code>FilesCreateUploadRequestItem</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#filesmoverequestitem'><code>FilesMoveRequestItem</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#filescreatedownloadresponseitem'><code>FilesCreateDownloadResponseItem</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#filescreateuploadresponseitem'><code>FilesCreateUploadResponseItem</code></a></td>
<td><i>No description</i></td>
</tr>
</tbody></table>


</details>

## Example: Renaming a file
<table>
<tr><th>Frequency of use</th><td>Common</td></tr>
<tr><th>Trigger</th><td>User-initiated action, typically though the user-interface</td></tr>
<tr><th>Pre-conditions</th><td><ul>
<li>A file present at /123/my/file</li>
<li>The user has EDIT permissions on the file</li>
</ul></td></tr>
<tr><th>Post-conditions</th><td><ul>
<li>The file is moved to /123/my/new_file</li>
</ul></td></tr>
<tr>
<th>Actors</th>
<td><ul>
<li>An authenticated user (<code>user</code>)</li>
</ul></td>
</tr>
</table>
<details>
<summary>
<b>Communication Flow:</b> Kotlin
</summary>

```kotlin
Files.move.call(
    bulkRequestOf(FilesMoveRequestItem(
        conflictPolicy = WriteConflictPolicy.REJECT, 
        newId = "/123/my/new_file", 
        oldId = "/123/my/file", 
    )),
    user
).orThrow()

/*
BulkResponse(
    responses = listOf(LongRunningTask.Complete()), 
)
*/
```


</details>

<details>
<summary>
<b>Communication Flow:</b> TypeScript
</summary>

```typescript
// Authenticated as user
await callAPI(FilesApi.move(
    {
        "items": [
            {
                "oldId": "/123/my/file",
                "newId": "/123/my/new_file",
                "conflictPolicy": "REJECT"
            }
        ]
    }
);

/*
{
    "responses": [
        {
            "type": "complete"
        }
    ]
}
*/
```


</details>

<details>
<summary>
<b>Communication Flow:</b> Curl
</summary>

```bash
# ------------------------------------------------------------------------------------------------------
# $host is the UCloud instance to contact. Example: 'http://localhost:8080' or 'https://cloud.sdu.dk'
# $accessToken is a valid access-token issued by UCloud
# ------------------------------------------------------------------------------------------------------

# Authenticated as user
curl -XPOST -H "Authorization: Bearer $accessToken" -H "Content-Type: content-type: application/json; charset=utf-8" "$host/api/files/move" -d '{
    "items": [
        {
            "oldId": "/123/my/file",
            "newId": "/123/my/new_file",
            "conflictPolicy": "REJECT"
        }
    ]
}'


# {
#     "responses": [
#         {
#             "type": "complete"
#         }
#     ]
# }

```


</details>

<details open>
<summary>
<b>Communication Flow:</b> Visual
</summary>

![](/docs/diagrams/files_rename_file.png)

</details>


## Example: Copying a file to itself
<table>
<tr><th>Frequency of use</th><td>Common</td></tr>
<tr><th>Trigger</th><td>User-initiated action, typically through the user-interface</td></tr>
<tr><th>Pre-conditions</th><td><ul>
<li>A file present at /123/my/file</li>
<li>The user has EDIT permissions on the file</li>
<li>The provider supports RENAME for conflict policies</li>
</ul></td></tr>
<tr><th>Post-conditions</th><td><ul>
<li>A new file present at '/123/my/file (1)'</li>
</ul></td></tr>
<tr>
<th>Actors</th>
<td><ul>
<li>An authenticated user (<code>user</code>)</li>
</ul></td>
</tr>
</table>
<details>
<summary>
<b>Communication Flow:</b> Kotlin
</summary>

```kotlin
Files.copy.call(
    bulkRequestOf(FilesCopyRequestItem(
        conflictPolicy = WriteConflictPolicy.RENAME, 
        newId = "/123/my/file", 
        oldId = "/123/my/file", 
    )),
    user
).orThrow()

/*
BulkResponse(
    responses = listOf(LongRunningTask.Complete()), 
)
*/
```


</details>

<details>
<summary>
<b>Communication Flow:</b> TypeScript
</summary>

```typescript
// Authenticated as user
await callAPI(FilesApi.copy(
    {
        "items": [
            {
                "oldId": "/123/my/file",
                "newId": "/123/my/file",
                "conflictPolicy": "RENAME"
            }
        ]
    }
);

/*
{
    "responses": [
        {
            "type": "complete"
        }
    ]
}
*/
```


</details>

<details>
<summary>
<b>Communication Flow:</b> Curl
</summary>

```bash
# ------------------------------------------------------------------------------------------------------
# $host is the UCloud instance to contact. Example: 'http://localhost:8080' or 'https://cloud.sdu.dk'
# $accessToken is a valid access-token issued by UCloud
# ------------------------------------------------------------------------------------------------------

# Authenticated as user
curl -XPOST -H "Authorization: Bearer $accessToken" -H "Content-Type: content-type: application/json; charset=utf-8" "$host/api/files/copy" -d '{
    "items": [
        {
            "oldId": "/123/my/file",
            "newId": "/123/my/file",
            "conflictPolicy": "RENAME"
        }
    ]
}'


# {
#     "responses": [
#         {
#             "type": "complete"
#         }
#     ]
# }

```


</details>

<details open>
<summary>
<b>Communication Flow:</b> Visual
</summary>

![](/docs/diagrams/files_copy_file_to_self.png)

</details>


## Example: Uploading a file
<table>
<tr><th>Frequency of use</th><td>Common</td></tr>
<tr><th>Trigger</th><td>User initiated</td></tr>
<tr><th>Pre-conditions</th><td><ul>
<li>A folder at '/123/folder'</li>
<li>The user has EDIT permissions on the file</li>
<li>The provider supports the CHUNKED protocol</li>
</ul></td></tr>
<tr><th>Post-conditions</th><td><ul>
<li>A new file present at '/123/folder/file'</li>
</ul></td></tr>
<tr>
<th>Actors</th>
<td><ul>
<li>An authenticated user (<code>user</code>)</li>
</ul></td>
</tr>
</table>
<details>
<summary>
<b>Communication Flow:</b> Kotlin
</summary>

```kotlin
Files.createUpload.call(
    bulkRequestOf(FilesCreateUploadRequestItem(
        conflictPolicy = WriteConflictPolicy.REJECT, 
        id = "/123/folder", 
        supportedProtocols = listOf(UploadProtocol.CHUNKED), 
    )),
    user
).orThrow()

/*
BulkResponse(
    responses = listOf(FilesCreateUploadResponseItem(
        endpoint = "https://provider.example.com/ucloud/example-provider/chunked", 
        protocol = UploadProtocol.CHUNKED, 
        token = "f1460d47e583653f7723204e5ff3f50bad91a658", 
    )), 
)
*/

/* The user can now proceed to upload using the chunked protocol at the provided endpoint */

```


</details>

<details>
<summary>
<b>Communication Flow:</b> TypeScript
</summary>

```typescript
// Authenticated as user
await callAPI(FilesApi.createUpload(
    {
        "items": [
            {
                "id": "/123/folder",
                "supportedProtocols": [
                    "CHUNKED"
                ],
                "conflictPolicy": "REJECT"
            }
        ]
    }
);

/*
{
    "responses": [
        {
            "endpoint": "https://provider.example.com/ucloud/example-provider/chunked",
            "protocol": "CHUNKED",
            "token": "f1460d47e583653f7723204e5ff3f50bad91a658"
        }
    ]
}
*/

/* The user can now proceed to upload using the chunked protocol at the provided endpoint */

```


</details>

<details>
<summary>
<b>Communication Flow:</b> Curl
</summary>

```bash
# ------------------------------------------------------------------------------------------------------
# $host is the UCloud instance to contact. Example: 'http://localhost:8080' or 'https://cloud.sdu.dk'
# $accessToken is a valid access-token issued by UCloud
# ------------------------------------------------------------------------------------------------------

# Authenticated as user
curl -XPOST -H "Authorization: Bearer $accessToken" -H "Content-Type: content-type: application/json; charset=utf-8" "$host/api/files/upload" -d '{
    "items": [
        {
            "id": "/123/folder",
            "supportedProtocols": [
                "CHUNKED"
            ],
            "conflictPolicy": "REJECT"
        }
    ]
}'


# {
#     "responses": [
#         {
#             "endpoint": "https://provider.example.com/ucloud/example-provider/chunked",
#             "protocol": "CHUNKED",
#             "token": "f1460d47e583653f7723204e5ff3f50bad91a658"
#         }
#     ]
# }

# The user can now proceed to upload using the chunked protocol at the provided endpoint

```


</details>

<details open>
<summary>
<b>Communication Flow:</b> Visual
</summary>

![](/docs/diagrams/files_upload.png)

</details>


## Example: Downloading a file
<table>
<tr><th>Frequency of use</th><td>Common</td></tr>
<tr><th>Trigger</th><td>User initiated</td></tr>
<tr><th>Pre-conditions</th><td><ul>
<li>A file at '/123/folder/file</li>
<li>The user has READ permissions on the file</li>
</ul></td></tr>
<tr>
<th>Actors</th>
<td><ul>
<li>An authenticated user (<code>user</code>)</li>
</ul></td>
</tr>
</table>
<details>
<summary>
<b>Communication Flow:</b> Kotlin
</summary>

```kotlin
Files.createDownload.call(
    bulkRequestOf(FilesCreateDownloadRequestItem(
        id = "/123/folder/file", 
    )),
    user
).orThrow()

/*
BulkResponse(
    responses = listOf(FilesCreateDownloadResponseItem(
        endpoint = "https://provider.example.com/ucloud/example-provider/download?token=d293435e94734c91394f17bb56268d3161c7f069", 
    )), 
)
*/

/* The user can now download the file through normal HTTP(s) GET at the provided endpoint */

```


</details>

<details>
<summary>
<b>Communication Flow:</b> TypeScript
</summary>

```typescript
// Authenticated as user
await callAPI(FilesApi.createDownload(
    {
        "items": [
            {
                "id": "/123/folder/file"
            }
        ]
    }
);

/*
{
    "responses": [
        {
            "endpoint": "https://provider.example.com/ucloud/example-provider/download?token=d293435e94734c91394f17bb56268d3161c7f069"
        }
    ]
}
*/

/* The user can now download the file through normal HTTP(s) GET at the provided endpoint */

```


</details>

<details>
<summary>
<b>Communication Flow:</b> Curl
</summary>

```bash
# ------------------------------------------------------------------------------------------------------
# $host is the UCloud instance to contact. Example: 'http://localhost:8080' or 'https://cloud.sdu.dk'
# $accessToken is a valid access-token issued by UCloud
# ------------------------------------------------------------------------------------------------------

# Authenticated as user
curl -XPOST -H "Authorization: Bearer $accessToken" -H "Content-Type: content-type: application/json; charset=utf-8" "$host/api/files/download" -d '{
    "items": [
        {
            "id": "/123/folder/file"
        }
    ]
}'


# {
#     "responses": [
#         {
#             "endpoint": "https://provider.example.com/ucloud/example-provider/download?token=d293435e94734c91394f17bb56268d3161c7f069"
#         }
#     ]
# }

# The user can now download the file through normal HTTP(s) GET at the provided endpoint

```


</details>

<details open>
<summary>
<b>Communication Flow:</b> Visual
</summary>

![](/docs/diagrams/files_download.png)

</details>


## Example: Creating a folder
<table>
<tr><th>Frequency of use</th><td>Common</td></tr>
<tr><th>Trigger</th><td>User initiated</td></tr>
<tr><th>Pre-conditions</th><td><ul>
<li>A folder at '/123/folder</li>
<li>The user has EDIT permissions on the file</li>
</ul></td></tr>
<tr><th>Post-conditions</th><td><ul>
<li>A new file exists at '/123/folder/a</li>
</ul></td></tr>
<tr>
<th>Actors</th>
<td><ul>
<li>An authenticated user (<code>user</code>)</li>
</ul></td>
</tr>
</table>
<details>
<summary>
<b>Communication Flow:</b> Kotlin
</summary>

```kotlin
Files.createFolder.call(
    bulkRequestOf(FilesCreateFolderRequestItem(
        conflictPolicy = WriteConflictPolicy.REJECT, 
        id = "/123/folder/a", 
    )),
    user
).orThrow()

/*
BulkResponse(
    responses = listOf(LongRunningTask.Complete()), 
)
*/
```


</details>

<details>
<summary>
<b>Communication Flow:</b> TypeScript
</summary>

```typescript
// Authenticated as user
await callAPI(FilesApi.createFolder(
    {
        "items": [
            {
                "id": "/123/folder/a",
                "conflictPolicy": "REJECT"
            }
        ]
    }
);

/*
{
    "responses": [
        {
            "type": "complete"
        }
    ]
}
*/
```


</details>

<details>
<summary>
<b>Communication Flow:</b> Curl
</summary>

```bash
# ------------------------------------------------------------------------------------------------------
# $host is the UCloud instance to contact. Example: 'http://localhost:8080' or 'https://cloud.sdu.dk'
# $accessToken is a valid access-token issued by UCloud
# ------------------------------------------------------------------------------------------------------

# Authenticated as user
curl -XPOST -H "Authorization: Bearer $accessToken" -H "Content-Type: content-type: application/json; charset=utf-8" "$host/api/files/folder" -d '{
    "items": [
        {
            "id": "/123/folder/a",
            "conflictPolicy": "REJECT"
        }
    ]
}'


# {
#     "responses": [
#         {
#             "type": "complete"
#         }
#     ]
# }

```


</details>

<details open>
<summary>
<b>Communication Flow:</b> Visual
</summary>

![](/docs/diagrams/files_create_folder.png)

</details>


## Example: Moving multiple files to trash
<table>
<tr><th>Frequency of use</th><td>Common</td></tr>
<tr><th>Trigger</th><td>User initiated</td></tr>
<tr><th>Pre-conditions</th><td><ul>
<li>A folder at '/123/folder'</li>
<li>A file at '/123/file'</li>
<li>The user has EDIT permissions for all files involved</li>
</ul></td></tr>
<tr><th>Post-conditions</th><td><ul>
<li>The folder and all children are moved to the provider's trash folder</li>
<li>The file is moved to the provider's trash folder</li>
</ul></td></tr>
<tr>
<th>Actors</th>
<td><ul>
<li>An authenticated user (<code>user</code>)</li>
</ul></td>
</tr>
</table>
<details>
<summary>
<b>Communication Flow:</b> Kotlin
</summary>

```kotlin
Files.trash.call(
    bulkRequestOf(FindByPath(
        id = "/123/folder", 
    ), FindByPath(
        id = "/123/file", 
    )),
    user
).orThrow()

/*
BulkResponse(
    responses = listOf(LongRunningTask.Complete(), LongRunningTask.Complete()), 
)
*/
```


</details>

<details>
<summary>
<b>Communication Flow:</b> TypeScript
</summary>

```typescript
// Authenticated as user
await callAPI(FilesApi.trash(
    {
        "items": [
            {
                "id": "/123/folder"
            },
            {
                "id": "/123/file"
            }
        ]
    }
);

/*
{
    "responses": [
        {
            "type": "complete"
        },
        {
            "type": "complete"
        }
    ]
}
*/
```


</details>

<details>
<summary>
<b>Communication Flow:</b> Curl
</summary>

```bash
# ------------------------------------------------------------------------------------------------------
# $host is the UCloud instance to contact. Example: 'http://localhost:8080' or 'https://cloud.sdu.dk'
# $accessToken is a valid access-token issued by UCloud
# ------------------------------------------------------------------------------------------------------

# Authenticated as user
curl -XPOST -H "Authorization: Bearer $accessToken" -H "Content-Type: content-type: application/json; charset=utf-8" "$host/api/files/trash" -d '{
    "items": [
        {
            "id": "/123/folder"
        },
        {
            "id": "/123/file"
        }
    ]
}'


# {
#     "responses": [
#         {
#             "type": "complete"
#         },
#         {
#             "type": "complete"
#         }
#     ]
# }

```


</details>

<details open>
<summary>
<b>Communication Flow:</b> Visual
</summary>

![](/docs/diagrams/files_move_to_trash.png)

</details>


## Example: Emptying trash folder
<table>
<tr><th>Frequency of use</th><td>Common</td></tr>
<tr><th>Trigger</th><td>User initiated</td></tr>
<tr><th>Pre-conditions</th><td><ul>
<li>A trash folder located at /home/trash</li>
<li>The trash folder contains two files and a folder</li>
</ul></td></tr>
<tr><th>Post-conditions</th><td><ul>
<li>The folder and all children are removed from the trash folder</li>
<li>The files is removed from the trash folder</li>
</ul></td></tr>
<tr>
<th>Actors</th>
<td><ul>
<li>An authenticated user (<code>user</code>)</li>
</ul></td>
</tr>
</table>
<details>
<summary>
<b>Communication Flow:</b> Kotlin
</summary>

```kotlin
Files.trash.call(
    bulkRequestOf(FindByPath(
        id = "/home/trash", 
    )),
    user
).orThrow()

/*
BulkResponse(
    responses = listOf(LongRunningTask.Complete()), 
)
*/
```


</details>

<details>
<summary>
<b>Communication Flow:</b> TypeScript
</summary>

```typescript
// Authenticated as user
await callAPI(FilesApi.trash(
    {
        "items": [
            {
                "id": "/home/trash"
            }
        ]
    }
);

/*
{
    "responses": [
        {
            "type": "complete"
        }
    ]
}
*/
```


</details>

<details>
<summary>
<b>Communication Flow:</b> Curl
</summary>

```bash
# ------------------------------------------------------------------------------------------------------
# $host is the UCloud instance to contact. Example: 'http://localhost:8080' or 'https://cloud.sdu.dk'
# $accessToken is a valid access-token issued by UCloud
# ------------------------------------------------------------------------------------------------------

# Authenticated as user
curl -XPOST -H "Authorization: Bearer $accessToken" -H "Content-Type: content-type: application/json; charset=utf-8" "$host/api/files/trash" -d '{
    "items": [
        {
            "id": "/home/trash"
        }
    ]
}'


# {
#     "responses": [
#         {
#             "type": "complete"
#         }
#     ]
# }

```


</details>

<details open>
<summary>
<b>Communication Flow:</b> Visual
</summary>

![](/docs/diagrams/files_empty_trash_folder.png)

</details>


## Example: Browsing the contents of a folder
<table>
<tr><th>Frequency of use</th><td>Common</td></tr>
<tr><th>Trigger</th><td>User initiated</td></tr>
<tr><th>Pre-conditions</th><td><ul>
<li>A folder at '/123/folder</li>
<li>The user has READ permissions on the file</li>
</ul></td></tr>
<tr>
<th>Actors</th>
<td><ul>
<li>An authenticated user (<code>user</code>)</li>
</ul></td>
</tr>
</table>
<details>
<summary>
<b>Communication Flow:</b> Kotlin
</summary>

```kotlin
Files.browse.call(
    ResourceBrowseRequest(
        consistency = null, 
        flags = UFileIncludeFlags(
            allowUnsupportedInclude = null, 
            filterByFileExtension = null, 
            filterCreatedAfter = null, 
            filterCreatedBefore = null, 
            filterCreatedBy = null, 
            filterHiddenFiles = false, 
            filterIds = null, 
            filterProductCategory = null, 
            filterProductId = null, 
            filterProvider = null, 
            filterProviderIds = null, 
            hideProductCategory = null, 
            hideProductId = null, 
            hideProvider = null, 
            includeMetadata = null, 
            includeOthers = false, 
            includePermissions = null, 
            includeProduct = false, 
            includeSizes = null, 
            includeSupport = false, 
            includeSyncStatus = null, 
            includeTimestamps = true, 
            includeUnixInfo = null, 
            includeUpdates = false, 
            path = null, 
        ), 
        itemsPerPage = null, 
        itemsToSkip = null, 
        next = null, 
        sortBy = null, 
        sortDirection = SortDirection.ascending, 
    ),
    user
).orThrow()

/*
PageV2(
    items = listOf(UFile(
        createdAt = 1632903417165, 
        id = "/123/folder/file.txt", 
        owner = ResourceOwner(
            createdBy = "user", 
            project = "f63919cd-60d3-45d3-926b-0246dcc697fd", 
        ), 
        permissions = null, 
        specification = UFileSpecification(
            collection = "123", 
            product = ProductReference(
                category = "u1-cephfs", 
                id = "u1-cephfs", 
                provider = "ucloud", 
            ), 
        ), 
        status = UFileStatus(
            accessedAt = null, 
            icon = null, 
            metadata = null, 
            modifiedAt = 1632903417165, 
            resolvedProduct = null, 
            resolvedSupport = null, 
            sizeInBytes = null, 
            sizeIncludingChildrenInBytes = null, 
            synced = null, 
            type = FileType.FILE, 
            unixGroup = null, 
            unixMode = null, 
            unixOwner = null, 
        ), 
        updates = emptyList(), 
        providerGeneratedId = "/123/folder/file.txt", 
    )), 
    itemsPerPage = 50, 
    next = null, 
)
*/
```


</details>

<details>
<summary>
<b>Communication Flow:</b> TypeScript
</summary>

```typescript
// Authenticated as user
await callAPI(FilesApi.browse(
    {
        "flags": {
            "includeOthers": false,
            "includeUpdates": false,
            "includeSupport": false,
            "includeProduct": false,
            "includePermissions": null,
            "includeTimestamps": true,
            "includeSizes": null,
            "includeUnixInfo": null,
            "includeMetadata": null,
            "includeSyncStatus": null,
            "filterCreatedBy": null,
            "filterCreatedAfter": null,
            "filterCreatedBefore": null,
            "filterProvider": null,
            "filterProductId": null,
            "filterProductCategory": null,
            "filterProviderIds": null,
            "filterByFileExtension": null,
            "path": null,
            "allowUnsupportedInclude": null,
            "filterHiddenFiles": false,
            "filterIds": null,
            "hideProductId": null,
            "hideProductCategory": null,
            "hideProvider": null
        },
        "itemsPerPage": null,
        "next": null,
        "consistency": null,
        "itemsToSkip": null,
        "sortBy": null,
        "sortDirection": "ascending"
    }
);

/*
{
    "itemsPerPage": 50,
    "items": [
        {
            "id": "/123/folder/file.txt",
            "specification": {
                "collection": "123",
                "product": {
                    "id": "u1-cephfs",
                    "category": "u1-cephfs",
                    "provider": "ucloud"
                }
            },
            "createdAt": 1632903417165,
            "status": {
                "type": "FILE",
                "icon": null,
                "sizeInBytes": null,
                "sizeIncludingChildrenInBytes": null,
                "modifiedAt": 1632903417165,
                "accessedAt": null,
                "unixMode": null,
                "unixOwner": null,
                "unixGroup": null,
                "metadata": null,
                "synced": null,
                "resolvedSupport": null,
                "resolvedProduct": null
            },
            "owner": {
                "createdBy": "user",
                "project": "f63919cd-60d3-45d3-926b-0246dcc697fd"
            },
            "permissions": null,
            "updates": [
            ]
        }
    ],
    "next": null
}
*/
```


</details>

<details>
<summary>
<b>Communication Flow:</b> Curl
</summary>

```bash
# ------------------------------------------------------------------------------------------------------
# $host is the UCloud instance to contact. Example: 'http://localhost:8080' or 'https://cloud.sdu.dk'
# $accessToken is a valid access-token issued by UCloud
# ------------------------------------------------------------------------------------------------------

# Authenticated as user
curl -XGET -H "Authorization: Bearer $accessToken" "$host/api/files/browse?includeOthers=false&includeUpdates=false&includeSupport=false&includeProduct=false&includeTimestamps=true&filterHiddenFiles=false&sortDirection=ascending" 

# {
#     "itemsPerPage": 50,
#     "items": [
#         {
#             "id": "/123/folder/file.txt",
#             "specification": {
#                 "collection": "123",
#                 "product": {
#                     "id": "u1-cephfs",
#                     "category": "u1-cephfs",
#                     "provider": "ucloud"
#                 }
#             },
#             "createdAt": 1632903417165,
#             "status": {
#                 "type": "FILE",
#                 "icon": null,
#                 "sizeInBytes": null,
#                 "sizeIncludingChildrenInBytes": null,
#                 "modifiedAt": 1632903417165,
#                 "accessedAt": null,
#                 "unixMode": null,
#                 "unixOwner": null,
#                 "unixGroup": null,
#                 "metadata": null,
#                 "synced": null,
#                 "resolvedSupport": null,
#                 "resolvedProduct": null
#             },
#             "owner": {
#                 "createdBy": "user",
#                 "project": "f63919cd-60d3-45d3-926b-0246dcc697fd"
#             },
#             "permissions": null,
#             "updates": [
#             ]
#         }
#     ],
#     "next": null
# }

```


</details>

<details open>
<summary>
<b>Communication Flow:</b> Visual
</summary>

![](/docs/diagrams/files_browse.png)

</details>


## Example: Retrieving a single file
<table>
<tr><th>Frequency of use</th><td>Common</td></tr>
<tr><th>Trigger</th><td>User initiated</td></tr>
<tr><th>Pre-conditions</th><td><ul>
<li>A file at '/123/folder</li>
<li>The user has READ permissions on the file</li>
</ul></td></tr>
<tr>
<th>Actors</th>
<td><ul>
<li>An authenticated user (<code>user</code>)</li>
</ul></td>
</tr>
</table>
<details>
<summary>
<b>Communication Flow:</b> Kotlin
</summary>

```kotlin
Files.retrieve.call(
    ResourceRetrieveRequest(
        flags = UFileIncludeFlags(
            allowUnsupportedInclude = null, 
            filterByFileExtension = null, 
            filterCreatedAfter = null, 
            filterCreatedBefore = null, 
            filterCreatedBy = null, 
            filterHiddenFiles = false, 
            filterIds = null, 
            filterProductCategory = null, 
            filterProductId = null, 
            filterProvider = null, 
            filterProviderIds = null, 
            hideProductCategory = null, 
            hideProductId = null, 
            hideProvider = null, 
            includeMetadata = null, 
            includeOthers = false, 
            includePermissions = null, 
            includeProduct = false, 
            includeSizes = null, 
            includeSupport = false, 
            includeSyncStatus = null, 
            includeTimestamps = true, 
            includeUnixInfo = null, 
            includeUpdates = false, 
            path = null, 
        ), 
        id = "/123/folder", 
    ),
    user
).orThrow()

/*
UFile(
    createdAt = 1632903417165, 
    id = "/123/folder", 
    owner = ResourceOwner(
        createdBy = "user", 
        project = "f63919cd-60d3-45d3-926b-0246dcc697fd", 
    ), 
    permissions = null, 
    specification = UFileSpecification(
        collection = "123", 
        product = ProductReference(
            category = "u1-cephfs", 
            id = "u1-cephfs", 
            provider = "ucloud", 
        ), 
    ), 
    status = UFileStatus(
        accessedAt = null, 
        icon = null, 
        metadata = null, 
        modifiedAt = 1632903417165, 
        resolvedProduct = null, 
        resolvedSupport = null, 
        sizeInBytes = null, 
        sizeIncludingChildrenInBytes = null, 
        synced = null, 
        type = FileType.DIRECTORY, 
        unixGroup = null, 
        unixMode = null, 
        unixOwner = null, 
    ), 
    updates = emptyList(), 
    providerGeneratedId = "/123/folder", 
)
*/
```


</details>

<details>
<summary>
<b>Communication Flow:</b> TypeScript
</summary>

```typescript
// Authenticated as user
await callAPI(FilesApi.retrieve(
    {
        "flags": {
            "includeOthers": false,
            "includeUpdates": false,
            "includeSupport": false,
            "includeProduct": false,
            "includePermissions": null,
            "includeTimestamps": true,
            "includeSizes": null,
            "includeUnixInfo": null,
            "includeMetadata": null,
            "includeSyncStatus": null,
            "filterCreatedBy": null,
            "filterCreatedAfter": null,
            "filterCreatedBefore": null,
            "filterProvider": null,
            "filterProductId": null,
            "filterProductCategory": null,
            "filterProviderIds": null,
            "filterByFileExtension": null,
            "path": null,
            "allowUnsupportedInclude": null,
            "filterHiddenFiles": false,
            "filterIds": null,
            "hideProductId": null,
            "hideProductCategory": null,
            "hideProvider": null
        },
        "id": "/123/folder"
    }
);

/*
{
    "id": "/123/folder",
    "specification": {
        "collection": "123",
        "product": {
            "id": "u1-cephfs",
            "category": "u1-cephfs",
            "provider": "ucloud"
        }
    },
    "createdAt": 1632903417165,
    "status": {
        "type": "DIRECTORY",
        "icon": null,
        "sizeInBytes": null,
        "sizeIncludingChildrenInBytes": null,
        "modifiedAt": 1632903417165,
        "accessedAt": null,
        "unixMode": null,
        "unixOwner": null,
        "unixGroup": null,
        "metadata": null,
        "synced": null,
        "resolvedSupport": null,
        "resolvedProduct": null
    },
    "owner": {
        "createdBy": "user",
        "project": "f63919cd-60d3-45d3-926b-0246dcc697fd"
    },
    "permissions": null,
    "updates": [
    ]
}
*/
```


</details>

<details>
<summary>
<b>Communication Flow:</b> Curl
</summary>

```bash
# ------------------------------------------------------------------------------------------------------
# $host is the UCloud instance to contact. Example: 'http://localhost:8080' or 'https://cloud.sdu.dk'
# $accessToken is a valid access-token issued by UCloud
# ------------------------------------------------------------------------------------------------------

# Authenticated as user
curl -XGET -H "Authorization: Bearer $accessToken" "$host/api/files/retrieve?includeOthers=false&includeUpdates=false&includeSupport=false&includeProduct=false&includeTimestamps=true&filterHiddenFiles=false&id=/123/folder" 

# {
#     "id": "/123/folder",
#     "specification": {
#         "collection": "123",
#         "product": {
#             "id": "u1-cephfs",
#             "category": "u1-cephfs",
#             "provider": "ucloud"
#         }
#     },
#     "createdAt": 1632903417165,
#     "status": {
#         "type": "DIRECTORY",
#         "icon": null,
#         "sizeInBytes": null,
#         "sizeIncludingChildrenInBytes": null,
#         "modifiedAt": 1632903417165,
#         "accessedAt": null,
#         "unixMode": null,
#         "unixOwner": null,
#         "unixGroup": null,
#         "metadata": null,
#         "synced": null,
#         "resolvedSupport": null,
#         "resolvedProduct": null
#     },
#     "owner": {
#         "createdBy": "user",
#         "project": "f63919cd-60d3-45d3-926b-0246dcc697fd"
#     },
#     "permissions": null,
#     "updates": [
#     ]
# }

```


</details>

<details open>
<summary>
<b>Communication Flow:</b> Visual
</summary>

![](/docs/diagrams/files_retrieve.png)

</details>


## Example: Deleting a file permanently
<table>
<tr><th>Frequency of use</th><td>Common</td></tr>
<tr><th>Trigger</th><td>User initiated</td></tr>
<tr><th>Pre-conditions</th><td><ul>
<li>A file at '/123/folder</li>
<li>The user has EDIT permissions on the file</li>
</ul></td></tr>
<tr>
<th>Actors</th>
<td><ul>
<li>An authenticated user (<code>user</code>)</li>
</ul></td>
</tr>
</table>
<details>
<summary>
<b>Communication Flow:</b> Kotlin
</summary>

```kotlin
Files.delete.call(
    bulkRequestOf(FindByStringId(
        id = "/123/folder", 
    )),
    user
).orThrow()

/*
BulkResponse(
    responses = listOf(Unit), 
)
*/
```


</details>

<details>
<summary>
<b>Communication Flow:</b> TypeScript
</summary>

```typescript
// Authenticated as user
await callAPI(FilesApi.delete(
    {
        "items": [
            {
                "id": "/123/folder"
            }
        ]
    }
);

/*
{
    "responses": [
        {
        }
    ]
}
*/
```


</details>

<details>
<summary>
<b>Communication Flow:</b> Curl
</summary>

```bash
# ------------------------------------------------------------------------------------------------------
# $host is the UCloud instance to contact. Example: 'http://localhost:8080' or 'https://cloud.sdu.dk'
# $accessToken is a valid access-token issued by UCloud
# ------------------------------------------------------------------------------------------------------

# Authenticated as user
curl -XDELETE -H "Authorization: Bearer $accessToken" -H "Content-Type: content-type: application/json; charset=utf-8" "$host/api/files" -d '{
    "items": [
        {
            "id": "/123/folder"
        }
    ]
}'


# {
#     "responses": [
#         {
#         }
#     ]
# }

```


</details>

<details open>
<summary>
<b>Communication Flow:</b> Visual
</summary>

![](/docs/diagrams/files_delete.png)

</details>


## Example: Retrieving a list of products supported by accessible providers
<table>
<tr><th>Frequency of use</th><td>Common</td></tr>
<tr><th>Trigger</th><td>Typically triggered by a client to determine which operations are supported</td></tr>
<tr><th>Pre-conditions</th><td><ul>
<li>The user has access to the 'ucloud' provider</li>
</ul></td></tr>
<tr>
<th>Actors</th>
<td><ul>
<li>An authenticated user (<code>user</code>)</li>
</ul></td>
</tr>
</table>
<details>
<summary>
<b>Communication Flow:</b> Kotlin
</summary>

```kotlin
Files.retrieveProducts.call(
    Unit,
    user
).orThrow()

/*
SupportByProvider(
    productsByProvider = mapOf("ucloud" to listOf(ResolvedSupport(
        product = Product.Storage(
            category = ProductCategoryId(
                id = "u1-cephfs", 
                name = "u1-cephfs", 
                provider = "ucloud", 
            ), 
            chargeType = ChargeType.DIFFERENTIAL_QUOTA, 
            description = "Storage provided by UCloud", 
            freeToUse = false, 
            hiddenInGrantApplications = false, 
            name = "u1-cephfs", 
            pricePerUnit = 1, 
            priority = 0, 
            productType = ProductType.STORAGE, 
            unitOfPrice = ProductPriceUnit.PER_UNIT, 
            version = 1, 
            balance = null, 
            id = "u1-cephfs", 
        ), 
        support = FSSupport(
            collection = FSCollectionSupport(
                aclModifiable = false, 
                usersCanCreate = true, 
                usersCanDelete = true, 
                usersCanRename = true, 
            ), 
            files = FSFileSupport(
                aclModifiable = false, 
                isReadOnly = false, 
                trashSupported = true, 
            ), 
            product = ProductReference(
                category = "u1-cephfs", 
                id = "u1-cephfs", 
                provider = "ucloud", 
            ), 
            stats = FSProductStatsSupport(
                accessedAt = false, 
                createdAt = true, 
                modifiedAt = true, 
                sizeInBytes = true, 
                sizeIncludingChildrenInBytes = true, 
                unixGroup = true, 
                unixOwner = true, 
                unixPermissions = true, 
            ), 
        ), 
    ))), 
)
*/
```


</details>

<details>
<summary>
<b>Communication Flow:</b> TypeScript
</summary>

```typescript
// Authenticated as user
await callAPI(FilesApi.retrieveProducts(
    {
    }
);

/*
{
    "productsByProvider": {
        "ucloud": [
            {
                "product": {
                    "balance": null,
                    "name": "u1-cephfs",
                    "pricePerUnit": 1,
                    "category": {
                        "name": "u1-cephfs",
                        "provider": "ucloud"
                    },
                    "description": "Storage provided by UCloud",
                    "priority": 0,
                    "version": 1,
                    "freeToUse": false,
                    "unitOfPrice": "PER_UNIT",
                    "chargeType": "DIFFERENTIAL_QUOTA",
                    "hiddenInGrantApplications": false,
                    "productType": "STORAGE"
                },
                "support": {
                    "product": {
                        "id": "u1-cephfs",
                        "category": "u1-cephfs",
                        "provider": "ucloud"
                    },
                    "stats": {
                        "sizeInBytes": true,
                        "sizeIncludingChildrenInBytes": true,
                        "modifiedAt": true,
                        "createdAt": true,
                        "accessedAt": false,
                        "unixPermissions": true,
                        "unixOwner": true,
                        "unixGroup": true
                    },
                    "collection": {
                        "aclModifiable": false,
                        "usersCanCreate": true,
                        "usersCanDelete": true,
                        "usersCanRename": true
                    },
                    "files": {
                        "aclModifiable": false,
                        "trashSupported": true,
                        "isReadOnly": false
                    }
                }
            }
        ]
    }
}
*/
```


</details>

<details>
<summary>
<b>Communication Flow:</b> Curl
</summary>

```bash
# ------------------------------------------------------------------------------------------------------
# $host is the UCloud instance to contact. Example: 'http://localhost:8080' or 'https://cloud.sdu.dk'
# $accessToken is a valid access-token issued by UCloud
# ------------------------------------------------------------------------------------------------------

# Authenticated as user
curl -XGET -H "Authorization: Bearer $accessToken" "$host/api/files/retrieveProducts" 

# {
#     "productsByProvider": {
#         "ucloud": [
#             {
#                 "product": {
#                     "balance": null,
#                     "name": "u1-cephfs",
#                     "pricePerUnit": 1,
#                     "category": {
#                         "name": "u1-cephfs",
#                         "provider": "ucloud"
#                     },
#                     "description": "Storage provided by UCloud",
#                     "priority": 0,
#                     "version": 1,
#                     "freeToUse": false,
#                     "unitOfPrice": "PER_UNIT",
#                     "chargeType": "DIFFERENTIAL_QUOTA",
#                     "hiddenInGrantApplications": false,
#                     "productType": "STORAGE"
#                 },
#                 "support": {
#                     "product": {
#                         "id": "u1-cephfs",
#                         "category": "u1-cephfs",
#                         "provider": "ucloud"
#                     },
#                     "stats": {
#                         "sizeInBytes": true,
#                         "sizeIncludingChildrenInBytes": true,
#                         "modifiedAt": true,
#                         "createdAt": true,
#                         "accessedAt": false,
#                         "unixPermissions": true,
#                         "unixOwner": true,
#                         "unixGroup": true
#                     },
#                     "collection": {
#                         "aclModifiable": false,
#                         "usersCanCreate": true,
#                         "usersCanDelete": true,
#                         "usersCanRename": true
#                     },
#                     "files": {
#                         "aclModifiable": false,
#                         "trashSupported": true,
#                         "isReadOnly": false
#                     }
#                 }
#             }
#         ]
#     }
# }

```


</details>

<details open>
<summary>
<b>Communication Flow:</b> Visual
</summary>

![](/docs/diagrams/files_retrieve_products.png)

</details>



## Remote Procedure Calls

### `browse`

[![API: Experimental/Beta](https://img.shields.io/static/v1?label=API&message=Experimental/Beta&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Browses the contents of a directory._

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.accounting.api.providers.ResourceBrowseRequest.md'>ResourceBrowseRequest</a>&lt;<a href='#ufileincludeflags'>UFileIncludeFlags</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.PageV2.md'>PageV2</a>&lt;<a href='#ufile'>UFile</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|

The results will be returned using the standard pagination API of UCloud. Consistency is slightly
relaxed for this endpoint as it is typically hard to enforce for filesystems. Provider's are heavily
encouraged to try and find all files on the first request and return information about them in
subsequent requests. For example, a client might list all file names in the initial request and use
this list for all subsequent requests and retrieve additional information about the files. If the files
no longer exist then the provider should simply not include these results.


### `retrieve`

[![API: Experimental/Beta](https://img.shields.io/static/v1?label=API&message=Experimental/Beta&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Retrieves information about a single file._

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.accounting.api.providers.ResourceRetrieveRequest.md'>ResourceRetrieveRequest</a>&lt;<a href='#ufileincludeflags'>UFileIncludeFlags</a>&gt;</code>|<code><a href='#ufile'>UFile</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|

This file can be of any type. Clients can request additional information about the file using the
`include*` flags of the request. Note that not all providers support all information. Clients can query
this information using [`files.collections.browse`](/docs/reference/files.collections.browse.md)  or 
[`files.collections.retrieve`](/docs/reference/files.collections.retrieve.md)  with the `includeSupport` flag.


### `retrieveProducts`

[![API: Experimental/Beta](https://img.shields.io/static/v1?label=API&message=Experimental/Beta&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Retrieve product support for all accessible providers_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.accounting.api.providers.SupportByProvider.md'>SupportByProvider</a>&lt;<a href='/docs/reference/dk.sdu.cloud.accounting.api.Product.Storage.md'>Product.Storage</a>, <a href='/docs/reference/dk.sdu.cloud.file.orchestrator.api.FSSupport.md'>FSSupport</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|

This endpoint will determine all providers that which the authenticated user has access to, in
the current workspace. A user has access to a product, and thus a provider, if the product is
either free or if the user has been granted credits to use the product.

See also:

- [`Product`](/docs/reference/dk.sdu.cloud.accounting.api.Product.md) 
- [Grants](/docs/developer-guide/accounting-and-projects/grants/grants.md)


### `search`

[![API: Experimental/Beta](https://img.shields.io/static/v1?label=API&message=Experimental/Beta&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Searches the catalog of available resources_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.accounting.api.providers.ResourceSearchRequest.md'>ResourceSearchRequest</a>&lt;<a href='#ufileincludeflags'>UFileIncludeFlags</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.PageV2.md'>PageV2</a>&lt;<a href='#ufile'>UFile</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `copy`

[![API: Experimental/Beta](https://img.shields.io/static/v1?label=API&message=Experimental/Beta&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Copies a file from one path to another_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkRequest.md'>BulkRequest</a>&lt;<a href='#filescopyrequestitem'>FilesCopyRequestItem</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkResponse.md'>BulkResponse</a>&lt;<a href='#longrunningtask'>LongRunningTask</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|

The file can be of any type. If a directory is chosen then this will recursively copy all of its
children. This request might fail half-way through. This can potentially lead to a situation where
a partial file is left on the file-system. It is left to the user to clean up this file.

This operation handles conflicts depending on the supplied `WriteConflictPolicy`.

This is a long running task. As a result, this operation might respond with a status code which
indicate that it will continue in the background. Progress of this job can be followed using the
task API.

UCloud applied metadata will not be copied to the new file. File-system metadata (e.g.
extended-attributes) may be moved, however this is provider dependant.

__Errors:__

| Status Code | Description |
|-------------|-------------|
| `HttpStatusCode(value=400, description=Bad Request)` | The operation couldn't be completed because of the write conflict policy |
| `HttpStatusCode(value=404, description=Not Found)` | Either the oldPath or newPath exists or you lack permissions |
| `HttpStatusCode(value=403, description=Forbidden)` | You lack permissions to perform this operation |

__Examples:__

| Example |
|---------|
| [Example of duplicating a file](/docs/reference/files_copy_file_to_self.md) |


### `createDownload`

[![API: Experimental/Beta](https://img.shields.io/static/v1?label=API&message=Experimental/Beta&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Creates a download session between the user and the provider_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkRequest.md'>BulkRequest</a>&lt;<a href='#filescreatedownloadrequestitem'>FilesCreateDownloadRequestItem</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkResponse.md'>BulkResponse</a>&lt;<a href='#filescreatedownloadresponseitem'>FilesCreateDownloadResponseItem</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|

The returned endpoint will respond with a download to the user.

__Errors:__

| Status Code | Description |
|-------------|-------------|
| `HttpStatusCode(value=404, description=Not Found)` | Either the oldPath or newPath exists or you lack permissions |
| `HttpStatusCode(value=403, description=Forbidden)` | You lack permissions to perform this operation |

__Examples:__

| Example |
|---------|
| [Downloading a file](/docs/reference/files_download.md) |


### `createFolder`

[![API: Experimental/Beta](https://img.shields.io/static/v1?label=API&message=Experimental/Beta&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Creates one or more folders_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkRequest.md'>BulkRequest</a>&lt;<a href='#filescreatefolderrequestitem'>FilesCreateFolderRequestItem</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkResponse.md'>BulkResponse</a>&lt;<a href='#longrunningtask'>LongRunningTask</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|

This folder will automatically create parent directories if needed. This request may fail half-way
through and leave the file-system in an inconsistent state. It is up to the user to clean this up.

__Errors:__

| Status Code | Description |
|-------------|-------------|
| `HttpStatusCode(value=404, description=Not Found)` | Either the oldPath or newPath exists or you lack permissions |
| `HttpStatusCode(value=403, description=Forbidden)` | You lack permissions to perform this operation |

__Examples:__

| Example |
|---------|
| [Creating a folder](/docs/reference/files_create_folder.md) |


### `createUpload`

[![API: Experimental/Beta](https://img.shields.io/static/v1?label=API&message=Experimental/Beta&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Creates an upload session between the user and the provider_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkRequest.md'>BulkRequest</a>&lt;<a href='#filescreateuploadrequestitem'>FilesCreateUploadRequestItem</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkResponse.md'>BulkResponse</a>&lt;<a href='#filescreateuploadresponseitem'>FilesCreateUploadResponseItem</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|

The returned endpoint will accept an upload from the user which will create a file at a location
specified in this request.

__Errors:__

| Status Code | Description |
|-------------|-------------|
| `HttpStatusCode(value=404, description=Not Found)` | Either the oldPath or newPath exists or you lack permissions |
| `HttpStatusCode(value=403, description=Forbidden)` | You lack permissions to perform this operation |

__Examples:__

| Example |
|---------|
| [Uploading a file with the chunked protocol](/docs/reference/files_upload.md) |


### `delete`

[![API: Experimental/Beta](https://img.shields.io/static/v1?label=API&message=Experimental/Beta&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Permanently deletes one or more files_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkRequest.md'>BulkRequest</a>&lt;<a href='/docs/reference/dk.sdu.cloud.FindByStringId.md'>FindByStringId</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkResponse.md'>BulkResponse</a>&lt;<a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|

This call will recursively delete files if needed. It is possible that a provider might fail to
completely delete the entire sub-tree. This can, for example, happen because of a crash or because the
file-system is unable to delete a given file. This will lead the file-system in an inconsistent state.
It is not guaranteed that the provider will be able to detect this error scenario. A client of the
API can check if the file has been deleted by calling `retrieve` on the file.


### `emptyTrash`

[![API: Experimental/Beta](https://img.shields.io/static/v1?label=API&message=Experimental/Beta&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Permanently deletes all files from the selected trash folder thereby emptying it_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkRequest.md'>BulkRequest</a>&lt;<a href='#findbypath'>FindByPath</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkResponse.md'>BulkResponse</a>&lt;<a href='#longrunningtask'>LongRunningTask</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|

This operation acts as a permanent delete for users. Users will NOT be able to restore the file 
later, if needed. 

Not all providers supports this endpoint. You can query [`files.collections.browse`](/docs/reference/files.collections.browse.md) 
or [`files.collections.retrieve`](/docs/reference/files.collections.retrieve.md)  with the `includeSupport` flag.

This is a long running task. As a result, this operation might respond with a status code which indicate
that it will continue in the background. Progress of this job can be followed using the task API.

__Errors:__

| Status Code | Description |
|-------------|-------------|
| `HttpStatusCode(value=404, description=Not Found)` | Either the oldPath or newPath exists or you lack permissions |
| `HttpStatusCode(value=403, description=Forbidden)` | You lack permissions to perform this operation |
| `HttpStatusCode(value=400, description=Bad Request)` | This operation is not supported by the provider |

__Examples:__

| Example |
|---------|
| [Moving files to trash](/docs/reference/files_empty_trash_folder.md) |


### `init`

[![API: Experimental/Beta](https://img.shields.io/static/v1?label=API&message=Experimental/Beta&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Request (potential) initialization of resources_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a></code>|<code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|

This request is sent by the client, if the client believes that initialization of resources 
might be needed. NOTE: This request might be sent even if initialization has already taken 
place. UCloud/Core does not check if initialization has already taken place, it simply validates
the request.


### `move`

[![API: Experimental/Beta](https://img.shields.io/static/v1?label=API&message=Experimental/Beta&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Move a file from one path to another_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkRequest.md'>BulkRequest</a>&lt;<a href='#filesmoverequestitem'>FilesMoveRequestItem</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkResponse.md'>BulkResponse</a>&lt;<a href='#longrunningtask'>LongRunningTask</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|

The file can be of any type. This request is also used for 'renames' of a file. This is simply
considered a move within a single directory. This operation handles conflicts depending on the supplied
`WriteConflictPolicy`.

This is a long running task. As a result, this operation might respond with a status code which indicate
that it will continue in the background. Progress of this job can be followed using the task API.

__Errors:__

| Status Code | Description |
|-------------|-------------|
| `HttpStatusCode(value=400, description=Bad Request)` | The operation couldn't be completed because of the write conflict policy |
| `HttpStatusCode(value=404, description=Not Found)` | Either the oldPath or newPath exists or you lack permissions |
| `HttpStatusCode(value=403, description=Forbidden)` | You lack permissions to perform this operation |

__Examples:__

| Example |
|---------|
| [Example of using `move` to rename a file](/docs/reference/files_rename_file.md) |


### `trash`

[![API: Experimental/Beta](https://img.shields.io/static/v1?label=API&message=Experimental/Beta&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Moves a file to the trash_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkRequest.md'>BulkRequest</a>&lt;<a href='#findbypath'>FindByPath</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkResponse.md'>BulkResponse</a>&lt;<a href='#longrunningtask'>LongRunningTask</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|

This operation acts as a non-permanent delete for users. Users will be able to restore the file from
trash later, if needed. It is up to the provider to determine if the trash should be automatically
deleted and where this trash should be stored.

Not all providers supports this endpoint. You can query [`files.collections.browse`](/docs/reference/files.collections.browse.md) 
or [`files.collections.retrieve`](/docs/reference/files.collections.retrieve.md)  with the `includeSupport` flag.

This is a long running task. As a result, this operation might respond with a status code which indicate
that it will continue in the background. Progress of this job can be followed using the task API.

__Errors:__

| Status Code | Description |
|-------------|-------------|
| `HttpStatusCode(value=404, description=Not Found)` | Either the oldPath or newPath exists or you lack permissions |
| `HttpStatusCode(value=403, description=Forbidden)` | You lack permissions to perform this operation |
| `HttpStatusCode(value=400, description=Bad Request)` | This operation is not supported by the provider |

__Examples:__

| Example |
|---------|
| [Moving files to trash](/docs/reference/files_move_to_trash.md) |


### `updateAcl`

[![API: Experimental/Beta](https://img.shields.io/static/v1?label=API&message=Experimental/Beta&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Updates the ACL of a single file._

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkRequest.md'>BulkRequest</a>&lt;<a href='/docs/reference/dk.sdu.cloud.provider.api.UpdatedAcl.md'>UpdatedAcl</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkResponse.md'>BulkResponse</a>&lt;<a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|

---

__⚠️ WARNING:__ No providers currently support this API. Instead use the
[`files.collections.updateAcl`](/docs/reference/files.collections.updateAcl.md)  endpoint.

---



## Data Models

### `UFile`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


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
<code>updates</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='/docs/reference/dk.sdu.cloud.file.orchestrator.api.UFileUpdate.md'>UFileUpdate</a>&gt;</code></code>
</summary>





</details>

<details>
<summary>
<code>providerGeneratedId</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>



</details>



---

### `UFileStatus`

[![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_General system-level stats about a file_

```kotlin
data class UFileStatus(
    val type: FileType?,
    val icon: FileIconHint?,
    val sizeInBytes: Long?,
    val sizeIncludingChildrenInBytes: Long?,
    val modifiedAt: Long?,
    val accessedAt: Long?,
    val unixMode: Int?,
    val unixOwner: Int?,
    val unixGroup: Int?,
    val metadata: FileMetadataHistory?,
    val synced: Boolean?,
    val resolvedSupport: ResolvedSupport<Product.Storage, FSSupport>?,
    val resolvedProduct: Product.Storage?,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>type</code>: <code><code><a href='#filetype'>FileType</a>?</code></code> Which type of file this is, see `FileType` for more information.
</summary>





</details>

<details>
<summary>
<code>icon</code>: <code><code><a href='#fileiconhint'>FileIconHint</a>?</code></code> A hint to clients about which icon to display next to this file. See `FileIconHint` for details.
</summary>





</details>

<details>
<summary>
<code>sizeInBytes</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a>?</code></code> The size of this file in bytes (Requires `includeSizes`)
</summary>





</details>

<details>
<summary>
<code>sizeIncludingChildrenInBytes</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a>?</code></code> The size of this file and any child (Requires `includeSizes`)
</summary>





</details>

<details>
<summary>
<code>modifiedAt</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a>?</code></code> The modified at timestamp (Requires `includeTimestamps`)
</summary>





</details>

<details>
<summary>
<code>accessedAt</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a>?</code></code> The accessed at timestamp (Requires `includeTimestamps`)
</summary>





</details>

<details>
<summary>
<code>unixMode</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/'>Int</a>?</code></code> The unix mode of a file (Requires `includeUnixInfo`
</summary>





</details>

<details>
<summary>
<code>unixOwner</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/'>Int</a>?</code></code> The unix owner of a file as a UID (Requires `includeUnixInfo`)
</summary>





</details>

<details>
<summary>
<code>unixGroup</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/'>Int</a>?</code></code> The unix group of a file as a GID (Requires `includeUnixInfo`)
</summary>





</details>

<details>
<summary>
<code>metadata</code>: <code><code><a href='#filemetadatahistory'>FileMetadataHistory</a>?</code></code> User-defined metadata for this file. See `FileMetadataTemplate` for details.
</summary>





</details>

<details>
<summary>
<code>synced</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code> If the file is added to synchronization or not
</summary>





</details>

<details>
<summary>
<code>resolvedSupport</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.accounting.api.providers.ResolvedSupport.md'>ResolvedSupport</a>&lt;<a href='/docs/reference/dk.sdu.cloud.accounting.api.Product.Storage.md'>Product.Storage</a>, <a href='/docs/reference/dk.sdu.cloud.file.orchestrator.api.FSSupport.md'>FSSupport</a>&gt;?</code></code>
</summary>





</details>

<details>
<summary>
<code>resolvedProduct</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.accounting.api.Product.Storage.md'>Product.Storage</a>?</code></code> The resolved product referenced by `product`.
</summary>



This attribute is not included by default unless `includeProduct` is specified.


</details>



</details>



---

### `FileIconHint`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_A hint to clients about which icon should be used in user-interfaces when representing a `UFile`_

```kotlin
enum class FileIconHint {
    DIRECTORY_STAR,
    DIRECTORY_SHARES,
    DIRECTORY_TRASH,
    DIRECTORY_JOBS,
}
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>DIRECTORY_STAR</code> A directory containing 'starred' items
</summary>





</details>

<details>
<summary>
<code>DIRECTORY_SHARES</code> A directory which contains items that are shared between users and projects
</summary>





</details>

<details>
<summary>
<code>DIRECTORY_TRASH</code> A directory which contains items that have been 'trashed'
</summary>





</details>

<details>
<summary>
<code>DIRECTORY_JOBS</code> A directory which contains items that are related to job results
</summary>





</details>



</details>



---

### `FileType`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_The type of a `UFile`_

```kotlin
enum class FileType {
    FILE,
    DIRECTORY,
    SOFT_LINK,
    DANGLING_METADATA,
}
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>FILE</code> A regular file
</summary>





</details>

<details>
<summary>
<code>DIRECTORY</code> A directory of files used for organization
</summary>





</details>

<details>
<summary>
<code>SOFT_LINK</code> A soft symbolic link which points to a different file path
</summary>





</details>

<details>
<summary>
<code>DANGLING_METADATA</code> Indicates that there used to be a file with metadata here, but the file no longer exists
</summary>





</details>



</details>



---

### `UFileSpecification`

[![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


__

```kotlin
data class UFileSpecification(
    val collection: String,
    val product: ProductReference,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>collection</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>product</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.accounting.api.ProductReference.md'>ProductReference</a></code></code> A reference to the product which backs this `Resource`
</summary>





</details>



</details>



---

### `FileMetadataOrDeleted.Deleted`

[![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_Indicates that the metadata document has been deleted is no longer in use_

```kotlin
data class Deleted(
    val id: String,
    val changeLog: String,
    val createdAt: Long,
    val createdBy: String,
    val status: FileMetadataDocument.Status,
    val type: String /* "deleted" */,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>id</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>changeLog</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code> Reason for this change
</summary>





</details>

<details>
<summary>
<code>createdAt</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a></code></code> Timestamp indicating when this change was made
</summary>





</details>

<details>
<summary>
<code>createdBy</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code> A reference to the user who made this change
</summary>





</details>

<details>
<summary>
<code>status</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.file.orchestrator.api.FileMetadataDocument.Status.md'>FileMetadataDocument.Status</a></code></code>
</summary>





</details>

<details>
<summary>
<code>type</code>: <code><code>String /* "deleted" */</code></code> The type discriminator
</summary>

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)




</details>



</details>



---

### `FileMetadataHistory`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class FileMetadataHistory(
    val templates: JsonObject,
    val metadata: JsonObject,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>templates</code>: <code><code><a href='https://kotlin.github.io/kotlinx.serialization/kotlinx-serialization-json/kotlinx-serialization-json/kotlinx.serialization.json/-json-object/index.html'>JsonObject</a></code></code>
</summary>





</details>

<details>
<summary>
<code>metadata</code>: <code><code><a href='https://kotlin.github.io/kotlinx.serialization/kotlinx-serialization-json/kotlinx-serialization-json/kotlinx.serialization.json/-json-object/index.html'>JsonObject</a></code></code>
</summary>





</details>



</details>



---

### `FileMetadataOrDeleted`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
sealed class FileMetadataOrDeleted {
    abstract val createdAt: Long
    abstract val createdBy: String
    abstract val id: String
    abstract val status: FileMetadataDocument.Status

    class Deleted : FileMetadataOrDeleted()
    class FileMetadataDocument : FileMetadataOrDeleted()
}
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>createdAt</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a></code></code>
</summary>





</details>

<details>
<summary>
<code>createdBy</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>id</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>status</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.file.orchestrator.api.FileMetadataDocument.Status.md'>FileMetadataDocument.Status</a></code></code>
</summary>





</details>



</details>



---

### `FindByPath`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class FindByPath(
    val id: String,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>id</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>



</details>



---

### `LongRunningTask`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
sealed class LongRunningTask {
    class Complete : LongRunningTask()
    class ContinuesInBackground : LongRunningTask()
}
```



---

### `LongRunningTask.Complete`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class Complete(
    val type: String /* "complete" */,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>type</code>: <code><code>String /* "complete" */</code></code> The type discriminator
</summary>

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)




</details>



</details>



---

### `LongRunningTask.ContinuesInBackground`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class ContinuesInBackground(
    val taskId: String,
    val type: String /* "continues_in_background" */,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>taskId</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>type</code>: <code><code>String /* "continues_in_background" */</code></code> The type discriminator
</summary>

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)




</details>



</details>



---

### `UFileIncludeFlags`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class UFileIncludeFlags(
    val includeOthers: Boolean?,
    val includeUpdates: Boolean?,
    val includeSupport: Boolean?,
    val includeProduct: Boolean?,
    val includePermissions: Boolean?,
    val includeTimestamps: Boolean?,
    val includeSizes: Boolean?,
    val includeUnixInfo: Boolean?,
    val includeMetadata: Boolean?,
    val includeSyncStatus: Boolean?,
    val filterCreatedBy: String?,
    val filterCreatedAfter: Long?,
    val filterCreatedBefore: Long?,
    val filterProvider: String?,
    val filterProductId: String?,
    val filterProductCategory: String?,
    val filterProviderIds: String?,
    val filterByFileExtension: String?,
    val path: String?,
    val allowUnsupportedInclude: Boolean?,
    val filterHiddenFiles: Boolean?,
    val filterIds: String?,
    val hideProductId: String?,
    val hideProductCategory: String?,
    val hideProvider: String?,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>includeOthers</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>includeUpdates</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>includeSupport</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>includeProduct</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code> Includes `specification.resolvedProduct`
</summary>





</details>

<details>
<summary>
<code>includePermissions</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>includeTimestamps</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>includeSizes</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>includeUnixInfo</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>includeMetadata</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>includeSyncStatus</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>filterCreatedBy</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>filterCreatedAfter</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>filterCreatedBefore</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>filterProvider</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>filterProductId</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>filterProductCategory</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>filterProviderIds</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code> Filters by the provider ID. The value is comma-separated.
</summary>





</details>

<details>
<summary>
<code>filterByFileExtension</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>path</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code> Path filter
</summary>





</details>

<details>
<summary>
<code>allowUnsupportedInclude</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code> Determines if the request should succeed if the underlying system does not support this data.
</summary>



This value is `true` by default


</details>

<details>
<summary>
<code>filterHiddenFiles</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code> Determines if dot files should be hidden from the result-set
</summary>





</details>

<details>
<summary>
<code>filterIds</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code> Filters by the resource ID. The value is comma-separated.
</summary>





</details>

<details>
<summary>
<code>hideProductId</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>hideProductCategory</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>hideProvider</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>



</details>



---

### `UploadProtocol`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
enum class UploadProtocol {
    CHUNKED,
}
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>CHUNKED</code>
</summary>





</details>



</details>



---

### `WriteConflictPolicy`

[![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_A policy for how UCloud should handle potential naming conflicts for certain operations (e.g. copy)_

```kotlin
enum class WriteConflictPolicy {
    RENAME,
    REJECT,
    REPLACE,
    MERGE_RENAME,
}
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>RENAME</code> UCloud should handle the conflict by renaming the file
</summary>





</details>

<details>
<summary>
<code>REJECT</code> UCloud should fail the request entirely
</summary>





</details>

<details>
<summary>
<code>REPLACE</code> UCloud should replace the existing file
</summary>





</details>

<details>
<summary>
<code>MERGE_RENAME</code> "Attempt to merge the results
</summary>



This will result in the merging of folders. Concretely this means that _directory_ conflicts will be resolved by
re-using the existing directory. If there any file conflicts in the operation then this will act identical to `RENAME`.

Note: This mode is not supported for all operations.
"


</details>



</details>



---

### `FilesCopyRequestItem`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class FilesCopyRequestItem(
    val oldId: String,
    val newId: String,
    val conflictPolicy: WriteConflictPolicy,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>oldId</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>newId</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>conflictPolicy</code>: <code><code><a href='#writeconflictpolicy'>WriteConflictPolicy</a></code></code>
</summary>





</details>



</details>



---

### `FilesCreateDownloadRequestItem`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class FilesCreateDownloadRequestItem(
    val id: String,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>id</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>



</details>



---

### `FilesCreateFolderRequestItem`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class FilesCreateFolderRequestItem(
    val id: String,
    val conflictPolicy: WriteConflictPolicy,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>id</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>conflictPolicy</code>: <code><code><a href='#writeconflictpolicy'>WriteConflictPolicy</a></code></code>
</summary>





</details>



</details>



---

### `FilesCreateUploadRequestItem`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class FilesCreateUploadRequestItem(
    val id: String,
    val supportedProtocols: List<UploadProtocol>,
    val conflictPolicy: WriteConflictPolicy,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>id</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>supportedProtocols</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='#uploadprotocol'>UploadProtocol</a>&gt;</code></code>
</summary>





</details>

<details>
<summary>
<code>conflictPolicy</code>: <code><code><a href='#writeconflictpolicy'>WriteConflictPolicy</a></code></code>
</summary>





</details>



</details>



---

### `FilesMoveRequestItem`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class FilesMoveRequestItem(
    val oldId: String,
    val newId: String,
    val conflictPolicy: WriteConflictPolicy,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>oldId</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>newId</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>conflictPolicy</code>: <code><code><a href='#writeconflictpolicy'>WriteConflictPolicy</a></code></code>
</summary>





</details>



</details>



---

### `FilesCreateDownloadResponseItem`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class FilesCreateDownloadResponseItem(
    val endpoint: String,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>endpoint</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>



</details>



---

### `FilesCreateUploadResponseItem`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class FilesCreateUploadResponseItem(
    val endpoint: String,
    val protocol: UploadProtocol,
    val token: String,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>endpoint</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>protocol</code>: <code><code><a href='#uploadprotocol'>UploadProtocol</a></code></code>
</summary>





</details>

<details>
<summary>
<code>token</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>



</details>



---

