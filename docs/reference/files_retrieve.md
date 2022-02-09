[UCloud Developer Guide](/docs/developer-guide/README.md) / [Orchestration of Resources](/docs/developer-guide/orchestration/README.md) / [Storage](/docs/developer-guide/orchestration/storage/README.md) / [Files](/docs/developer-guide/orchestration/storage/files.md)

# Example: Retrieving a single file

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


